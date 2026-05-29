package com.jabcode.panama.colors;

import com.jabcode.panama.colors.palettes.Mode7Palette;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Mode7PaletteTest {

    @Test
    void propertiesAndMapping() {
        var p = new Mode7Palette();
        assertEquals(256, p.getColorCount());
        assertEquals(8, p.getBitsPerModule());

        int idxBlack = p.getColorIndex(0,0,0);
        assertArrayEquals(new int[]{0,0,0}, p.getRGB(idxBlack));
    }

    @Test
    void embeddedIsSubsetOfFull() {
        var p = new Mode7Palette();
        int[][] full = p.generateFullPalette();
        int[][] embed = p.generateEmbeddedPalette();
        
        assertEquals(256, full.length);
        assertEquals(64, embed.length); // Embedded is 64 colors (4R x 4G x 4B)
    }

    @Test
    void interpolationRequired() {
        assertTrue(ColorMode.MODE_256.requiresInterpolation());
    }

    @Test
    void allIndicesValid() {
        var p = new Mode7Palette();
        for (int i = 0; i < 256; i++) {
            int[] rgb = p.getRGB(i);
            assertNotNull(rgb);
            assertEquals(3, rgb.length);
        }
    }
}
