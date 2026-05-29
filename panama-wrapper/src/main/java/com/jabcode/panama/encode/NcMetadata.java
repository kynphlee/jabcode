package com.jabcode.panama.encode;

import com.jabcode.panama.colors.ColorMode;

/**
 * Nc (color mode) metadata encoding/decoding per ISO/IEC 23634.
 * Part I: Nc encoded in 3-color mode (Black=0, Cyan=1, Yellow=2) for backward compatibility.
 * Part II: Nc encoded in the symbol's full palette.
 */
public final class NcMetadata {
    private NcMetadata() {}

    /** 3-color palette for Nc Part I encoding */
    private static final int[][] NC_PALETTE_3COLOR = {
        {0, 0, 0},       // Black (bit value 0)
        {0, 255, 255},   // Cyan  (bit value 1)
        {255, 255, 0}    // Yellow (bit value 2)
    };

    /**
     * Encode Nc value (0-7) as 3-bit value using 3-color palette indices.
     * Returns array of 3 color indices from {0=Black, 1=Cyan, 2=Yellow}.
     * 
     * @param mode ColorMode to encode
     * @return 3-element array of palette indices
     */
    public static int[] encodeNcPart1(ColorMode mode) {
        int nc = mode.getNcValue();
        return new int[]{
            (nc >> 2) & 1,  // bit 2
            (nc >> 1) & 1,  // bit 1
            nc & 1          // bit 0
        };
    }

    /**
     * Decode Nc value from 3 color indices in Part I (3-color mode).
     * 
     * @param indices 3-element array of color indices {0,1,2}
     * @return Decoded Nc value (0-7)
     */
    public static int decodeNcPart1(int[] indices) {
        if (indices.length != 3) {
            throw new IllegalArgumentException("Nc Part I requires 3 indices");
        }
        return (indices[0] << 2) | (indices[1] << 1) | indices[2];
    }

    /**
     * Encode Nc value in Part II using the symbol's full palette.
     * Same 3-bit encoding but using palette's first colors.
     * 
     * @param mode ColorMode to encode
     * @return 3-element array of palette indices
     */
    public static int[] encodeNcPart2(ColorMode mode) {
        // Part II uses the same bit pattern but with the full palette's colors
        return encodeNcPart1(mode);
    }

    /**
     * Decode Nc value from Part II.
     * 
     * @param indices 3-element array from full palette
     * @return Decoded Nc value (0-7)
     */
    public static int decodeNcPart2(int[] indices) {
        return decodeNcPart1(indices);
    }

    /**
     * Get the 3-color palette RGB values for Part I.
     */
    public static int[][] get3ColorPalette() {
        return NC_PALETTE_3COLOR.clone();
    }
}
