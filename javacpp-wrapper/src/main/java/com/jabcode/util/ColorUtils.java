package com.jabcode.util;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for color operations related to JABCode
 */
public class ColorUtils {
    
    /**
     * Get the color palette for a JABCode with the specified color mode
     * @param colorMode the color mode (number of colors)
     * @return the color palette as an array of Color objects
     */
    public static Color[] getJABCodePalette(int colorMode) {
        switch (colorMode) {
            case 2: // Binary (black and white)
                return new Color[] {
                    Color.BLACK,
                    Color.WHITE
                };
            case 4: // Quaternary
                return new Color[] {
                    Color.BLACK,
                    Color.WHITE,
                    Color.RED,
                    Color.GREEN
                };
            case 8: // Octal
                return new Color[] {
                    Color.BLACK,
                    Color.WHITE,
                    Color.RED,
                    Color.GREEN,
                    Color.BLUE,
                    Color.YELLOW,
                    Color.CYAN,
                    Color.MAGENTA
                };
            case 16: // Hexadecimal
                return new Color[] {
                    Color.BLACK,
                    Color.WHITE,
                    Color.RED,
                    Color.GREEN,
                    Color.BLUE,
                    Color.YELLOW,
                    Color.CYAN,
                    Color.MAGENTA,
                    new Color(128, 0, 0),    // Dark red
                    new Color(0, 128, 0),    // Dark green
                    new Color(0, 0, 128),    // Dark blue
                    new Color(128, 128, 0),  // Olive
                    new Color(128, 0, 128),  // Purple
                    new Color(0, 128, 128),  // Teal
                    new Color(128, 128, 128),// Gray
                    new Color(255, 128, 0)   // Orange
                };
            default:
                // For higher color modes, generate a color palette
                return generateColorPalette(colorMode);
        }
    }
    
    /**
     * Generate a color palette with the specified number of colors
     * @param colorCount the number of colors
     * @return the generated color palette
     */
    private static Color[] generateColorPalette(int colorCount) {
        Color[] palette = new Color[colorCount];
        
        // Always include black and white
        palette[0] = Color.BLACK;
        palette[1] = Color.WHITE;
        
        // Generate the rest of the colors using HSB color space
        for (int i = 2; i < colorCount; i++) {
            float hue = (float)(i - 2) / (colorCount - 2);
            float saturation = 0.9f;
            float brightness = 0.9f;
            
            palette[i] = Color.getHSBColor(hue, saturation, brightness);
        }
        
        return palette;
    }
    
    /**
     * Quantize the colors in an image to match the JABCode color palette
     * @param image the image to quantize
     * @param colorMode the color mode (number of colors)
     * @return the quantized image
     */
    public static BufferedImage quantizeColors(BufferedImage image, int colorMode) {
        // Get the JABCode color palette
        Color[] palette = getJABCodePalette(colorMode);
        
        // Create a new image with the same dimensions
        BufferedImage result = new BufferedImage(
            image.getWidth(),
            image.getHeight(),
            BufferedImage.TYPE_INT_RGB
        );
        
        // Quantize each pixel
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y));
                Color closestColor = findClosestColor(color, palette);
                result.setRGB(x, y, closestColor.getRGB());
            }
        }
        
        return result;
    }
    
    /**
     * Find the closest color in a palette to a given color
     * @param color the color to match
     * @param palette the color palette
     * @return the closest color from the palette
     */
    private static Color findClosestColor(Color color, Color[] palette) {
        Color closestColor = palette[0];
        double minDistance = Double.MAX_VALUE;
        
        for (Color paletteColor : palette) {
            double distance = colorDistance(color, paletteColor);
            if (distance < minDistance) {
                minDistance = distance;
                closestColor = paletteColor;
            }
        }
        
        return closestColor;
    }
    
    /**
     * Calculate the distance between two colors in RGB space
     * @param c1 the first color
     * @param c2 the second color
     * @return the distance between the colors
     */
    private static double colorDistance(Color c1, Color c2) {
        int rDiff = c1.getRed() - c2.getRed();
        int gDiff = c1.getGreen() - c2.getGreen();
        int bDiff = c1.getBlue() - c2.getBlue();
        
        // Weighted Euclidean distance in RGB space
        // The weights are based on human perception of color
        return Math.sqrt(
            0.299 * rDiff * rDiff +
            0.587 * gDiff * gDiff +
            0.114 * bDiff * bDiff
        );
    }
    
    /**
     * Enhance the contrast between colors in an image
     * This is particularly useful for JABCode images to improve readability
     * @param image the image to enhance
     * @return the enhanced image
     */
    public static BufferedImage enhanceColorContrast(BufferedImage image) {
        // Create a new image with the same dimensions
        BufferedImage result = new BufferedImage(
            image.getWidth(),
            image.getHeight(),
            BufferedImage.TYPE_INT_RGB
        );
        
        // Get the unique colors in the image
        List<Color> uniqueColors = getUniqueColors(image);
        
        // If there are too many unique colors, reduce them
        if (uniqueColors.size() > 16) {
            // Quantize the image to 16 colors
            return quantizeColors(image, 16);
        }
        
        // Enhance the contrast between the unique colors
        Map<Integer, Color> enhancedColors = new HashMap<>();
        for (Color color : uniqueColors) {
            // Convert to HSB
            float[] hsb = Color.RGBtoHSB(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                null
            );
            
            // Enhance saturation and brightness
            hsb[1] = Math.min(1.0f, hsb[1] * 1.2f); // Increase saturation
            hsb[2] = Math.max(0.2f, Math.min(1.0f, hsb[2] * 1.1f)); // Adjust brightness
            
            // Convert back to RGB
            Color enhancedColor = Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
            enhancedColors.put(color.getRGB(), enhancedColor);
        }
        
        // Apply the enhanced colors to the image
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                Color enhancedColor = enhancedColors.get(rgb);
                result.setRGB(x, y, enhancedColor.getRGB());
            }
        }
        
        return result;
    }
    
    /**
     * Get the unique colors in an image
     * @param image the image to analyze
     * @return the list of unique colors
     */
    private static List<Color> getUniqueColors(BufferedImage image) {
        Map<Integer, Color> uniqueColors = new HashMap<>();
        
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                uniqueColors.put(rgb, new Color(rgb));
            }
        }
        
        return new ArrayList<>(uniqueColors.values());
    }
}
