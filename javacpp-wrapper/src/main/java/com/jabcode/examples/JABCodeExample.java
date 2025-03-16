package com.jabcode.examples;

import com.jabcode.core.JABCode;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Example usage of the JABCode library
 */
public class JABCodeExample {
    public static void main(String[] args) {
        try {
            // Example 1: Generate a JABCode with default settings (8 colors)
            System.out.println("Example 1: Generating JABCode with default settings");
            BufferedImage image = JABCode.encode("Hello, JABCode!");
            JABCode.save(image, "example1_jabcode.png");
            System.out.println("  ✓ JABCode saved to example1_jabcode.png");
            
            // Example 2: Use the builder for more options
            System.out.println("\nExample 2: Generating JABCode with custom settings");
            BufferedImage customImage = JABCode.builder()
                .withData("Hello, JABCode with custom settings!")
                .withColorMode(JABCode.ColorMode.HEXADECIMAL) // 16 colors
                .withSymbolCount(2) // Use 2 symbols
                .withEccLevel(5) // Error correction level
                .build();
            JABCode.save(customImage, "example2_jabcode.png");
            System.out.println("  ✓ JABCode saved to example2_jabcode.png");
            
            // Example 3: Build directly to a file
            System.out.println("\nExample 3: Generating JABCode directly to a file");
            JABCode.builder()
                .withData("Hello, JABCode with 256 colors!")
                .withColorMode(JABCode.ColorMode.MODE_256) // 256 colors
                .buildToFile("example3_jabcode.png");
            System.out.println("  ✓ JABCode saved to example3_jabcode.png");
            
            // Example 4: Generate JABCodes with different color modes
            System.out.println("\nExample 4: Generating JABCodes with different color modes");
            generateWithColorMode(JABCode.ColorMode.BINARY, "example4_binary.png");
            generateWithColorMode(JABCode.ColorMode.QUATERNARY, "example4_quaternary.png");
            generateWithColorMode(JABCode.ColorMode.OCTAL, "example4_octal.png");
            generateWithColorMode(JABCode.ColorMode.HEXADECIMAL, "example4_hexadecimal.png");
            generateWithColorMode(JABCode.ColorMode.MODE_32, "example4_32.png");
            generateWithColorMode(JABCode.ColorMode.MODE_64, "example4_64.png");
            generateWithColorMode(JABCode.ColorMode.MODE_128, "example4_128.png");
            generateWithColorMode(JABCode.ColorMode.MODE_256, "example4_256.png");
            
            // Example 5: Decode a JABCode
            System.out.println("\nExample 5: Decoding JABCodes");
            decodeJABCode("example1_jabcode.png");
            decodeJABCode("example2_jabcode.png");
            decodeJABCode("example3_jabcode.png");
            
            System.out.println("\nAll examples completed successfully!");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Generate a JABCode with the specified color mode
     * 
     * @param colorMode the color mode to use
     * @param outputFile the output file path
     * @throws IOException if an I/O error occurs
     */
    private static void generateWithColorMode(JABCode.ColorMode colorMode, String outputFile) throws IOException {
        String text = "JABCode with " + colorMode.getColorCount() + " colors";
        BufferedImage image = JABCode.builder()
            .withData(text)
            .withColorMode(colorMode)
            .build();
        JABCode.save(image, outputFile);
        System.out.println("  ✓ JABCode with " + colorMode.getColorCount() + " colors saved to " + outputFile);
    }
    
    /**
     * Decode a JABCode and print the result
     * 
     * @param inputFile the input file path
     * @throws IOException if an I/O error occurs
     */
    private static void decodeJABCode(String inputFile) throws IOException {
        try {
            String decodedText = JABCode.decodeToString(inputFile);
            System.out.println("  ✓ Decoded " + inputFile + ": " + decodedText);
        } catch (Exception e) {
            System.out.println("  ✗ Failed to decode " + inputFile + ": " + e.getMessage());
        }
    }
}
