package com.jabcode.panama;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-symbol error correction across a cascade.
 *
 * <p>Both encode paths used to write {@code symbol_ecc_levels[0]} and nothing else, so every slave
 * stayed unset and {@code wcwr_for_level()} normalised it to {@code DEFAULT_ECC_LEVEL} (3). A cascade
 * requested at ECC 10 therefore protected its primary at 10 and every slave at 3 — silently, and in
 * both directions, since a request below 3 was raised instead.
 *
 * <p>The load-bearing tests here are the CAPACITY ones. Asserting that the encoder accepts a config
 * proves nothing about ECC: capacity is what error correction actually costs, so a level that fails
 * to reach the slaves shows up as a cascade holding the wrong amount of data. That is measurable
 * through the real {@code libjabcode.so} without reading back native memory.
 */
@DisplayName("Cascade per-symbol ECC levels")
class SymbolEccLevelsTest {

    /** A 3-symbol horizontal strip: primary + two docked slaves, all the same size. */
    private static JABCodeEncoder.Config.Builder strip(int symbols) {
        return JABCodeEncoder.Config.builder()
                .colorNumber(8)
                .symbolNumber(symbols)
                .symbolVersions(java.util.Collections.nCopies(symbols, new SymbolVersion(4)));
    }

    private static byte[] payload(int n) {
        byte[] b = new byte[n];
        Arrays.fill(b, (byte) 'A');
        return b;
    }

