package com.jabcode.panama.demo;

import com.jabcode.panama.JABCodeEncoder;
import com.jabcode.panama.JABCodeDecoder;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Demo: Generate sample JABCodes for all supported color modes
 */
public class JABCodeGenerator {
    
    public static void main(String[] args) {
        JABCodeEncoder encoder = new JABCodeEncoder();
        JABCodeDecoder decoder = new JABCodeDecoder();
        
        String demoMessage = "JABCode Demo: Multi-color barcode technology! 🎉";
        Path outputDir = Paths.get("/tmp/jabcode-demo");
        outputDir.toFile().mkdirs();
        
        System.out.println("=".repeat(60));
        System.out.println("JABCode Generator Demo");
        System.out.println("=".repeat(60));
        System.out.println("Message: " + demoMessage);
        System.out.println("Output: " + outputDir);
        System.out.println();
        
        // Generate for all working color modes
        generateAndVerify(encoder, decoder, demoMessage, 16, "Mode 3 (16-color)", outputDir);
        generateAndVerify(encoder, decoder, demoMessage, 32, "Mode 4 (32-color)", outputDir);
        generateAndVerify(encoder, decoder, demoMessage, 64, "Mode 5 (64-color)", outputDir);
        generateAndVerify(encoder, decoder, demoMessage, 128, "Mode 6 (128-color)", outputDir);
        
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("✅ All barcodes generated successfully!");
        System.out.println("=".repeat(60));
        System.out.println();
        System.out.println("Open the generated PNG files to see the barcodes:");
        System.out.println("  " + outputDir + "/jabcode-*.png");
        
        JABCodeDecoder.resetDecoderState();
    }
    
    private static void generateAndVerify(
        JABCodeEncoder encoder,
        JABCodeDecoder decoder,
        String message,
        int colorNumber,
        String modeName,
        Path outputDir
    ) {
        System.out.println(modeName + ":");
        System.out.println("-".repeat(60));
        
        // Configure encoder
        int eccLevel = colorNumber >= 128 ? 10 : (colorNumber >= 16 ? 9 : 7);
        JABCodeEncoder.Config config = JABCodeEncoder.Config.builder()
            .colorNumber(colorNumber)
            .eccLevel(eccLevel)
            .moduleSize(16)
            .build();
        
        // Generate barcode
        Path outputFile = outputDir.resolve("jabcode-" + colorNumber + "color.png");
        boolean encoded = encoder.encodeToPNG(message, outputFile.toString(), config);
        
        if (!encoded) {
            System.out.println("  ❌ Encoding failed");
            return;
        }
        
        System.out.println("  ✅ Encoded: " + outputFile.getFileName());
        System.out.println("     Size: " + outputFile.toFile().length() + " bytes");
        
        // Verify by decoding
        boolean collectObservations = colorNumber >= 64;
        JABCodeDecoder.DecodedResultWithObservations result = 
            decoder.decodeWithObservations(outputFile, JABCodeDecoder.MODE_NORMAL, collectObservations);
        
        if (result.isSuccess() && message.equals(result.getData())) {
            System.out.println("  ✅ Decode verified: roundtrip successful");
        } else {
            System.out.println("  ⚠️  Decode verification failed");
        }
        
        System.out.println();
    }
}
