package com.jabcode;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;

import com.jabcode.internal.JABCodeNative;
import com.jabcode.internal.NativeLibraryLoader;

/**
 * SimpleWrapperTest - Simple test for the JABCode native library
 */
public class SimpleWrapperTest {
    
    // Load the native library
    static {
        try {
            NativeLibraryLoader.load();
        } catch (Throwable e) {
            System.err.println("Failed to load native library: " + e.getMessage());
            throw new RuntimeException("Failed to load native library", e);
        }
    }
    
    /**
     * Main method
     * @param args command line arguments
     */
    public static void main(String[] args) {
        System.out.println("Testing JABCode native library...");
        
        try {
            // Create a JABCode encoding context
            JABCodeNative.jab_encode enc = JABCodeNative.createEncode(8, 1);
            if (enc == null) {
                throw new RuntimeException("Failed to create JABCode encoding context");
            }
            
            try {
                // Set the error correction level for each symbol
                BytePointer eccLevels = enc.symbol_ecc_levels();
                eccLevels.position(0).put((byte)3);
                
                // Create a data structure for the input data
                String text = "Hello JABCode!";
                byte[] data = text.getBytes();
                
                JABCodeNative.jab_data jabData = new JABCodeNative.jab_data();
                jabData.length(data.length);
                
                // Copy the input data to the JABCode data structure
                BytePointer dataPtr = jabData.data();
                for (int i = 0; i < data.length; i++) {
                    dataPtr.put(i, data[i]);
                }
                
                // Generate the JABCode
                int result = JABCodeNative.generateJABCode(enc, jabData);
                if (result != JABCodeNative.JAB_SUCCESS) {
                    throw new RuntimeException("Failed to generate JABCode");
                }
                
                // Get the bitmap from the encoding context
                JABCodeNative.jab_bitmap bitmap = enc.bitmap();
                if (bitmap == null) {
                    throw new RuntimeException("Failed to get JABCode bitmap");
                }
                
                // Convert the bitmap to a BufferedImage
                BufferedImage image = convertBitmapToBufferedImage(bitmap);
                
                // Save the image to a file
                File outputFile = new File("jabcode.png");
                ImageIO.write(image, "png", outputFile);
                
                System.out.println("JABCode generated and saved to jabcode.png");
            } finally {
                // Clean up the encoding context
                JABCodeNative.destroyEncode(enc);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Convert a JABCode bitmap to a BufferedImage
     * @param bitmap the JABCode bitmap
     * @return the BufferedImage
     */
    private static BufferedImage convertBitmapToBufferedImage(JABCodeNative.jab_bitmap bitmap) {
        int width = bitmap.width();
        int height = bitmap.height();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        BytePointer pixels = bitmap.pixel();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = (y * width + x) * 4; // 4 bytes per pixel (RGBA)
                int r = pixels.get(index) & 0xFF;
                int g = pixels.get(index + 1) & 0xFF;
                int b = pixels.get(index + 2) & 0xFF;
                int a = pixels.get(index + 3) & 0xFF;
                
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, y, argb);
            }
        }
        
        return image;
    }
}
