package com.jabcode;

import static org.junit.Assert.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assume;
import org.junit.Ignore;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import com.jabcode.internal.NativeLibraryLoader;

/**
 * Unit tests for the OptimizedJABCode class
 */
public class OptimizedJABCodeTest {
    
    private static boolean nativeLibraryAvailable = false;
    private List<String> filesToCleanup = new ArrayList<>();
    private static final String TEST_OUTPUT_DIR = "test-output";
    
    // Load the native library
    static {
        try {
            NativeLibraryLoader.load();
            nativeLibraryAvailable = true;
            System.out.println("Native library loaded from: " + NativeLibraryLoader.getLoadedLibraryPath());
        } catch (Throwable e) {
            System.err.println("Failed to load native library: " + e.getMessage());
            nativeLibraryAvailable = false;
        }
    }
    
    @Before
    public void setUp() {
        // Skip tests that require native libraries if they're not available
        Assume.assumeTrue("Native library is not available", nativeLibraryAvailable);
        
        // Create output directory if it doesn't exist
        File outputDir = new File(TEST_OUTPUT_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
    }
    
    @After
    public void tearDown() {
        // Clean up generated files
        for (String filePath : filesToCleanup) {
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }
        }
    }
    
    /**
     * Test encoding with default settings
     */
    @Test
    public void testEncodeDefault() throws IOException {
        // Generate a JABCode with default settings
        String text = "Hello, JABCode!";
        BufferedImage image = OptimizedJABCode.encode(text);
        
        // Verify the image was created
        assertNotNull("Generated image should not be null", image);
        assertTrue("Image width should be greater than 0", image.getWidth() > 0);
        assertTrue("Image height should be greater than 0", image.getHeight() > 0);
        
        // Save the image
        String outputFile = TEST_OUTPUT_DIR + "/optimized_jabcode_default.png";
        OptimizedJABCode.saveToFile(image, outputFile);
        filesToCleanup.add(outputFile);
        
        // Verify the file exists
        File file = new File(outputFile);
        assertTrue("Generated file should exist", file.exists());
        assertTrue("Generated file should not be empty", file.length() > 0);
    }
    
    /**
     * Test encoding with custom settings
     */
    @Test
    public void testEncodeCustom() throws IOException {
        // Generate a JABCode with custom settings
        String text = "Hello, JABCode with custom settings!";
        BufferedImage image = OptimizedJABCode.builder()
            .withData(text)
            .withColorMode(OptimizedJABCode.ColorMode.HEXADECIMAL)
            .withSymbolCount(2)
            .withEccLevel(5)
            .build();
        
        // Verify the image was created
        assertNotNull("Generated image should not be null", image);
        assertTrue("Image width should be greater than 0", image.getWidth() > 0);
        assertTrue("Image height should be greater than 0", image.getHeight() > 0);
        
        // Save the image
        String outputFile = TEST_OUTPUT_DIR + "/optimized_jabcode_custom.png";
        OptimizedJABCode.saveToFile(image, outputFile);
        filesToCleanup.add(outputFile);
        
        // Verify the file exists
        File file = new File(outputFile);
        assertTrue("Generated file should exist", file.exists());
        assertTrue("Generated file should not be empty", file.length() > 0);
    }
    
