package com.jabcode.panama;

/**
 * Palette interpolation for high-color modes (128 and 256 colors)
 * 
 * According to ISO/IEC 23634 Annex F:
 * - Mode 6 (128 colors): Interpolate R channel only
 * - Mode 7 (256 colors): Interpolate both R and G channels
 * 
 * The embedded palette contains a subset of colors which must be
 * interpolated to reconstruct the full palette for decoding.
 */
public class ColorPaletteInterpolator {
    
    /**
     * Interpolate full 128-color palette from embedded 64-color subset
     * 
     * 128 colors = 8×4×4 (R×G×B)
     * - Embedded R: {0, 73, 182, 255} (4 values)
     * - Full R: {0, 36, 73, 109, 145, 182, 218, 255} (8 values via linear interpolation)
     * - G, B: {0, 85, 170, 255} (4 values, no interpolation needed)
     * 
     * @param embedded The 64-color embedded palette (192 bytes)
     * @return Full 128-color palette (384 bytes)
     */
    public static byte[] interpolate128ColorPalette(byte[] embedded) {
        if (embedded == null || embedded.length != 64 * 3) {
            throw new IllegalArgumentException("Embedded palette must be 64 colors (192 bytes)");
        }
        
        byte[] full = new byte[128 * 3];
        
        // R interpolation values: 8 levels
        int[] rFull = {0, 36, 73, 109, 145, 182, 218, 255};
        
        // G and B values: 4 levels (no interpolation)
        int[] gbValues = {0, 85, 170, 255};
        
        int idx = 0;
        for (int r : rFull) {
            for (int g : gbValues) {
                for (int b : gbValues) {
                    full[idx++] = (byte) r;
                    full[idx++] = (byte) g;
                    full[idx++] = (byte) b;
                }
            }
        }
        
        return full;
    }
    
    /**
     * Interpolate full 256-color palette from embedded 64-color subset
     * 
     * 256 colors = 8×8×4 (R×G×B)
     * - Embedded R, G: {0, 73, 182, 255} (4 values each)
     * - Full R, G: {0, 36, 73, 109, 145, 182, 218, 255} (8 values each via linear interpolation)
     * - B: {0, 85, 170, 255} (4 values, no interpolation needed)
     * 
     * @param embedded The 64-color embedded palette (192 bytes)
     * @return Full 256-color palette (768 bytes)
     */
    public static byte[] interpolate256ColorPalette(byte[] embedded) {
        if (embedded == null || embedded.length != 64 * 3) {
            throw new IllegalArgumentException("Embedded palette must be 64 colors (192 bytes)");
        }
        
        byte[] full = new byte[256 * 3];
        
        // R and G interpolation values: 8 levels
        int[] rgFull = {0, 36, 73, 109, 145, 182, 218, 255};
        
        // B values: 4 levels (no interpolation)
        int[] bValues = {0, 85, 170, 255};
        
        int idx = 0;
        for (int r : rgFull) {
            for (int g : rgFull) {
                for (int b : bValues) {
                    full[idx++] = (byte) r;
                    full[idx++] = (byte) g;
                    full[idx++] = (byte) b;
                }
            }
        }
        
        return full;
    }
    
    /**
     * Find the nearest color in palette for given RGB values
     * 
     * Uses Euclidean distance in RGB color space to find the
     * closest matching color index.
     * 
     * @param palette Color palette (N colors × 3 bytes RGB)
     * @param r Red component (0-255)
     * @param g Green component (0-255)
     * @param b Blue component (0-255)
     * @return Index of nearest color in palette (0-based)
     */
    public static int findNearestColor(byte[] palette, int r, int g, int b) {
        if (palette == null || palette.length % 3 != 0) {
            throw new IllegalArgumentException("Invalid palette");
        }
        
        int colorCount = palette.length / 3;
        int minDistance = Integer.MAX_VALUE;
        int nearestIndex = 0;
        
        for (int i = 0; i < colorCount; i++) {
            int pr = palette[i * 3] & 0xFF;
            int pg = palette[i * 3 + 1] & 0xFF;
            int pb = palette[i * 3 + 2] & 0xFF;
            
            // Euclidean distance squared (no need for sqrt for comparison)
            int distance = (r - pr) * (r - pr) + 
                          (g - pg) * (g - pg) + 
                          (b - pb) * (b - pb);
            
            if (distance < minDistance) {
                minDistance = distance;
                nearestIndex = i;
            }
        }
        
        return nearestIndex;
    }
    
    /**
     * Interpolate a single channel linearly
     * 
     * Helper method for custom interpolation schemes if needed.
     * 
     * @param embedded Embedded values (typically 4)
     * @param targetCount Target interpolated count (typically 8)
     * @return Interpolated values
     */
    public static int[] interpolateChannel(int[] embedded, int targetCount) {
        if (embedded == null || embedded.length == 0) {
            throw new IllegalArgumentException("Embedded values cannot be empty");
        }
        
        if (targetCount <= embedded.length) {
            return embedded;  // No interpolation needed
        }
        
        int[] result = new int[targetCount];
        
        // Linear interpolation
        for (int i = 0; i < targetCount; i++) {
            double position = (double) i / (targetCount - 1) * (embedded.length - 1);
            int lowerIndex = (int) Math.floor(position);
            int upperIndex = Math.min(lowerIndex + 1, embedded.length - 1);
            
            double fraction = position - lowerIndex;
            result[i] = (int) Math.round(
                embedded[lowerIndex] * (1 - fraction) + embedded[upperIndex] * fraction
            );
        }
        
        return result;
    }
}
