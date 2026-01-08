package com.jabcode.panama.encode;

import com.jabcode.panama.colors.ColorMode;
import com.jabcode.panama.colors.ColorPaletteFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaletteEmbeddingTest {

    @Test
    void encodeDecodeRoundTrip() {
        var palette = ColorPaletteFactory.create(ColorMode.MODE_8);
        byte[] encoded = PaletteEmbedding.encodePalette(palette);
        
        assertEquals(8 * 3, encoded.length); // 8 colors × 3 bytes
        
        int[][] decoded = PaletteEmbedding.decodePalette(encoded);
        assertEquals(8, decoded.length);
        
        // Verify round-trip preserves colors
        int[][] original = palette.generateEmbeddedPalette();
        for (int i = 0; i < 8; i++) {
            assertArrayEquals(original[i], decoded[i],
                "Color " + i + " mismatch after round-trip");
        }
    }

    @Test
    void encodeMode4Palette() {
        var palette = ColorPaletteFactory.create(ColorMode.MODE_4);
        byte[] encoded = PaletteEmbedding.encodePalette(palette);
        
        assertEquals(4 * 3, encoded.length);
        
        // Check first color is Black (0,0,0)
        assertEquals(0, encoded[0]);
        assertEquals(0, encoded[1]);
        assertEquals(0, encoded[2]);
    }

    @Test
    void encodeMode64EmbedsSameAsFull() {
        var palette = ColorPaletteFactory.create(ColorMode.MODE_64);
        byte[] encoded = PaletteEmbedding.encodePalette(palette);
        
        assertEquals(64 * 3, encoded.length);
    }

    @Test
    void encodeMode128EmbedsSubset() {
        var palette = ColorPaletteFactory.create(ColorMode.MODE_128);
        byte[] encoded = PaletteEmbedding.encodePalette(palette);
        
        // Mode 128 embeds only 64 colors
        assertEquals(64 * 3, encoded.length);
    }

    @Test
    void decodeInvalidLengthThrows() {
        byte[] invalid = new byte[10]; // Not multiple of 3
        assertThrows(IllegalArgumentException.class, () -> PaletteEmbedding.decodePalette(invalid));
    }
}
