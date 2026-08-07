package com.jabcode.panama;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for caller-supplied symbol positions.
 *
 * <p>Positions are indices into the codec's 61-slot placement lattice
 * ({@code jab_symbol_pos}, {@code src/jabcode/encoder.h:111}). Slot 0 is the
 * master; 1 is directly above it, 2 below, 3 left, 4 right. So {@code 0,1,2}
 * is a vertical column and {@code 0,3,4} a horizontal row — the same three
 * symbols in two genuinely different shapes.</p>
 *
 * <p>The wrapper's contract is narrow on purpose: range, uniqueness and count.
 * Adjacency and docked-side sizing stay with the codec.</p>
 */
@DisplayName("Symbol Positions")
class SymbolPositionsTest {

    /** Long enough to actually fill a three-symbol cascade. */
    private static final String CASCADE_MESSAGE = "X".repeat(1000);

    private static final List<SymbolVersion> THREE_EQUAL_VERSIONS = List.of(
        new SymbolVersion(12, 12),
        new SymbolVersion(12, 12),
        new SymbolVersion(12, 12));

    private JABCodeEncoder encoder;
    private JABCodeDecoder decoder;

    @BeforeEach
    void setUp() {
        encoder = new JABCodeEncoder();
        decoder = new JABCodeDecoder();
    }

    // ---------------------------------------------------------------- config

    @Test
    @DisplayName("Unset positions leave the config on the sequential default")
    void unsetPositionsAreNull() {
        assertNull(JABCodeEncoder.Config.defaults().getSymbolPositions());
    }

    @Test
    @DisplayName("Explicit positions are retained on the config")
    void explicitPositionsRetained() {
        var config = JABCodeEncoder.Config.builder()
            .symbolNumber(3)
            .symbolVersions(THREE_EQUAL_VERSIONS)
            .symbolPositions(List.of(0, 3, 4))
            .build();

        assertEquals(List.of(0, 3, 4), config.getSymbolPositions());
    }

    @Test
    @DisplayName("Null or empty positions restore the sequential default")
    void nullOrEmptyPositionsRestoreDefault() {
        var fromNull = JABCodeEncoder.Config.builder()
            .symbolNumber(3)
            .symbolVersions(THREE_EQUAL_VERSIONS)
            .symbolPositions(List.of(0, 3, 4))
            .symbolPositions(null)
            .build();
        assertNull(fromNull.getSymbolPositions());

        var fromEmpty = JABCodeEncoder.Config.builder()
            .symbolNumber(3)
            .symbolVersions(THREE_EQUAL_VERSIONS)
            .symbolPositions(List.of(0, 3, 4))
            .symbolPositions(List.of())
            .build();
        assertNull(fromEmpty.getSymbolPositions());
    }

    @Test
    @DisplayName("The retained list is a defensive, unmodifiable copy")
    void positionsListIsDefensivelyCopied() {
        var mutable = new ArrayList<>(List.of(0, 3, 4));
        var config = JABCodeEncoder.Config.builder()
            .symbolNumber(3)
            .symbolVersions(THREE_EQUAL_VERSIONS)
            .symbolPositions(mutable)
            .build();

        mutable.set(1, 7);
        assertEquals(List.of(0, 3, 4), config.getSymbolPositions());
        assertThrows(UnsupportedOperationException.class,
            () -> config.getSymbolPositions().set(0, 9));
    }

    @Test
    @DisplayName("The builder setter returns the builder")
    void builderMethodReturnsBuilder() {
        var builder = JABCodeEncoder.Config.builder();
        assertSame(builder, builder.symbolPositions(List.of(0)));
    }

    // ------------------------------------------------------------ validation

    @Test
    @DisplayName("Positions outside 0..60 are rejected")
    void outOfRangePositionsRejected() {
        // 61 is the interesting one: the C guard at encoder.c:2173 reads
        // "> MAX_SYMBOL_NUMBER" where it should read ">=", so 61 slips past it
        // and is dereferenced as jab_symbol_pos[61] -- one past the end of a
        // 61-entry table. The wrapper stops it here instead.
        int[] invalid = {-1, 61, 62, 100, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int position : invalid) {
            assertThrows(IllegalArgumentException.class,
                () -> JABCodeEncoder.Config.builder().symbolPositions(List.of(position)),
                "Should reject symbol position: " + position);
        }
    }

