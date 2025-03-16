package com.jabcode;

import org.junit.Test;
import static org.junit.Assert.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * JUnit test class for the OptimizedJABCode
 */
public class JUnitSimpleTest {
    
    /**
     * Test that we can encode a simple message
     */
    @Test
    public void testEncode() {
        try {
            // Create output directory if it doesn't exist
            File outputDir = new File("test-output");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            // Generate a JABCode with default settings
            String text = "Hello, JABCode!";
            BufferedImage image = OptimizedJABCode.encode(text);
            
            // Verify the image was created
            assertNotNull("Generated image should not be null", image);
            assertTrue("Image width should be > 0", image.getWidth() > 0);
            assertTrue("Image height should be > 0", image.getHeight() > 0);
            
            // Save the image
            String outputFile = "test-output/junit_test.png";
            OptimizedJABCode.saveToFile(image, outputFile);
            
            // Verify the file was created
            File file = new File(outputFile);
            assertTrue("Output file should exist", file.exists());
            
            System.out.println("JABCode test passed successfully");
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception occurred: " + e.getMessage());
        }
    }
    
    /**
     * Simple sanity test to ensure JUnit is working
     */
    @Test
    public void testSanity() {
        assertTrue("JUnit is working", true);
    }
}
