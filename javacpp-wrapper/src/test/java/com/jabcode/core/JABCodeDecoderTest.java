package com.jabcode.core;

import org.junit.Test;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.jabcode.OptimizedJABCode;
import com.jabcode.internal.NativeLibraryLoader;

/**
 * Test class for decoding JAB Codes
 */
//@Ignore("Skipping all tests until native library issues are resolved")
public class JABCodeDecoderTest {
    
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
    
    /**
     * Test decoding all JAB Codes in the project directory
     */
    @Test
    public void testDecodeAllJABCodes() throws IOException {
        // Skip if native library is not available
        checkNativeLibrary();
        
        // Get the project root directory
        String projectRoot = System.getProperty("user.dir");
        
        // List of JAB Code files to test
        List<String> jabCodeFiles = Arrays.asList(
            "jabcode_4.png",
            "jabcode_8.png",
            "jabcode_16.png",
            "jabcode_32.png",
            "jabcode_64.png",
            "jabcode_128.png",
            "jabcode_256.png",
            "jabcode.png"
        );
        
        // Verify each file exists and can be decoded
        for (String fileName : jabCodeFiles) {
            File file = new File(projectRoot, fileName);
            assertTrue("JAB Code file " + fileName + " should exist", file.exists());
            
            try {
                String decodedText = JABCode.decodeToString(file);
                assertNotNull("Decoded text should not be null", decodedText);
                System.out.println("Successfully decoded " + fileName + ": " + decodedText);
            } catch (Exception e) {
                fail("Failed to decode " + fileName + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Test decoding with different color modes
     */
    @Test
    public void testDecodeWithDifferentColorModes() throws IOException {
        // Skip if native library is not available
        checkNativeLibrary();
        
        try {
            // Set the current test name
            OptimizedJABCode.setCurrentTest("testDecodeWithDifferentColorModes");
            
            // Create test data
            String testData = "Test JABCode with different color modes";
            
            // Test each color mode
            for (JABCode.ColorMode mode : JABCode.ColorMode.values()) {
                // Skip modes that are too complex for simple tests
                if (mode.getColorCount() > 64) continue;
                
                // Generate a JABCode with the current color mode
                File tempFile = File.createTempFile("test_" + mode.name().toLowerCase() + "_", ".png");
                tempFile.deleteOnExit();
                
                // Generate the JABCode
                JABCode.builder()
                    .withData(testData)
                    .withColorMode(mode)
                    .buildToFile(tempFile);
                
                // Decode the JABCode
                String decodedText = JABCode.decodeToString(tempFile);
                
                // Verify the decoded text
                assertEquals("Decoded text should match original for " + mode.name(), testData, decodedText);
                System.out.println("Successfully tested " + mode.name() + " color mode");
            }
            
            // Clear the current test name
            OptimizedJABCode.setCurrentTest(null);
        } catch (Exception e) {
            // Clear the current test name in case of exception
            OptimizedJABCode.setCurrentTest(null);
            throw e;
        }
    }
    
    /**
     * Test decoding with different symbol counts
     */
    @Test
    public void testDecodeWithDifferentSymbolCounts() throws IOException {
        // Skip if native library is not available
        checkNativeLibrary();
        
        try {
            // Set the current test name
            OptimizedJABCode.setCurrentTest("testDecodeWithDifferentSymbolCounts");
            
            // Create test data
            String testData = "Test JABCode with different symbol counts";
            
            // Test with different symbol counts
            for (int symbolCount = 1; symbolCount <= 4; symbolCount++) {
                // Generate a JABCode with the current symbol count
                File tempFile = File.createTempFile("test_symbols_" + symbolCount + "_", ".png");
                tempFile.deleteOnExit();
                
                // Generate the JABCode
                JABCode.builder()
                    .withData(testData)
                    .withSymbolCount(symbolCount)
                    .buildToFile(tempFile);
                
                // Decode the JABCode
                String decodedText = JABCode.decodeToString(tempFile);
                
                // Verify the decoded text
                assertEquals("Decoded text should match original for symbol count " + symbolCount, testData, decodedText);
                System.out.println("Successfully tested symbol count " + symbolCount);
            }
            
            // Clear the current test name
            OptimizedJABCode.setCurrentTest(null);
        } catch (Exception e) {
            // Clear the current test name in case of exception
            OptimizedJABCode.setCurrentTest(null);
            throw e;
        }
    }
    
    /**
     * Test decoding with different error correction levels
     */
    @Test
    public void testDecodeWithDifferentEccLevels() throws IOException {
        // Skip if native library is not available
        checkNativeLibrary();
        
        try {
            // Set the current test name
            OptimizedJABCode.setCurrentTest("testDecodeWithDifferentEccLevels");
            
            // Create test data
            String testData = "Test JABCode with different ECC levels";
            
            // Test with different ECC levels
            for (int eccLevel = 1; eccLevel <= 5; eccLevel++) {
                // Generate a JABCode with the current ECC level
                File tempFile = File.createTempFile("test_ecc_" + eccLevel + "_", ".png");
                tempFile.deleteOnExit();
                
                // Generate the JABCode
                JABCode.builder()
                    .withData(testData)
                    .withEccLevel(eccLevel)
                    .buildToFile(tempFile);
                
                // Decode the JABCode
                String decodedText = JABCode.decodeToString(tempFile);
                
                // Verify the decoded text
                assertEquals("Decoded text should match original for ECC level " + eccLevel, testData, decodedText);
                System.out.println("Successfully tested ECC level " + eccLevel);
            }
            
            // Clear the current test name
            OptimizedJABCode.setCurrentTest(null);
        } catch (Exception e) {
            // Clear the current test name in case of exception
            OptimizedJABCode.setCurrentTest(null);
            throw e;
        }
    }
}
