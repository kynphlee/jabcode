package com.jabcode.internal;

import java.awt.image.BufferedImage;
import java.awt.Color;
import java.util.Arrays;

import org.apache.commons.math3.util.FastMath;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.CvScalar;
import org.bytedeco.opencv.opencv_core.TermCriteria;

import com.jabcode.OptimizedJABCode.ColorMode;

/**
 * ColorModeConverter - Utility class for converting between different color modes
 * This class provides methods for converting between different color modes for JABCode
 */
public class ColorModeConverter {
    
    // Standard palettes for different color modes
    private static final int[][] BINARY_PALETTE = {
        {0, 0, 0},       // Black
        {255, 255, 255}  // White
    };
    
    private static final int[][] QUATERNARY_PALETTE = {
        {0, 255, 255},   // Cyan
        {255, 0, 255},   // Magenta
        {255, 255, 0},   // Yellow
        {0, 0, 0}        // Black
    };
    
    private static final int[][] OCTAL_PALETTE = {
        {0, 255, 255},   // Cyan
        {255, 0, 255},   // Magenta
        {255, 255, 0},   // Yellow
        {0, 0, 0},       // Black
        {0, 0, 255},     // Blue
        {255, 0, 0},     // Red
        {0, 255, 0},     // Green
        {255, 255, 255}  // White
    };
    
    /**
     * Convert a color mode to the native color mode supported by the JABCode library
     * @param colorMode the color mode to convert
     * @return the native color mode (4 or 8)
     */
    public static int toNativeColorMode(ColorMode colorMode) {
        switch (colorMode) {
            case BINARY:
                return 4; // Use 4-color mode with custom palette
            case QUATERNARY:
                return 4;
            case OCTAL:
                return 8;
            case HEXADECIMAL:
            case MODE_32:
            case MODE_64:
            case MODE_128:
            case MODE_256:
                return 8; // Use 8-color mode with dithering
            default:
                throw new IllegalArgumentException("Unsupported color mode: " + colorMode);
        }
    }
    
    /**
     * Get the palette for a given color mode
     * @param colorMode the color mode
     * @return the palette as an array of RGB values
     */
    public static int[][] getPalette(ColorMode colorMode) {
        switch (colorMode) {
            case BINARY:
                return BINARY_PALETTE;
            case QUATERNARY:
                return QUATERNARY_PALETTE;
            case OCTAL:
                return OCTAL_PALETTE;
            case HEXADECIMAL:
                return generatePalette(16);
            case MODE_32:
                return generatePalette(32);
            case MODE_64:
                return generatePalette(64);
            case MODE_128:
                return generatePalette(128);
            case MODE_256:
                return generatePalette(256);
            default:
                throw new IllegalArgumentException("Unsupported color mode: " + colorMode);
        }
    }
    
    /**
     * Generate a palette with the given number of colors
     * @param colorCount the number of colors
     * @return the palette as an array of RGB values
     */
    private static int[][] generatePalette(int colorCount) {
        int[][] palette = new int[colorCount][3];
        
        // For higher color counts, generate a mathematically distributed palette
        if (colorCount <= 8) {
            // For 8 or fewer colors, use the standard palettes
            if (colorCount == 2) {
                return BINARY_PALETTE;
            } else if (colorCount == 4) {
                return QUATERNARY_PALETTE;
            } else if (colorCount == 8) {
                return OCTAL_PALETTE;
            }
        }
        
        // For higher color counts, generate a mathematically distributed palette
        // This is a simple RGB cube subdivision
        int colorCubeSide = (int) Math.ceil(Math.pow(colorCount, 1.0/3.0));
        int step = 255 / (colorCubeSide - 1);
        
        int index = 0;
        for (int r = 0; r < colorCubeSide && index < colorCount; r++) {
            for (int g = 0; g < colorCubeSide && index < colorCount; g++) {
                for (int b = 0; b < colorCubeSide && index < colorCount; b++) {
                    palette[index][0] = r * step;
                    palette[index][1] = g * step;
                    palette[index][2] = b * step;
                    index++;
                    
                    if (index >= colorCount) {
                        break;
                    }
                }
            }
        }
        
        return palette;
    }
    
