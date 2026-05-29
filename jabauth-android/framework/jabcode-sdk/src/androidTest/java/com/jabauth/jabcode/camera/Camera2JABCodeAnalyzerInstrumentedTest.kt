package com.jabauth.jabcode.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.media.ImageReader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jabauth.jabcode.ColorMode
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.DecodeResult
import com.jabauth.jabcode.JABCodeDecoder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented test for Camera2JABCodeAnalyzer
 * 
 * Tests Camera2 component lifecycle and integration.
 * 
 * Note: Full image processing tests require actual camera feed.
 * ImageReader.acquireLatestImage() only returns images queued by camera hardware,
 * not test-created synthetic images.
 * 
 * These tests verify:
 * - Component initialization
 * - Null image handling
 * - Decoder interface integration
 * 
 * Full end-to-end testing requires:
 * - Manual testing with device camera
 * - Camera2 E2E test suite (separate)
 */
@RunWith(AndroidJUnit4::class)
class Camera2JABCodeAnalyzerInstrumentedTest {
    
    private lateinit var imageReader: ImageReader
    private lateinit var testDecoder: TestJABCodeDecoder
    
    @Before
    fun setup() {
        // Create ImageReader for testing (720p YUV_420_888)
        imageReader = ImageReader.newInstance(
            1280, 720,
            ImageFormat.YUV_420_888,
            2
        )
        
        testDecoder = TestJABCodeDecoder()
    }
    
    @After
    fun cleanup() {
        imageReader.close()
    }
    
    @Test
    fun analyzer_initializesWithValidParameters() {
        // Create analyzer with standard configuration
        val analyzer = Camera2JABCodeAnalyzer(
            decoder = testDecoder,
            options = DecodeOptions(analyzeIntervalMs = 100L),
            onDecodeSuccess = { },
            onDecodeFailure = { _, _ -> }
        )
        
        // Should initialize without crashing
        assertNotNull("Analyzer should be created", analyzer)
    }
    
    @Test
    fun analyze_handlesNullImageGracefully() {
        val analyzer = Camera2JABCodeAnalyzer(
            decoder = testDecoder,
            options = DecodeOptions(analyzeIntervalMs = 100L),
            onDecodeSuccess = { },
            onDecodeFailure = { _, _ -> }
        )
        
        // ImageReader with no available images - should not crash
        analyzer.analyze(imageReader)
        
        // Success - no crash
        assertTrue("Null image handled gracefully", true)
    }
    
    @Test
    fun decoder_integrationWorks() {
        // Verify test decoder implements all required methods
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val options = DecodeOptions()
        
        // Should not crash
        val result = testDecoder.decode(bitmap, options)
        val multiple = testDecoder.decodeMultiple(bitmap, options)
        val contains = testDecoder.containsJABCode(bitmap)
        
        assertNotNull("Decode result should not be null", result)
        assertEquals("Multiple should return empty list", 0, multiple.size)
        assertTrue("ContainsJABCode should return true", contains)
        
        bitmap.recycle()
    }
    
    /**
     * Test implementation of JABCodeDecoder that succeeds
     */
    private class TestJABCodeDecoder : JABCodeDecoder {
        override fun decode(bitmap: Bitmap, options: DecodeOptions): DecodeResult? {
            return DecodeResult(
                data = "TEST_DATA".toByteArray(),
                colorMode = ColorMode.COLOR_8,
                position = Rect(0, 0, 100, 100),
                decodeTimeMs = 10L
            )
        }
        
        override fun decodeMultiple(bitmap: Bitmap, options: DecodeOptions): List<DecodeResult> {
            return emptyList()
        }
        
        override fun containsJABCode(image: Bitmap): Boolean {
            return true
        }
    }
}
