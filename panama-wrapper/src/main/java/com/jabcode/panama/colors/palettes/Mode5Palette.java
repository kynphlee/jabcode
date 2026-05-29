package com.jabcode.panama.colors.palettes;

import com.jabcode.panama.colors.ColorMode;
import com.jabcode.panama.colors.ColorPalette;
import com.jabcode.panama.colors.ColorUtils;

public class Mode5Palette implements ColorPalette {
    private static final int[] V = {0, 85, 170, 255};
    private static final int[][] FULL = generate(V, V, V); // 4x4x4 = 64

    private static int[][] generate(int[] rv, int[] gv, int[] bv) {
        int[][] out = new int[rv.length * gv.length * bv.length][3];
        int idx = 0;
        for (int r : rv) {
            for (int g : gv) {
                for (int b : bv) {
                    out[idx][0] = r;
                    out[idx][1] = g;
                    out[idx][2] = b;
                    idx++;
                }
            }
        }
        return out;
    }

    @Override
    public int[][] generateFullPalette() {
        return ColorUtils.copy(FULL);
    }

    @Override
    public int[][] generateEmbeddedPalette() {
        return generateFullPalette();
    }

    @Override
    public int[] getRGB(int colorIndex) {
        if (colorIndex < 0 || colorIndex >= FULL.length) {
            throw new IllegalArgumentException("Index must be 0-63");
        }
        return ColorUtils.copy(FULL[colorIndex]);
    }

    @Override
    public int getColorIndex(int r, int g, int b) {
        return ColorUtils.nearestIndex(r, g, b, FULL);
    }

    @Override
    public int getBitsPerModule() {
        return ColorMode.MODE_64.getBitsPerModule();
    }

    @Override
    public int getColorCount() {
        return FULL.length;
    }
}
