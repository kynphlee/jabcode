package com.jabcode.panama.encode;

import com.jabcode.panama.colors.ColorMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NcMetadataTest {

    @Test
    void encodeDecodeNcPart1RoundTrip() {
        for (ColorMode mode : ColorMode.values()) {
            if (mode == ColorMode.RESERVED_0) continue;
            
            int[] encoded = NcMetadata.encodeNcPart1(mode);
            assertEquals(3, encoded.length);
            
            int decoded = NcMetadata.decodeNcPart1(encoded);
            assertEquals(mode.getNcValue(), decoded,
                "Nc round-trip failed for " + mode);
        }
    }

    @Test
    void encodeNcValue1() {
        // Nc=1 (MODE_4) = binary 001
        int[] encoded = NcMetadata.encodeNcPart1(ColorMode.MODE_4);
        assertArrayEquals(new int[]{0, 0, 1}, encoded);
    }

    @Test
    void encodeNcValue2() {
        // Nc=2 (MODE_8) = binary 010
        int[] encoded = NcMetadata.encodeNcPart1(ColorMode.MODE_8);
        assertArrayEquals(new int[]{0, 1, 0}, encoded);
    }

    @Test
    void encodeNcValue7() {
        // Nc=7 (MODE_256) = binary 111
        int[] encoded = NcMetadata.encodeNcPart1(ColorMode.MODE_256);
        assertArrayEquals(new int[]{1, 1, 1}, encoded);
    }

    @Test
    void decodeNcValue5() {
        // binary 101 = 5 (MODE_64)
        int decoded = NcMetadata.decodeNcPart1(new int[]{1, 0, 1});
        assertEquals(5, decoded);
        assertEquals(ColorMode.MODE_64, ColorMode.fromNcValue(decoded));
    }

    @Test
    void part2EncodingMatchesPart1() {
        for (ColorMode mode : ColorMode.values()) {
            if (mode == ColorMode.RESERVED_0) continue;
            
            int[] part1 = NcMetadata.encodeNcPart1(mode);
            int[] part2 = NcMetadata.encodeNcPart2(mode);
            
            assertArrayEquals(part1, part2,
                "Part1 and Part2 encoding differ for " + mode);
        }
    }

    @Test
    void get3ColorPaletteReturnsCorrectColors() {
        int[][] palette = NcMetadata.get3ColorPalette();
        assertEquals(3, palette.length);
        
        // Black
        assertArrayEquals(new int[]{0, 0, 0}, palette[0]);
        // Cyan
        assertArrayEquals(new int[]{0, 255, 255}, palette[1]);
        // Yellow
        assertArrayEquals(new int[]{255, 255, 0}, palette[2]);
    }

    @Test
    void decodeInvalidIndicesLengthThrows() {
        assertThrows(IllegalArgumentException.class, 
            () -> NcMetadata.decodeNcPart1(new int[]{0, 1})); // Only 2 indices
    }
}
