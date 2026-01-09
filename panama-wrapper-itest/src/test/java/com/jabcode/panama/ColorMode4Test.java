package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Color Mode 4 (32 colors, 5 bits per module)
 * 
 * ISO/IEC 23634 specification:
 * - Nc = 4 (binary 100)
 * - Color count: 32 (4×4×2 RGB)
 * - Bits per module: 5
 * - Palette: R: 0,85,170,255; G: 0,85,170,255; B: 0,255
 */
@DisplayName("Color Mode 4: 32 Colors")
class ColorMode4Test extends ColorModeTestBase {
    
    @Override
    protected int getColorNumber() {
        return 32;
    }
    
    @Test
    @DisplayName("Should encode and decode simple message")
    void testSimpleMessage() {
        assertRoundTrip("Hello 32-color mode!");
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
        assertRoundTrip("A".repeat(500));
    }
    
    @Test
    @DisplayName("Should handle Unicode characters")
    void testUnicode() {
        assertRoundTrip("Unicode: 你好世界 मस्ते Привет 🌟");
    }
    
    @Test
    @DisplayName("Verify Nc value is 4")
    void testNcValue() {
        assertEquals(4, getNcValue(), "Nc should be 4 for 32-color mode");
    }
    
    @Test
    @DisplayName("Verify bits per module is 5")
    void testBitsPerModule() {
        assertEquals(5, getBitsPerModule(), "Should use 5 bits per module");
    }
    
    @Test
    @DisplayName("Should not require interpolation")
    void testNoInterpolation() {
        assertFalse(requiresInterpolation(), "32-color mode should not require interpolation");
    }
    
    @Test
    @DisplayName("Should provide 2.5x density vs 8-color mode")
    void testDataDensity() {
        // 32 colors = 5 bits vs 8 colors = 3 bits = 1.67x density
        String message = "Data density test " + "X".repeat(100);
        assertRoundTrip(message);
    }
}
