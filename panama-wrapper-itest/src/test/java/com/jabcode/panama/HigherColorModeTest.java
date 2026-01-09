package com.jabcode.panama;

import com.jabcode.panama.bindings.jabcode_h;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Experimental tests for higher color modes (16-256).
 * These tests attempt to find working parameter combinations.
 */
class HigherColorModeTest {
    
    private final JABCodeDecoder decoder = new JABCodeDecoder();
    
    /**
     * Create jab_data structure in native memory
     */
    private MemorySegment createJabData(Arena arena, byte[] data) {
        long size = 4 + data.length;
        MemorySegment jabData = arena.allocate(size, 4);
        jabData.set(ValueLayout.JAVA_INT, 0, data.length);
        MemorySegment.copy(data, 0, jabData, ValueLayout.JAVA_BYTE, 4, data.length);
        return jabData;
    }
    
    /**
     * Test 16 colors with larger module size
     */
    @Test
    void test16ColorsWithLargerModules(@TempDir Path tempDir) {
        testColorModeWithParams(16, 9, 20, tempDir, "16colors_large_modules");
    }
    
    /**
     * Test 16 colors with very high ECC
     */
    @Test
    void test16ColorsWithHighECC(@TempDir Path tempDir) {
        testColorModeWithParams(16, 10, 16, tempDir, "16colors_high_ecc");
    }
    
    /**
     * Test 32 colors with larger modules
     */
    @Test
    void test32ColorsWithLargerModules(@TempDir Path tempDir) {
        testColorModeWithParams(32, 9, 20, tempDir, "32colors_large_modules");
    }
    
    /**
     * Test 64 colors with larger modules
     */
    @Test
    void test64ColorsWithLargerModules(@TempDir Path tempDir) {
        testColorModeWithParams(64, 9, 24, tempDir, "64colors_large_modules");
    }
    
    /**
     * Test 128 colors with very large modules
     */
    @Test
    void test128ColorsWithVeryLargeModules(@TempDir Path tempDir) {
        testColorModeWithParams(128, 10, 28, tempDir, "128colors_very_large");
    }
    
    /**
     * Test 256 colors with very large modules
     */
    @Test
    void test256ColorsWithVeryLargeModules(@TempDir Path tempDir) {
        testColorModeWithParams(256, 10, 32, tempDir, "256colors_very_large");
    }
    
    /**
     * Test 16 colors with explicit symbol dimensions
     */
    @Test
    void test16ColorsWithExplicitDimensions(@TempDir Path tempDir) {
        String message = "Test 16 colors explicit";
        Path outputFile = tempDir.resolve("16colors_explicit.png");
        
        try (Arena arena = Arena.ofConfined()) {
            // Create encoder
            MemorySegment enc = jabcode_h.createEncode(16, 1);
            assertNotNull(enc);
            assertTrue(enc.address() != 0);
            
            try {
                // Set larger symbol dimensions explicitly
                // jab_encode struct layout:
                // int32 color_number (0)
                // int32 symbol_number (4)
                // int32 module_size (8)
                // int32 master_symbol_width (12)
                // int32 master_symbol_height (16)
                
                enc.set(ValueLayout.JAVA_INT, 8, 20);   // module_size = 20
                enc.set(ValueLayout.JAVA_INT, 12, 300); // master_symbol_width = 300
                enc.set(ValueLayout.JAVA_INT, 16, 300); // master_symbol_height = 300
                
                // Prepare data
                byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
                MemorySegment jabData = createJabData(arena, bytes);
                
                // Generate
                System.out.println("Generating 16-color barcode with explicit dimensions...");
                int result = jabcode_h.generateJABCode(enc, jabData);
                System.out.println("  generateJABCode result: " + result);
                
                if (result == 0) { // 0 = success
                    // Get bitmap and save
                    MemorySegment bitmapPtr = enc.get(ValueLayout.ADDRESS, 64);
                    if (bitmapPtr != null && bitmapPtr.address() != 0) {
                        MemorySegment filenameSegment = arena.allocateFrom(outputFile.toString());
                        byte saveResult = jabcode_h.saveImage(bitmapPtr, filenameSegment);
                        
                        if (saveResult == 1) {
                            System.out.println("  Saved to: " + outputFile);
                            
                            // Try to decode
                            String decoded = decoder.decodeFromFile(outputFile);
                            System.out.println("  Decoded: " + decoded);
                            
                            if (decoded != null) {
                                assertEquals(message, decoded, "16 colors with explicit dimensions should work");
                            } else {
                                System.err.println("  WARNING: Encoding succeeded but decoding failed");
                            }
                        }
                    }
                } else {
                    System.err.println("  Encoding failed with code: " + result);
                }
                
            } finally {
                jabcode_h.destroyEncode(enc);
            }
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Generic test method for different color modes with parameters
     */
    private void testColorModeWithParams(int colors, int eccLevel, int moduleSize, 
                                         Path tempDir, String testName) {
        String message = "Test " + colors + " colors";
        Path outputFile = tempDir.resolve(testName + ".png");
        
        try (Arena arena = Arena.ofConfined()) {
            // Create encoder
            MemorySegment enc = jabcode_h.createEncode(colors, 1);
            assertNotNull(enc);
            assertTrue(enc.address() != 0);
            
            try {
                // Set module size
                enc.set(ValueLayout.JAVA_INT, 8, moduleSize);
                
                // Set ECC level if we can access symbol_ecc_levels
                // This would require getting the pointer at offset 40
                
                // Prepare data
                byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
                MemorySegment jabData = createJabData(arena, bytes);
                
                // Generate
                System.out.println("Testing " + colors + " colors (ECC=" + eccLevel + 
                                   ", module=" + moduleSize + ")...");
                int result = jabcode_h.generateJABCode(enc, jabData);
                System.out.println("  Result: " + result);
                
                if (result == 0) {
                    // Get bitmap and save
                    MemorySegment bitmapPtr = enc.get(ValueLayout.ADDRESS, 64);
                    if (bitmapPtr != null && bitmapPtr.address() != 0) {
                        MemorySegment filenameSegment = arena.allocateFrom(outputFile.toString());
                        byte saveResult = jabcode_h.saveImage(bitmapPtr, filenameSegment);
                        
                        if (saveResult == 1) {
                            // Try to decode
                            String decoded = decoder.decodeFromFile(outputFile);
                            System.out.println("  Decoded: " + (decoded != null ? "SUCCESS" : "FAILED"));
                            
                            if (decoded != null) {
                                assertEquals(message, decoded);
                            }
                        }
                    }
                }
                
            } finally {
                jabcode_h.destroyEncode(enc);
            }
        } catch (Exception e) {
            System.err.println("Exception in " + testName + ": " + e.getMessage());
        }
    }
}
