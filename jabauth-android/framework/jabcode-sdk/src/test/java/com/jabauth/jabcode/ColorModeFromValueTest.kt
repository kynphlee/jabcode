package com.jabauth.jabcode

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * WS-5 Council Session 3 — Kotlin unit coverage for the ColorMode.fromValue
 * mapping that JABCodeDecoderImpl uses to convert the native bridge's
 * `outColorNumber[0]` into a typed ColorMode for DecodeResult.
 *
 * Scope: pure-logic mapping. The native-bridge call itself is exercised
 * end-to-end by JABCodeDecoderWithMetaInstrumentedTest (androidTest/) so
 * we don't add final-class mocking infrastructure for the bridge here.
 *
 * See: docs/cassandra-register/H_partI_clean_data_failure.md for the
 * underlying root-cause this contract bridges.
 */
class ColorModeFromValueTest {

    @Test
    fun `fromValue maps 16 to COLOR_16 — the canonical Nc=3 case`() {
        // This is the user's observed bug: scanning an Nc=3 (16-color)
        // print was decoding as COLOR_8 because outColorNumber wasn't
        // being captured. WithMeta fixes that capture; this assertion
        // locks the mapping.
        assertEquals(ColorMode.COLOR_16, ColorMode.fromValue(16))
    }

    @Test
    fun `fromValue maps each native color count to the expected ColorMode`() {
        assertEquals(ColorMode.COLOR_2, ColorMode.fromValue(2))
        assertEquals(ColorMode.COLOR_4, ColorMode.fromValue(4))
        assertEquals(ColorMode.COLOR_8, ColorMode.fromValue(8))
        assertEquals(ColorMode.COLOR_16, ColorMode.fromValue(16))
        assertEquals(ColorMode.COLOR_32, ColorMode.fromValue(32))
        assertEquals(ColorMode.COLOR_64, ColorMode.fromValue(64))
        assertEquals(ColorMode.COLOR_128, ColorMode.fromValue(128))
    }

    @Test
    fun `fromValue falls back to COLOR_8 on the failure-path value 0`() {
        // Native bridge sets outColorNumber[0] = 0 on decode failure
        // (see mobile_bridge.c::jabMobileDecodeCameraWithMeta). The
        // SDK should not propagate a synthetic "COLOR_0" — fallback
        // to COLOR_8 is the documented behavior.
        assertEquals(ColorMode.COLOR_8, ColorMode.fromValue(0))
    }

    @Test
    fun `fromValue falls back to COLOR_8 on unexpected values`() {
        // The bridge currently does not surface Nc=7 (256) as an enum
        // member. Any unrecognized value must collapse to the default.
        assertEquals(ColorMode.COLOR_8, ColorMode.fromValue(256))
        assertEquals(ColorMode.COLOR_8, ColorMode.fromValue(99))
        assertEquals(ColorMode.COLOR_8, ColorMode.fromValue(-1))
    }
}
