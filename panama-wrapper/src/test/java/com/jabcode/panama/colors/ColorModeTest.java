package com.jabcode.panama.colors;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColorModeTest {

    @Test
    void bitsPerModuleAreCorrect() {
        assertEquals(2, ColorMode.MODE_4.getBitsPerModule());
        assertEquals(3, ColorMode.MODE_8.getBitsPerModule());
        assertEquals(8, ColorMode.MODE_256.getBitsPerModule());
    }

    @Test
    void interpolationFlag() {
        assertFalse(ColorMode.MODE_4.requiresInterpolation());
        assertFalse(ColorMode.MODE_64.requiresInterpolation());
        assertTrue(ColorMode.MODE_128.requiresInterpolation());
        assertTrue(ColorMode.MODE_256.requiresInterpolation());
    }

    @Test
    void fromNcValueMapping() {
        assertEquals(ColorMode.MODE_4, ColorMode.fromNcValue(1));
        assertEquals(ColorMode.MODE_8, ColorMode.fromNcValue(2));
        assertEquals(ColorMode.MODE_256, ColorMode.fromNcValue(7));
        assertThrows(IllegalArgumentException.class, () -> ColorMode.fromNcValue(9));
    }
}
