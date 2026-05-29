package com.jabcode.panama.colors;

import com.jabcode.panama.colors.palettes.Mode2Palette;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Mode2PaletteTest {

    @Test
    void propertiesAndMapping() {
        var p = new Mode2Palette();
        assertEquals(8, p.getColorCount());
        assertEquals(3, p.getBitsPerModule());

        assertEquals(0, p.getColorIndex(0, 0, 0));     // Black
        assertEquals(1, p.getColorIndex(0, 0, 255));   // Blue
        assertEquals(2, p.getColorIndex(0, 255, 0));   // Green
        assertEquals(7, p.getColorIndex(255, 255, 255)); // White

        assertArrayEquals(new int[]{0,0,0}, p.getRGB(0));
        assertArrayEquals(new int[]{255,255,255}, p.getRGB(7));
    }
}
