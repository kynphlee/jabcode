package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for cascaded multi-symbol JABCode encoding.
 * 
 * These tests verify that the Java API correctly configures symbol versions
 * for multi-symbol cascades and that the native encoder accepts the configuration.
 */
@DisplayName("Cascaded Multi-Symbol Encoding")
class CascadedEncodingTest {
    
    private JABCodeEncoder encoder;
    private JABCodeDecoder decoder;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        encoder = new JABCodeEncoder();
        decoder = new JABCodeDecoder();
    }
    
    @Test
    @DisplayName("Should encode 2-symbol cascade with explicit versions")
    void testTwoSymbolCascade() {
        String message = "This is a cascaded JABCode with 2 symbols. " +
            "The primary symbol contains this text, and if the message is long enough, " +
            "a secondary symbol will be created to hold the overflow data. " +
            "Let's make this message even longer to ensure we need multiple symbols... " +
            "Adding more text to force the encoder to create a second symbol. " +
            "This should be sufficient data to require cascading.";
        
        var config = JABCodeEncoder.Config.builder()
            .colorNumber(64)
            .eccLevel(6)
            .symbolNumber(2)
            .symbolVersions(List.of(
                new SymbolVersion(12, 12),  // Both symbols same size
                new SymbolVersion(12, 12)   // (JABCode requirement)
            ))
            .build();
        
        Path output = tempDir.resolve("cascade-2-symbols.png");
        boolean result = encoder.encodeToPNG(message, output.toString(), config);
        
        assertTrue(result, "Should successfully encode 2-symbol cascade");
        assertTrue(output.toFile().exists(), "Output file should exist");
        assertTrue(output.toFile().length() > 0, "Output file should not be empty");
    }
    
    @Test
    @DisplayName("Should encode 3-symbol cascade")
    void testThreeSymbolCascade() {
        String message = "X".repeat(1000); // Large message to force multiple symbols
        
        var config = JABCodeEncoder.Config.builder()
            .colorNumber(32)
            .eccLevel(5)
            .symbolNumber(3)
            .symbolVersions(List.of(
                new SymbolVersion(12, 12),  // All symbols same size
                new SymbolVersion(12, 12),  // (JABCode requirement)
                new SymbolVersion(12, 12)
            ))
            .build();
        
        Path output = tempDir.resolve("cascade-3-symbols.png");
        boolean result = encoder.encodeToPNG(message, output.toString(), config);
        
        assertTrue(result, "Should successfully encode 3-symbol cascade");
    }
    
    @Test
    @DisplayName("Should accept rectangular symbols (all same shape)")
    void testRectangularSymbols() {
        String message = "Testing rectangular symbols in cascade";
        
        var config = JABCodeEncoder.Config.builder()
            .colorNumber(16)
            .symbolNumber(2)
            .symbolVersions(List.of(
                new SymbolVersion(12, 10),  // Both symbols same rectangular shape
                new SymbolVersion(12, 10)   // (JABCode requires matching dimensions)
            ))
            .build();
        
        Path output = tempDir.resolve("cascade-rectangular.png");
        boolean result = encoder.encodeToPNG(message, output.toString(), config);
        
        assertTrue(result, "Should accept rectangular symbol versions");
    }
    
    @Test
    @DisplayName("Should work with various color modes")
    void testDifferentColorModes() {
        String message = "Color mode test for cascaded encoding";
        
        int[] colorModes = {4, 8, 16, 32, 64, 128};
        
        for (int colorMode : colorModes) {
            var config = JABCodeEncoder.Config.builder()
                .colorNumber(colorMode)
                .symbolNumber(2)
                .symbolVersions(List.of(
                    new SymbolVersion(10, 10),  // Both same size
                    new SymbolVersion(10, 10)   // (JABCode requirement)
                ))
                .build();
            
            Path output = tempDir.resolve("cascade-color-" + colorMode + ".png");
            boolean result = encoder.encodeToPNG(message, output.toString(), config);
            
            assertTrue(result, "Should work with " + colorMode + "-color mode");
        }
    }
    
    @Test
    @DisplayName("Should reject mismatched version count")
    void testMismatchedVersionCount() {
        assertThrows(IllegalArgumentException.class, () -> {
            JABCodeEncoder.Config.builder()
                .symbolNumber(3)
                .symbolVersions(List.of(
                    new SymbolVersion(10, 10),
                    new SymbolVersion(8, 8)
                    // Missing 3rd version!
                ))
                .build();
        });
    }
    
    @Test
    @DisplayName("Should warn when multi-symbol without versions")
    void testMultiSymbolWithoutVersions() {
        // This should print a warning but not fail
        var config = JABCodeEncoder.Config.builder()
            .symbolNumber(2)
            // No symbolVersions specified
            .build();
        
        assertNotNull(config);
        assertEquals(2, config.getSymbolNumber());
        assertNull(config.getSymbolVersions());
    }
    
    @Test
    @DisplayName("Should accept null versions for single symbol")
    void testSingleSymbolNoVersions() {
        var config = JABCodeEncoder.Config.builder()
            .symbolNumber(1)
            // No symbolVersions needed for single symbol
            .build();
        
        assertNotNull(config);
        assertNull(config.getSymbolVersions());
    }
    
    @Test
    @DisplayName("Should handle large cascades up to spec limit")
    void testLargeCascade() {
        // ISO spec allows up to 61 symbols
        int symbolCount = 5; // Test with 5 (full 61 would be slow)
        
        var versions = new java.util.ArrayList<SymbolVersion>();
        for (int i = 0; i < symbolCount; i++) {
            versions.add(new SymbolVersion(8 + i, 8 + i));
        }
        
        var config = JABCodeEncoder.Config.builder()
            .colorNumber(16)
            .symbolNumber(symbolCount)
            .symbolVersions(versions)
            .build();
        
        assertEquals(symbolCount, config.getSymbolVersions().size());
    }
    
    @Test
    @DisplayName("Should configure all symbols with same ECC level")
    void testFuturePerSymbolECC() {
        // Note: All symbols in cascade must have same dimensions
        // Future enhancement could add per-symbol ECC: .eccLevels(List.of(7, 5))
        
        String message = "Testing cascaded ECC configuration";
        
        var config = JABCodeEncoder.Config.builder()
            .colorNumber(32)
            .eccLevel(7)  // Currently applies to all symbols
            .symbolNumber(2)
            .symbolVersions(List.of(
                new SymbolVersion(12, 12),  // Same size required
                new SymbolVersion(12, 12)
            ))
            .build();
        
        Path output = tempDir.resolve("cascade-ecc.png");
        boolean result = encoder.encodeToPNG(message, output.toString(), config);
        
        assertTrue(result);
    }
    
    @Test
    @DisplayName("Should maintain immutability of symbol versions list")
    void testImmutability() {
        var versions = new java.util.ArrayList<SymbolVersion>();
        versions.add(new SymbolVersion(10, 10));
        versions.add(new SymbolVersion(8, 8));
        
        var config = JABCodeEncoder.Config.builder()
            .symbolNumber(2)
            .symbolVersions(versions)
            .build();
        
        // Modify original list
        versions.add(new SymbolVersion(6, 6));
        
        // Config should be unchanged
        assertEquals(2, config.getSymbolVersions().size());
        
        // Returned list should be unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            config.getSymbolVersions().add(new SymbolVersion(6, 6));
        });
    }
}
