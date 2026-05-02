package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generate sample JABCode barcodes for demonstration
 */
@DisplayName("JABCode Sample Generator")
class GenerateSampleBarcodes {
    
    @TempDir
    Path tempDir;
    
    @Test
    @DisplayName("Generate sample barcodes for all supported color modes")
    void generateSamples() throws IOException {
        JABCodeEncoder encoder = new JABCodeEncoder();
        JABCodeDecoder decoder = new JABCodeDecoder();
        
        // Create output directory
        Path outputDir = Paths.get("/tmp/jabcode-samples");
        outputDir.toFile().mkdirs();
        
        String message = "JABCode: Multi-color barcode technology! Visit github.com/jabcode 🎉";
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("JABCode Sample Generator");
        System.out.println("=".repeat(70));
        System.out.println("Message: " + message);
        System.out.println("Output:  " + outputDir);
        System.out.println("=".repeat(70) + "\n");
        
        // Generate for each working color mode
        generateSample(encoder, decoder, message, 4, "4-color (Mode 1)", outputDir);
        generateSample(encoder, decoder, message, 8, "8-color (Mode 2)", outputDir);
        generateSample(encoder, decoder, message, 16, "16-color (Mode 3)", outputDir);
        generateSample(encoder, decoder, message, 32, "32-color (Mode 4)", outputDir);
        generateSample(encoder, decoder, message, 64, "64-color (Mode 5)", outputDir);
        generateSample(encoder, decoder, message, 128, "128-color (Mode 6)", outputDir);
        
        System.out.println("=".repeat(70));
        System.out.println("✅ All samples generated successfully!");
        System.out.println("=".repeat(70));
        System.out.println("\nView barcodes at: " + outputDir);
        System.out.println("Files:");
        Files.list(outputDir)
            .filter(p -> p.toString().endsWith(".png"))
            .sorted()
            .forEach(p -> System.out.println("  - " + p.getFileName()));
        System.out.println();
        
        JABCodeDecoder.resetDecoderState();
    }
    
    private void generateSample(
        JABCodeEncoder encoder,
        JABCodeDecoder decoder,
        String message,
        int colorNumber,
        String modeName,
        Path outputDir
    ) throws IOException {
        System.out.println(modeName + ":");
        
        // Configure encoder
        int eccLevel = colorNumber >= 128 ? 10 : (colorNumber >= 16 ? 9 : 7);
        JABCodeEncoder.Config config = JABCodeEncoder.Config.builder()
            .colorNumber(colorNumber)
            .eccLevel(eccLevel)
            .moduleSize(16)
            .build();
        
        // Generate to temp file first
        Path tempFile = tempDir.resolve("temp_" + colorNumber + ".png");
        boolean encoded = encoder.encodeToPNG(message, tempFile.toString(), config);
        assertTrue(encoded, "Encoding should succeed");
        
        // Copy to output directory
        Path outputFile = outputDir.resolve("jabcode-" + colorNumber + "color.png");
        Files.copy(tempFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
        
        long fileSize = Files.size(outputFile);
        System.out.println("  ✅ Generated: " + outputFile.getFileName() + " (" + fileSize + " bytes)");
        
        // Verify decode
        boolean collectObservations = colorNumber >= 64;
        JABCodeDecoder.DecodedResultWithObservations result = 
            decoder.decodeWithObservations(outputFile, JABCodeDecoder.MODE_NORMAL, collectObservations);
        
        assertTrue(result.isSuccess(), "Decode should succeed");
        assertEquals(message, result.getData(), "Roundtrip should preserve message");
        System.out.println("  ✅ Verified: roundtrip successful\n");
    }
}
