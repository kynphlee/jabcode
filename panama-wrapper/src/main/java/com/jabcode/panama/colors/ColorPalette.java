package com.jabcode.panama.colors;

/**
 * Abstraction for a JABCode color palette per color mode.
 */
public interface ColorPalette {
    /**
     * Full palette used for encoding/decoding.
     * @return array of {r,g,b}
     */
    int[][] generateFullPalette();

    /**
     * Embedded palette that fits within the 64-colour embed limit.
     * For modes <= 64, this is identical to full palette.
     */
    int[][] generateEmbeddedPalette();

    /**
     * Map color index to RGB triple.
     */
    int[] getRGB(int colorIndex);

    /**
     * Find nearest color index in palette for an RGB triple.
     */
    int getColorIndex(int r, int g, int b);

    /**
     * Bits per module for this palette (ceil(log2(color count))).
     */
    int getBitsPerModule();

    /**
     * Number of colors in this palette.
     */
    int getColorCount();
}
