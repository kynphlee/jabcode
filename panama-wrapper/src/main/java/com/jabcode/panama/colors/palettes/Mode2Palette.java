package com.jabcode.panama.colors.palettes;

import com.jabcode.panama.colors.ColorMode;
import com.jabcode.panama.colors.ColorPalette;
import com.jabcode.panama.colors.ColorUtils;

public class Mode2Palette implements ColorPalette {
    private static final int[][] COLORS = new int[][]{
        {0, 0, 0},       // 0 Black
        {0, 0, 255},     // 1 Blue
        {0, 255, 0},     // 2 Green
        {0, 255, 255},   // 3 Cyan
        {255, 0, 0},     // 4 Red
        {255, 0, 255},   // 5 Magenta
        {255, 255, 0},   // 6 Yellow
        {255, 255, 255}  // 7 White
    };

    @Override
    public int[][] generateFullPalette() {
        return ColorUtils.copy(COLORS);
    }

    @Override
    public int[][] generateEmbeddedPalette() {
        return generateFullPalette();
    }

    @Override
    public int[] getRGB(int colorIndex) {
        if (colorIndex < 0 || colorIndex >= COLORS.length) {
            throw new IllegalArgumentException("Index must be 0-7");
        }
        return ColorUtils.copy(COLORS[colorIndex]);
    }

    @Override
    public int getColorIndex(int r, int g, int b) {
        return ColorUtils.nearestIndex(r, g, b, COLORS);
    }

    @Override
    public int getBitsPerModule() {
        return ColorMode.MODE_8.getBitsPerModule();
    }

    @Override
    public int getColorCount() {
        return COLORS.length;
    }
}
