package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip integration tests for MULTI-SYMBOL (cascade) JABCodes.
 *
 * <p>{@link CascadedEncodingTest} proves the encoder ACCEPTS a multi-symbol
 * configuration and writes a PNG, but never decodes the result -- so nothing
 * verified that a cascade's payload actually survives encode -> decode. These
 * tests close that gap: each encodes a payload the encoder splits across 2+
 * docked symbols, then asserts the decoder returns the EXACT original bytes.
 * The C core's {@code decodeJABCode} reassembles every symbol's slice into one
 * payload, so a correct cascade must round-trip losslessly.</p>
 *
 * <p>Gated on the native library being present (same pattern as
 * {@link InMemoryRoundTripIntegrationTest}); skipped, never failed, without it.</p>
 */
@EnabledIf("isNativeLibraryAvailable")
class CascadedRoundTripIntegrationTest {

    private final JABCodeEncoder encoder = new JABCodeEncoder();
    private final JABCodeDecoder decoder = new JABCodeDecoder();

    static boolean isNativeLibraryAvailable() {
        String[] candidates = {
            "../src/jabcode/build/libjabcode.so",
            "../lib/libjabcode.so",
            "libjabcode.so"
        };
        for (String p : candidates) {
            if (java.nio.file.Files.exists(java.nio.file.Path.of(p))) {
                return true;
            }
        }
        String ld = System.getenv("LD_LIBRARY_PATH");
        return ld != null && !ld.isEmpty();
    }

    /** Encode {@code message} as an N-symbol cascade and assert it decodes back exactly. */
    private void assertCascadeRoundTrips(String message, int colorNumber, int eccLevel,
                                         int symbolNumber, int sideVersion) {
        List<SymbolVersion> versions = new ArrayList<>();
        for (int i = 0; i < symbolNumber; i++) {
            versions.add(new SymbolVersion(sideVersion, sideVersion)); // all symbols same size
        }
        JABCodeEncoder.Config config = JABCodeEncoder.Config.builder()
            .colorNumber(colorNumber)
            .eccLevel(eccLevel)
            .symbolNumber(symbolNumber)
            .symbolVersions(versions)
            .build();

        byte[] png = encoder.encodeWithConfig(message, config);
        assertNotNull(png, symbolNumber + "-symbol cascade encode should return PNG bytes");
        assertTrue(png.length > 0, symbolNumber + "-symbol cascade PNG must be non-empty");

        JABCodeDecoder.resetDecoderState();
        String decoded = decoder.decode(png);
        assertEquals(message, decoded,
            symbolNumber + "-symbol cascade must round-trip the full payload "
            + "(decodeJABCode reassembles all symbols)");
    }

    @Test
    void twoSymbolCascadeRoundTrips() {
        String message = "This is a cascaded JABCode round-trip. The primary symbol carries "
            + "part of this text and the secondary symbol carries the remainder; the decoder "
            + "must reassemble both halves into the exact original payload. Extra padding here "
            + "ensures the encoder's proportional data split actually populates two symbols.";
        assertCascadeRoundTrips(message, 64, 6, 2, 12);
    }

    @Test
    void threeSymbolCascadeRoundTrips() {
        String message = "CASCADE-3-" + "X".repeat(900) + "-END";
        assertCascadeRoundTrips(message, 32, 5, 3, 12);
    }

    /**
     * Locks in the high-colour cascade fix: before it, the slave palette path used
     * fixed-size tables ({@code slave_palette_placement_index[8]},
     * {@code slave_palette_position[32]}) only dimensioned for &le;8 colours, so a
     * 256-colour cascade overflowed them and never round-tripped. The fix mirrors the
     * master high-Nc scheme (sequential palette index, palette[1] synthesis, extended
     * position table), so 256-colour cascades now decode losslessly.
     *
     * <p>Uses slave version 12: at high colour, cascade still fails at versions
     * &equiv; 0 (mod 5) (v10/v15/v20...) due to a separate pre-existing slave
     * capacity/alignment-geometry resonance — tracked as a follow-up and NOT the
     * palette bug fixed here. v12 is a realistic, unaffected cascade size.</p>
     */
    @Test
    void twoSymbolCascadeRoundTripsAt256Colour() {
        String message = "This is a 256-colour cascaded JABCode round-trip. The primary symbol "
            + "carries part of this text and the secondary symbol carries the remainder; the "
            + "decoder must reassemble both halves into the exact original payload. High-colour "
            + "(Nc=256) exercises the slave palette path that previously overflowed its fixed "
            + "8/32-entry tables. Extra padding here ensures the proportional split populates "
            + "two symbols.";
        assertCascadeRoundTrips(message, 256, 5, 2, 12);
    }
}
