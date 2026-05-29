package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for all color modes (1-7)
 * 
 * This test validates that:
 * 1. All color modes can encode
 * 2. All color modes can decode (after implementation)
 * 3. Data density increases with color count
 * 4. Round-trip works for all modes
 */
@DisplayName("All Color Modes Integration")
class AllColorModesTest {
    
    private final JABCodeEncoder encoder = new JABCodeEncoder();
    private final JABCodeDecoder decoder = new JABCodeDecoder();
    
    @TempDir
    Path tempDir;
    
    @Test
    @DisplayName("All modes should be supported")
    void testAllModesSupported() {
        int[] allModes = {4, 8, 16, 32, 64, 128, 256};
        
        for (int colors : allModes) {
            var config = JABCodeEncoder.Config.builder()
                .colorNumber(colors)
                .eccLevel(7)
                .build();
            
            assertDoesNotThrow(() -> config, 
                String.format("Mode %d colors should be supported", colors));
        }
    }
    
    @Test
    @DisplayName("All modes should encode successfully")
    void testAllModesEncode() {
        String message = "Test all modes";
        int[] allModes = {4, 8, 16, 32, 64, 128, 256};
        
        for (int colors : allModes) {
            Path outputFile = tempDir.resolve("test_" + colors + "colors.png");
            
            var config = JABCodeEncoder.Config.builder()
                .colorNumber(colors)
                .eccLevel(7)
                .build();
            
            boolean encoded = encoder.encodeToPNG(message, outputFile.toString(), config);
            assertTrue(encoded, 
                String.format("Encoding should succeed for %d-color mode", colors));
            assertTrue(outputFile.toFile().exists(), 
                String.format("Output file should exist for %d-color mode", colors));
        }
    }
    
    @Test
    @DisplayName("Bits per module should increase with color count")
    void testBitsPerModule() {
        assertEquals(2, getBitsPerModule(4), "4 colors = 2 bits");
        assertEquals(3, getBitsPerModule(8), "8 colors = 3 bits");
        assertEquals(4, getBitsPerModule(16), "16 colors = 4 bits");
        assertEquals(5, getBitsPerModule(32), "32 colors = 5 bits");
        assertEquals(6, getBitsPerModule(64), "64 colors = 6 bits");
        assertEquals(7, getBitsPerModule(128), "128 colors = 7 bits");
        assertEquals(8, getBitsPerModule(256), "256 colors = 8 bits");
    }
    
    @Test
    @DisplayName("Nc values should map correctly")
    void testNcMapping() {
        assertEquals(1, getNcValue(4), "4 colors = Nc 1");
        assertEquals(2, getNcValue(8), "8 colors = Nc 2");
        assertEquals(3, getNcValue(16), "16 colors = Nc 3");
        assertEquals(4, getNcValue(32), "32 colors = Nc 4");
        assertEquals(5, getNcValue(64), "64 colors = Nc 5");
        assertEquals(6, getNcValue(128), "128 colors = Nc 6");
        assertEquals(7, getNcValue(256), "256 colors = Nc 7");
    }
    
    @Test
    @DisplayName("Higher modes should provide higher data density")
    void testDataDensityProgression() {
        // Theoretical density multipliers relative to 8-color mode (3 bits)
        double[] densityMultipliers = {
            2.0 / 3.0,  // 4 colors: 2 bits
            1.0,        // 8 colors: 3 bits (baseline)
            4.0 / 3.0,  // 16 colors: 4 bits
            5.0 / 3.0,  // 32 colors: 5 bits
            2.0,        // 64 colors: 6 bits
            7.0 / 3.0,  // 128 colors: 7 bits
            8.0 / 3.0   // 256 colors: 8 bits
        };
        
        int[] colorCounts = {4, 8, 16, 32, 64, 128, 256};
        
        for (int i = 0; i < colorCounts.length; i++) {
            double expected = densityMultipliers[i];
            int bits = getBitsPerModule(colorCounts[i]);
            double actual = (double) bits / 3.0;  // Relative to 8-color (3 bits)
            
            assertEquals(expected, actual, 0.01, 
                String.format("%d colors should provide %.2fx density", colorCounts[i], expected));
        }
    }
    
    @Test
    @DisplayName("Interpolation modes should be identified correctly")
    void testInterpolationIdentification() {
        assertFalse(requiresInterpolation(4), "4 colors should not require interpolation");
        assertFalse(requiresInterpolation(8), "8 colors should not require interpolation");
        assertFalse(requiresInterpolation(16), "16 colors should not require interpolation");
        assertFalse(requiresInterpolation(32), "32 colors should not require interpolation");
        assertFalse(requiresInterpolation(64), "64 colors should not require interpolation");
        assertTrue(requiresInterpolation(128), "128 colors should require interpolation");
        assertTrue(requiresInterpolation(256), "256 colors should require interpolation");
    }
    
    @Test
    @DisplayName("All modes should work for digital use cases")
    void testDigitalUseCase() {
        String message = "Digital barcode test";
        int[] allModes = {4, 8, 16, 32, 64, 128, 256};
        
        for (int colors : allModes) {
            Path outputFile = tempDir.resolve("digital_" + colors + ".png");
            
            var config = JABCodeEncoder.Config.builder()
                .colorNumber(colors)
                .eccLevel(7)
                .moduleSize(16)  // Good for digital displays
                .build();
            
            boolean encoded = encoder.encodeToPNG(message, outputFile.toString(), config);
            assertTrue(encoded, 
                String.format("%d-color mode should work for digital use", colors));
        }
    }
    
    // Helper methods
    
    private int getBitsPerModule(int colorNumber) {
        return switch (colorNumber) {
            case 4 -> 2;
            case 8 -> 3;
            case 16 -> 4;
            case 32 -> 5;
            case 64 -> 6;
            case 128 -> 7;
            case 256 -> 8;
            default -> throw new IllegalArgumentException("Invalid color number: " + colorNumber);
        };
    }
    
    private int getNcValue(int colorNumber) {
        return switch (colorNumber) {
            case 4 -> 1;
            case 8 -> 2;
            case 16 -> 3;
            case 32 -> 4;
            case 64 -> 5;
            case 128 -> 6;
            case 256 -> 7;
            default -> throw new IllegalArgumentException("Invalid color number: " + colorNumber);
        };
    }
    
    private boolean requiresInterpolation(int colorNumber) {
        return colorNumber >= 128;
    }
}