    /**
     * Convert an image to the given color mode
     * @param image the image to convert
     * @param colorMode the target color mode
     * @return the converted image
     */
    public static BufferedImage convertToColorMode(BufferedImage image, ColorMode colorMode) {
        switch (colorMode) {
            case BINARY:
                return convertToBinary(image);
            case QUATERNARY:
                return quantizeToColorMode(image, colorMode);
            case OCTAL:
                return quantizeToColorMode(image, colorMode);
            case HEXADECIMAL:
            case MODE_32:
            case MODE_64:
            case MODE_128:
            case MODE_256:
                return ditherToColorMode(image, colorMode);
            default:
                throw new IllegalArgumentException("Unsupported color mode: " + colorMode);
        }
    }
    
    /**
     * Convert an image to binary (black and white)
     * @param image the image to convert
     * @return the binary image
     */
    private static BufferedImage convertToBinary(BufferedImage image) {
        // Convert to OpenCV Mat
        Java2DFrameConverter java2dConverter = new Java2DFrameConverter();
        OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();
        
        Mat mat = matConverter.convert(java2dConverter.convert(image));
        Mat grayMat = new Mat();
        Mat binaryMat = new Mat();
        
        // Convert to grayscale
        opencv_imgproc.cvtColor(mat, grayMat, opencv_imgproc.COLOR_BGR2GRAY);
        
        // Apply threshold to get binary image
        opencv_imgproc.threshold(grayMat, binaryMat, 128, 255, opencv_imgproc.THRESH_BINARY);
        
        // Convert back to BufferedImage
        return java2dConverter.convert(matConverter.convert(binaryMat));
    }
    
    /**
     * Quantize an image to the given color mode using simple color mapping
     * @param image the image to quantize
     * @param colorMode the target color mode
     * @return the quantized image
     */
    private static BufferedImage quantizeToColorMode(BufferedImage image, ColorMode colorMode) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[][] palette = getPalette(colorMode);
        
        // Create a copy of the image to work with
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        // Map each pixel to the closest color in the palette
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = new Color(image.getRGB(x, y));
                int r = color.getRed();
                int g = color.getGreen();
                int b = color.getBlue();
                
                int closestIndex = findClosestColorIndex(r, g, b, palette);
                int[] closestColor = palette[closestIndex];
                
