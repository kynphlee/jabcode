package com.jabcode.panama;

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
                if (colorNumber != 2 && colorNumber != 4 && colorNumber != 8) {
                    throw new IllegalArgumentException("Color number must be 2, 4, or 8");
                }
                this.colorNumber = colorNumber;
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
     * @param colorNumber Number of colors (2, 4, or 8)
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
        
        // Note: Implementation will use generated Panama bindings
        // This is a template showing the API structure
        
        // TODO: Once bindings are generated, implement using:
        // try (Arena arena = Arena.ofConfined()) {
        //     // Create encoder
        //     MemorySegment enc = createEncode(colorNumber, symbolNumber);
        //     
        //     // Prepare data
        //     byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        //     MemorySegment dataSegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
        //     
        //     // Call generateJABCode
        //     int result = generateJABCode(enc, dataSegment, bytes.length);
        //     
        //     // Extract bitmap
        //     MemorySegment bitmap = ... get bitmap from enc
        //     
        //     // Return byte array
        //     return ...
        // }
        
        throw new UnsupportedOperationException(
            "Implementation requires generated Panama bindings. " +
            "Run: ./jextract.sh to generate bindings first."
        );
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
        // TODO: Implement using generated bindings and saveImage function
        throw new UnsupportedOperationException(
            "Implementation requires generated Panama bindings. " +
            "Run: ./jextract.sh to generate bindings first."
        );
    }
}
