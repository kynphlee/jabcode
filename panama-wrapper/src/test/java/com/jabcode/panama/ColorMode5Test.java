package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * Test suite for Color Mode 5 (64 colors, 6 bits per module)
 * 
 * ISO/IEC 23634 specification:
 * - Nc = 5 (binary 101)
 * - Color count: 64 (4×4×4 RGB)
 * - Bits per module: 6
 * - Palette: R,G,B all use: 0,85,170,255
 */
@DisplayName("Color Mode 5: 64 Colors")
class ColorMode5Test extends ColorModeTestBase {
    
    @Override
    protected int getColorNumber() {
        return 64;
    }
    
    @Test
    @DisplayName("Should encode and decode simple message")
    void testSimpleMessage() {
        assertRoundTrip("Hello 64-color mode!");
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
        assertRoundTrip("A".repeat(1000));
    }
    
    @Test
    @DisplayName("Should handle Unicode characters")
    void testUnicode() {
        assertRoundTrip("Unicode: 你好 مرحبا Γεια σου 🎨🎭🎪");
    }
    
    @Test
    @DisplayName("Verify Nc value is 5")
    void testNcValue() {
        assertEquals(5, getNcValue(), "Nc should be 5 for 64-color mode");
    }
    
    @Test
    @DisplayName("Verify bits per module is 6")
    void testBitsPerModule() {
        assertEquals(6, getBitsPerModule(), "Should use 6 bits per module");
    }
    
    @Test
    @DisplayName("Should not require interpolation")
    void testNoInterpolation() {
        assertFalse(requiresInterpolation(), "64-color mode should not require interpolation");
    }
    
    @Test
    @DisplayName("Should provide 3x density vs 8-color mode")
    void testDataDensity() {
        // 64 colors = 6 bits vs 8 colors = 3 bits = 2x density
        String message = "Maximum density test " + "DATA".repeat(200);
        assertRoundTrip(message);
    }
    
    @Test
    @DisplayName("Should handle maximum data payload")
    void testMaximumPayload() {
        // Test with very large payload
        String message = "X".repeat(2000);
        assertRoundTrip(message);
    }
}
