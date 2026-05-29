package com.jabcode.panama.colors;

import com.jabcode.panama.colors.palettes.Mode4Palette;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Mode4PaletteTest {

    @Test
    void propertiesAndMapping() {
        var p = new Mode4Palette();
        assertEquals(32, p.getColorCount());
        assertEquals(5, p.getBitsPerModule());

        int idxBlack = p.getColorIndex(0,0,0);
        assertArrayEquals(new int[]{0,0,0}, p.getRGB(idxBlack));

        int idxWhite = p.getColorIndex(255,255,255);
        int[] rgbWhite = p.getRGB(idxWhite);
        assertTrue(rgbWhite[0] >= 170);
        assertTrue(rgbWhite[1] >= 170);
        assertTrue(rgbWhite[2] == 255);
    }

    @Test
    void fullAndEmbeddedAreSame() {
        var p = new Mode4Palette();
        int[][] full = p.generateFullPalette();
        int[][] embed = p.generateEmbeddedPalette();
        assertEquals(full.length, embed.length);
    }
}
