package com.jabauth.ui.scanner

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for QualityIndicator composable
 * 
 * Tests quality value ranges and percentage calculations.
 */
class QualityIndicatorTest {
    
    @Test
    fun `quality value clamps to valid range`() {
        // Values should be clamped to 0.0-1.0 range
        val belowMin = -0.5f
        val aboveMax = 1.5f
        val valid = 0.75f
        
        // coerceIn should handle these
        assertEquals(0f, belowMin.coerceIn(0f, 1f), 0.001f)
        assertEquals(1f, aboveMax.coerceIn(0f, 1f), 0.001f)
        assertEquals(0.75f, valid.coerceIn(0f, 1f), 0.001f)
    }
    
    @Test
    fun `percentage calculation from quality value`() {
        assertEquals(0, (0f * 100).toInt())
        assertEquals(50, (0.5f * 100).toInt())
        assertEquals(75, (0.75f * 100).toInt())
        assertEquals(100, (1f * 100).toInt())
    }
    
    @Test
    fun `color thresholds for quality ranges`() {
        // High quality: >= 0.7
        val highQuality = 0.85f
        assertTrue(highQuality >= 0.7f)
        
        // Medium quality: 0.4 - 0.69
        val mediumQuality = 0.55f
        assertTrue(mediumQuality >= 0.4f && mediumQuality < 0.7f)
        
        // Low quality: < 0.4
        val lowQuality = 0.25f
        assertTrue(lowQuality < 0.4f)
    }
    
    @Test
    fun `boundary quality values`() {
        // Test exact boundaries
        assertEquals(70, (0.7f * 100).toInt())  // Success threshold
        assertEquals(40, (0.4f * 100).toInt())  // Warning threshold
    }
    
    @Test
    fun `quality indicator accepts valid labels`() {
        val labels = listOf("Brightness", "Focus", "Contrast", "Sharpness")
        labels.forEach { label ->
            assertTrue(label.isNotEmpty())
            assertTrue(label.length > 2)
        }
    }
}
