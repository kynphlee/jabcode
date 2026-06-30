package com.jabcode.panama;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boundary tests for {@link JabCodeLimits}, the single authoritative
 * encode-parameter limits surface.
 */
class JabCodeLimitsTest {

    @Test
    void pinnedConstants() {
        assertEquals(0, JabCodeLimits.ECC_MIN);
        // ECC_MAX is pinned to the codec's ecclevel2wcwr[11][2] table
        // (encoder.h:231); highest valid index is 10.
        assertEquals(10, JabCodeLimits.ECC_MAX);
        assertEquals(3, JabCodeLimits.ECC_DEFAULT);
        assertEquals(1, JabCodeLimits.SYMBOL_MIN);
        assertEquals(61, JabCodeLimits.SYMBOL_MAX);
        assertEquals(1, JabCodeLimits.VERSION_MIN);
        assertEquals(32, JabCodeLimits.VERSION_MAX);
    }

    @Test
    void colorNumbersAreTheEightModes() {
        assertEquals(java.util.Set.of(2, 4, 8, 16, 32, 64, 128, 256),
            JabCodeLimits.COLOR_NUMBERS);
    }

    @Test
    void colorNumbersSetIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
            () -> JabCodeLimits.COLOR_NUMBERS.add(512));
    }

    // --- colour number -----------------------------------------------------

    @Test
    void validColorNumbersAccepted() {
        for (int c : new int[] {2, 4, 8, 16, 32, 64, 128, 256}) {
            assertEquals(c, JabCodeLimits.validateColorNumber(c));
        }
    }

    @Test
    void invalidColorNumbersRejected() {
        for (int c : new int[] {0, 1, 3, 5, 7, 100, 255, 512}) {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> JabCodeLimits.validateColorNumber(c));
            assertTrue(ex.getMessage().contains(Integer.toString(c)),
                "message should name offending value: " + ex.getMessage());
        }
    }

    // --- ECC level ---------------------------------------------------------

    @Test
    void eccLevelBoundariesAccepted() {
        assertEquals(0, JabCodeLimits.validateEccLevel(0));
        assertEquals(10, JabCodeLimits.validateEccLevel(10));
        assertEquals(3, JabCodeLimits.validateEccLevel(3));
    }

    @Test
    void eccLevelOutOfRangeRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> JabCodeLimits.validateEccLevel(-1));
        // 11 used to be rejected and still is: it sits one past the
        // codec-authoritative maximum (ecclevel2wcwr has 11 rows, indices 0..10).
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> JabCodeLimits.validateEccLevel(11));
        assertTrue(ex.getMessage().contains("11"), ex.getMessage());
        assertTrue(ex.getMessage().contains("10"), ex.getMessage());
    }

    // --- symbol number -----------------------------------------------------

    @Test
    void symbolNumberBoundariesAccepted() {
        assertEquals(1, JabCodeLimits.validateSymbolNumber(1));
        assertEquals(61, JabCodeLimits.validateSymbolNumber(61));
    }

    @Test
    void symbolNumberOutOfRangeRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> JabCodeLimits.validateSymbolNumber(0));
        assertThrows(IllegalArgumentException.class,
            () -> JabCodeLimits.validateSymbolNumber(62));
    }

    // --- version -----------------------------------------------------------

    @Test
    void versionBoundariesAccepted() {
        assertEquals(1, JabCodeLimits.validateVersion("X", 1));
        assertEquals(32, JabCodeLimits.validateVersion("Y", 32));
    }

    @Test
    void versionOutOfRangeRejected() {
        IllegalArgumentException low = assertThrows(
            IllegalArgumentException.class,
            () -> JabCodeLimits.validateVersion("X", 0));
        assertTrue(low.getMessage().startsWith("X"), low.getMessage());

        IllegalArgumentException high = assertThrows(
            IllegalArgumentException.class,
            () -> JabCodeLimits.validateVersion("Y", 33));
        assertTrue(high.getMessage().startsWith("Y"), high.getMessage());
        assertTrue(high.getMessage().contains("33"), high.getMessage());
    }
}
