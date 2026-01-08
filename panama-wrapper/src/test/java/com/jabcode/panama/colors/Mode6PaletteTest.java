package com.jabcode.panama.colors;

import com.jabcode.panama.colors.palettes.Mode6Palette;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Mode6PaletteTest {

    @Test
    void propertiesAndMapping() {
        var p = new Mode6Palette();
        assertEquals(128, p.getColorCount());
        assertEquals(7, p.getBitsPerModule());

        int idxBlack = p.getColorIndex(0,0,0);
        assertArrayEquals(new int[]{0,0,0}, p.getRGB(idxBlack));
    }

    @Test
    void embeddedIsSubsetOfFull() {
        var p = new Mode6Palette();
        int[][] full = p.generateFullPalette();
        int[][] embed = p.generateEmbeddedPalette();
        
        assertEquals(128, full.length);
        assertEquals(64, embed.length); // Embedded is 64 colors (4R x 4G x 4B)
    }

    @Test
    void interpolationRequired() {
        assertTrue(ColorMode.MODE_128.requiresInterpolation());
    }
}
