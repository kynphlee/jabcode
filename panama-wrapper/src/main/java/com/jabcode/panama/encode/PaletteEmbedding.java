package com.jabcode.panama.encode;

import com.jabcode.panama.colors.ColorPalette;

/**
 * Palette embedding utilities for JABCode encoding.
 * Per ISO/IEC 23634, palettes up to 64 colors are fully embedded.
 * Modes 128 and 256 embed a 64-color subset.
 */
public final class PaletteEmbedding {
    private PaletteEmbedding() {}

    /**
     * Encode palette colors into metadata region bytes.
     * Each color is 3 bytes (R,G,B).
     * 
     * @param palette The palette to embed
     * @return byte array containing RGB triples (length = colorCount * 3)
     */
    public static byte[] encodePalette(ColorPalette palette) {
        int[][] colors = palette.generateEmbeddedPalette();
        byte[] bytes = new byte[colors.length * 3];
        int idx = 0;
        for (int[] rgb : colors) {
            bytes[idx++] = (byte) rgb[0];
            bytes[idx++] = (byte) rgb[1];
            bytes[idx++] = (byte) rgb[2];
        }
        return bytes;
    }

    /**
     * Decode palette from embedded metadata bytes.
     * 
     * @param bytes RGB byte array (length must be multiple of 3)
     * @return 2D array of RGB triples
     */
    public static int[][] decodePalette(byte[] bytes) {
        if (bytes.length % 3 != 0) {
            throw new IllegalArgumentException("Palette bytes length must be multiple of 3");
        }
        int colorCount = bytes.length / 3;
        int[][] palette = new int[colorCount][3];
        int idx = 0;
        for (int i = 0; i < colorCount; i++) {
            palette[i][0] = Byte.toUnsignedInt(bytes[idx++]);
            palette[i][1] = Byte.toUnsignedInt(bytes[idx++]);
            palette[i][2] = Byte.toUnsignedInt(bytes[idx++]);
        }
        return palette;
    }
}
