package com.jabcode.panama;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple test to verify timing instrumentation in libjabcode.so
 */
public class TimingTest {
    public static void main(String[] args) throws Exception {
        System.err.println("=== Starting Timing Test ===");
        
        // Create encoder and decoder
        JABCodeEncoder encoder = new JABCodeEncoder();
        JABCodeDecoder decoder = new JABCodeDecoder();
        
        // Generate test message
        String message = "A".repeat(1000);
        
        // Create temp file
        Path tempFile = Files.createTempFile("timing-test-", ".png");
        
        try {
            // Encode
            System.err.println("\n[TEST] Encoding 64-color, 1KB message...");
            JABCodeEncoder.Config config = JABCodeEncoder.Config.builder()
                .colorNumber(64)
                .eccLevel(5)
                .symbolNumber(1)
                .build();
            
            boolean encoded = encoder.encodeToPNG(message, tempFile.toString(), config);
            if (!encoded) {
                System.err.println("[TEST] Encoding failed!");
                System.exit(1);
            }
            System.err.println("[TEST] Encoding complete\n");
            
            // Decode - this should show timing output
            System.err.println("[TEST] Starting decode (timing output should appear below)...");
            String decoded = decoder.decodeFromFile(tempFile);
            
            if (decoded == null || !decoded.equals(message)) {
                System.err.println("[TEST] Decode failed or mismatch!");
                System.exit(1);
            }
            
            System.err.println("\n[TEST] Decode complete - verification passed");
            System.err.println("=== Timing Test Complete ===");
            
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
