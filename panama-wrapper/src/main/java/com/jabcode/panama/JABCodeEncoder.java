package com.jabcode.panama;

import com.jabcode.panama.bindings.jabcode_h;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

/**
 * High-level Java API for encoding JABCode barcodes using Panama FFM.
 * 
 * This class provides a clean, type-safe interface to the JABCode C library
 * without requiring any C++ wrapper code.
 * 
 * Example usage:
 * <pre>{@code
 * var encoder = new JABCodeEncoder();
 * byte[] encoded = encoder.encode("Hello World", 8, 5);
 * }</pre>
 */
public class JABCodeEncoder {
    
    /**
     * Configuration for JABCode encoding
     */
    public static class Config {
        private final int colorNumber;
        private final int eccLevel;
        private final int symbolNumber;
        private final int moduleSize;
        private final int masterSymbolWidth;
        private final int masterSymbolHeight;
        
        private Config(Builder builder) {
            this.colorNumber = builder.colorNumber;
            this.eccLevel = builder.eccLevel;
            this.symbolNumber = builder.symbolNumber;
            this.moduleSize = builder.moduleSize;
            this.masterSymbolWidth = builder.masterSymbolWidth;
            this.masterSymbolHeight = builder.masterSymbolHeight;
        }
        
        public int getColorNumber() { return colorNumber; }
        public int getEccLevel() { return eccLevel; }
        public int getSymbolNumber() { return symbolNumber; }
        public int getModuleSize() { return moduleSize; }
        public int getMasterSymbolWidth() { return masterSymbolWidth; }
        public int getMasterSymbolHeight() { return masterSymbolHeight; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static Config defaults() {
            return builder().build();
        }
        
        public static class Builder {
            private int colorNumber = 8;           // 8-color mode (default)
            private int eccLevel = 5;              // ECC level 5 (medium)
            private int symbolNumber = 1;          // Single symbol
            private int moduleSize = 12;           // 12 pixel modules
            private int masterSymbolWidth = 0;     // Auto width
            private int masterSymbolHeight = 0;    // Auto height
            
            public Builder colorNumber(int colorNumber) {
                // Allowed per ISO/IEC 23634 Annex G: 4,8,16,32,64,128,256
                switch (colorNumber) {
                    case 4, 8, 16, 32, 64, 128, 256 -> this.colorNumber = colorNumber;
                    default -> throw new IllegalArgumentException(
                        "Color number must be one of 4,8,16,32,64,128,256");
                }
                return this;
            }
            
            public Builder eccLevel(int eccLevel) {
                if (eccLevel < 0 || eccLevel > 10) {
                    throw new IllegalArgumentException("ECC level must be between 0 and 10");
                }
                this.eccLevel = eccLevel;
                return this;
            }
            
            public Builder symbolNumber(int symbolNumber) {
                if (symbolNumber < 1 || symbolNumber > 61) {
                    throw new IllegalArgumentException("Symbol number must be between 1 and 61");
                }
                this.symbolNumber = symbolNumber;
                return this;
            }
            
            public Builder moduleSize(int moduleSize) {
                if (moduleSize < 1) {
                    throw new IllegalArgumentException("Module size must be positive");
                }
                this.moduleSize = moduleSize;
                return this;
            }
            
            public Builder masterSymbolWidth(int width) {
                this.masterSymbolWidth = width;
                return this;
            }
            
            public Builder masterSymbolHeight(int height) {
                this.masterSymbolHeight = height;
                return this;
            }
            
            public Config build() {
                return new Config(this);
            }
        }
    }
    
    /**
     * Encode data into JABCode format with default settings (8-color, ECC level 5)
     * 
     * @param data The data to encode
     * @return Encoded bitmap data as byte array, or null if encoding fails
     */
    public byte[] encode(String data) {
        return encode(data, 8, 5);
    }
    
    /**
     * Encode data into JABCode format with specified parameters
     * 
     * @param data The data to encode
     * @param colorNumber Number of colors (4,8,16,32,64,128,256)
     * @param eccLevel Error correction level (0-10)
     * @return Encoded bitmap data as byte array, or null if encoding fails
     */
    public byte[] encode(String data, int colorNumber, int eccLevel) {
        var config = Config.builder()
            .colorNumber(colorNumber)
            .eccLevel(eccLevel)
            .build();
        return encodeWithConfig(data, config);
    }
    
