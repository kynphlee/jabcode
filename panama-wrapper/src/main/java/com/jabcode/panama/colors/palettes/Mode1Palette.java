package com.jabcode.panama.colors.palettes;

import com.jabcode.panama.colors.ColorMode;
import com.jabcode.panama.colors.ColorPalette;
import com.jabcode.panama.colors.ColorUtils;

public class Mode1Palette implements ColorPalette {
    private static final int[][] COLORS = new int[][]{
        {0, 0, 0},       // Black (index 0)
        {0, 255, 255},   // Cyan  (index 1)
        {255, 0, 255},   // Magenta (index 2)
        {255, 255, 0}    // Yellow (index 3)
    };

    @Override
    public int[][] generateFullPalette() {
        return ColorUtils.copy(COLORS);
    }

    @Override
    public int[][] generateEmbeddedPalette() {
        // For <=64 colors, embedded == full
        return generateFullPalette();
    }

    @Override
    public int[] getRGB(int colorIndex) {
        if (colorIndex < 0 || colorIndex >= COLORS.length) {
            throw new IllegalArgumentException("Index must be 0-3");
        }
        return ColorUtils.copy(COLORS[colorIndex]);
    }

    @Override
    public int getColorIndex(int r, int g, int b) {
        return ColorUtils.nearestIndex(r, g, b, COLORS);
    }

    @Override
    public int getBitsPerModule() {
        return ColorMode.MODE_4.getBitsPerModule();
    }

    @Override
    public int getColorCount() {
        return COLORS.length;
    }
}
