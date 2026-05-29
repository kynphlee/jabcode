package com.jabcode.panama;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
        System.out.println("   Note: Using extended messages with explicit symbol versions\n");
        int cascadedCount = 0;
        for (int colorNumber : colorModes) {
            String message = createLongMessage(colorNumber, 5, 12, 2);
            if (generateCascadedSample(colorNumber, message, 2, 5, 12, outputDir)) {
                cascadedCount++;
            }
        }
        
        System.out.println("\n✅ Sample generation complete!");
        System.out.println("📁 Location: " + outputDir.toAbsolutePath());
        System.out.println("📈 Single-symbol samples: " + simpleCount + "/" + colorModes.length);
        System.out.println("📈 Cascaded samples: " + cascadedCount + "/" + colorModes.length);
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
     * Generate a simple single-symbol JABCode sample
     * 
     * @param colorNumber Number of colors in palette (4, 8, 16, 32, 64, 128)
     * @param message Message to encode
     * @param symbolNumber Number of symbols (should be 1)
     * @param eccLevel Error correction level (0-10)
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
            
            String filename = String.format("sample_%d_color_simple.png", colorNumber);
            Path outputPath = outputDir.resolve(filename);
            
            boolean success = encoder.encodeToPNG(message, outputPath.toString(), config);
            
            if (success) {
                System.out.printf("  ✅ %3d-color simple   : %s (1 symbol)\n", 
                    colorNumber, filename);
                return true;
            } else {
                System.err.printf("  ❌ Failed to generate %d-color simple JABCode\n", colorNumber);
                return false;
            }
            
        } catch (Exception e) {
            System.err.printf("  ❌ Error generating %d-color JABCode: %s\n", colorNumber, e.getMessage());
            return false;
        }
    }
    
    /**
     * Generate a cascaded multi-symbol JABCode sample
     * 
     * @param colorNumber Number of colors in palette (4, 8, 16, 32, 64, 128)
     * @param message Message to encode
     * @param symbolNumber Number of symbols (2 for cascaded)
     * @param eccLevel Error correction level (0-10)
     * @param moduleSize Module size in pixels
     * @param outputDir Output directory
     * @return true if generation succeeded, false otherwise
     */
    private static boolean generateCascadedSample(int colorNumber, String message, int symbolNumber,
                                                   int eccLevel, int moduleSize, Path outputDir) {
        try {
            JABCodeEncoder encoder = new JABCodeEncoder();
            
            // Create symbol versions - all must be same size per JABCode spec
            // Use version 12×12 for good data capacity
            var symbolVersions = List.of(
                new SymbolVersion(12, 12),  // Primary symbol
                new SymbolVersion(12, 12)   // Secondary symbol (same size required)
            );
            
            JABCodeEncoder.Config config = JABCodeEncoder.Config.builder()
                .colorNumber(colorNumber)
                .eccLevel(eccLevel)
                .moduleSize(moduleSize)
                .symbolNumber(symbolNumber)
                .symbolVersions(symbolVersions)
                .build();
            
            String filename = String.format("sample_%d_color_cascaded.png", colorNumber);
            Path outputPath = outputDir.resolve(filename);
            
            boolean success = encoder.encodeToPNG(message, outputPath.toString(), config);
            
            if (success) {
                System.out.printf("  ✅ %3d-color cascaded : %s (%d symbols)\n", 
                    colorNumber, filename, symbolNumber);
                return true;
            } else {
                System.err.printf("  ❌ Failed to generate %d-color cascaded JABCode\n", colorNumber);
                return false;
            }
            
        } catch (Exception e) {
            System.err.printf("  ❌ Error generating %d-color cascaded JABCode: %s\n", 
                colorNumber, e.getMessage());
            return false;
        }
    }
}