    @Test
    @DisplayName("Positions on the 0..60 boundary are accepted")
    void boundaryPositionsAccepted() {
        assertDoesNotThrow(
            () -> JABCodeEncoder.Config.builder().symbolPositions(List.of(0)));
        assertDoesNotThrow(
            () -> JABCodeEncoder.Config.builder().symbolPositions(List.of(60)));
    }

    @Test
    @DisplayName("Duplicate positions are rejected")
    void duplicatePositionsRejected() {
        var exception = assertThrows(IllegalArgumentException.class,
            () -> JABCodeEncoder.Config.builder().symbolPositions(List.of(0, 3, 3)));
        assertTrue(exception.getMessage().contains("Duplicate"),
            "Message should name the problem, got: " + exception.getMessage());
    }

    @Test
    @DisplayName("Null entries are rejected")
    void nullPositionEntryRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> JABCodeEncoder.Config.builder()
                .symbolPositions(Arrays.asList(0, null, 4)));
    }

    @Test
    @DisplayName("Position count must match symbol count, in either setter order")
    void positionCountMustMatchSymbolCount() {
        assertThrows(IllegalArgumentException.class,
            () -> JABCodeEncoder.Config.builder()
                .symbolNumber(3)
                .symbolVersions(THREE_EQUAL_VERSIONS)
                .symbolPositions(List.of(0, 3))
                .build(),
            "Two positions for three symbols should be rejected");

        // The count check lives in build(), so the setters commute.
        assertThrows(IllegalArgumentException.class,
            () -> JABCodeEncoder.Config.builder()
                .symbolPositions(List.of(0, 3))
                .symbolNumber(3)
                .symbolVersions(THREE_EQUAL_VERSIONS)
                .build(),
            "Setting positions before symbolNumber should still be caught");

        assertDoesNotThrow(
            () -> JABCodeEncoder.Config.builder()
                .symbolPositions(List.of(0, 3, 4))
                .symbolNumber(3)
                .symbolVersions(THREE_EQUAL_VERSIONS)
                .build());
    }

    // ---------------------------------------------------------------- native

    @Test
    @DisplayName("Explicit sequential positions reproduce the default output byte for byte")
    void explicitSequentialPositionsMatchDefault() {
        byte[] fromDefault = encoder.encodeWithConfig(CASCADE_MESSAGE, cascadeConfig(null));
        byte[] fromExplicit = encoder.encodeWithConfig(CASCADE_MESSAGE, cascadeConfig(List.of(0, 1, 2)));

        assertNotNull(fromDefault, "Default cascade should encode");
        assertNotNull(fromExplicit, "Explicitly sequential cascade should encode");
        assertArrayEquals(fromDefault, fromExplicit,
            "Supplying 0,1,2 must be indistinguishable from supplying nothing");
    }

    @Test
    @DisplayName("A different layout is honoured and still round-trips")
    void alternativeLayoutIsHonoured() {
        // 0,1,2 stacks the slaves above and below the master (a column);
        // 0,3,4 puts them left and right (a row). Both dock every slave
        // directly to the master, so both are legal cascades.
        byte[] column = encoder.encodeWithConfig(CASCADE_MESSAGE, cascadeConfig(List.of(0, 1, 2)));
        byte[] row = encoder.encodeWithConfig(CASCADE_MESSAGE, cascadeConfig(List.of(0, 3, 4)));

        assertNotNull(column, "Column layout should encode");
        assertNotNull(row, "Row layout should encode");
        assertFalse(Arrays.equals(column, row),
            "A different lattice layout must produce a different symbol");

        assertEquals(CASCADE_MESSAGE, decoder.decode(row),
            "The row layout should still carry the payload");
    }

    private static JABCodeEncoder.Config cascadeConfig(List<Integer> positions) {
        return JABCodeEncoder.Config.builder()
            .colorNumber(32)
            .eccLevel(5)
            .symbolNumber(3)
            .symbolVersions(THREE_EQUAL_VERSIONS)
            .symbolPositions(positions)
            .build();
    }
}
