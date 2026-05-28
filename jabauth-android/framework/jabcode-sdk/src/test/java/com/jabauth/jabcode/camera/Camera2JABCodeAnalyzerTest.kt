package com.jabauth.jabcode.camera

import android.media.Image
import android.media.ImageReader
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.DecodeResult
import com.jabauth.jabcode.JABCodeDecoder
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import org.junit.Assert.*

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
            onDecodeFailure = onDecodeFailure
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
        val mockResult = mock<DecodeResult>()
        whenever(mockImageReader.acquireLatestImage()).thenReturn(mockImage)
        whenever(mockDecoder.decode(any(), any())).thenReturn(mockResult)

        analyzer.analyze(mockImageReader)

        verify(onDecodeSuccess).invoke(mockResult)
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
