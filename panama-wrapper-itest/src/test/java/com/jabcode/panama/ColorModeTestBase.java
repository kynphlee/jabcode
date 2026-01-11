package com.jabcode.panama;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for color mode testing with common utilities and assertions
 */
public abstract class ColorModeTestBase {
    
    protected JABCodeEncoder encoder;
    protected JABCodeDecoder decoder;
    
    @TempDir
    protected Path tempDir;
    
    @BeforeEach
    void setUp() {
        encoder = new JABCodeEncoder();
        decoder = new JABCodeDecoder();
    }
    
    @AfterEach
    void tearDown() {
        // Phase 1: Reset native decoder state between tests
        // Prevents Panama FFI Arena lifecycle crashes in multi-test scenarios
        JABCodeDecoder.resetDecoderState();
    }
    
    /**
     * Get the color number for this test
     */
    protected abstract int getColorNumber();
    
    /**
     * Get the Nc value (0-7) for this color mode
     */
    protected int getNcValue() {
        return switch (getColorNumber()) {
            case 4 -> 1;
            case 8 -> 2;
            case 16 -> 3;
            case 32 -> 4;
            case 64 -> 5;
            case 128 -> 6;
            case 256 -> 7;
            default -> throw new IllegalArgumentException("Invalid color number: " + getColorNumber());
        };
    }
    
    /**
     * Get bits per module for this color mode
     */
    protected int getBitsPerModule() {
        return switch (getColorNumber()) {
            case 4 -> 2;
            case 8 -> 3;
            case 16 -> 4;
            case 32 -> 5;
            case 64 -> 6;
            case 128 -> 7;
            case 256 -> 8;
            default -> throw new IllegalArgumentException("Invalid color number: " + getColorNumber());
        };
    }
    
    /**
     * Check if this mode requires palette interpolation
     */
    protected boolean requiresInterpolation() {
        return getColorNumber() >= 128;
    }
    
    /**
     * Create a default config for this color mode
     */
    protected JABCodeEncoder.Config createDefaultConfig() {
        // Higher color modes need more ECC to force larger barcodes with alignment patterns
        // Modes 1-2 (4-8 colors): ECC 7
        // Modes 3-5 (16-64 colors): ECC 9
        // Modes 6-7 (128-256 colors): ECC 10
        int eccLevel = 7;
        int colorNumber = getColorNumber();
        if (colorNumber >= 128) {
            eccLevel = 10;  // Maximum ECC for 128-256 colors
        } else if (colorNumber >= 16) {
            eccLevel = 9;   // High ECC for 16-64 colors
        }
        
        return JABCodeEncoder.Config.builder()
            .colorNumber(colorNumber)
            .eccLevel(eccLevel)
            .moduleSize(16)  // Larger modules for digital
            .build();
    }
    
    /**
     * Create config with custom parameters
     */
    protected JABCodeEncoder.Config createConfig(int eccLevel, int moduleSize) {
        return JABCodeEncoder.Config.builder()
            .colorNumber(getColorNumber())
            .eccLevel(eccLevel)
            .moduleSize(moduleSize)
            .build();
    }
    
    /**
     * Perform round-trip test: encode then decode
     */
    protected void assertRoundTrip(String message, JABCodeEncoder.Config config) {
        Path outputFile = tempDir.resolve("test_" + getColorNumber() + "colors.png");
        
        // Encode
        boolean encoded = encoder.encodeToPNG(message, outputFile.toString(), config);
        assertTrue(encoded, 
            String.format("Encoding should succeed for %d-color mode", getColorNumber()));
        
        // Verify file exists
        assertTrue(outputFile.toFile().exists(), "Output file should exist");
        assertTrue(outputFile.toFile().length() > 0, "Output file should not be empty");
        
        // Decode with observation collection for adaptive palette (Nc >= 5)
        boolean collectObservations = getNcValue() >= 5;
        JABCodeDecoder.DecodedResultWithObservations result = 
            decoder.decodeWithObservations(outputFile, JABCodeDecoder.MODE_NORMAL, collectObservations);
        
        assertNotNull(result, "Decode result should not be null");
        assertTrue(result.isSuccess(), 
            String.format("Decoding should succeed for %d-color mode", getColorNumber()));
        
        String decoded = result.getData();
        assertNotNull(decoded, 
            String.format("Decoded data should not be null for %d-color mode", getColorNumber()));
        
        // Verify content
        assertEquals(message, decoded, 
            String.format("Round-trip should preserve data for %d-color mode", getColorNumber()));
    }
    
