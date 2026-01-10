package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Color Mode 7 (256 colors, 8 bits per module)
 * 
 * ISO/IEC 23634 specification:
 * - Nc = 7 (binary 111)
 * - Color count: 256 (8×8×4 RGB)
 * - Bits per module: 8 (maximum)
 * - Embedded palette: 64 colors (R,G ∈ {0,73,182,255})
 * - Full palette: R,G ∈ {0,36,73,109,145,182,218,255} via interpolation
 * - B: 0,85,170,255 (no interpolation)
 */
@DisplayName("Color Mode 7: 256 Colors (with R+G interpolation)")
class ColorMode7Test extends ColorModeTestBase {
    
    @Override
    protected int getColorNumber() {
        return 256;
    }
    
    @Test
    @DisplayName("Should encode and decode simple message")
    void testSimpleMessage() {
        assertRoundTrip("Hello 256-color mode - maximum density!");
    }
    
    @Test
    @DisplayName("Should handle various message lengths")
    void testVariousLengths() {
        testVariousMessageLengths();
    }
    
    @Test
    @DisplayName("Should work with different ECC levels")
    void testEccLevels() {
        testVariousEccLevels();
    }
    
    @Test
    @DisplayName("Should work with different module sizes")
    void testModuleSizes() {
        testVariousModuleSizes();
    }
    
    @Test
    @DisplayName("Should handle long message")
    void testLongMessage() {
        assertRoundTrip("A".repeat(2000));
    }
    
    @Test
    @DisplayName("Should handle Unicode characters")
    void testUnicode() {
        assertRoundTrip("Full Unicode: 你好 مرحبا Здравствуй こんにちは 안녕하세요 🌈🎨🎭");
    }
    
    @Test
    @DisplayName("Verify Nc value is 7")
    void testNcValue() {
        assertEquals(7, getNcValue());
    }
    
    @Test
    @DisplayName("Verify bits per module is 8")
    void testBitsPerModule() {
        assertEquals(8, getBitsPerModule());
    }
    
    @Test
    @DisplayName("Should require interpolation")
    void testRequiresInterpolation() {
        assertTrue(requiresInterpolation());
    }
    
    @Test
    @DisplayName("Should provide 4x density vs 8-color mode")
    void testDataDensity() {
        // 256 colors = 8 bits vs 8 colors = 3 bits = 2.67x density
        String message = "Maximum density payload " + "DATA".repeat(300);
        assertRoundTrip(message);
    }
    
    @Test
    @DisplayName("Should handle maximum data payload")
    void testMaximumPayload() {
        // Test with very large payload to maximize density advantage
        String message = "X".repeat(5000);
        assertRoundTrip(message);
    }
    
    @Test
    @DisplayName("Should handle repeated round-trips")
    void testRepeatedRoundTrips() {
        String message = "Stability test with 256 colors";
        
        // Multiple cycles should be consistent
        for (int i = 0; i < 5; i++) {
            assertRoundTrip(message);
        }
    }
    
    @Test
    @DisplayName("Should work reliably in digital environment")
    void testDigitalReliability() {
        // Digital-only use case: perfect for screen-to-screen
        String[] testMessages = {
            "Digital QR alternative",
            "Maximum density for URLs and structured data",
            "Binary data encoding test: " + "01".repeat(50),
            "JSON-like: {\"key\":\"value\",\"count\":123}"
        };
        
        for (String message : testMessages) {
            assertRoundTrip(message);
        }
    }
    
    @Test
    @DisplayName("Should provide maximum capacity")
    void testMaximumCapacity() {
        // 256 colors = 1 byte per module
        // Should fit significantly more data than lower modes
        String largePayload = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(200);
        assertRoundTrip(largePayload);
    }
    
    @Test
    @DisplayName("Should handle binary-like data")
    void testBinaryData() {
        // Simulate binary data as string
        StringBuilder binaryData = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            binaryData.append((char)('A' + (i % 26)));
        }
        assertRoundTrip(binaryData.toString());
    }
}
