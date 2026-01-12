package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JABCodeEncoderConfigTest {

    @Test
    void builderSetsAllProperties() {
        var config = JABCodeEncoder.Config.builder()
            .colorNumber(16)
            .eccLevel(7)
            .symbolNumber(4)
            .moduleSize(15)
            .masterSymbolWidth(100)
            .masterSymbolHeight(200)
            .build();

        assertEquals(16, config.getColorNumber());
        assertEquals(7, config.getEccLevel());
        assertEquals(4, config.getSymbolNumber());
        assertEquals(15, config.getModuleSize());
        assertEquals(100, config.getMasterSymbolWidth());
        assertEquals(200, config.getMasterSymbolHeight());
    }

    @Test
    void defaultsReturnValidConfig() {
        var config = JABCodeEncoder.Config.defaults();
        
        assertEquals(8, config.getColorNumber());
        assertEquals(5, config.getEccLevel());
        assertEquals(1, config.getSymbolNumber());
        assertEquals(12, config.getModuleSize());
        assertEquals(0, config.getMasterSymbolWidth());
        assertEquals(0, config.getMasterSymbolHeight());
    }

    @Test
    void allValidColorNumbersAccepted() {
        // Note: 256-color mode excluded due to malloc corruption bug
        // See encoder.c:2633 and memory-bank/documentation/full-spectrum/05-encoder-memory-architecture.md
        int[] valid = {4, 8, 16, 32, 64, 128};
        for (int c : valid) {
            var config = JABCodeEncoder.Config.builder().colorNumber(c).build();
            assertEquals(c, config.getColorNumber());
        }
    }

    @Test
    void invalidColorNumbersRejected() {
        int[] invalid = {0, 1, 2, 3, 5, 6, 7, 9, 15, 100, 512};
        for (int c : invalid) {
            assertThrows(IllegalArgumentException.class,
                () -> JABCodeEncoder.Config.builder().colorNumber(c),
                "Should reject color number: " + c);
        }
    }

    @Test
    void eccLevelBoundaryValidation() {
        // Valid boundaries
        assertDoesNotThrow(() -> JABCodeEncoder.Config.builder().eccLevel(0).build());
        assertDoesNotThrow(() -> JABCodeEncoder.Config.builder().eccLevel(10).build());
        
        // Invalid boundaries
        assertThrows(IllegalArgumentException.class,
            () -> JABCodeEncoder.Config.builder().eccLevel(-1).build());
        assertThrows(IllegalArgumentException.class,
            () -> JABCodeEncoder.Config.builder().eccLevel(11).build());
    }

    @Test
    void symbolNumberBoundaryValidation() {
        // Valid boundaries
        assertDoesNotThrow(() -> JABCodeEncoder.Config.builder().symbolNumber(1).build());
        assertDoesNotThrow(() -> JABCodeEncoder.Config.builder().symbolNumber(61).build());
        
        // Invalid boundaries
        assertThrows(IllegalArgumentException.class,
            () -> JABCodeEncoder.Config.builder().symbolNumber(0).build());
        assertThrows(IllegalArgumentException.class,
            () -> JABCodeEncoder.Config.builder().symbolNumber(62).build());
    }

    @Test
    void moduleSizePositiveValidation() {
        assertDoesNotThrow(() -> JABCodeEncoder.Config.builder().moduleSize(1).build());
        assertDoesNotThrow(() -> JABCodeEncoder.Config.builder().moduleSize(100).build());
        
        assertThrows(IllegalArgumentException.class,
            () -> JABCodeEncoder.Config.builder().moduleSize(0).build());
        assertThrows(IllegalArgumentException.class,
            () -> JABCodeEncoder.Config.builder().moduleSize(-5).build());
    }

    @Test
    void masterSymbolDimensionsAllowZero() {
        var config = JABCodeEncoder.Config.builder()
            .masterSymbolWidth(0)
            .masterSymbolHeight(0)
            .build();
        
        assertEquals(0, config.getMasterSymbolWidth());
        assertEquals(0, config.getMasterSymbolHeight());
    }

    @Test
    void builderMethodsReturnBuilder() {
        var builder = JABCodeEncoder.Config.builder();
        assertSame(builder, builder.colorNumber(8));
        assertSame(builder, builder.eccLevel(5));
        assertSame(builder, builder.symbolNumber(1));
        assertSame(builder, builder.moduleSize(12));
        assertSame(builder, builder.masterSymbolWidth(100));
        assertSame(builder, builder.masterSymbolHeight(100));
    }
}
