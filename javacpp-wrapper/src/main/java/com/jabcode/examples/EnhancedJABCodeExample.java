package com.jabcode.examples;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.apache.commons.math3.util.Precision;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReader;

import com.jabcode.core.JABCode;
import com.jabcode.core.JABCode.ColorMode;
import com.jabcode.util.ColorUtils;
import com.jabcode.util.JABCodeImageProcessor;

/**
 * Enhanced example demonstrating JABCode generation and decoding
 * with advanced image processing using the new dependencies
 */
public class EnhancedJABCodeExample {

    /**
     * Main method to demonstrate JABCode functionality
     * @param args command line arguments
     */
    public static void main(String[] args) {
        try {
            System.out.println("JABCode Enhanced Example");
            System.out.println("========================");
            
            // Generate JABCodes with different color modes
            generateJABCodes();
            
            // Process and enhance a JABCode image
            enhanceJABCodeImage();
            
            // Decode JABCodes
            decodeJABCodes();
            
            System.out.println("Example completed successfully!");
        } catch (Exception e) {
            System.err.println("Error in JABCode example: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Generate JABCodes with different color modes
     */
    private static void generateJABCodes() throws IOException {
        System.out.println("\nGenerating JABCodes with different color modes...");
        
        // Data to encode
        String data = "JABCode Example: This is a test of the JABCode library with enhanced features.";
        
        // Generate JABCodes with different color modes
        ColorMode[] colorModes = {
            ColorMode.QUATERNARY,
            ColorMode.OCTAL,
            ColorMode.HEXADECIMAL,
            ColorMode.MODE_32
        };
        
        for (ColorMode colorMode : colorModes) {
            // Create a JABCode with the specified color mode
            BufferedImage jabCodeImage = JABCode.builder()
                .withColorMode(colorMode)
                .withSymbolCount(1)
                .withEccLevel(3)
                .withData(data)
                .build();
            
            // Save the JABCode as an image
            String filename = "enhanced_jabcode_" + colorMode.getColorCount() + ".png";
            JABCode.save(jabCodeImage, filename);
            
            System.out.println("  Generated " + filename + " with " + colorMode.getColorCount() + " colors");
            
            // Calculate the theoretical capacity
            int bitsPerModule = (int) Math.floor(Math.log(colorMode.getColorCount()) / Math.log(2));
            // We don't have direct access to module count, so we'll estimate based on image size
            int moduleCount = (jabCodeImage.getWidth() * jabCodeImage.getHeight()) / 100; // rough estimate
            double capacityBytes = Precision.round(moduleCount * bitsPerModule / 8.0, 2);
            
            System.out.println("  Theoretical capacity: " + capacityBytes + " bytes");
        }
    }
    
    /**
     * Process and enhance a JABCode image using OpenCV
     */
    private static void enhanceJABCodeImage() throws IOException {
        System.out.println("\nEnhancing JABCode image...");
        
        // Load the JABCode image
        String inputFilename = "enhanced_jabcode_8.png";
        BufferedImage inputImage = ImageIO.read(new File(inputFilename));
        
        // Convert BufferedImage to OpenCV Mat
        Java2DFrameConverter java2dConverter = new Java2DFrameConverter();
        OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();
        Frame frame = java2dConverter.convert(inputImage);
        Mat mat = matConverter.convert(frame);
        
        // Apply image processing
        // 1. Resize the image
        Mat resizedMat = new Mat();
        resize(mat, resizedMat, new Size(inputImage.getWidth() * 2, inputImage.getHeight() * 2), 0, 0, INTER_CUBIC);
        
        // 2. Apply adaptive threshold to improve contrast
        Mat processedMat = new Mat();
        cvtColor(resizedMat, processedMat, COLOR_BGR2GRAY);
        adaptiveThreshold(processedMat, processedMat, 255, ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY, 11, 2);
        
        // 3. Apply morphological operations to clean up the image
        Mat kernel = getStructuringElement(MORPH_RECT, new Size(3, 3));
        morphologyEx(processedMat, processedMat, MORPH_OPEN, kernel);
        
        // Convert back to BufferedImage
        Frame processedFrame = matConverter.convert(processedMat);
        BufferedImage processedImage = java2dConverter.convert(processedFrame);
        
        // Save the processed image
        String outputFilename = "enhanced_jabcode_processed.png";
        ImageIO.write(processedImage, "PNG", new File(outputFilename));
        
        System.out.println("  Processed image saved as " + outputFilename);
    }
    
    /**
     * Decode JABCodes
     */
    private static void decodeJABCodes() {
        System.out.println("\nDecoding JABCodes...");
        
        // Files to decode
        String[] files = {
            "enhanced_jabcode_4.png",
            "enhanced_jabcode_8.png",
            "enhanced_jabcode_16.png",
            "enhanced_jabcode_32.png"
        };
        
        for (String filename : files) {
            try {
                // Decode the JABCode
                String decodedData = JABCode.decodeToString(filename);
                
                // Print the decoded data
                System.out.println("  Decoded " + filename + ": " + decodedData);
            } catch (Exception e) {
                System.err.println("  Error decoding " + filename + ": " + e.getMessage());
            }
        }
    }
}
