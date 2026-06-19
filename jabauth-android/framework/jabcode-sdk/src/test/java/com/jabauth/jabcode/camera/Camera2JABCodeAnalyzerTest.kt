package com.jabauth.jabcode.camera

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import android.media.ImageReader
import com.jabauth.jabcode.ColorMode
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.DecodeResult
import com.jabauth.jabcode.JABCodeDecoder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.junit.Assert.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the [Camera2JABCodeAnalyzer] decode **control flow** —
 * frame throttling, success/failure callback routing, exception handling and
 * image cleanup. The YUV_420_888→Bitmap conversion is an Android-boundary
 * concern covered by Camera2JABCodeAnalyzerInstrumentedTest (androidTest/);
 * here it is replaced via the analyzer's `imageToBitmap` seam with a stub
 * returning a tiny real Bitmap, so a mocked [Image] (which has no YUV planes)
 * never reaches the real conversion.
 *
 * Runs under Robolectric for two reasons: (1) on a bare JVM (JDK 23) the
 * Mockito inline mock-maker cannot instrument android.media.ImageReader /
 * android.media.Image — every test failed with "Mockito cannot mock this
 * class: class android.media.ImageReader"; (2) the analyzer reads
 * bitmap.getPixel(...) on the converted frame, which needs a real Bitmap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Camera2JABCodeAnalyzerTest {

    private lateinit var mockDecoder: JABCodeDecoder
    private lateinit var mockImageReader: ImageReader
    private lateinit var mockImage: Image
    private lateinit var onDecodeSuccess: (DecodeResult) -> Unit
    private lateinit var onDecodeFailure: (String, Long) -> Unit
    private lateinit var analyzer: Camera2JABCodeAnalyzer

    @Before
    fun setup() {
        mockDecoder = mock()
        mockImageReader = mock()
        mockImage = mock()
        onDecodeSuccess = mock()
        onDecodeFailure = mock()

        analyzer = Camera2JABCodeAnalyzer(
            decoder = mockDecoder,
            options = DecodeOptions(analyzeIntervalMs = 100L),
            onDecodeSuccess = onDecodeSuccess,
            onDecodeFailure = onDecodeFailure,
            // Stub the Image→Bitmap conversion: a mocked Image has no real YUV
            // planes, so the production CameraUtils.imageToBitmap path cannot
            // run here. These tests exercise decode control flow, not the
            // conversion. Returns a fresh 2x2 bitmap each call (analyze()
            // recycles it).
            imageToBitmap = { Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888) }
        )
    }

    @Test
    fun `analyze skips frame when no image available`() {
        whenever(mockImageReader.acquireLatestImage()).thenReturn(null)

        analyzer.analyze(mockImageReader)

        verify(mockImageReader).acquireLatestImage()
        verifyNoInteractions(mockDecoder)
        verifyNoInteractions(onDecodeSuccess)
        verifyNoInteractions(onDecodeFailure)
    }

    @Test
    fun `analyze closes image after processing`() {
        whenever(mockImageReader.acquireLatestImage()).thenReturn(mockImage)
        whenever(mockDecoder.decode(any(), any())).thenReturn(null)

        analyzer.analyze(mockImageReader)

        verify(mockImage).close()
    }

    @Test
    fun `analyze invokes onDecodeSuccess when JABCode found`() {
        // Use a REAL DecodeResult rather than a mock: it is a Kotlin data
        // class (final), which the subclass mock-maker this module uses
        // cannot mock. A real instance is also more faithful — the analyzer
        // reads result.colorMode.value on the success path; COLOR_16 keeps it
        // out of the (default-disabled) nc2 consensus branch.
        val result = DecodeResult(
            data = "OK".toByteArray(),
            colorMode = ColorMode.COLOR_16,
            position = Rect(0, 0, 0, 0),
            decodeTimeMs = 0L
        )
        whenever(mockImageReader.acquireLatestImage()).thenReturn(mockImage)
        whenever(mockDecoder.decode(any(), any())).thenReturn(result)

        analyzer.analyze(mockImageReader)

        verify(onDecodeSuccess).invoke(result)
        verifyNoInteractions(onDecodeFailure)
    }

    @Test
    fun `analyze invokes onDecodeFailure when result is null (a873969+)`() {
        // Per the analyzer-bug fix (a873969), null result is a real failure
        // outcome and MUST invoke onDecodeFailure so upstream telemetry can
        // attribute failures. Mockito returns null for an unstubbed
        // decoder.getLastError(), so the analyzer falls back to the literal
        // "No JABCode found" sentinel string.
        whenever(mockImageReader.acquireLatestImage()).thenReturn(mockImage)
        whenever(mockDecoder.decode(any(), any())).thenReturn(null)

        analyzer.analyze(mockImageReader)

        verifyNoInteractions(onDecodeSuccess)
        verify(onDecodeFailure).invoke(eq("No JABCode found"), any())
    }

    @Test
    fun `analyze invokes onDecodeFailure when exception occurs`() {
        whenever(mockImageReader.acquireLatestImage()).thenReturn(mockImage)
        whenever(mockDecoder.decode(any(), any())).thenThrow(RuntimeException("Test error"))

        analyzer.analyze(mockImageReader)

        verify(onDecodeFailure).invoke(argThat { contains("Test error") }, eq(0L))
        verifyNoInteractions(onDecodeSuccess)
    }

    @Test
    fun `analyze throttles frames based on analyzeIntervalMs`() {
        whenever(mockImageReader.acquireLatestImage()).thenReturn(mockImage)

        // First analysis should process
        analyzer.analyze(mockImageReader)
        verify(mockImageReader, times(1)).acquireLatestImage()

        // Immediate second analysis should be throttled (within 100ms interval)
        analyzer.analyze(mockImageReader)
        verify(mockImageReader, times(1)).acquireLatestImage() // Still 1, not 2
    }
}
