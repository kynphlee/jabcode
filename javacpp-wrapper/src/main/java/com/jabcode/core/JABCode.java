package com.jabcode.core;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import com.jabcode.OptimizedJABCode;

/**
 * JABCode - Just Another Barcode
 * 
 * This class provides methods for generating and decoding JABCode barcodes.
 * JABCode is a colorful 2D matrix barcode, similar to QR codes but with support
 * for multiple colors, which allows it to store more information in the same space.
 */
public class JABCode {
    
    /**
     * Color modes supported by JABCode
     */
    public enum ColorMode {
        /** 2 colors (black and white) */
        BINARY(2),
        /** 4 colors */
        QUATERNARY(4),
        /** 8 colors (default) */
        OCTAL(8),
        /** 16 colors */
        HEXADECIMAL(16),
        /** 32 colors */
        MODE_32(32),
        /** 64 colors */
        MODE_64(64),
        /** 128 colors */
        MODE_128(128),
        /** 256 colors */
        MODE_256(256);
        
        private final int colorCount;
        
        ColorMode(int colorCount) {
            this.colorCount = colorCount;
        }
        
        /**
         * Get the number of colors for this color mode
         * @return the number of colors
         */
        public int getColorCount() {
            return colorCount;
        }
        
        /**
         * Get the ColorMode enum value for a given color count
         * @param colorCount the number of colors
         * @return the corresponding ColorMode
         * @throws IllegalArgumentException if the color count is not supported
         */
        public static ColorMode fromColorCount(int colorCount) {
            for (ColorMode mode : values()) {
                if (mode.colorCount == colorCount) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unsupported color count: " + colorCount);
        }
    }
    
    /**
     * Builder for configuring JABCode generation
     */
    public static class Builder {
        private byte[] data;
        private ColorMode colorMode = ColorMode.OCTAL;
        private int symbolCount = 1;
        private int eccLevel = 3; // Default ECC level
        
        /**
         * Set the data to encode
         * @param data the data to encode
         * @return this builder
         */
        public Builder withData(byte[] data) {
            this.data = data;
            return this;
        }
        
        /**
         * Set the data to encode
         * @param data the data to encode as a string
         * @return this builder
         */
        public Builder withData(String data) {
            this.data = data.getBytes();
            return this;
        }
        
        /**
         * Set the color mode
         * @param colorMode the color mode to use
         * @return this builder
         */
        public Builder withColorMode(ColorMode colorMode) {
            this.colorMode = colorMode;
            return this;
        }
        
        /**
         * Set the number of symbols
         * @param symbolCount the number of symbols to use
         * @return this builder
         */
        public Builder withSymbolCount(int symbolCount) {
            this.symbolCount = symbolCount;
            return this;
        }
        
        /**
         * Set the error correction level
         * @param eccLevel the error correction level to use (0-10)
         * @return this builder
         */
        public Builder withEccLevel(int eccLevel) {
            this.eccLevel = eccLevel;
            return this;
        }
        
        /**
         * Build the JABCode
         * @return the encoded JABCode as a BufferedImage
         * @throws JABCodeException if encoding fails
         */
        public BufferedImage build() {
            return JABCode.encode(data, colorMode, symbolCount, eccLevel);
        }
        
        /**
         * Build the JABCode and save it to a file
         * @param file the file to save to
         * @throws JABCodeException if encoding or saving fails
         * @throws IOException if saving fails
         */
        public void buildToFile(File file) throws IOException {
            BufferedImage image = build();
            JABCode.save(image, file);
        }
        
        /**
         * Build the JABCode and save it to a file
         * @param filePath the path of the file to save to
         * @throws JABCodeException if encoding or saving fails
         * @throws IOException if saving fails
         */
        public void buildToFile(String filePath) throws IOException {
            buildToFile(new File(filePath));
        }
    }
    
    /**
     * Create a new JABCode builder
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Encode data into a JABCode
     * @param data the data to encode
     * @param colorMode the color mode to use
     * @param symbolCount the number of symbols to use
     * @param eccLevel the error correction level to use
     * @return the encoded JABCode as a BufferedImage
     * @throws JABCodeException if encoding fails
     */
    public static BufferedImage encode(byte[] data, ColorMode colorMode, int symbolCount, int eccLevel) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        
        if (symbolCount < 1 || symbolCount > 16) { // MAX_SYMBOL_NUMBER is 16
            throw new IllegalArgumentException("Symbol count must be between 1 and 16");
        }
        
        if (eccLevel < 0 || eccLevel > 10) {
            throw new IllegalArgumentException("ECC level must be between 0 and 10");
        }
        
        try {
            // Convert ColorMode to OptimizedJABCode.ColorMode
            OptimizedJABCode.ColorMode optimizedColorMode = 
                OptimizedJABCode.ColorMode.fromColorCount(colorMode.getColorCount());
            
            // Generate the JABCode using OptimizedJABCode
            BufferedImage image = OptimizedJABCode.builder()
                .withData(data)
                .withColorMode(optimizedColorMode)
                .withSymbolCount(symbolCount)
                .withEccLevel(eccLevel)
                .build();
            
            if (image == null) {
                throw new JABCodeException("Failed to generate JABCode");
            }
            
            return image;
        } catch (Exception e) {
            throw new JABCodeException("Failed to generate JABCode", e);
        }
    }
    