    /**
     * Test encoding with different color modes
     */
    @Test
    public void testEncodeAllColorModes() throws IOException {
        OptimizedJABCode.ColorMode[] colorModes = {
            OptimizedJABCode.ColorMode.BINARY,
            OptimizedJABCode.ColorMode.QUATERNARY,
            OptimizedJABCode.ColorMode.OCTAL,
            OptimizedJABCode.ColorMode.HEXADECIMAL,
            OptimizedJABCode.ColorMode.MODE_32,
            OptimizedJABCode.ColorMode.MODE_64,
            OptimizedJABCode.ColorMode.MODE_128,
            OptimizedJABCode.ColorMode.MODE_256
        };
        
        String text = "Hello, JABCode with different color modes!";
        
        for (OptimizedJABCode.ColorMode colorMode : colorModes) {
            // Generate a JABCode with the current color mode
            BufferedImage image = OptimizedJABCode.builder()
                .withData(text)
                .withColorMode(colorMode)
                .build();
            
            // Verify the image was created
            assertNotNull("Generated image should not be null for color mode " + colorMode.name(), image);
            assertTrue("Image width should be greater than 0 for color mode " + colorMode.name(), image.getWidth() > 0);
            assertTrue("Image height should be greater than 0 for color mode " + colorMode.name(), image.getHeight() > 0);
            
            // Save the image
            String outputFile = TEST_OUTPUT_DIR + "/optimized_jabcode_" + colorMode.getColorCount() + ".png";
            OptimizedJABCode.saveToFile(image, outputFile);
            filesToCleanup.add(outputFile);
            
            // Verify the file exists
            File file = new File(outputFile);
            assertTrue("Generated file should exist for color mode " + colorMode.name(), file.exists());
            assertTrue("Generated file should not be empty for color mode " + colorMode.name(), file.length() > 0);
        }
    }
    
    /**
     * Test encoding with image processing disabled
     */
    @Test
    public void testEncodeWithoutImageProcessing() throws IOException {
        // Generate a JABCode with image processing disabled
        String text = "Hello, JABCode without image processing!";
        BufferedImage image = OptimizedJABCode.builder()
            .withData(text)
            .withImageProcessing(false)
            .build();
        
        // Verify the image was created
        assertNotNull("Generated image should not be null", image);
        assertTrue("Image width should be greater than 0", image.getWidth() > 0);
        assertTrue("Image height should be greater than 0", image.getHeight() > 0);
        
        // Save the image
        String outputFile = TEST_OUTPUT_DIR + "/optimized_jabcode_no_processing.png";
        OptimizedJABCode.saveToFile(image, outputFile);
        filesToCleanup.add(outputFile);
        
        // Verify the file exists
        File file = new File(outputFile);
        assertTrue("Generated file should exist", file.exists());
        assertTrue("Generated file should not be empty", file.length() > 0);
    }
    
    /**
     * Test encoding with byte array data
     */
    @Test
    public void testEncodeByteArray() throws IOException {
        // Generate a JABCode with byte array data
        byte[] data = {0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x2C, 0x20, 0x57, 0x6F, 0x72, 0x6C, 0x64, 0x21}; // "Hello, World!"
        BufferedImage image = OptimizedJABCode.encode(data);
        
        // Verify the image was created
        assertNotNull("Generated image should not be null", image);
        assertTrue("Image width should be greater than 0", image.getWidth() > 0);
        assertTrue("Image height should be greater than 0", image.getHeight() > 0);
        
        // Save the image
        String outputFile = TEST_OUTPUT_DIR + "/optimized_jabcode_byte_array.png";
        OptimizedJABCode.saveToFile(image, outputFile);
        filesToCleanup.add(outputFile);
        
        // Verify the file exists
        File file = new File(outputFile);
        assertTrue("Generated file should exist", file.exists());
        assertTrue("Generated file should not be empty", file.length() > 0);
    }
    
    /**
     * Test encoding with invalid parameters
     */
    @Test(expected = IllegalArgumentException.class)
    public void testEncodeNullData() {
        // This should throw an IllegalArgumentException
        OptimizedJABCode.encode((String)null);
    }
    
    /**
     * Test encoding with empty data
     */
    @Test(expected = IllegalArgumentException.class)
    public void testEncodeEmptyData() {
        // This should throw an IllegalArgumentException
        OptimizedJABCode.encode("");
    }
    
    /**
     * Test encoding with invalid symbol count
     */
    @Test(expected = IllegalArgumentException.class)
    public void testEncodeInvalidSymbolCount() {
        // This should throw an IllegalArgumentException
        OptimizedJABCode.builder()
            .withData("Test")
            .withSymbolCount(0) // Invalid: must be >= 1
            .build();
    }
    
