package com.jabauth.jabcode

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WS-5 Council Session 3 — instrumented canary for jabMobileDecodeCameraWithMeta.
 *
 * Drives the real native library end-to-end on a checked-in Nc=3 fixture PNG
 * and asserts the SDK surfaces ColorMode.COLOR_16. Its job is to catch JNI
 * marshaling regressions that mocked unit tests cannot see — the exact class
 * of bug that silently broke the original claude/ws-5-color-metadata branch
 * (decoder switched from decodeJABCode to decodeJABCodeEx → 200+ decodes → 0).
 *
 * Single canary by design — heavier coverage is on the C-level
 * test_jab_mobile_with_meta gate which exercises the same decode path
 * without device dependency.
 *
 * See: docs/cassandra-register/H_partI_clean_data_failure.md
 */
@RunWith(AndroidJUnit4::class)
class JABCodeDecoderWithMetaInstrumentedTest {

    @Test
    fun decode_nc3_fixture_returns_COLOR_16() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val assetStream = context.assets.open("nc3-16c.png")
        val bitmap = BitmapFactory.decodeStream(assetStream)
        assetStream.close()
        assertNotNull("Fixture PNG must decode into a Bitmap", bitmap)

        val decoder = JABCodeDecoderImpl()
        val result = decoder.decode(bitmap, DecodeOptions(timeout = 2000L))

        assertNotNull("Decode of Nc=3 fixture must succeed", result)
        assertEquals(
            "Nc=3 fixture must report ColorMode.COLOR_16 — this is the bug fix " +
                "that motivated jabMobileDecodeCameraWithMeta",
            ColorMode.COLOR_16,
            result!!.colorMode
        )
    }
}
