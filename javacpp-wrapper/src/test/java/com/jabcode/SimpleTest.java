package com.jabcode;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Simple test for the OptimizedJABCode class
 */
public class SimpleTest {
    public static void main(String[] args) {
        try {
            // Create output directory if it doesn't exist
            File outputDir = new File("test-output");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            // Generate a JABCode with default settings
            String text = "Hello, JABCode!";
            System.out.println("Generating JABCode with text: " + text);
            
            BufferedImage image = OptimizedJABCode.encode(text);
            
            // Save the image
            String outputFile = "test-output/simple_test.png";
            OptimizedJABCode.saveToFile(image, outputFile);
            
            System.out.println("JABCode saved to: " + outputFile);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
