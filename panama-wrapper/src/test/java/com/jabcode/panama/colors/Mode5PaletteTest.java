package com.jabcode.panama.colors;

import com.jabcode.panama.colors.palettes.Mode5Palette;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Mode5PaletteTest {

    @Test
    void propertiesAndMapping() {
        var p = new Mode5Palette();
        assertEquals(64, p.getColorCount());
        assertEquals(6, p.getBitsPerModule());

        int idxBlack = p.getColorIndex(0,0,0);
        assertArrayEquals(new int[]{0,0,0}, p.getRGB(idxBlack));

        int idxWhite = p.getColorIndex(255,255,255);
        int[] rgbWhite = p.getRGB(idxWhite);
        assertEquals(255, rgbWhite[0]);
        assertEquals(255, rgbWhite[1]);
        assertEquals(255, rgbWhite[2]);
    }

    @Test
    void fullAndEmbeddedAreSame() {
        var p = new Mode5Palette();
        int[][] full = p.generateFullPalette();
        int[][] embed = p.generateEmbeddedPalette();
        assertEquals(full.length, embed.length);
    }
}
