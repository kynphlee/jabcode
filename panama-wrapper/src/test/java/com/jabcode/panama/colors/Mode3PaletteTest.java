package com.jabcode.panama.colors;

import com.jabcode.panama.colors.palettes.Mode3Palette;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Mode3PaletteTest {

    @Test
    void propertiesAndMapping() {
        var p = new Mode3Palette();
        assertEquals(16, p.getColorCount());
        assertEquals(4, p.getBitsPerModule());

        // A few sample checks
        int idxBlack = p.getColorIndex(0,0,0);
        assertArrayEquals(new int[]{0,0,0}, p.getRGB(idxBlack));

        int idxMax = p.getColorIndex(255,255,255);
        int[] rgbMax = p.getRGB(idxMax);
        assertTrue(rgbMax[0] >= 170);
        assertTrue(rgbMax[1] == 255);
        assertTrue(rgbMax[2] == 255);
    }
}