    /**
     * Perform round-trip test with default config
     */
    protected void assertRoundTrip(String message) {
        assertRoundTrip(message, createDefaultConfig());
    }
    
    /**
     * Test encoding with various message lengths
     */
    protected void testVariousMessageLengths() {
        String[] messages = {
            "A",                          // 1 char
            "Hello",                      // 5 chars
            "Test message 123",           // 17 chars
            "A".repeat(50),              // 50 chars
            "Testing with special chars: !@#$%^&*()", // Special chars
            "Unicode: 你好世界 🎉"          // Unicode
        };
        
        for (String message : messages) {
            assertRoundTrip(message);
        }
    }
    
    /**
     * Test with various ECC levels
     */
    protected void testVariousEccLevels() {
        String message = "Test ECC levels";
        
        for (int ecc : new int[]{3, 5, 7, 9}) {
            assertRoundTrip(message, createConfig(ecc, 16));
        }
    }
    
    /**
     * Test with various module sizes
     */
    protected void testVariousModuleSizes() {
        String message = "Test module sizes";
        
        for (int size : new int[]{8, 12, 16, 20}) {
            assertRoundTrip(message, createConfig(7, size));
        }
    }
    
    /**
     * Assert that palette has correct number of colors
     */
    protected void assertPaletteSize(byte[] palette, int expectedColors) {
        assertNotNull(palette, "Palette should not be null");
        assertEquals(expectedColors * 3, palette.length, 
            String.format("Palette should have %d colors (RGB)", expectedColors));
    }
    
    /**
     * Assert that colors in palette are distinct
     */
    protected void assertColorsDistinct(byte[] palette) {
        int colorCount = palette.length / 3;
        
        for (int i = 0; i < colorCount; i++) {
            for (int j = i + 1; j < colorCount; j++) {
                int r1 = palette[i * 3] & 0xFF;
                int g1 = palette[i * 3 + 1] & 0xFF;
                int b1 = palette[i * 3 + 2] & 0xFF;
                
                int r2 = palette[j * 3] & 0xFF;
                int g2 = palette[j * 3 + 1] & 0xFF;
                int b2 = palette[j * 3 + 2] & 0xFF;
                
                boolean distinct = (r1 != r2) || (g1 != g2) || (b1 != b2);
                assertTrue(distinct, 
                    String.format("Colors %d and %d should be distinct: (%d,%d,%d) vs (%d,%d,%d)",
                        i, j, r1, g1, b1, r2, g2, b2));
            }
        }
    }
    
    /**
     * Calculate minimum color distance in palette
     */
    protected int calculateMinColorDistance(byte[] palette) {
        int colorCount = palette.length / 3;
        int minDistance = Integer.MAX_VALUE;
        
        for (int i = 0; i < colorCount; i++) {
            for (int j = i + 1; j < colorCount; j++) {
                int r1 = palette[i * 3] & 0xFF;
                int g1 = palette[i * 3 + 1] & 0xFF;
                int b1 = palette[i * 3 + 2] & 0xFF;
                
                int r2 = palette[j * 3] & 0xFF;
                int g2 = palette[j * 3 + 1] & 0xFF;
                int b2 = palette[j * 3 + 2] & 0xFF;
                
                int distance = (r1 - r2) * (r1 - r2) + 
                              (g1 - g2) * (g1 - g2) + 
                              (b1 - b2) * (b1 - b2);
                
                minDistance = Math.min(minDistance, distance);
            }
        }
        
        return minDistance;
    }
    
    /**
     * Assert minimum color distance meets threshold
     */
    protected void assertMinColorDistance(byte[] palette, int minThreshold) {
        int minDistance = calculateMinColorDistance(palette);
        assertTrue(minDistance >= minThreshold,
            String.format("Minimum color distance %d should be >= %d", minDistance, minThreshold));
    }
}
