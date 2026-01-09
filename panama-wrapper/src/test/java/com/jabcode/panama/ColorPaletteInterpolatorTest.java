package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ColorPaletteInterpolator
 * 
 * Tests palette interpolation logic for modes 6 (128 colors) and 7 (256 colors)
 * according to ISO/IEC 23634 Annex F
 */
@DisplayName("ColorPaletteInterpolator")
class ColorPaletteInterpolatorTest {
    
    @Test
    @DisplayName("Should interpolate 128-color palette correctly")
    void testInterpolate128Colors() {
        // Create embedded palette (64 colors with R ∈ {0,73,182,255})
        byte[] embedded = createEmbedded128Palette();
        
        byte[] full = ColorPaletteInterpolator.interpolate128ColorPalette(embedded);
        
        // Verify full palette has 128 colors
        assertEquals(128 * 3, full.length, "Should have 128 RGB colors");
        
        // Verify R channel interpolation: {0,36,73,109,145,182,218,255}
        int[] expectedR = {0, 36, 73, 109, 145, 182, 218, 255};
        
        // Check that all expected R values exist in palette
        for (int r : expectedR) {
            boolean found = false;
            for (int i = 0; i < 128; i++) {
                if ((full[i * 3] & 0xFF) == r) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "R value " + r + " should be in palette");
        }
    }
    
    @Test
    @DisplayName("Should interpolate 256-color palette correctly")
    void testInterpolate256Colors() {
        // Create embedded palette (64 colors with R,G ∈ {0,73,182,255})
        byte[] embedded = createEmbedded256Palette();
        
        byte[] full = ColorPaletteInterpolator.interpolate256ColorPalette(embedded);
        
        // Verify full palette has 256 colors
        assertEquals(256 * 3, full.length, "Should have 256 RGB colors");
        
        // Verify both R and G channel interpolation
        int[] expectedRG = {0, 36, 73, 109, 145, 182, 218, 255};
        
        // Check that all R values exist
        for (int r : expectedRG) {
            boolean found = false;
            for (int i = 0; i < 256; i++) {
                if ((full[i * 3] & 0xFF) == r) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "R value " + r + " should be in palette");
        }
        
        // Check that all G values exist
        for (int g : expectedRG) {
            boolean found = false;
            for (int i = 0; i < 256; i++) {
                if ((full[i * 3 + 1] & 0xFF) == g) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "G value " + g + " should be in palette");
        }
    }
    
    @Test
    @DisplayName("Should find nearest color correctly")
    void testFindNearestColor() {
        // Create simple palette with 4 colors
        byte[] palette = new byte[] {
            0, 0, 0,        // Black
            (byte)255, 0, 0,        // Red
            0, (byte)255, 0,        // Green
            0, 0, (byte)255         // Blue
        };
        
        // Test exact matches
        assertEquals(0, ColorPaletteInterpolator.findNearestColor(palette, 0, 0, 0), "Should find black");
        assertEquals(1, ColorPaletteInterpolator.findNearestColor(palette, 255, 0, 0), "Should find red");
        assertEquals(2, ColorPaletteInterpolator.findNearestColor(palette, 0, 255, 0), "Should find green");
        assertEquals(3, ColorPaletteInterpolator.findNearestColor(palette, 0, 0, 255), "Should find blue");
        
        // Test approximate matches
        assertEquals(1, ColorPaletteInterpolator.findNearestColor(palette, 200, 0, 0), "Should find nearest to red");
        assertEquals(2, ColorPaletteInterpolator.findNearestColor(palette, 0, 200, 0), "Should find nearest to green");
        assertEquals(3, ColorPaletteInterpolator.findNearestColor(palette, 0, 0, 200), "Should find nearest to blue");
    }
    
    @Test
    @DisplayName("Should find nearest color with ambiguous input")
    void testFindNearestColorAmbiguous() {
        byte[] palette = createSimple8ColorPalette();
        
        // Test color in the middle of color space
        int nearest = ColorPaletteInterpolator.findNearestColor(palette, 128, 128, 128);
        assertTrue(nearest >= 0 && nearest < 8, "Should return valid index");
    }
    
    @Test
    @DisplayName("Should handle edge case colors")
    void testEdgeCaseColors() {
        byte[] palette = createSimple8ColorPalette();
        
        // All black
        int black = ColorPaletteInterpolator.findNearestColor(palette, 0, 0, 0);
        assertTrue(black >= 0 && black < 8);
        
        // All white
        int white = ColorPaletteInterpolator.findNearestColor(palette, 255, 255, 255);
        assertTrue(white >= 0 && white < 8);
    }
    
    @Test
    @DisplayName("Should maintain color distance properties")
    void testColorDistances() {
        byte[] full128 = ColorPaletteInterpolator.interpolate128ColorPalette(createEmbedded128Palette());
        
        // All colors should be distinct
        for (int i = 0; i < 128; i++) {
            for (int j = i + 1; j < 128; j++) {
                int r1 = full128[i * 3] & 0xFF;
                int g1 = full128[i * 3 + 1] & 0xFF;
                int b1 = full128[i * 3 + 2] & 0xFF;
                
                int r2 = full128[j * 3] & 0xFF;
                int g2 = full128[j * 3 + 1] & 0xFF;
                int b2 = full128[j * 3 + 2] & 0xFF;
                
                boolean distinct = (r1 != r2) || (g1 != g2) || (b1 != b2);
                assertTrue(distinct, String.format("Colors %d and %d should be distinct", i, j));
            }
        }
    }
    
    // Helper methods
    
    private byte[] createEmbedded128Palette() {
        // 64 colors: R ∈ {0,73,182,255}, G ∈ {0,85,170,255}, B ∈ {0,85,170,255}
        byte[] palette = new byte[64 * 3];
        int idx = 0;
        
        int[] rValues = {0, 73, 182, 255};
        int[] gbValues = {0, 85, 170, 255};
        
        for (int r : rValues) {
            for (int g : gbValues) {
                for (int b : gbValues) {
                    palette[idx++] = (byte)r;
                    palette[idx++] = (byte)g;
                    palette[idx++] = (byte)b;
                }
            }
        }
        
        return palette;
    }
    
    private byte[] createEmbedded256Palette() {
        // 64 colors: R,G ∈ {0,73,182,255}, B ∈ {0,85,170,255}
        byte[] palette = new byte[64 * 3];
        int idx = 0;
        
        int[] rgValues = {0, 73, 182, 255};
        int[] bValues = {0, 85, 170, 255};
        
        for (int r : rgValues) {
            for (int g : rgValues) {
                for (int b : bValues) {
                    palette[idx++] = (byte)r;
                    palette[idx++] = (byte)g;
                    palette[idx++] = (byte)b;
                }
            }
        }
        
        return palette;
    }
    
    private byte[] createSimple8ColorPalette() {
        // Standard 8-color palette (RGB cube vertices)
        return new byte[] {
            0, 0, 0,                    // 000 Black
            0, 0, (byte)255,            // 001 Blue
            0, (byte)255, 0,            // 010 Green
            0, (byte)255, (byte)255,    // 011 Cyan
            (byte)255, 0, 0,            // 100 Red
            (byte)255, 0, (byte)255,    // 101 Magenta
            (byte)255, (byte)255, 0,    // 110 Yellow
            (byte)255, (byte)255, (byte)255  // 111 White
        };
    }
}