    /** Largest payload the encoder accepts for this config — the guaranteed floor, not a lucky fit. */
    private static int capacity(JABCodeEncoder.Config config) {
        JABCodeEncoder enc = new JABCodeEncoder();
        int lo = 1;
        int hi = 20000;
        int best = 0;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            boolean ok;
            try {
                ok = enc.encodeBytes(payload(mid), config) != null;
            } catch (RuntimeException e) {
                ok = false;
            }
            if (ok) { best = mid; lo = mid + 1; } else { hi = mid - 1; }
        }
        return best;
    }

    // ---------------------------------------------------------------- the bug

    /**
     * The regression test. Stronger ECC must cost capacity on a CASCADE, not only on a lone symbol.
     *
     * <p>Pre-fix, a 3-symbol cascade at ECC 1 and at ECC 10 reported nearly the same capacity,
     * because only the primary's level ever changed — two thirds of the code sat at level 3 either
     * way. Post-fix the two differ substantially.
     */
    @Test
    @DisplayName("ECC reaches the slaves: a cascade's capacity responds to the requested level")
    void eccLevelReachesEverySymbol() {
        int weak = capacity(strip(3).eccLevel(1).build());
        int strong = capacity(strip(3).eccLevel(10).build());

        assertTrue(weak > 0 && strong > 0, "both cascades must encode at all");
        assertTrue(strong < weak,
                "ECC 10 must hold less than ECC 1 across a 3-symbol cascade "
                        + "(weak=" + weak + " strong=" + strong + ")");

        // Scale check: if only the primary honoured the level, the gap would be roughly a third of
        // what it should be. Requiring a >2x ratio is comfortably outside that failure mode.
        assertTrue(weak > strong * 2,
                "ECC 1 should hold more than twice ECC 10 when every symbol honours the level "
                        + "(weak=" + weak + " strong=" + strong + ")");
    }

    /** The single-symbol case was always correct — it must stay correct. */
    @Test
    @DisplayName("Single symbol unchanged: the primary already honoured its level")
    void singleSymbolIsUnaffected() {
        int weak = capacity(strip(1).eccLevel(1).build());
        int strong = capacity(strip(1).eccLevel(10).build());
        assertTrue(weak > strong, "weak=" + weak + " strong=" + strong);
    }

    /** Capacity must fall monotonically as ECC rises, at every symbol count. */
    @ParameterizedTest(name = "{0} symbol(s)")
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("Capacity decreases monotonically with ECC level")
    void capacityFallsAsEccRises(int symbols) {
        int prev = Integer.MAX_VALUE;
        for (int ecc : new int[]{1, 3, 5, 7, 10}) {
            int cap = capacity(strip(symbols).eccLevel(ecc).build());
            assertTrue(cap > 0, "ECC " + ecc + " must encode");
            assertTrue(cap <= prev,
                    "capacity must not rise as ECC goes to " + ecc + " (was " + prev + ", now " + cap + ")");
            prev = cap;
        }
    }

    // ------------------------------------------------------- per-symbol control

    /**
     * An explicit per-symbol list is honoured. Mixing levels must land strictly between the two
     * uniform extremes — that can only happen if each symbol takes its own value.
     */
    @Test
    @DisplayName("Per-symbol levels land between the uniform extremes")
    void perSymbolLevelsAreHonoured() {
        int allWeak = capacity(strip(3).eccLevel(1).build());
        int allStrong = capacity(strip(3).eccLevel(10).build());
        int mixed = capacity(strip(3).eccLevel(1).symbolEccLevels(List.of(1, 10, 10)).build());

        assertTrue(mixed < allWeak && mixed > allStrong,
                "a mixed cascade must sit between uniform-weak and uniform-strong "
                        + "(weak=" + allWeak + " mixed=" + mixed + " strong=" + allStrong + ")");
    }

    /** Supplying the scalar level for every symbol must equal not supplying a list at all. */
    @Test
    @DisplayName("An explicit uniform list equals the scalar default")
    void explicitUniformListMatchesScalar() {
        int viaScalar = capacity(strip(3).eccLevel(7).build());
        int viaList = capacity(strip(3).eccLevel(7).symbolEccLevels(List.of(7, 7, 7)).build());
        assertEquals(viaScalar, viaList, "the list form must be a no-op when it repeats the scalar");
    }

    /** {@code 0} is the spec's inherit sentinel and must be accepted for a slave. */
    @Test
    @DisplayName("Level 0 (inherit from host) is accepted for slaves")
    void inheritSentinelIsAccepted() {
        assertDoesNotThrow(() -> strip(3).eccLevel(5).symbolEccLevels(List.of(5, 0, 0)).build());
        int inheriting = capacity(strip(3).eccLevel(5).symbolEccLevels(List.of(5, 0, 0)).build());
        assertTrue(inheriting > 0, "an inheriting cascade must still encode");
    }

    // ------------------------------------------------------------- validation

    @ParameterizedTest(name = "level {0} rejected")
    @ValueSource(ints = {-1, 11, 99})
    @DisplayName("Out-of-range per-symbol levels are rejected")
    void outOfRangeRejected(int bad) {
        assertThrows(IllegalArgumentException.class,
                () -> strip(3).symbolEccLevels(List.of(1, bad, 1)).build());
    }

    @Test
    @DisplayName("A null entry is rejected")
    void nullEntryRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> strip(3).symbolEccLevels(Arrays.asList(1, null, 1)).build());
    }

    @ParameterizedTest(name = "{0} levels for {1} symbols")
    @CsvSource({"2,3", "4,3", "1,2"})
    @DisplayName("Count must match symbolNumber")
    void countMustMatchSymbolNumber(int levels, int symbols) {
        Integer[] list = new Integer[levels];
        Arrays.fill(list, 5);
        assertThrows(IllegalArgumentException.class,
                () -> strip(symbols).symbolEccLevels(Arrays.asList(list)).build());
    }

    /** The scalar has no host to inherit from, so 0 stays illegal there. */
    @Test
    @DisplayName("Scalar eccLevel still rejects 0 — nothing to inherit from")
    void scalarStillRejectsZero() {
        assertThrows(IllegalArgumentException.class,
                () -> JABCodeEncoder.Config.builder().eccLevel(0));
    }

    /** Round-trip: an ECC-varied cascade still decodes to the exact bytes. */
    @Test
    @DisplayName("A mixed-ECC cascade round-trips through the decoder")
    void mixedEccCascadeRoundTrips() {
        byte[] data = "mixed-ecc cascade round trip".getBytes(StandardCharsets.UTF_8);
        byte[] png = new JABCodeEncoder().encodeBytes(
                data, strip(3).eccLevel(5).symbolEccLevels(List.of(8, 3, 3)).build());
        assertNotNull(png, "mixed-ECC cascade must encode");

        byte[] out = new JABCodeDecoder().decodeBytes(png);
        assertArrayEquals(data, out, "a mixed-ECC cascade must decode back to the exact payload");
    }
}
