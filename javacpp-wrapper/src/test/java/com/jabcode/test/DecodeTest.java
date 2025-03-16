package com.jabcode.test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import com.jabcode.OptimizedJABCode;

/**
 * Test class for decoding JABCode images
 */
public class DecodeTest {
    
    /**
     * Main method to decode a JABCode image
     * @param args command line arguments:
     *             args[0] = input file path
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java com.jabcode.test.DecodeTest <input_file>");
            System.exit(1);
        }
        
        String inputFile = args[0];
        
        try {
            // Load the image
            BufferedImage image = ImageIO.read(new File(inputFile));
            if (image == null) {
                throw new IOException("Failed to read image: " + inputFile);
            }
            
            System.out.println("Decoding JABCode from: " + inputFile);
            System.out.println("Image dimensions: " + image.getWidth() + "x" + image.getHeight() + " pixels");
            
            // Decode the JABCode
            try {
                // Get extended information
                OptimizedJABCode.DecodedResult result = OptimizedJABCode.decodeEx(image, 1);
                
                // Print the decoded data
                System.out.println("Decoded data: " + result.getDataAsString());
                System.out.println("Color mode: " + result.getColorMode());
                System.out.println("Color count: " + result.getColorCount());
                System.out.println("Symbol count: " + result.getSymbolCount());
                System.out.println("ECC level: " + result.getEccLevel());
                
                // Return the decoded data
                System.out.println("\nDecoded text:");
                System.out.println(result.getDataAsString());
            } catch (Exception e) {
                System.err.println("Failed to decode JABCode: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
