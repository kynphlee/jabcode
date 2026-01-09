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
     * Decode JABCode from image byte array.
     * Note: Direct byte array decoding requires creating a temporary file.
     * For better performance, use decodeFromFile() directly.
     * 
     * @param imageData Raw image data (PNG, JPG, etc.)
     * @return Decoded data as string, or null if decoding fails
     */
    public String decode(byte[] imageData) {
        DecodedResult result = decodeEx(imageData);
        return result.isSuccess() ? result.getData() : null;
    }
    
    /**
     * Decode JABCode with extended information.
     * Note: Not yet implemented - requires bitmap creation from bytes.
     * Use decodeFromFile() instead.
     * 
     * @param imageData Raw image data
     * @return DecodedResult with data and metadata
     */
    public DecodedResult decodeEx(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            throw new IllegalArgumentException("Image data cannot be null or empty");
        }
        
        // TODO: Implement bitmap creation from byte array
        // This would require either:
        // 1. Writing to temp file and using readImage()
        // 2. Using libpng directly to create jab_bitmap from memory
        // For now, use decodeFromFile() instead
        
        throw new UnsupportedOperationException(
            "Decoding from byte array not yet implemented. " +
            "Use decodeFromFile(Path) instead."
        );
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
