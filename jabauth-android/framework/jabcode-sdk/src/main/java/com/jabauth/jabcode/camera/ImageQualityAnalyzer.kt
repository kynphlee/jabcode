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
         * Check if all metrics meet minimum thresholds
         * 
         * @param minBrightness Minimum acceptable brightness (default: 0.3)
         * @param minFocus Minimum acceptable focus (default: 0.4)
         * @param minContrast Minimum acceptable contrast (default: 0.3)
         * @return True if all metrics meet thresholds
         */
        fun meetsThresholds(
            minBrightness: Float = 0.3f,
            minFocus: Float = 0.4f,
            minContrast: Float = 0.3f
        ): Boolean {
            return brightness >= minBrightness &&
                   focus >= minFocus &&
                   contrast >= minContrast
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
     * Analyze image quality from Bitmap
     * 
     * @param bitmap Source image
     * @return QualityMetrics with normalized values
     */
    fun analyze(bitmap: Bitmap): QualityMetrics {
        val brightness = calculateBrightness(bitmap)
        val focus = calculateFocus(bitmap)
        val contrast = calculateContrast(bitmap)
        
        return QualityMetrics(
            brightness = (brightness / BRIGHTNESS_MAX).coerceIn(0f, 1f),
            focus = (focus / FOCUS_THRESHOLD).coerceIn(0f, 1f),
            contrast = (contrast / CONTRAST_MAX).coerceIn(0f, 1f)
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
    
    /**
     * Calculate brightness (average luminosity)
     * 
     * Uses standard luminosity formula with RGB weighting:
     * L = 0.299*R + 0.587*G + 0.114*B
     * 
     * Samples every Nth pixel for performance.
     * 
     * @param bitmap Source image
     * @return Average luminosity (0-255)
     */
    private fun calculateBrightness(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        
        var totalBrightness = 0L
        var pixelCount = 0
        
        for (y in 0 until height step SAMPLE_SIZE) {
            for (x in 0 until width step SAMPLE_SIZE) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                // Standard luminosity formula (perceived brightness)
                val luminosity = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                totalBrightness += luminosity
                pixelCount++
            }
        }
        
        return if (pixelCount > 0) {
            (totalBrightness.toFloat() / pixelCount)
        } else {
            0f
        }
    }
    
    /**
     * Calculate focus quality using Laplacian variance
     * 
     * Measures edge sharpness by applying Laplacian kernel.
     * Blurry images have low variance (smooth edges).
     * Sharp images have high variance (crisp edges).
     * 
     * Uses 3x3 Laplacian kernel:
     * ```
     * [-1 -1 -1]
     * [-1  8 -1]
     * [-1 -1 -1]
     * ```
     * 
     * @param bitmap Source image
     * @return Laplacian variance (higher = sharper)
     */
    private fun calculateFocus(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        
        // Convert to grayscale for simpler processing
        val grayPixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                grayPixels[y * width + x] = ((r + g + b) / 3)
            }
        }
        
        // Apply Laplacian kernel
        var laplacianSum = 0.0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                
                // 3x3 Laplacian kernel
                val laplacian = abs(
                    -grayPixels[idx - width - 1] - grayPixels[idx - width] - grayPixels[idx - width + 1] +
                    -grayPixels[idx - 1] + 8 * grayPixels[idx] - grayPixels[idx + 1] +
                    -grayPixels[idx + width - 1] - grayPixels[idx + width] - grayPixels[idx + width + 1]
                )
                laplacianSum += laplacian * laplacian
            }
        }
        
        // Variance of Laplacian
        val variance = laplacianSum / ((width - 2) * (height - 2))
        return variance.toFloat()
    }
    
    /**
     * Calculate contrast (standard deviation of pixel intensities)
     * 
     * Measures spread of pixel values across the image.
     * Flat images (same color everywhere) have low contrast.
     * High-contrast images have wide value distribution.
     * 
     * Uses two-pass algorithm:
     * 1. Calculate mean intensity
     * 2. Calculate standard deviation from mean
     * 
     * Samples every Nth pixel for performance.
     * 
     * @param bitmap Source image
     * @return Standard deviation of intensities (0-255)
     */
    private fun calculateContrast(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        
        // First pass: calculate mean
        var sum = 0L
        var count = 0
        for (y in 0 until height step SAMPLE_SIZE) {
            for (x in 0 until width step SAMPLE_SIZE) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val gray = (r + g + b) / 3
                sum += gray
                count++
            }
        }
        val mean = sum.toFloat() / count
        
        // Second pass: calculate standard deviation
        var varianceSum = 0.0
        for (y in 0 until height step SAMPLE_SIZE) {
            for (x in 0 until width step SAMPLE_SIZE) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val gray = (r + g + b) / 3
                val diff = gray - mean
                varianceSum += diff * diff
            }
        }
        val stdDev = sqrt(varianceSum / count)
        return stdDev.toFloat()
    }
}
