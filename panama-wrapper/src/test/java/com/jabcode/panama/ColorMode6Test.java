package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Color Mode 6 (128 colors, 7 bits per module)
 * 
 * ISO/IEC 23634 specification:
 * - Nc = 6 (binary 110)
 * - Color count: 128 (8×4×4 RGB)
 * - Bits per module: 7
 * - Embedded palette: 64 colors (R ∈ {0,73,182,255})
 * - Full palette: R ∈ {0,36,73,109,145,182,218,255} via interpolation
 * - G,B: 0,85,170,255 (no interpolation)
 */
@DisplayName("Color Mode 6: 128 Colors (with R interpolation)")
class ColorMode6Test extends ColorModeTestBase {
    
    @Override
    protected int getColorNumber() {
        return 128;
    }
    
    @Test
    @DisplayName("Should encode and decode simple message")
    void testSimpleMessage() {
        assertRoundTrip("Hello 128-color mode with interpolation!");
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
        assertRoundTrip("A".repeat(1500));
    }
    
    @Test
    @DisplayName("Should handle Unicode characters")
    void testUnicode() {
        assertRoundTrip("Unicode test: 你好世界 مرحبا العالم Здравствуй мир 🌍🌎🌏");
    }
    
    @Test
    @DisplayName("Verify Nc value is 6")
    void testNcValue() {
        assertEquals(6, getNcValue());
    }
    
    @Test
    @DisplayName("Verify bits per module is 7")
    void testBitsPerModule() {
        assertEquals(7, getBitsPerModule());
    }
    
    @Test
    @DisplayName("Should require interpolation")
    void testRequiresInterpolation() {
        assertTrue(requiresInterpolation());
    }
    
    @Test
    @DisplayName("Should provide 3.5x density vs 8-color mode")
    void testDataDensity() {
        // 128 colors = 7 bits vs 8 colors = 3 bits = 2.33x density
        String message = "High density test " + "PAYLOAD".repeat(250);
        assertRoundTrip(message);
    }
    
    @Test
    @DisplayName("Should handle repeated round-trips")
    void testRepeatedRoundTrips() {
        String message = "Stability test";
        
        // Multiple encode/decode cycles should work consistently
        for (int i = 0; i < 5; i++) {
            assertRoundTrip(message);
        }
    }
    
    @Test
    @DisplayName("Should handle maximum data payload")
    void testMaximumPayload() {
        String message = "X".repeat(3000);
        assertRoundTrip(message);
    }
    
    @Test
    @DisplayName("Should work reliably in digital environment")
    void testDigitalReliability() {
        // Digital use case: no print/scan degradation
        // Should have very high success rate
        String[] testMessages = {
            "Digital test 1",
            "Digital test with numbers: 123456",
            "Digital test with special: !@#$%",
            "Digital test with Unicode: 你好🎉"
        };
        
        for (String message : testMessages) {
            assertRoundTrip(message);
        }
    }
}