    /**
     * Encode data with full configuration control
     * 
     * @param data The data to encode
     * @param config Encoding configuration
     * @return Encoded bitmap data as byte array, or null if encoding fails
     */
    public byte[] encodeWithConfig(String data, Config config) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        
        try (Arena arena = Arena.ofConfined()) {
            // Create encoder
            MemorySegment enc = jabcode_h.createEncode(
                config.getColorNumber(),
                config.getSymbolNumber()
            );
            
            if (enc == null || enc.address() == 0) {
                return null;
            }
            
            try {
                // Prepare jab_data structure: { int32 length; char data[]; }
                byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
                MemorySegment jabData = createJabData(arena, bytes);
                
                // Generate JABCode
                int result = jabcode_h.generateJABCode(enc, jabData);
                if (result != 1) { // JAB_SUCCESS = 1
                    return null;
                }
                
                // Bitmap extraction not yet implemented - use encodeToPNG() instead
                return null;
                
            } finally {
                jabcode_h.destroyEncode(enc);
            }
        } catch (Exception e) {
            throw new RuntimeException("Encoding failed", e);
        }
    }
    
    /**
     * Encode data and save directly to PNG file
     * 
     * @param data The data to encode
     * @param outputPath Path to output PNG file
     * @param config Encoding configuration
     * @return true if successful, false otherwise
     */
    public boolean encodeToPNG(String data, String outputPath, Config config) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        
        try (Arena arena = Arena.ofConfined()) {
            // Create encoder
            MemorySegment enc = jabcode_h.createEncode(
                config.getColorNumber(),
                config.getSymbolNumber()
            );
            
            if (enc == null || enc.address() == 0) {
                return false;
            }
            
            try {
                // Prepare data
                byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
                MemorySegment jabData = createJabData(arena, bytes);
                
                // Generate JABCode
                int result = jabcode_h.generateJABCode(enc, jabData);
                if (result != 0) {  // 0 = success, non-zero = error code
                    return false;
                }
                
                // Get bitmap from encoder (at offset 60 in jab_encode struct)
                MemorySegment bitmapPtr = getBitmapFromEncoder(enc);
                if (bitmapPtr == null || bitmapPtr.address() == 0) {
                    return false;
                }
                
                // Save to file
                MemorySegment filenameSegment = arena.allocateFrom(outputPath);
                byte saveResult = jabcode_h.saveImage(bitmapPtr, filenameSegment);
                
                return saveResult == 1; // JAB_SUCCESS = 1
                
            } finally {
                jabcode_h.destroyEncode(enc);
            }
        } catch (Exception e) {
            throw new RuntimeException("Encoding to PNG failed", e);
        }
    }
    
    /**
     * Create jab_data structure in native memory.
     * The C struct is: { int32 length; char data[]; }
     */
    private MemorySegment createJabData(Arena arena, byte[] data) {
        // Allocate: 4 bytes for length + data bytes
        long size = 4 + data.length;
        MemorySegment jabData = arena.allocate(size, 4); // 4-byte alignment
        
        // Set length field (first 4 bytes)
        jabData.set(ValueLayout.JAVA_INT, 0, data.length);
        
        // Copy data (flexible array member starts at offset 4)
        MemorySegment.copy(data, 0, jabData, ValueLayout.JAVA_BYTE, 4, data.length);
        
        return jabData;
    }
    
    /**
     * Get bitmap pointer from jab_encode struct.
     * The bitmap field is at offset 64 (on 64-bit systems with 8-byte alignment).
     */
    private MemorySegment getBitmapFromEncoder(MemorySegment enc) {
        // jab_encode layout (64-bit pointers with proper alignment):
        // int32 color_number (0)
        // int32 symbol_number (4)
        // int32 module_size (8)
        // int32 master_symbol_width (12)
        // int32 master_symbol_height (16)
        // [4 bytes padding for pointer alignment]
        // byte* palette (24, 8 bytes)
        // vector2d* symbol_versions (32, 8 bytes)
        // byte* symbol_ecc_levels (40, 8 bytes)
        // int32* symbol_positions (48, 8 bytes)
        // symbol* symbols (56, 8 bytes)
        // bitmap* bitmap (64, 8 bytes) <-- THIS
        
        long bitmapFieldOffset = 64;
        long bitmapAddress = enc.get(ValueLayout.ADDRESS, bitmapFieldOffset).address();
        
        if (bitmapAddress == 0) {
            return null;
        }
        
        return MemorySegment.ofAddress(bitmapAddress);
    }
}
