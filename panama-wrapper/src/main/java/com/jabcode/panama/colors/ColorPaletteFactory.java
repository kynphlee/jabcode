package com.jabcode.panama.colors;

import com.jabcode.panama.colors.palettes.Mode1Palette;
import com.jabcode.panama.colors.palettes.Mode2Palette;
import com.jabcode.panama.colors.palettes.Mode3Palette;
import com.jabcode.panama.colors.palettes.Mode4Palette;
import com.jabcode.panama.colors.palettes.Mode5Palette;
import com.jabcode.panama.colors.palettes.Mode6Palette;
import com.jabcode.panama.colors.palettes.Mode7Palette;

/**
 * Factory for creating ColorPalette instances per ColorMode.
 */
public final class ColorPaletteFactory {
    private ColorPaletteFactory() {}

    public static ColorPalette create(ColorMode mode) {
        return switch (mode) {
            case MODE_4 -> new Mode1Palette();
            case MODE_8 -> new Mode2Palette();
            case MODE_16 -> new Mode3Palette();
            case MODE_32 -> new Mode4Palette();
            case MODE_64 -> new Mode5Palette();
            case MODE_128 -> new Mode6Palette();
            case MODE_256 -> new Mode7Palette();
            default -> throw new IllegalArgumentException("Unsupported mode in current phase: " + mode);
        };
    }

    /**
     * Convenience: map a color count (4, 8, etc.) to a palette.
     */
    public static ColorPalette fromColorCount(int colorCount) {
        ColorMode mode = switch (colorCount) {
            case 4 -> ColorMode.MODE_4;
            case 8 -> ColorMode.MODE_8;
            case 16 -> ColorMode.MODE_16;
            case 32 -> ColorMode.MODE_32;
            case 64 -> ColorMode.MODE_64;
            case 128 -> ColorMode.MODE_128;
            case 256 -> ColorMode.MODE_256;
            default -> throw new IllegalArgumentException("Invalid color count: " + colorCount);
        };
        return create(mode);
    }
}
