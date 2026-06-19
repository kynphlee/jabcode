package com.jabauth.jabcode.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the derived members of [ImageQualityAnalyzer.QualityMetrics]
 * (sharpness, isInFocus, isWellExposed, qualityScore) — the concepts absorbed
 * from the retired ImageQualityMetrics model.
 */
class QualityMetricsTest {

    private fun metrics(brightness: Float, focus: Float, contrast: Float) =
        ImageQualityAnalyzer.QualityMetrics(brightness, focus, contrast)

    @Test
    fun sharpnessAliasesFocus() {
        assertEquals(0.7f, metrics(0.5f, 0.7f, 0.5f).sharpness, 0f)
    }

    @Test
    fun isInFocusTracksFocusThreshold() {
        assertTrue(metrics(0.5f, 0.40f, 0.5f).isInFocus)   // at threshold (MIN_FOCUS)
        assertTrue(metrics(0.5f, 0.80f, 0.5f).isInFocus)
        assertFalse(metrics(0.5f, 0.39f, 0.5f).isInFocus)  // just below
    }

    @Test
    fun isWellExposedOnlyWithinBand() {
        assertTrue(metrics(0.50f, 0.5f, 0.5f).isWellExposed)   // mid-band
        assertFalse(metrics(0.20f, 0.5f, 0.5f).isWellExposed)  // too dark
        assertFalse(metrics(0.99f, 0.5f, 0.5f).isWellExposed)  // blown out
    }

    @Test
    fun qualityScoreBoundedAndRanksGoodAbovePoor() {
        val good = metrics(0.6f, 0.9f, 0.8f).qualityScore
        val poor = metrics(0.1f, 0.1f, 0.2f).qualityScore
        assertTrue(good in 0f..1f)
        assertTrue(poor in 0f..1f)
        assertTrue("sharp+exposed should outrank blurry+dark", good > poor)
    }

    @Test
    fun qualityScoreIsFocusDominant() {
        // Equal exposure + contrast; the sharper frame must score higher.
        val sharper = metrics(0.6f, 0.9f, 0.5f).qualityScore
        val blurrier = metrics(0.6f, 0.2f, 0.5f).qualityScore
        assertTrue(sharper > blurrier)
    }

    @Test
    fun meetsThresholdsPassesGoodFailsLowFocus() {
        assertTrue(metrics(0.5f, 0.6f, 0.5f).meetsThresholds())
        assertFalse(metrics(0.5f, 0.30f, 0.5f).meetsThresholds())  // focus below MIN_FOCUS
    }
}