    /**
     * Test encoding with invalid ECC level
     */
    @Test(expected = IllegalArgumentException.class)
    public void testEncodeInvalidEccLevel() {
        // This should throw an IllegalArgumentException
        OptimizedJABCode.builder()
            .withData("Test")
            .withEccLevel(11) // Invalid: must be <= 10
            .build();
    }
    
    /**
     * Test saving to invalid file
     */
    @Test(expected = IOException.class)
    public void testSaveToInvalidFile() throws IOException {
        // Generate a JABCode
        String text = "Hello, JABCode!";
        BufferedImage image = OptimizedJABCode.encode(text);
        
        // Try to save to an invalid location
        OptimizedJABCode.saveToFile(image, "/invalid/path/file.png");
    }
    
    /**
     * Test saving with null image
     */
    @Test(expected = IllegalArgumentException.class)
    public void testSaveNullImage() throws IOException {
        // This should throw an IllegalArgumentException
        OptimizedJABCode.saveToFile(null, "test.png");
    }
    
    /**
     * Test saving with null file
     */
    @Test(expected = IllegalArgumentException.class)
    public void testSaveNullFile() throws IOException {
        // Generate a JABCode
        String text = "Hello, JABCode!";
        BufferedImage image = OptimizedJABCode.encode(text);
        
        // This should throw an IllegalArgumentException
        OptimizedJABCode.saveToFile(image, (String)null);
    }
    
    /**
     * Test the ColorMode enum
     */
    @Test
    public void testColorModeEnum() {
        // Test fromColorCount method
        assertEquals(OptimizedJABCode.ColorMode.BINARY, OptimizedJABCode.ColorMode.fromColorCount(2));
        assertEquals(OptimizedJABCode.ColorMode.QUATERNARY, OptimizedJABCode.ColorMode.fromColorCount(4));
        assertEquals(OptimizedJABCode.ColorMode.OCTAL, OptimizedJABCode.ColorMode.fromColorCount(8));
        assertEquals(OptimizedJABCode.ColorMode.HEXADECIMAL, OptimizedJABCode.ColorMode.fromColorCount(16));
        assertEquals(OptimizedJABCode.ColorMode.MODE_32, OptimizedJABCode.ColorMode.fromColorCount(32));
        assertEquals(OptimizedJABCode.ColorMode.MODE_64, OptimizedJABCode.ColorMode.fromColorCount(64));
        assertEquals(OptimizedJABCode.ColorMode.MODE_128, OptimizedJABCode.ColorMode.fromColorCount(128));
        assertEquals(OptimizedJABCode.ColorMode.MODE_256, OptimizedJABCode.ColorMode.fromColorCount(256));
    }
    
    /**
     * Test the ColorMode enum with invalid color count
     */
    @Test(expected = IllegalArgumentException.class)
    public void testColorModeEnumInvalidCount() {
        // This should throw an IllegalArgumentException
        OptimizedJABCode.ColorMode.fromColorCount(3); // Invalid: not a supported color count
    }
    
    /**
     * Test encode and decode roundtrip
     */
    @Test
    public void testEncodeDecodeRoundtrip() throws IOException {
        // Skip this test if decoding is not supported
        try {
            // Set the current test name
            OptimizedJABCode.setCurrentTest("testEncodeDecodeRoundtrip");
            
            // Generate a JABCode
            String text = "Hello, JABCode roundtrip test!";
            BufferedImage image = OptimizedJABCode.encode(text);
            
            // Save the image
            String outputFile = TEST_OUTPUT_DIR + "/optimized_jabcode_roundtrip.png";
            OptimizedJABCode.saveToFile(image, outputFile);
            filesToCleanup.add(outputFile);
            
            // Decode the image
            String decodedText = OptimizedJABCode.decodeToString(new File(outputFile));
            
            // Verify the decoded text matches the original
            assertEquals("Decoded text should match original", text, decodedText);
            
            // Clear the current test name
            OptimizedJABCode.setCurrentTest(null);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            // Skip this test if decoding is not supported
            System.out.println("Skipping decode test due to native code issues: " + e.getMessage());
            Assume.assumeNoException(e);
        }
    }
}
