package com.jabcode;

import com.jabcode.core.JABCode;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Backward compatibility wrapper for the SimpleJABCode class
 * 
 * @deprecated This class is deprecated and will be removed in a future release.
 * Please use {@link com.jabcode.OptimizedJABCode} instead, which provides a more
 * robust and efficient implementation with better error handling and a more
 * flexible API. See README-MIGRATION.md for migration instructions.
 */
@Deprecated
public class SimpleJABCode {
    /**
     * Color mode: 4 colors
     * @deprecated Use {@link com.jabcode.OptimizedJABCode.ColorMode#QUATERNARY} instead
     */
    @Deprecated
    public static final int COLOR_MODE_4 = 4;
    
    /**
     * Color mode: 8 colors
     * @deprecated Use {@link com.jabcode.OptimizedJABCode.ColorMode#OCTAL} instead
     */
    @Deprecated
    public static final int COLOR_MODE_8 = 8;
    
    /**
     * Color mode: 16 colors
     * @deprecated Use {@link com.jabcode.OptimizedJABCode.ColorMode#HEXADECIMAL} instead
     */
    @Deprecated
    public static final int COLOR_MODE_16 = 16;
    
    /**
     * Color mode: 32 colors
     * @deprecated Use {@link com.jabcode.OptimizedJABCode.ColorMode#MODE_32} instead
     */
    @Deprecated
    public static final int COLOR_MODE_32 = 32;
    
    /**
     * Color mode: 64 colors
     * @deprecated Use {@link com.jabcode.OptimizedJABCode.ColorMode#MODE_64} instead
     */
    @Deprecated
    public static final int COLOR_MODE_64 = 64;
    
    /**
     * Color mode: 128 colors
     * @deprecated Use {@link com.jabcode.OptimizedJABCode.ColorMode#MODE_128} instead
     */
    @Deprecated
    public static final int COLOR_MODE_128 = 128;
    
    /**
     * Color mode: 256 colors
     * @deprecated Use {@link com.jabcode.OptimizedJABCode.ColorMode#MODE_256} instead
     */
    @Deprecated
    public static final int COLOR_MODE_256 = 256;
    
    /**
     * Generate a JABCode with default settings (8 colors)
     * 
     * @param text the text to encode
     * @param outputFile the output file path
     * @throws IOException if an I/O error occurs
     * @deprecated Use {@link com.jabcode.OptimizedJABCode#builder()} instead
     */
    @Deprecated
    public static void generateJABCode(String text, String outputFile) throws IOException {
        generateJABCodeWithColorMode(text, outputFile, COLOR_MODE_8);
    }
    
    /**
     * Generate a JABCode with the specified color mode
     * 
     * @param text the text to encode
     * @param outputFile the output file path
     * @param colorMode the color mode to use
     * @throws IOException if an I/O error occurs
     * @deprecated Use {@link com.jabcode.OptimizedJABCode#builder()} instead
     */
    @Deprecated
    public static void generateJABCodeWithColorMode(String text, String outputFile, int colorMode) throws IOException {
        OptimizedJABCode.ColorMode mode = OptimizedJABCode.ColorMode.fromColorCount(colorMode);
        BufferedImage image = OptimizedJABCode.builder()
            .withData(text)
            .withColorMode(mode)
            .build();
        OptimizedJABCode.saveToFile(image, outputFile);
    }
    
    /**
     * Decode a JABCode
     * 
     * @param inputFile the input file path
     * @return the decoded text
     * @throws IOException if an I/O error occurs
     * @deprecated Use {@link com.jabcode.OptimizedJABCode#decodeToString(String)} instead
     */
    @Deprecated
    public static String decodeJABCode(String inputFile) throws IOException {
        return OptimizedJABCode.decodeToString(inputFile);
    }
    
    /**
     * Decode a JABCode
     * 
     * @param inputFile the input file
     * @return the decoded text
     * @throws IOException if an I/O error occurs
     * @deprecated Use {@link com.jabcode.OptimizedJABCode#decodeToString(File)} instead
     */
    @Deprecated
    public static String decodeJABCode(File inputFile) throws IOException {
        return OptimizedJABCode.decodeToString(inputFile);
    }
}
