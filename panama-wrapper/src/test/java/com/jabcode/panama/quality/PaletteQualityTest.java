package com.jabcode.panama.quality;

import com.jabcode.panama.colors.ColorMode;
import com.jabcode.panama.colors.ColorPaletteFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaletteQualityTest {

    @Test
    void minColorSeparationMode4() {
        var palette = ColorPaletteFactory.create(ColorMode.MODE_4);
        int[][] colors = palette.generateFullPalette();
        
        double minDist = PaletteQuality.minColorSeparation(colors);
        assertTrue(minDist > 0, "Colors must be distinguishable");
        assertTrue(minDist > 200, "Mode 4 colors should be well separated");
    }

    @Test
    void minColorSeparationMode8() {
        var palette = ColorPaletteFactory.create(ColorMode.MODE_8);
        int[][] colors = palette.generateFullPalette();
        
        double minDist = PaletteQuality.minColorSeparation(colors);
        assertTrue(minDist > 0);
        assertTrue(minDist > 100, "Mode 8 RGB cube vertices should be separated");
    }

    @Test
    void minColorSeparationSingleColor() {
        int[][] single = {{0, 0, 0}};
        double minDist = PaletteQuality.minColorSeparation(single);
        assertEquals(Double.MAX_VALUE, minDist);
    }

    @Test
    void minColorSeparationIdenticalColors() {
        int[][] identical = {{100, 100, 100}, {100, 100, 100}};
        double minDist = PaletteQuality.minColorSeparation(identical);
        assertEquals(0.0, minDist, 1e-9);
    }

    @Test
    void validatePaletteAccuracyAllModes() {
        for (ColorMode mode : ColorMode.values()) {
            if (mode == ColorMode.RESERVED_0) continue;
            
            var palette = ColorPaletteFactory.create(mode);
            int[][] colors = palette.generateFullPalette();
            
            assertTrue(PaletteQuality.validatePaletteAccuracy(colors, 5),
                mode + " palette should meet accuracy requirements");
        }
    }

    @Test
    void validatePaletteAccuracyRejectsInvalid() {
        int[][] invalid = {{-1, 0, 0}, {0, 256, 0}};
        assertFalse(PaletteQuality.validatePaletteAccuracy(invalid, 5));
    }

    @Test
    void colorVariationIsPositiveForMultiColorPalettes() {
        var palette4 = ColorPaletteFactory.create(ColorMode.MODE_4);
        var palette64 = ColorPaletteFactory.create(ColorMode.MODE_64);
        
        double var4 = PaletteQuality.colorVariation(palette4.generateFullPalette());
        double var64 = PaletteQuality.colorVariation(palette64.generateFullPalette());
        
        // Both should have positive variation
        assertTrue(var4 > 0, "Mode 4 should have color variation");
        assertTrue(var64 > 0, "Mode 64 should have color variation");
        
        // Note: variation depends on distribution, not just count
        // Mode 4 CMYK primaries are far apart, Mode 64 is more uniform
    }

    @Test
    void colorVariationSingleColorIsZero() {
        int[][] single = {{128, 128, 128}};
        double variation = PaletteQuality.colorVariation(single);
        assertEquals(0.0, variation, 1e-9);
    }

    @Test
    void allModesPassQualityChecks() {
        for (ColorMode mode : ColorMode.values()) {
            if (mode == ColorMode.RESERVED_0) continue;
            
            var palette = ColorPaletteFactory.create(mode);
            int[][] colors = palette.generateFullPalette();
            
            // Minimum separation check
            double minSep = PaletteQuality.minColorSeparation(colors);
            assertTrue(minSep > 0, mode + " has indistinguishable colors");
            
            // Accuracy check
            assertTrue(PaletteQuality.validatePaletteAccuracy(colors, 5),
                mode + " fails accuracy check");
            
            // Variation check
            double variation = PaletteQuality.colorVariation(colors);
            assertTrue(variation >= 0, mode + " has negative variation");
        }
    }
}
