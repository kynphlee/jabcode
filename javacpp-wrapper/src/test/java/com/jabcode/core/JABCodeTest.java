package com.jabcode.core;

import org.junit.Test;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import com.jabcode.OptimizedJABCode;
import com.jabcode.internal.NativeLibraryLoader;

/**
 * Unit tests for the JABCode class
 */
//@Ignore("Skipping all tests until native library issues are resolved")
public class JABCodeTest {
    
    private static final String TEST_OUTPUT_DIR = "test-output";
    private static boolean nativeLibraryAvailable = false;
    
    // Load the native library
    static {
        try {
            NativeLibraryLoader.load();
            nativeLibraryAvailable = true;
        } catch (Throwable e) {
            System.err.println("Failed to load native library: " + e.getMessage());
            nativeLibraryAvailable = false;
        }
    }
    
    private void checkNativeLibrary() {
        // Skip tests that require native libraries if they're not available
        // or if the skipNativeTests property is set
        boolean skipTests = Boolean.getBoolean("skipNativeTests");
        Assume.assumeTrue("Native library is not available", nativeLibraryAvailable && !skipTests);
    }
    
    @Before
    public void setUp() {
        // Create the test output directory if it doesn't exist
        File outputDir = new File(TEST_OUTPUT_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
    }
    
    /**
     * Test generating a JABCode with default settings
     */
    @Test
    public void testGenerateDefault() {
        // Skip if native library is not available
        checkNativeLibrary();
        
        try {
            // Generate a JABCode with default settings
            BufferedImage image = JABCode.encode("Test JABCode");
            
            // Verify the image
            assertNotNull("Generated image should not be null", image);
            assertTrue("Image width should be > 0", image.getWidth() > 0);
            assertTrue("Image height should be > 0", image.getHeight() > 0);
            
            // Save the image for visual inspection
            File outputFile = new File(TEST_OUTPUT_DIR, "test_default.png");
            JABCode.save(image, outputFile);
            assertTrue("Output file should exist", outputFile.exists());
            assertTrue("Output file should not be empty", outputFile.length() > 0);
            
            // Clean up
//            outputFile.delete();
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }
    
    /**
     * Test generating a JABCode with custom settings
     */
    @Test
    public void testGenerateCustom() {
        // Skip if native library is not available
        checkNativeLibrary();
        
        try {
            // Generate a JABCode with custom settings
            BufferedImage image = JABCode.builder()
                .withData("Test JABCode with custom settings")
                .withColorMode(JABCode.ColorMode.HEXADECIMAL)
                .withSymbolCount(1)
                .withEccLevel(5)
                .build();
            
            // Verify the image
            assertNotNull("Generated image should not be null", image);
            assertTrue("Image width should be > 0", image.getWidth() > 0);
            assertTrue("Image height should be > 0", image.getHeight() > 0);
            
            // Save the image for visual inspection
            File outputFile = new File(TEST_OUTPUT_DIR, "test_custom.png");
            JABCode.save(image, outputFile);
            assertTrue("Output file should exist", outputFile.exists());
            assertTrue("Output file should not be empty", outputFile.length() > 0);
            
            // Clean up
//            outputFile.delete();
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }
    
    /**
     * Test generating a JABCode with all color modes
     */
    @Test
    public void testAllColorModes() {
        // Skip if native library is not available
        checkNativeLibrary();
        
        try {
            // Test all color modes
            for (JABCode.ColorMode mode : JABCode.ColorMode.values()) {
                // Generate a JABCode with the current color mode
                BufferedImage image = JABCode.builder()
                    .withData("Test JABCode with " + mode.name() + " color mode")
                    .withColorMode(mode)
                    .build();
                
                // Verify the image
                assertNotNull("Generated image should not be null", image);
                assertTrue("Image width should be > 0", image.getWidth() > 0);
                assertTrue("Image height should be > 0", image.getHeight() > 0);
                
                // Save the image for visual inspection
                File outputFile = new File(TEST_OUTPUT_DIR, "test_" + mode.name().toLowerCase() + ".png");
                JABCode.save(image, outputFile);
                assertTrue("Output file should exist", outputFile.exists());
                assertTrue("Output file should not be empty", outputFile.length() > 0);
                
                // Clean up
//                outputFile.delete();
            }
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }
    
    /**
     * Test generating and decoding a JABCode
     */
    @Test
    public void testGenerateAndDecode() {
        // Skip if native library is not available
        checkNativeLibrary();
        
        try {
            // Set the current test name
            OptimizedJABCode.setCurrentTest("testGenerateAndDecode");
            
            // Generate a JABCode
            String originalText = "Test JABCode for decoding";
            BufferedImage image = JABCode.encode(originalText);
            
            // Save the image
            File outputFile = new File(TEST_OUTPUT_DIR, "test_decode.png");
            JABCode.save(image, outputFile);
            
            // Decode the image
            String decodedText = JABCode.decodeToString(outputFile);
            
            // Verify the decoded text
            assertEquals("Decoded text should match original text", originalText, decodedText);
            
            // Clean up
//            outputFile.delete();
            
            // Clear the current test name
            OptimizedJABCode.setCurrentTest(null);
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }
    
    /**
     * Test error handling for invalid inputs
     */
    @Test
    public void testErrorHandling() {
        // Skip if native library is not available
        checkNativeLibrary();
        
        // Test null data
        try {
            JABCode.encode((byte[])null);
            fail("Should throw IllegalArgumentException for null data");
        } catch (IllegalArgumentException e) {
            // Expected
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName());
        }
        
        // Test empty data
        try {
            JABCode.encode(new byte[0]);
            fail("Should throw IllegalArgumentException for empty data");
        } catch (IllegalArgumentException e) {
            // Expected
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName());
        }
        
        // Test invalid symbol count
        try {
            JABCode.builder()
                .withData("Test")
                .withSymbolCount(0)
                .build();
            fail("Should throw IllegalArgumentException for invalid symbol count");
        } catch (IllegalArgumentException e) {
            // Expected
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName());
        }
        
        // Test invalid ECC level
        try {
            JABCode.builder()
                .withData("Test")
                .withEccLevel(-1)
                .build();
            fail("Should throw IllegalArgumentException for invalid ECC level");
        } catch (IllegalArgumentException e) {
            // Expected
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName());
        }
    }
    
    /**
     * Test the ColorMode enum
     */
    @Test
    public void testColorMode() {
        // Test getColorCount
        assertEquals("BINARY should have 2 colors", 2, JABCode.ColorMode.BINARY.getColorCount());
        assertEquals("QUATERNARY should have 4 colors", 4, JABCode.ColorMode.QUATERNARY.getColorCount());
        assertEquals("OCTAL should have 8 colors", 8, JABCode.ColorMode.OCTAL.getColorCount());
        assertEquals("HEXADECIMAL should have 16 colors", 16, JABCode.ColorMode.HEXADECIMAL.getColorCount());
        assertEquals("MODE_32 should have 32 colors", 32, JABCode.ColorMode.MODE_32.getColorCount());
        assertEquals("MODE_64 should have 64 colors", 64, JABCode.ColorMode.MODE_64.getColorCount());
        assertEquals("MODE_128 should have 128 colors", 128, JABCode.ColorMode.MODE_128.getColorCount());
        assertEquals("MODE_256 should have 256 colors", 256, JABCode.ColorMode.MODE_256.getColorCount());
        
        // Test fromColorCount
        assertEquals("fromColorCount(2) should return BINARY", JABCode.ColorMode.BINARY, JABCode.ColorMode.fromColorCount(2));
        assertEquals("fromColorCount(4) should return QUATERNARY", JABCode.ColorMode.QUATERNARY, JABCode.ColorMode.fromColorCount(4));
        assertEquals("fromColorCount(8) should return OCTAL", JABCode.ColorMode.OCTAL, JABCode.ColorMode.fromColorCount(8));
        assertEquals("fromColorCount(16) should return HEXADECIMAL", JABCode.ColorMode.HEXADECIMAL, JABCode.ColorMode.fromColorCount(16));
        assertEquals("fromColorCount(32) should return MODE_32", JABCode.ColorMode.MODE_32, JABCode.ColorMode.fromColorCount(32));
        assertEquals("fromColorCount(64) should return MODE_64", JABCode.ColorMode.MODE_64, JABCode.ColorMode.fromColorCount(64));
        assertEquals("fromColorCount(128) should return MODE_128", JABCode.ColorMode.MODE_128, JABCode.ColorMode.fromColorCount(128));
        assertEquals("fromColorCount(256) should return MODE_256", JABCode.ColorMode.MODE_256, JABCode.ColorMode.fromColorCount(256));
        
        // Test invalid color count
        try {
            JABCode.ColorMode.fromColorCount(3);
            fail("Should throw IllegalArgumentException for invalid color count");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }
}
