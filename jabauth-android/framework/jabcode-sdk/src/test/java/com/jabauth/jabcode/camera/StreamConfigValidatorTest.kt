package com.jabauth.jabcode.camera

import android.graphics.ImageFormat
import android.util.Size
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for StreamConfigValidator
 *
 * Tests stream configuration helper functions
 * Full validation tests done via instrumented tests
 *
 * Runs under Robolectric so `android.util.Size` resolves to a real
 * implementation. Without it, the unit-test stub (returnDefaultValues=true)
 * makes Size.width / Size.height return 0, so the size assertions below
 * compared 1280/720 against 0 and failed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamConfigValidatorTest {

    private lateinit var validator: StreamConfigValidator

    @Before
    fun setup() {
        validator = StreamConfigValidator()
    }

    @Test
    fun previewPlusAnalysisConfig_returnsCorrectFormats() {
        val config = StreamConfigValidator.previewPlusAnalysisConfig()

        assertEquals("Should have 2 streams", 2, config.size)
        assertEquals("First stream should be PRIVATE", ImageFormat.PRIVATE, config[0].format)
        assertEquals("Second stream should be YUV_420_888", ImageFormat.YUV_420_888, config[1].format)
    }

    @Test
    fun previewPlusAnalysisConfig_usesDefaultSizes() {
        val config = StreamConfigValidator.previewPlusAnalysisConfig()

        assertEquals("Default preview width", 1280, config[0].size.width)
        assertEquals("Default preview height", 720, config[0].size.height)
        assertEquals("Default analysis width", 1280, config[1].size.width)
        assertEquals("Default analysis height", 720, config[1].size.height)
    }

    @Test
    fun previewPlusAnalysisConfig_usesCustomSizes() {
        val config = StreamConfigValidator.previewPlusAnalysisConfig(
            previewSize = Size(1920, 1080),
            analysisSize = Size(640, 480)
        )

        assertEquals("Custom preview width", 1920, config[0].size.width)
        assertEquals("Custom preview height", 1080, config[0].size.height)
        assertEquals("Custom analysis width", 640, config[1].size.width)
        assertEquals("Custom analysis height", 480, config[1].size.height)
    }
}
