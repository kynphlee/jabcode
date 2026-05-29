package com.jabcode.panama.colors;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColorUtilsTest {

    @Test
    void distanceIsSymmetricAndZeroForSame() {
        double d0 = ColorUtils.distance(10, 20, 30, 10, 20, 30);
        assertEquals(0.0, d0, 1e-9);
        double d1 = ColorUtils.distance(0, 0, 0, 255, 255, 255);
        double d2 = ColorUtils.distance(255, 255, 255, 0, 0, 0);
        assertEquals(d1, d2, 1e-9);
    }

    @Test
    void nearestIndexFindsExactMatch() {
        int[][] palette = new int[][]{{0,0,0},{255,0,0},{0,255,0},{0,0,255}};
        assertEquals(0, ColorUtils.nearestIndex(0,0,0, palette));
        assertEquals(1, ColorUtils.nearestIndex(255,0,0, palette));
        assertEquals(2, ColorUtils.nearestIndex(0,255,0, palette));
        assertEquals(3, ColorUtils.nearestIndex(0,0,255, palette));
    }
}
