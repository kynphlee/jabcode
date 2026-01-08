package com.jabcode.panama.colors;

import com.jabcode.panama.colors.palettes.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColorPaletteFactoryTest {

    @Test
    void createReturnsCorrectImplementation() {
        assertTrue(ColorPaletteFactory.create(ColorMode.MODE_4) instanceof Mode1Palette);
        assertTrue(ColorPaletteFactory.create(ColorMode.MODE_8) instanceof Mode2Palette);
        assertTrue(ColorPaletteFactory.create(ColorMode.MODE_16) instanceof Mode3Palette);
        assertTrue(ColorPaletteFactory.create(ColorMode.MODE_32) instanceof Mode4Palette);
        assertTrue(ColorPaletteFactory.create(ColorMode.MODE_64) instanceof Mode5Palette);
        assertTrue(ColorPaletteFactory.create(ColorMode.MODE_128) instanceof Mode6Palette);
        assertTrue(ColorPaletteFactory.create(ColorMode.MODE_256) instanceof Mode7Palette);
    }

    @Test
    void fromColorCountReturnsCorrectPalette() {
        assertEquals(4, ColorPaletteFactory.fromColorCount(4).getColorCount());
        assertEquals(8, ColorPaletteFactory.fromColorCount(8).getColorCount());
        assertEquals(16, ColorPaletteFactory.fromColorCount(16).getColorCount());
        assertEquals(32, ColorPaletteFactory.fromColorCount(32).getColorCount());
        assertEquals(64, ColorPaletteFactory.fromColorCount(64).getColorCount());
        assertEquals(128, ColorPaletteFactory.fromColorCount(128).getColorCount());
        assertEquals(256, ColorPaletteFactory.fromColorCount(256).getColorCount());
    }

    @Test
    void invalidColorCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> ColorPaletteFactory.fromColorCount(3));
        assertThrows(IllegalArgumentException.class, () -> ColorPaletteFactory.fromColorCount(100));
    }

    @Test
    void reservedModeThrows() {
        assertThrows(IllegalArgumentException.class, () -> ColorPaletteFactory.create(ColorMode.RESERVED_0));
    }
}
