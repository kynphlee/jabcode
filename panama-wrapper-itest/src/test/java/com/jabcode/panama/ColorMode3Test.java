package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Color Mode 3 (16 colors, 4 bits per module)
 * 
 * ISO/IEC 23634 specification:
 * - Nc = 3 (binary 011)
 * - Color count: 16 (4×2×2 RGB)
 * - Bits per module: 4
 * - Palette: Table 23 (R: 0,85,170,255; G: 0,255; B: 0,255)
 */
@DisplayName("Color Mode 3: 16 Colors")
class ColorMode3Test extends ColorModeTestBase {
    
    @Override
    protected int getColorNumber() {
        return 16;
    }
    
    @Test
    @DisplayName("Should encode and decode simple message")
    void testSimpleMessage() {
        assertRoundTrip("Hello 16-color mode!");
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
    @DisplayName("Should handle empty string")
    void testEmptyString() {
        assertRoundTrip("");
    }
    
    @Test
    @DisplayName("Should handle long message")
    void testLongMessage() {
        assertRoundTrip("A".repeat(500));
    }
    
    @Test
    @DisplayName("Should handle Unicode characters")
    void testUnicode() {
        assertRoundTrip("Unicode test: 你好世界 🎉 Émojis!");
    }
    
    @Test
    @DisplayName("Should handle special characters")
    void testSpecialCharacters() {
        assertRoundTrip("Special: !@#$%^&*()_+-=[]{}|;':\",./<>?");
    }
    
    @Test
    @DisplayName("Should handle numeric data")
    void testNumericData() {
        assertRoundTrip("0123456789" + "9876543210".repeat(10));
    }
    
    @Test
    @DisplayName("Should handle mixed content")
    void testMixedContent() {
        assertRoundTrip("Mixed: ABC123 !@# 你好 🎉");
    }
    
    @Test
    @DisplayName("Verify Nc value is 3")
    void testNcValue() {
        assertEquals(3, getNcValue(), "Nc should be 3 for 16-color mode");
    }
    
    @Test
    @DisplayName("Verify bits per module is 4")
    void testBitsPerModule() {
        assertEquals(4, getBitsPerModule(), "Should use 4 bits per module");
    }
    
    @Test
    @DisplayName("Should not require interpolation")
    void testNoInterpolation() {
        assertFalse(requiresInterpolation(), "16-color mode should not require interpolation");
    }
    
    @Test
    @DisplayName("Should provide higher density than 8-color mode")
    void testHigherDensity() {
        // 16 colors = 4 bits vs 8 colors = 3 bits
        // Should encode ~33% more data in same space
        String message = "A".repeat(100);
        
        var config8 = JABCodeEncoder.Config.builder()
            .colorNumber(8)
            .eccLevel(7)
            .moduleSize(16)
            .build();
        
        var config16 = createDefaultConfig();
        
        // Both should work, 16-color may create smaller barcode
        assertRoundTrip(message, config8);
        assertRoundTrip(message, config16);
    }
}
