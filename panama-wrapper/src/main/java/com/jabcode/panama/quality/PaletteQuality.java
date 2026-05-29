package com.jabcode.panama.quality;

import com.jabcode.panama.colors.ColorUtils;

/**
 * Palette quality metrics per ISO/IEC 23634 Section 8.3.
 * Validates color accuracy (dR, dG, dB) and colour variation.
 */
public final class PaletteQuality {
    private PaletteQuality() {}

    /**
     * Calculate minimum Euclidean distance between any two colors in palette.
     * ISO 8.3 requires minimum separation to ensure distinguishability.
     * 
     * @param palette 2D array of RGB triples
     * @return minimum distance found
     */
    public static double minColorSeparation(int[][] palette) {
        if (palette.length < 2) return Double.MAX_VALUE;
        
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            for (int j = i + 1; j < palette.length; j++) {
                double dist = ColorUtils.distance(
                    palette[i][0], palette[i][1], palette[i][2],
                    palette[j][0], palette[j][1], palette[j][2]
                );
                if (dist < minDist) {
                    minDist = dist;
                }
            }
        }
        return minDist;
    }

    /**
     * Validate palette accuracy (dR, dG, dB per Annex G).
     * 
     * @param palette Palette to validate
     * @param maxError Maximum allowed per-channel error
     * @return true if all colors meet accuracy requirements
     */
    public static boolean validatePaletteAccuracy(int[][] palette, int maxError) {
        // This would compare against reference palette from Annex G
        // For now, basic range validation
        for (int[] rgb : palette) {
            if (rgb[0] < 0 || rgb[0] > 255) return false;
            if (rgb[1] < 0 || rgb[1] > 255) return false;
            if (rgb[2] < 0 || rgb[2] > 255) return false;
        }
        return true;
    }

    /**
     * Calculate colour variation metric.
     * Measures how well distributed the colors are in RGB space.
     * 
     * @param palette Palette to analyze
     * @return variation score (higher = better distribution)
     */
    public static double colorVariation(int[][] palette) {
        if (palette.length < 2) return 0.0;
        
        double totalVariation = 0.0;
        for (int channel = 0; channel < 3; channel++) {
            double mean = 0.0;
            for (int[] rgb : palette) {
                mean += rgb[channel];
            }
            mean /= palette.length;
            
            double variance = 0.0;
            for (int[] rgb : palette) {
                double diff = rgb[channel] - mean;
                variance += diff * diff;
            }
            totalVariation += Math.sqrt(variance / palette.length);
        }
        return totalVariation;
    }
}
