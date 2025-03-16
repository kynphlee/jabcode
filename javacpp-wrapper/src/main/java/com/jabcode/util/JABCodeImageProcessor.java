package com.jabcode.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.imgscalr.Scalr;

/**
 * Utility class for JAB Code image processing operations
 */
public class JABCodeImageProcessor {
    
    /**
     * Enhance a JABCode image for better detection
     * @param image the image to enhance
     * @return the enhanced image
     */
    public static BufferedImage enhanceJABCode(BufferedImage image) {
        // Create a copy of the image
        BufferedImage result = new BufferedImage(
            image.getWidth(), 
            image.getHeight(), 
            BufferedImage.TYPE_INT_RGB
        );
        
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        
        // Apply contrast enhancement
        result = adjustContrast(result, 1.2f);
        
        // Apply sharpening
        result = sharpenImage(result);
        
        return result;
    }
    
    /**
     * Improve color detection for JABCode
     * This is particularly important for JAB Codes which rely on color differentiation
     * @param image the image to process
     * @return the processed image with enhanced colors
     */
    public static BufferedImage enhanceJABCodeColors(BufferedImage image) {
        // Use ColorUtils to enhance the color contrast
        return ColorUtils.enhanceColorContrast(image);
    }
    
    /**
     * Detect and extract JAB Code from an image
     * @param image the image containing the JAB Code
     * @return the extracted JAB Code image, or null if no JAB Code is detected
     */
    public static BufferedImage extractJABCode(BufferedImage image) {
        // This is a simplified implementation
        // In a real implementation, we would use more advanced techniques
        // to detect and extract the JAB Code
        
        // For now, just return the original image
        return image;
    }
    
    /**
     * Resize a JABCode image while preserving its readability
     * @param image the JABCode image to resize
     * @param targetSize the target size in pixels (width)
     * @return the resized JABCode image
     */
    public static BufferedImage resizeJABCode(BufferedImage image, int targetSize) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null");
        }
        
        if (targetSize <= 0) {
            throw new IllegalArgumentException("Target size must be positive");
        }
        
        // Use imgscalr for high-quality resizing
        // The QUALITY method is important for JAB Codes to preserve color information
        BufferedImage resized = Scalr.resize(image, Scalr.Method.QUALITY, targetSize);
        
        // Apply a sharpening filter to enhance the JAB Code modules
        // Instead of using Scalr.apply, we'll use our own sharpenImage method
        BufferedImage sharpened = sharpenImage(resized);
        
        return sharpened;
    }
    
    /**
     * Adjust the contrast of an image
     * @param image the image to adjust
     * @param contrastFactor the contrast factor (1.0 = no change, > 1.0 = increase, < 1.0 = decrease)
     * @return the adjusted image
     */
    private static BufferedImage adjustContrast(BufferedImage image, float contrastFactor) {
        BufferedImage result = new BufferedImage(
            image.getWidth(), 
            image.getHeight(), 
            BufferedImage.TYPE_INT_RGB
        );
        
        // Apply contrast adjustment
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y));
                
                int r = adjustColorComponent(color.getRed(), contrastFactor);
                int g = adjustColorComponent(color.getGreen(), contrastFactor);
                int b = adjustColorComponent(color.getBlue(), contrastFactor);
                
                result.setRGB(x, y, new Color(r, g, b).getRGB());
            }
        }
        
        return result;
    }
    
    /**
     * Adjust a color component based on the contrast factor
     * @param value the color component value (0-255)
     * @param contrastFactor the contrast factor
     * @return the adjusted color component value
     */
    private static int adjustColorComponent(int value, float contrastFactor) {
        // Apply contrast formula: newValue = ((value / 255.0 - 0.5) * contrastFactor + 0.5) * 255.0
        float normalized = value / 255.0f;
        float adjusted = ((normalized - 0.5f) * contrastFactor + 0.5f) * 255.0f;
        
        // Clamp to 0-255
        return Math.max(0, Math.min(255, Math.round(adjusted)));
    }
    
    /**
     * Apply a sharpening filter to an image
     * @param image the image to sharpen
     * @return the sharpened image
     */
    private static BufferedImage sharpenImage(BufferedImage image) {
        // Create a sharpening kernel
        float[] sharpenKernel = {
            0, -0.2f, 0,
            -0.2f, 1.8f, -0.2f,
            0, -0.2f, 0
        };
        
        Kernel kernel = new Kernel(3, 3, sharpenKernel);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        
        return op.filter(image, null);
    }
}
