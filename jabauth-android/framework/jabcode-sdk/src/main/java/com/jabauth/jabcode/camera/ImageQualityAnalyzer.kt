package com.jabauth.jabcode.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Image quality analyzer for camera scanning
 * 
 * Calculates quality metrics to help users achieve optimal scan conditions:
 * - **Brightness:** Average luminosity (0.0 = dark, 1.0 = bright)
 * - **Focus:** Edge sharpness via Laplacian variance (0.0 = blurry, 1.0 = sharp)
 * - **Contrast:** Standard deviation of intensities (0.0 = flat, 1.0 = high contrast)
 * 
 * All metrics normalized to 0.0-1.0 range for UI display.
 * 
 * **Migrated from diagnostic app** (2026-05-07)
 * - Source: CameraAnalyzer.kt + JABCodeAnalyzer.kt
 * - Consolidated best algorithms from both implementations
 * 
 * **Usage:**
 * ```kotlin
 * val analyzer = ImageQualityAnalyzer()
 * val metrics = analyzer.analyze(bitmap)
 * println("Brightness: ${metrics.brightness}, Focus: ${metrics.focus}")
 * ```
 */
class ImageQualityAnalyzer {
    
    /**
     * Quality metrics result
     * 
     * @property brightness Average luminosity (0.0-1.0)
     * @property focus Edge sharpness (0.0-1.0)
     * @property contrast Intensity variation (0.0-1.0)
     */
    data class QualityMetrics(
        val brightness: Float,
        val focus: Float,
        val contrast: Float
    ) {
        /**
         * Sharpness is the focus score under another name — an alias kept for
         * callers migrated from the retired ImageQualityMetrics model.
         */
        val sharpness: Float get() = focus

        /** Focus clears the decodability threshold (i.e. the frame is not blurry). */
        val isInFocus: Boolean get() = focus >= MIN_FOCUS

        /** Brightness sits in the usable band — neither too dark nor blown out. */
        val isWellExposed: Boolean get() = brightness in MIN_BRIGHTNESS..MAX_BRIGHTNESS

        /**
         * Single 0..1 composite for ranking frames (e.g. best-frame selection).
         * Focus-weighted, because detection/framing is the field bottleneck;
         * exposure and contrast are secondary contributors.
         */
        val qualityScore: Float
            get() = (FOCUS_WEIGHT * focus +
                     CONTRAST_WEIGHT * contrast +
                     EXPOSURE_WEIGHT * exposureScore()).coerceIn(0f, 1f)

        /** 1.0 within the well-exposed band, ramping linearly to 0 at the dark/bright extremes. */
        private fun exposureScore(): Float = when {
            brightness < MIN_BRIGHTNESS -> brightness / MIN_BRIGHTNESS
            brightness > MAX_BRIGHTNESS -> (1f - brightness) / (1f - MAX_BRIGHTNESS)
            else -> 1f
        }.coerceIn(0f, 1f)

        /**
         * Check if all metrics meet minimum thresholds (defaults from the
         * companion constants; override per call site if needed).
         *
         * @return True if brightness, focus and contrast all clear their minimums.
         */
        fun meetsThresholds(
            minBrightness: Float = MIN_BRIGHTNESS,
            minFocus: Float = MIN_FOCUS,
            minContrast: Float = MIN_CONTRAST
        ): Boolean {
            return brightness >= minBrightness &&
                   focus >= minFocus &&
                   contrast >= minContrast
        }

        companion object {
            // Decodability thresholds (0..1). Provisional — to be calibrated against
            // field decode-rate data (R4 acquisition; see robustness/r4-acquisition).
            const val MIN_BRIGHTNESS = 0.3f
            const val MAX_BRIGHTNESS = 0.95f
            const val MIN_FOCUS = 0.4f
            const val MIN_CONTRAST = 0.3f

            // qualityScore weights — focus-dominant, since framing is the field bottleneck.
            private const val FOCUS_WEIGHT = 0.6f
            private const val CONTRAST_WEIGHT = 0.2f
            private const val EXPOSURE_WEIGHT = 0.2f
        }
    }
    
    companion object {
        // Normalization constants
        private const val BRIGHTNESS_MAX = 255f
        private const val FOCUS_THRESHOLD = 100f
        private const val CONTRAST_MAX = 128f
        
        // Sampling for performance (analyze every Nth pixel)
        private const val SAMPLE_SIZE = 10
    }
    
    /**
     * Buffer for [android.graphics.Bitmap.getPixels], reused across frames.
     *
     * The scanner analyses a frame several times a second and each crop is the same size, so a
     * fresh 4 MB array per frame is churn the GC does not need. Reallocated only when the crop
     * size changes.
     */
    private var pixelBuffer: IntArray = IntArray(0)

    /**
     * Analyze image quality from Bitmap.
     *
     * ## What changed, and why it was 451ms
     *
     * The three metrics used to walk the bitmap independently through `Bitmap.getPixel`, one JNI
     * crossing per pixel. Brightness and contrast sampled every tenth pixel; focus did not sample
     * at all, so on a 989x989 crop it made 978,121 individual calls and then convolved all of
     * them. Measured on an SM-S918U: **451ms per frame**, against 52ms for the JABCode decode it
     * was supposed to be assisting — 76% of the scanner's per-frame budget spent on three floats.
     *
     * Now: one `getPixels` into a reused buffer, then [QualityMath] over a sampled grid.
     */
    fun analyze(bitmap: Bitmap): QualityMetrics {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return QualityMetrics(0f, 0f, 0f)

        if (pixelBuffer.size < w * h) pixelBuffer = IntArray(w * h)
        bitmap.getPixels(pixelBuffer, 0, w, 0, 0, w, h)

        val raw = QualityMath.analyse(pixelBuffer, w, h, SAMPLE_SIZE)

        return QualityMetrics(
            brightness = (raw.brightness / BRIGHTNESS_MAX).coerceIn(0f, 1f),
            focus = (raw.focusVariance / FOCUS_THRESHOLD).coerceIn(0f, 1f),
            contrast = (raw.contrast / CONTRAST_MAX).coerceIn(0f, 1f)
        )
    }

    /**
     * Analyze image quality from CameraX ImageProxy
     * 
     * Convenience method that handles YUV conversion automatically.
     * 
     * @param image ImageProxy from camera
     * @return QualityMetrics or null if conversion fails
     */
    fun analyzeFromImageProxy(image: ImageProxy): QualityMetrics? {
        val bitmap = CameraUtils.imageProxyToBitmap(image) ?: return null
        return try {
            analyze(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}
