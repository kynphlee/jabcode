package com.jabcode.panama.colors;

/**
 * JABCode color modes (Nc) as defined by ISO/IEC 23634.
 */
public enum ColorMode {
    RESERVED_0(0, 0, "Reserved"),
    MODE_4(1, 4, "4 colors (CMYK primaries)"),
    MODE_8(2, 8, "8 colors (RGB cube vertices)"),
    MODE_16(3, 16, "16 colors (Annex G)"),
    MODE_32(4, 32, "32 colors (Annex G)"),
    MODE_64(5, 64, "64 colors (Annex G)"),
    MODE_128(6, 128, "128 colors (Annex G, interpolation)"),
    MODE_256(7, 256, "256 colors (Annex G, interpolation)");

    private final int ncValue;
    private final int colorCount;
    private final String description;

    ColorMode(int ncValue, int colorCount, String description) {
        this.ncValue = ncValue;
        this.colorCount = colorCount;
        this.description = description;
    }

    public int getNcValue() {
        return ncValue;
    }

    public int getColorCount() {
        return colorCount;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Bits per module = ceil(log2(colorCount)). Returns 0 for reserved mode 0.
     */
    public int getBitsPerModule() {
        if (colorCount <= 1) return 0;
        int bits = 0;
        int c = colorCount - 1;
        while (c > 0) {
            bits++;
            c >>= 1;
        }
        return bits;
    }

    /**
     * Modes with more than 64 colors require interpolation (Annex G).
     */
    public boolean requiresInterpolation() {
        return colorCount > 64;
    }

    public static ColorMode fromNcValue(int nc) {
        for (ColorMode m : values()) {
            if (m.ncValue == nc) return m;
        }
        throw new IllegalArgumentException("Invalid Nc value: " + nc);
    }
}