    /**
     * Encode data into a JABCode with default settings
     * @param data the data to encode
     * @return the encoded JABCode as a BufferedImage
     * @throws JABCodeException if encoding fails
     */
    public static BufferedImage encode(byte[] data) {
        return encode(data, ColorMode.OCTAL, 1, 3); // Default ECC level is 3
    }
    
    /**
     * Encode data into a JABCode with default settings
     * @param data the data to encode as a string
     * @return the encoded JABCode as a BufferedImage
     * @throws JABCodeException if encoding fails
     */
    public static BufferedImage encode(String data) {
        return encode(data.getBytes(), ColorMode.OCTAL, 1, 3); // Default ECC level is 3
    }
    
    
    /**
     * Decode a JABCode
     * @param image the image containing the JABCode
     * @return the decoded data
     * @throws JABCodeException if decoding fails
     */
    public static byte[] decode(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null");
        }
        
        try {
            // Use OptimizedJABCode for decoding
            return OptimizedJABCode.decode(image);
        } catch (Exception e) {
            String msg = e.getMessage();
            throw new JABCodeException("Failed to decode JABCode" + (msg != null ? ": " + msg : ""), e);
        }
    }
    
    /**
     * Decode a JABCode from a file
     * @param file the file containing the JABCode
     * @return the decoded data
     * @throws JABCodeException if decoding fails
     * @throws IOException if reading the file fails
     */
    public static byte[] decode(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        try {
            return OptimizedJABCode.decode(file);
        } catch (Exception e) {
            String msg = e.getMessage();
            throw new JABCodeException("Failed to decode JABCode" + (msg != null ? ": " + msg : ""), e);
        }
    }
    
    /**
     * Decode a JABCode from a file
     * @param filePath the path of the file containing the JABCode
     * @return the decoded data
     * @throws JABCodeException if decoding fails
     * @throws IOException if reading the file fails
     */
    public static byte[] decode(String filePath) throws IOException {
        return decode(new File(filePath));
    }
    
    /**
     * Decode a JABCode and return the result as a string
     * @param image the image containing the JABCode
     * @return the decoded data as a string
     * @throws JABCodeException if decoding fails
     */
    public static String decodeToString(BufferedImage image) {
        return new String(decode(image));
    }
    
    /**
     * Decode a JABCode from a file and return the result as a string
     * @param file the file containing the JABCode
     * @return the decoded data as a string
     * @throws JABCodeException if decoding fails
     * @throws IOException if reading the file fails
     */
    public static String decodeToString(File file) throws IOException {
        return new String(decode(file));
    }
    
    /**
     * Decode a JABCode from a file and return the result as a string
     * @param filePath the path of the file containing the JABCode
     * @return the decoded data as a string
     * @throws JABCodeException if decoding fails
     * @throws IOException if reading the file fails
     */
    public static String decodeToString(String filePath) throws IOException {
        return new String(decode(filePath));
    }
    
    /**
     * Decode a JABCode with extended information
     * @param image the image containing the JABCode
     * @param maxSymbolCount the maximum number of symbols to decode
     * @return the decoded result
     * @throws JABCodeException if decoding fails
     */
    public static DecodedResult decodeEx(BufferedImage image, int maxSymbolCount) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null");
        }
        
        if (maxSymbolCount < 1 || maxSymbolCount > 16) { // MAX_SYMBOL_NUMBER is 16
            throw new IllegalArgumentException("Max symbol count must be between 1 and 16");
        }
        
        try {
            // Use OptimizedJABCode for extended decoding
            OptimizedJABCode.DecodedResult optimizedResult = OptimizedJABCode.decodeEx(image, maxSymbolCount);
            
            // Convert to our DecodedResult
            DecodedResult result = new DecodedResult();
            result.data = optimizedResult.getData();
            
            return result;
        } catch (Exception e) {
            String msg = e.getMessage();
            throw new JABCodeException("Failed to decode JABCode with extended information" + (msg != null ? ": " + msg : ""), e);
        }
    }
    
    /**
     * Save a JABCode to a file
     * @param image the JABCode image
     * @param file the file to save to
     * @throws IOException if saving fails
     */
    public static void save(BufferedImage image, File file) throws IOException {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null");
        }
        
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        
        // Get file extension
        String name = file.getName();
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        
        // Save image
        if (!ImageIO.write(image, extension, file)) {
            throw new IOException("Failed to save image: no appropriate writer found for " + extension);
        }
    }
    
    /**
     * Save a JABCode to a file
     * @param image the JABCode image
     * @param filePath the path of the file to save to
     * @throws IOException if saving fails
     */
    public static void save(BufferedImage image, String filePath) throws IOException {
        save(image, new File(filePath));
    }
    
    /**
     * Result of decoding a JABCode with extended information
     */
    public static class DecodedResult {
        private byte[] data;
        
        /**
         * Get the decoded data
         * @return the decoded data
         */
        public byte[] getData() {
            return data;
        }
        
        /**
         * Get the decoded data as a string
         * @return the decoded data as a string
         */
        public String getDataAsString() {
            return new String(data);
        }
    }
    
    /**
     * Exception thrown when JABCode operations fail
     */
    public static class JABCodeException extends RuntimeException {
        /**
         * Create a new JABCodeException
         * @param message the error message
         */
        public JABCodeException(String message) {
            super(message);
        }
        
        /**
         * Create a new JABCodeException
         * @param message the error message
         * @param cause the cause of the error
         */
        public JABCodeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
