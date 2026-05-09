package com.jabauth.jabcode.camera.metadata

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ImageQualityMetrics data class
 * 
 * Tests image quality metric properties
 */
class ImageQualityMetricsTest {
    
    @Test
    fun imageQualityMetrics_storesCorrectProperties() {
        val metrics = ImageQualityMetrics(
            brightness = 128.5f,
            contrast = 0.75f,
            sharpness = 0.82f,
            isWellExposed = true,
            isInFocus = true,
            qualityScore = 0.85f
        )
        
        assertEquals(128.5f, metrics.brightness, 0.001f)
        assertEquals(0.75f, metrics.contrast, 0.001f)
        assertEquals(0.82f, metrics.sharpness, 0.001f)
        assertTrue(metrics.isWellExposed)
        assertTrue(metrics.isInFocus)
        assertEquals(0.85f, metrics.qualityScore, 0.001f)
    }
    
    @Test
    fun imageQualityMetrics_copyWorks() {
        val original = ImageQualityMetrics(
            brightness = 100.0f,
            contrast = 0.5f,
            sharpness = 0.6f,
            isWellExposed = false,
            isInFocus = false,
            qualityScore = 0.5f
        )
        
        val modified = original.copy(
            isWellExposed = true,
            qualityScore = 0.8f
        )
        
        assertEquals(100.0f, modified.brightness, 0.001f)
        assertTrue(modified.isWellExposed)
        assertEquals(0.8f, modified.qualityScore, 0.001f)
    }
    
    @Test
    fun imageQualityMetrics_brightnessInRange() {
        val darkMetrics = ImageQualityMetrics(
            brightness = 50.0f,
            contrast = 0.5f,
            sharpness = 0.5f,
            isWellExposed = false,
            isInFocus = true,
            qualityScore = 0.5f
        )
        
        val brightMetrics = ImageQualityMetrics(
            brightness = 200.0f,
            contrast = 0.5f,
            sharpness = 0.5f,
            isWellExposed = false,
            isInFocus = true,
            qualityScore = 0.5f
        )
        
        assertTrue("Dark image should have low brightness", darkMetrics.brightness < 100.0f)
        assertTrue("Bright image should have high brightness", brightMetrics.brightness > 150.0f)
    }
    
    @Test
    fun imageQualityMetrics_qualityScoreNormalized() {
        val lowQuality = ImageQualityMetrics(
            brightness = 128.0f,
            contrast = 0.3f,
            sharpness = 0.4f,
            isWellExposed = true,
            isInFocus = false,
            qualityScore = 0.3f
        )
        
        val highQuality = ImageQualityMetrics(
            brightness = 128.0f,
            contrast = 0.8f,
            sharpness = 0.9f,
            isWellExposed = true,
            isInFocus = true,
            qualityScore = 0.9f
        )
        
        assertTrue("Quality score should be between 0 and 1", 
            lowQuality.qualityScore >= 0.0f && lowQuality.qualityScore <= 1.0f)
        assertTrue("Quality score should be between 0 and 1",
            highQuality.qualityScore >= 0.0f && highQuality.qualityScore <= 1.0f)
    }
}
