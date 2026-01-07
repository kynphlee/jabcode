package com.jabcode.panama.colors;

public final class ColorUtils {
    private ColorUtils() {}

    public static double distance(int r1, int g1, int b1, int r2, int g2, int b2) {
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return Math.sqrt(dr * dr + dg * dg + db * db);
        }

    public static int nearestIndex(int r, int g, int b, int[][] palette) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int[] c = palette[i];
            double d = distance(r, g, b, c[0], c[1], c[2]);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    public static int[][] copy(int[][] src) {
        int[][] out = new int[src.length][3];
        for (int i = 0; i < src.length; i++) {
            out[i][0] = src[i][0];
            out[i][1] = src[i][1];
            out[i][2] = src[i][2];
        }
        return out;
    }

    public static int[] copy(int[] src) {
        return new int[]{src[0], src[1], src[2]};
    }
}
