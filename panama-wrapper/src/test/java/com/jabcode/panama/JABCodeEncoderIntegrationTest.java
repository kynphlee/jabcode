package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JABCodeEncoder with native library.
 * These tests require libjabcode.so to be available.
 */
@EnabledIf("isNativeLibraryAvailable")
class JABCodeEncoderIntegrationTest {

    private final JABCodeEncoder encoder = new JABCodeEncoder();

    static boolean isNativeLibraryAvailable() {
        // Panama FFM loads libraries via SymbolLookup, not System.loadLibrary
        // Check if library file exists in expected locations
        String[] possiblePaths = {
            "../src/jabcode/build/libjabcode.so",
            "../lib/libjabcode.so",
            "libjabcode.so"
        };
        
        for (String path : possiblePaths) {
            if (java.nio.file.Files.exists(java.nio.file.Path.of(path))) {
                System.out.println("Found native library at: " + path);
                return true;
            }
        }
        
        // Also check LD_LIBRARY_PATH
        String ldPath = System.getenv("LD_LIBRARY_PATH");
        if (ldPath != null && !ldPath.isEmpty()) {
            System.out.println("LD_LIBRARY_PATH is set: " + ldPath);
            // If LD_LIBRARY_PATH is set, assume library will be found
            return true;
        }
        
        System.err.println("Native library not available. Skipping integration tests.");
        System.err.println("Set LD_LIBRARY_PATH or use ./run-integration-tests.sh");
        return false;
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
        // encodeWithConfig now returns the PNG bytes in memory (no temp file).
        byte[] result = encoder.encodeWithConfig(
            "Test data",
            JABCodeEncoder.Config.defaults()
        );

        assertNotNull(result, "encodeWithConfig should return in-memory PNG bytes");
        assertTrue(result.length > 0, "PNG byte[] must be non-empty");
        assertEquals((byte) 0x89, result[0], "output must start with the PNG signature");
    }

    @Test
    void encodeMethodWithColorAndEcc(@TempDir Path tempDir) {
        // Convenience encode(data, colorNumber, eccLevel) -> in-memory PNG bytes.
        byte[] result = encoder.encode("Test", 8, 5);

        assertNotNull(result, "encode() should return in-memory PNG bytes");
        assertTrue(result.length > 0, "PNG byte[] must be non-empty");
        assertEquals((byte) 0x89, result[0], "output must start with the PNG signature");
    }

    @Test
    void encodeDefaultMethod() {
        // encode(data) with defaults -> in-memory PNG bytes.
        byte[] result = encoder.encode("Test");

        assertNotNull(result, "encode() with defaults should return in-memory PNG bytes");
        assertTrue(result.length > 0, "PNG byte[] must be non-empty");
        assertEquals((byte) 0x89, result[0], "output must start with the PNG signature");
    }
}
