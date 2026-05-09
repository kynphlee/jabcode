package com.jabauth.jabcode.camera

import android.graphics.ImageFormat
import android.util.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for StreamConfigValidator
 * 
 * Tests stream configuration helper functions on real Android runtime
 */
@RunWith(AndroidJUnit4::class)
class StreamConfigValidatorInstrumentedTest {
    
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
    
    @Test
    fun streamConfig_returnsCorrectValues() {
        val config = StreamConfigValidator.StreamConfig(
            format = ImageFormat.JPEG,
            size = Size(800, 600),
            isInput = false
        )
        
        assertEquals(ImageFormat.JPEG, config.format)
        assertEquals(800, config.size.width)
        assertEquals(600, config.size.height)
        assertFalse(config.isInput)
    }
    
    @Test
    fun validationResult_storesCorrectValues() {
        val successResult = StreamConfigValidator.ValidationResult(isValid = true)
        assertTrue(successResult.isValid)
        assertNull(successResult.reason)
        
        val failResult = StreamConfigValidator.ValidationResult(
            isValid = false,
            reason = "Test error"
        )
        assertFalse(failResult.isValid)
        assertEquals("Test error", failResult.reason)
    }
}
