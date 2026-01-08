package com.jabcode.panama;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JABCodeEncoder with native library.
 * These tests require libjabcode.so to be available.
 * 
 * This is a dedicated integration test module - tests always run (no @EnabledIf).
 * Library path must be configured via Maven property or environment variable.
 */
class JABCodeEncoderIntegrationTest {

    private final JABCodeEncoder encoder = new JABCodeEncoder();

    @BeforeAll
    static void checkLibraryAvailability() {
        String ldPath = System.getenv("LD_LIBRARY_PATH");
        String javaLibPath = System.getProperty("java.library.path");
        
        System.out.println("=== Native Library Configuration ===");
        System.out.println("LD_LIBRARY_PATH: " + (ldPath != null ? ldPath : "<not set>"));
        System.out.println("java.library.path: " + (javaLibPath != null ? javaLibPath : "<not set>"));
        System.out.println("====================================");
    }

    @Test
    void encodeToPNGWithDefaultConfig(@TempDir Path tempDir) {
        Path outputFile = tempDir.resolve("test.png");
        
        boolean result = encoder.encodeToPNG(
            "Hello JABCode!",
            outputFile.toString(),
            JABCodeEncoder.Config.defaults()
        );
        
        assertTrue(result, "Encoding should succeed");
        assertTrue(Files.exists(outputFile), "Output file should exist");
        assertTrue(Files.isRegularFile(outputFile), "Output should be a regular file");
    }

    @Test
    void encodeToPNGWith4Colors(@TempDir Path tempDir) {
        Path outputFile = tempDir.resolve("test-4color.png");
        
        var config = JABCodeEncoder.Config.builder()
            .colorNumber(4)
            .eccLevel(5)
            .build();
        
        boolean result = encoder.encodeToPNG("Test 4-color", outputFile.toString(), config);
        
        assertTrue(result, "4-color encoding should succeed");
        assertTrue(Files.exists(outputFile), "Output file should exist");
    }

    @Test
    void encodeToPNGWith256Colors(@TempDir Path tempDir) {
        Path outputFile = tempDir.resolve("test-256color.png");
        
        var config = JABCodeEncoder.Config.builder()
            .colorNumber(256)
            .eccLevel(5)
            .build();
        
        boolean result = encoder.encodeToPNG("Test 256-color", outputFile.toString(), config);
        
        assertTrue(result, "256-color encoding should succeed");
        assertTrue(Files.exists(outputFile), "Output file should exist");
    }

    @Test
    void encodeToPNGAllColorModes(@TempDir Path tempDir) {
        int[] colorModes = {4, 8, 16, 32, 64, 128, 256};
        
        for (int colors : colorModes) {
            Path outputFile = tempDir.resolve("test-" + colors + "color.png");
            
            var config = JABCodeEncoder.Config.builder()
                .colorNumber(colors)
                .eccLevel(5)
                .build();
            
            boolean result = encoder.encodeToPNG(
                "Test " + colors + " colors",
                outputFile.toString(),
                config
            );
            
            assertTrue(result, colors + "-color encoding should succeed");
            assertTrue(Files.exists(outputFile), "Output file for " + colors + " colors should exist");
        }
    }

    @Test
    void encodeRejectsNullData(@TempDir Path tempDir) {
        Path outputFile = tempDir.resolve("null-test.png");
        
        assertThrows(IllegalArgumentException.class, () -> 
            encoder.encodeToPNG(null, outputFile.toString(), JABCodeEncoder.Config.defaults())
        );
    }

    @Test
    void encodeRejectsEmptyData(@TempDir Path tempDir) {
        Path outputFile = tempDir.resolve("empty-test.png");
        
        assertThrows(IllegalArgumentException.class, () -> 
            encoder.encodeToPNG("", outputFile.toString(), JABCodeEncoder.Config.defaults())
        );
    }

    @Test
    void encodeLongDataString(@TempDir Path tempDir) {
        Path outputFile = tempDir.resolve("long-data.png");
        
        String longData = "A".repeat(1000);
        
        boolean result = encoder.encodeToPNG(
            longData,
            outputFile.toString(),
            JABCodeEncoder.Config.defaults()
        );
        
        assertTrue(result, "Encoding long data should succeed");
        assertTrue(Files.exists(outputFile), "Output file should exist");
    }

    @Test
    void encodeWithDifferentECCLevels(@TempDir Path tempDir) {
        for (int eccLevel = 0; eccLevel <= 10; eccLevel++) {
            Path outputFile = tempDir.resolve("ecc-" + eccLevel + ".png");
            
            var config = JABCodeEncoder.Config.builder()
                .colorNumber(8)
                .eccLevel(eccLevel)
                .build();
            
            boolean result = encoder.encodeToPNG(
                "ECC Level " + eccLevel,
                outputFile.toString(),
                config
            );
            
            assertTrue(result, "ECC level " + eccLevel + " should work");
        }
    }

    @Test
    void encodeUnicodeData(@TempDir Path tempDir) {
        Path outputFile = tempDir.resolve("unicode.png");
        
        String unicodeData = "Hello 世界 🌍 Привет";
        
        boolean result = encoder.encodeToPNG(
            unicodeData,
            outputFile.toString(),
            JABCodeEncoder.Config.defaults()
        );
        
        assertTrue(result, "Unicode encoding should succeed");
        assertTrue(Files.exists(outputFile), "Output file should exist");
    }

    @Test
    void encodeWithConfigMethod(@TempDir Path tempDir) {
        // This tests the encodeWithConfig method which currently returns null
        byte[] result = encoder.encodeWithConfig(
            "Test data",
            JABCodeEncoder.Config.defaults()
        );
        
        // Currently expected to return null as bitmap extraction not implemented
        assertNull(result, "encodeWithConfig currently returns null (bitmap extraction TODO)");
    }

    @Test
    void encodeMethodWithColorAndEcc(@TempDir Path tempDir) {
        // This uses the convenience encode(data, colorNumber, eccLevel) method
        byte[] result = encoder.encode("Test", 8, 5);
        
        // Currently expected to return null
        assertNull(result, "encode() currently returns null (bitmap extraction TODO)");
    }

    @Test
    void encodeDefaultMethod() {
        // This uses the encode(data) method with defaults
        byte[] result = encoder.encode("Test");
        
        // Currently expected to return null
        assertNull(result, "encode() with defaults currently returns null");
    }
}