                result.setRGB(x, y, new Color(closestColor[0], closestColor[1], closestColor[2]).getRGB());
            }
        }
        
        return result;
    }
    
    /**
     * Apply dithering to an image to simulate a higher color count
     * @param image the image to dither
     * @param colorMode the target color mode
     * @return the dithered image
     */
    private static BufferedImage ditherToColorMode(BufferedImage image, ColorMode colorMode) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[][] palette = getPalette(colorMode);
        
        // Create a copy of the image to work with
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        // Apply Floyd-Steinberg dithering
        int[][] errorR = new int[height][width];
        int[][] errorG = new int[height][width];
        int[][] errorB = new int[height][width];
        
        // Initialize image data
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = new Color(image.getRGB(x, y));
                errorR[y][x] = color.getRed();
                errorG[y][x] = color.getGreen();
                errorB[y][x] = color.getBlue();
            }
        }
        
        // Apply dithering
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Get the current pixel color with error
                int r = errorR[y][x];
                int g = errorG[y][x];
                int b = errorB[y][x];
                
                // Clamp values to 0-255 range
                r = Math.min(255, Math.max(0, r));
                g = Math.min(255, Math.max(0, g));
                b = Math.min(255, Math.max(0, b));
                
                // Find the closest color in the palette
                int closestIndex = findClosestColorIndex(r, g, b, palette);
                int[] closestColor = palette[closestIndex];
                
                // Set the pixel to the closest color
                result.setRGB(x, y, new Color(closestColor[0], closestColor[1], closestColor[2]).getRGB());
                
                // Calculate the error
                int errR = r - closestColor[0];
                int errG = g - closestColor[1];
                int errB = b - closestColor[2];
                
                // Distribute the error to neighboring pixels
                if (x + 1 < width) {
                    errorR[y][x + 1] += errR * 7 / 16;
                    errorG[y][x + 1] += errG * 7 / 16;
                    errorB[y][x + 1] += errB * 7 / 16;
                }
                
                if (y + 1 < height) {
                    if (x - 1 >= 0) {
                        errorR[y + 1][x - 1] += errR * 3 / 16;
                        errorG[y + 1][x - 1] += errG * 3 / 16;
                        errorB[y + 1][x - 1] += errB * 3 / 16;
                    }
                    
                    errorR[y + 1][x] += errR * 5 / 16;
                    errorG[y + 1][x] += errG * 5 / 16;
                    errorB[y + 1][x] += errB * 5 / 16;
                    
                    if (x + 1 < width) {
                        errorR[y + 1][x + 1] += errR * 1 / 16;
                        errorG[y + 1][x + 1] += errG * 1 / 16;
                        errorB[y + 1][x + 1] += errB * 1 / 16;
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Find the index of the closest color in the palette
     * @param r the red component
     * @param g the green component
     * @param b the blue component
     * @param palette the palette to search
     * @return the index of the closest color
     */
    private static int findClosestColorIndex(int r, int g, int b, int[][] palette) {
        int closestIndex = 0;
        double closestDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < palette.length; i++) {
            int[] color = palette[i];
            double distance = colorDistance(r, g, b, color[0], color[1], color[2]);
            
            if (distance < closestDistance) {
                closestDistance = distance;
                closestIndex = i;
            }
        }
        
        return closestIndex;
    }
    
    /**
     * Calculate the distance between two colors in RGB space
     * @param r1 the red component of the first color
     * @param g1 the green component of the first color
     * @param b1 the blue component of the first color
     * @param r2 the red component of the second color
     * @param g2 the green component of the second color
     * @param b2 the blue component of the second color
     * @return the distance between the two colors
     */
    private static double colorDistance(int r1, int g1, int b1, int r2, int g2, int b2) {
        // Use Euclidean distance in RGB space
        double dr = r1 - r2;
        double dg = g1 - g2;
        double db = b1 - b2;
        
        return FastMath.sqrt(dr * dr + dg * dg + db * db);
    }
    
    /**
     * Convert a BufferedImage to a JABCodeNative.jab_bitmap
     * @param image the image to convert
     * @return the jab_bitmap
     */
    public static JABCodeNative.jab_bitmap convertToJabBitmap(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Create a new jab_bitmap
        JABCodeNative.jab_bitmap bitmap = new JABCodeNative.jab_bitmap();
        bitmap.width(width);
        bitmap.height(height);
        bitmap.bits_per_pixel(JABCodeNative.BITMAP_BITS_PER_PIXEL);
        bitmap.bits_per_channel(JABCodeNative.BITMAP_BITS_PER_CHANNEL);
        bitmap.channel_count(JABCodeNative.BITMAP_CHANNEL_COUNT);
        
        // Copy the pixel data from the BufferedImage to the jab_bitmap
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int index = (y * width + x) * JABCodeNative.BITMAP_CHANNEL_COUNT;
                
                // RGBA format
                bitmap.pixel(index, (byte) ((rgb >> 16) & 0xFF)); // R
                bitmap.pixel(index + 1, (byte) ((rgb >> 8) & 0xFF)); // G
                bitmap.pixel(index + 2, (byte) (rgb & 0xFF)); // B
                bitmap.pixel(index + 3, (byte) ((rgb >> 24) & 0xFF)); // A
            }
        }
        
        return bitmap;
    }
    
    /**
     * Convert a JABCodeNative.jab_bitmap to a BufferedImage
     * @param bitmap the jab_bitmap to convert
     * @return the BufferedImage
     */
    public static BufferedImage convertToBufferedImage(JABCodeNative.jab_bitmap bitmap) {
        int width = bitmap.width();
        int height = bitmap.height();
        
        // Create a new BufferedImage
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        // Copy the pixel data from the jab_bitmap to the BufferedImage
        BytePointer pixelData = bitmap.pixel();
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = (y * width + x) * JABCodeNative.BITMAP_CHANNEL_COUNT;
                
                // RGBA format
                int r = pixelData.get(index) & 0xFF;
                int g = pixelData.get(index + 1) & 0xFF;
                int b = pixelData.get(index + 2) & 0xFF;
                int a = pixelData.get(index + 3) & 0xFF;
                
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, y, argb);
            }
        }
        
        return image;
    }
}
