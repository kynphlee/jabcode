package com.jabcode.panama.colors;

import com.jabcode.panama.colors.palettes.Mode1Palette;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Mode1PaletteTest {

    @Test
    void propertiesAndMapping() {
        var p = new Mode1Palette();
        assertEquals(4, p.getColorCount());
        assertEquals(2, p.getBitsPerModule());

        // Exact RGBs map to expected indices
        assertEquals(0, p.getColorIndex(0, 0, 0));
        assertEquals(1, p.getColorIndex(0, 255, 255));
        assertEquals(2, p.getColorIndex(255, 0, 255));
        assertEquals(3, p.getColorIndex(255, 255, 0));

        // getRGB returns the same color back
        assertArrayEquals(new int[]{0,0,0}, p.getRGB(0));
        assertArrayEquals(new int[]{0,255,255}, p.getRGB(1));
        assertArrayEquals(new int[]{255,0,255}, p.getRGB(2));
        assertArrayEquals(new int[]{255,255,0}, p.getRGB(3));
    }
}
