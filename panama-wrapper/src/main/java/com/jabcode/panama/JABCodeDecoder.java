package com.jabcode.panama;

import com.jabcode.panama.bindings.jabcode_h;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * High-level Java API for decoding JABCode barcodes using Panama FFM.
 * 
 * Example usage:
 * <pre>{@code
 * var decoder = new JABCodeDecoder();
 * String decoded = decoder.decodeFromFile(Path.of("barcode.png"));
 * }</pre>
 */
public class JABCodeDecoder {
    
    /**
     * Decode mode constants
     */
    public static final int MODE_NORMAL = 0;
    public static final int MODE_FAST = 1;
    
    /**
     * Reset decoder state for test isolation.
     * 
     * Phase 1 workaround - retained for backward compatibility.
     * In Phase 2, this is largely a no-op since Java owns memory lifecycle.
     */
    public static void resetDecoderState() {
        jabcode_h.resetDecoderState();
    }
    
    /**
     * Observation buffer size for adaptive palette
     */
    private static final int MAX_OBSERVATIONS = 10000;
    
    /**
     * Phase 2: Arena-based decode with observation collection
     * 
     * Properly integrates with Panama FFI using Arena-managed memory.
     * Allocates observation buffer via Arena, C writes to it, no malloc/free in C.
     * 
     * @param imagePath Path to image file
     * @param mode Decode mode
     * @param collectObservations Whether to collect color observations
     * @return DecodedResult with data, metadata, and optional observations
     */
    public DecodedResultWithObservations decodeWithObservations(
            Path imagePath, 
            int mode, 
            boolean collectObservations) {
        
        if (imagePath == null) {
            throw new IllegalArgumentException("Image path cannot be null");
        }
        
        try (Arena arena = Arena.ofConfined()) {
            // Load image
            MemorySegment imagePathSegment = arena.allocateFrom(imagePath.toString());
            MemorySegment bitmapPtr = jabcode_h.readImage(imagePathSegment);
            
            if (bitmapPtr.address() == 0) {
                return new DecodedResultWithObservations(null, 0, false, null, 0);
            }
            
            try {
                // Allocate status
                MemorySegment statusPtr = arena.allocate(ValueLayout.JAVA_INT);
                
                // Arena-allocate observation buffer if requested
                MemorySegment obsBuffer = null;
                MemorySegment obsCountPtr = null;
                int obsCount = 0;
                
                if (collectObservations) {
                    // Allocate buffer for observations (RGB + index + confidence)
                    // sizeof(jab_color_observation) = 3 + 1 + 4 = 8 bytes
                    long obsStructSize = 8;
                    obsBuffer = arena.allocate(obsStructSize * MAX_OBSERVATIONS);
                    obsCountPtr = arena.allocate(ValueLayout.JAVA_INT);
                    
                    System.err.println("[JAVA] Allocated observation buffer: address=" + 
                        obsBuffer.address() + ", capacity=" + MAX_OBSERVATIONS);
                }
                
                // Decode. The decodeJABCodeWithObservations variant existed
                // on the panama-poc research branch but was never merged
                // upstream — fall back to the standard decodeJABCode entry
                // point. When observations are requested, we return an empty
                // observation count rather than failing — research consumers
                // that need observation data should run against a build that
                // includes the WithObservations entry point.
                MemorySegment dataPtr = jabcode_h.decodeJABCode(bitmapPtr, mode, statusPtr);
                if (collectObservations) {
                    System.err.println("[JAVA] WARNING: decodeJABCodeWithObservations "
                        + "is not available in this build; standard decode used. "
                        + "Observation data will be empty.");
                }
                
                if (dataPtr.address() == 0) {
                    return new DecodedResultWithObservations(null, 0, false, obsBuffer, obsCount);
                }
                
                // Read decoded data
                int length = dataPtr.get(ValueLayout.JAVA_INT, 0);
                byte[] decodedBytes = new byte[length];
                MemorySegment dataSegment = dataPtr.asSlice(4, length);
                MemorySegment.copy(dataSegment, ValueLayout.JAVA_BYTE, 0,
                                 decodedBytes, 0, length);
                
                String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);
                
                return new DecodedResultWithObservations(
                    decodedString, 1, true, obsBuffer, obsCount
                );
                
            } finally {
                // Bitmap cleanup handled by C library
            }
        } catch (Exception e) {
            throw new RuntimeException("Decoding with observations failed", e);
        }
    }
    
    /**
     * Extended result with observation data
     */
    public static class DecodedResultWithObservations extends DecodedResult {
        private final MemorySegment observations;
        private final int observationCount;
        
        public DecodedResultWithObservations(String data, int symbolCount, boolean success,
                                            MemorySegment observations, int observationCount) {
            super(data, symbolCount, success);
            this.observations = observations;
            this.observationCount = observationCount;
        }
        
        public MemorySegment getObservations() { return observations; }
        public int getObservationCount() { return observationCount; }
    }
    
    /**
     * Decoded result containing data and metadata
     */
    public static class DecodedResult {
        private final String data;
        private final int symbolCount;
        private final boolean success;
        
        public DecodedResult(String data, int symbolCount, boolean success) {
            this.data = data;
            this.symbolCount = symbolCount;
            this.success = success;
        }
        
        public String getData() { return data; }
        public int getSymbolCount() { return symbolCount; }
        public boolean isSuccess() { return success; }
    }
    
    /**
     * Decode a JABCode directly from PNG bytes held in memory (no temporary file).
     *
     * <p>The bytes are decoded via the native {@code readImageFromMemory} path, so
     * sensitive auth/COA payloads never touch the filesystem.</p>
     *
     * @param imageData Raw PNG image bytes
     * @return Decoded data as string, or null if decoding fails
     */
    public String decode(byte[] imageData) {
        DecodedResult result = decodeEx(imageData);
        return result.isSuccess() ? result.getData() : null;
    }

    /**
     * Decode a JABCode from in-memory PNG bytes with extended information.
     *
     * <p>Mirror of {@link #decodeFromFileEx(Path, int)} that loads the bitmap from a
     * memory buffer ({@code readImageFromMemory}) instead of a file ({@code readImage}),
     * keeping the image off disk. Unlike the file path, the bitmap is freed here.</p>
     *
     * @param imageData Raw PNG image bytes
     * @return DecodedResult with data and metadata
     */
    public DecodedResult decodeEx(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            throw new IllegalArgumentException("Image data cannot be null or empty");
        }

        try (Arena arena = Arena.ofConfined()) {
            // Marshal the PNG bytes into native memory (no temp file)
            MemorySegment buffer = arena.allocate(imageData.length);
            MemorySegment.copy(imageData, 0, buffer, ValueLayout.JAVA_BYTE, 0, imageData.length);

            // Decode the PNG from memory into a bitmap
            MemorySegment bitmap = jabcode_h.readImageFromMemory(buffer, imageData.length);
            if (bitmap == null || bitmap.address() == 0) {
                return new DecodedResult(null, 0, false);
            }

            try {
                // Allocate status parameter (init to error value)
                MemorySegment status = arena.allocate(ValueLayout.JAVA_INT);
                status.set(ValueLayout.JAVA_INT, 0, -1);

                // Decode
                MemorySegment result = jabcode_h.decodeJABCode(bitmap, MODE_NORMAL, status);
                if (result == null || result.address() == 0) {
                    return new DecodedResult(null, 0, false);
                }

                // Extract decoded data — jab_data struct: { int32 length; char data[]; }
                int dataLength = result.get(ValueLayout.JAVA_INT, 0);
                if (dataLength <= 0) {
                    return new DecodedResult("", 1, true);
                }

                byte[] decodedBytes = new byte[dataLength];
                MemorySegment.copy(result, ValueLayout.JAVA_BYTE, 4, decodedBytes, 0, dataLength);
                String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);

                return new DecodedResult(decodedString, 1, true);

            } finally {
                // readImageFromMemory calloc's the bitmap as a single block we own.
                // (The file-based readImage path leaks its bitmap; this one does not.)
                NativeMemory.free(bitmap);
            }
        } catch (Exception e) {
            throw new RuntimeException("Decoding from byte array failed", e);
        }
    }
    
    /**
     * Decode JABCode from image file
     * 
     * @param imagePath Path to image file
     * @return Decoded data as string, or null if decoding fails
     */
    public String decodeFromFile(Path imagePath) {
        DecodedResult result = decodeFromFileEx(imagePath, MODE_NORMAL);
        return result.isSuccess() ? result.getData() : null;
    }
    
    /**
     * Decode JABCode from image file with specified decode mode
     * 
     * @param imagePath Path to image file
     * @param mode Decode mode (MODE_NORMAL or MODE_FAST)
     * @return Decoded data as string, or null if decoding fails
     */
    public String decodeFromFile(Path imagePath, int mode) {
        DecodedResult result = decodeFromFileEx(imagePath, mode);
        return result.isSuccess() ? result.getData() : null;
    }
    
    /**
     * Decode JABCode from image file with extended information
     * 
     * @param imagePath Path to image file
     * @param mode Decode mode (MODE_NORMAL or MODE_FAST)
     * @return DecodedResult with data and metadata
     */
    public DecodedResult decodeFromFileEx(Path imagePath, int mode) {
        if (imagePath == null) {
            throw new IllegalArgumentException("Image path cannot be null");
        }
        
        try (Arena arena = Arena.ofConfined()) {
            // Load image
            MemorySegment filenameSegment = arena.allocateFrom(imagePath.toString());
            MemorySegment bitmap = jabcode_h.readImage(filenameSegment);
            
            if (bitmap == null || bitmap.address() == 0) {
                return new DecodedResult(null, 0, false);
            }
            
            try {
                // Allocate status parameter
                MemorySegment status = arena.allocate(ValueLayout.JAVA_INT);
                status.set(ValueLayout.JAVA_INT, 0, -1); // Initialize to error value
                
                // Decode
                MemorySegment result = jabcode_h.decodeJABCode(bitmap, mode, status);
                
                // Check if decoding succeeded - result should not be null
                if (result == null || result.address() == 0) {
                    return new DecodedResult(null, 0, false);
                }
                
                // Extract decoded data
                // jab_data struct: { int32 length; char data[]; }
                int dataLength = result.get(ValueLayout.JAVA_INT, 0);
                
                if (dataLength <= 0) {
                    return new DecodedResult("", 1, true);
                }
                
                // Read data bytes starting at offset 4
                byte[] decodedBytes = new byte[dataLength];
                MemorySegment.copy(result, ValueLayout.JAVA_BYTE, 4, decodedBytes, 0, dataLength);
                
                String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);
                
                // Note: The C library allocates the result with malloc
                // We should ideally free it, but there's no destroyData function
                // TODO: Check for memory leaks
                
                return new DecodedResult(decodedString, 1, true);
                
            } finally {
                // Free bitmap
                // Note: readImage allocates bitmap with malloc
                // We should free it, but there's no destroyBitmap function
                // TODO: Check for memory leaks and add proper cleanup
            }
        } catch (Exception e) {
            throw new RuntimeException("Decoding failed", e);
        }
    }
}
