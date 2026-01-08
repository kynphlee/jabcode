package com.jabcode.panama.colors.palettes;

import com.jabcode.panama.colors.ColorMode;
import com.jabcode.panama.colors.ColorPalette;
import com.jabcode.panama.colors.ColorUtils;

public class Mode7Palette implements ColorPalette {
    // Full mode: 8 R x 8 G x 4 B = 256
    private static final int[] R_FULL = {0, 36, 73, 109, 146, 182, 219, 255};
    private static final int[] G_FULL = {0, 36, 73, 109, 146, 182, 219, 255};
    private static final int[] B_FULL = {0, 85, 170, 255};

    // Embedded subset: 4 R x 4 G x 4 B = 64 (R and G subsets)
    private static final int[] R_EMBED = {0, 73, 182, 255};
    private static final int[] G_EMBED = {0, 73, 182, 255};

    private static final int[][] FULL = generate(R_FULL, G_FULL, B_FULL);
    private static final int[][] EMBED = generate(R_EMBED, G_EMBED, B_FULL);

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
        return ColorUtils.copy(EMBED);
    }

    @Override
    public int[] getRGB(int colorIndex) {
        if (colorIndex < 0 || colorIndex >= FULL.length) {
            throw new IllegalArgumentException("Index must be 0-255");
        }
        return ColorUtils.copy(FULL[colorIndex]);
    }

    @Override
    public int getColorIndex(int r, int g, int b) {
        return ColorUtils.nearestIndex(r, g, b, FULL);
    }

    @Override
    public int getBitsPerModule() {
        return ColorMode.MODE_256.getBitsPerModule();
    }

    @Override
    public int getColorCount() {
        return FULL.length;
    }
}
