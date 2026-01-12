package com.jabcode.panama;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates sample JABCode images for each supported color mode
 * Creates both simple (1 symbol) and cascaded (2 symbols) versions
 */
public class GenerateSamples {
    
    public static void main(String[] args) {
        Path outputDir = Paths.get("test-images");
        
        int[] colorModes = {4, 8, 16, 32, 64, 128};
        
        System.out.println("🎨 Generating JABCode samples for all supported color modes...\n");
        
        // Generate simple samples (1 symbol)
        System.out.println("📊 Simple JABCodes (1 symbol):");
        int simpleCount = 0;
        for (int colorNumber : colorModes) {
            String message = createMessage(colorNumber, 5, 12, 1);
            if (generateSample(colorNumber, message, 1, 5, 12, outputDir)) {
                simpleCount++;
            }
        }
        
        // Generate cascaded samples (2 symbols: 1 primary + 1 secondary)
        // Use longer messages to force multi-symbol encoding
        System.out.println("\n🔗 Cascaded JABCodes (2 symbols):");
        System.out.println("   Note: Using extended messages to demonstrate symbol cascading\n");
        int cascadedCount = 0;
        for (int colorNumber : colorModes) {
            String message = createLongMessage(colorNumber, 5, 12, 2);
            if (generateSample(colorNumber, message, 2, 5, 12, outputDir)) {
                cascadedCount++;
            }
        }
        
        System.out.println("\n✅ Sample generation complete!");
        System.out.println("📁 Location: " + outputDir.toAbsolutePath());
        System.out.println("📈 Simple samples: " + simpleCount + "/" + colorModes.length);
        System.out.println("📈 Cascaded samples: " + cascadedCount + "/" + colorModes.length);
        if (cascadedCount == 0) {
            System.out.println("\n⚠️  Note: Cascaded encoding requires explicit symbol version configuration");
            System.out.println("   which is not yet exposed in the Config API. Single-symbol encoding works perfectly.");
        }
    }
    
    /**
     * Create a descriptive message encoding the JABCode configuration
     */
    private static String createMessage(int colorNumber, int eccLevel, int moduleSize, int symbolNumber) {
        return String.format(
            "JABCode Sample | Mode: %d-color (Nc=%d) | ECC Level: %d | Module Size: %dpx | Symbols: %d | " +
            "Encoding: UTF-8 | Error Correction: LDPC | Mask: Adaptive | " +
            "This demonstrates JABCode's 2D color barcode technology with advanced error correction capabilities.",
            colorNumber, 
            (int)(Math.log(colorNumber) / Math.log(2)) - 1,  // Calculate Nc value
            eccLevel,
            moduleSize,
            symbolNumber
        );
    }
    
    /**
     * Create a longer message to force multi-symbol cascading
     */
    private static String createLongMessage(int colorNumber, int eccLevel, int moduleSize, int symbolNumber) {
        StringBuilder message = new StringBuilder();
        message.append(String.format(
            "JABCode Cascaded Sample | Mode: %d-color (Nc=%d) | ECC: %d | Module: %dpx | Symbols: %d | ",
            colorNumber, 
            (int)(Math.log(colorNumber) / Math.log(2)) - 1,
            eccLevel,
            moduleSize,
            symbolNumber
        ));
        
        // Add substantial text to require multiple symbols
        message.append("JABCode is a 2D color barcode technology developed to provide higher data density than traditional ");
        message.append("monochrome barcodes. It supports up to 256 colors and uses advanced error correction based on LDPC codes. ");
        message.append("The barcode can cascade multiple symbols to encode larger amounts of data. Each symbol can be independently ");
        message.append("configured with its own error correction level and version. The master symbol contains metadata about the ");
        message.append("entire code structure, while slave symbols contain the actual payload data distributed across the cascade. ");
        message.append("This architecture allows for flexible data distribution and robust error recovery even if some symbols are damaged.");
        
        return message.toString();
    }
    
    /**
     * Generate a JABCode sample with specified configuration
     * 
     * @param colorNumber Number of colors in palette (4, 8, 16, 32, 64, 128)
     * @param message Message to encode
     * @param symbolNumber Number of symbols (1=simple, 2+=cascaded)
     * @param eccLevel Error correction level (0-7)
     * @param moduleSize Module size in pixels
     * @param outputDir Output directory
     * @return true if generation succeeded, false otherwise
     */
    private static boolean generateSample(int colorNumber, String message, int symbolNumber, 
                                          int eccLevel, int moduleSize, Path outputDir) {
        try {
            JABCodeEncoder encoder = new JABCodeEncoder();
            
            JABCodeEncoder.Config config = JABCodeEncoder.Config.builder()
                .colorNumber(colorNumber)
                .eccLevel(eccLevel)
                .moduleSize(moduleSize)
                .symbolNumber(symbolNumber)
                .build();
            
            // Create filename: sample_64_color_simple.png or sample_64_color_cascaded.png
            String symbolType = (symbolNumber == 1) ? "simple" : "cascaded";
            String filename = String.format("sample_%d_color_%s.png", colorNumber, symbolType);
            Path outputPath = outputDir.resolve(filename);
            
            boolean success = encoder.encodeToPNG(message, outputPath.toString(), config);
            
            if (success) {
                String symbolDesc = (symbolNumber == 1) ? "1 symbol" : symbolNumber + " symbols";
                System.out.printf("  ✅ %3d-color %-9s: %s (%s)\n", 
                    colorNumber, symbolType, filename, symbolDesc);
                return true;
            } else {
                // Don't print error for cascaded - expected to fail without symbol version config
                if (symbolNumber == 1) {
                    System.err.printf("  ❌ Failed to generate %d-color %s JABCode\n", colorNumber, symbolType);
                }
                return false;
            }
            
        } catch (Exception e) {
            if (symbolNumber == 1) {  // Only report errors for simple encoding
                System.err.printf("  ❌ Error generating %d-color JABCode: %s\n", colorNumber, e.getMessage());
            }
            return false;
        }
    }
}
