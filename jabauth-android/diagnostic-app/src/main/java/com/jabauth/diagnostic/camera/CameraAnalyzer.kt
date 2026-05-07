package com.jabauth.diagnostic.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Camera Image Analyzer
 * 
 * Analyzes camera frames to calculate quality metrics:
 * - Brightness: Average luminosity (0.0 = dark, 1.0 = bright)
 * - Focus: Edge sharpness via Laplacian variance (0.0 = blurry, 1.0 = sharp)
 * - Contrast: Standard deviation of pixel intensities (0.0 = flat, 1.0 = high contrast)
 * 
 * Updates callback on each frame with normalized [0.0-1.0] values.
 * 
 * @param onQualityUpdate Callback invoked with (brightness, focus, contrast)
 */
class CameraAnalyzer(
    private val onQualityUpdate: (brightness: Float, focus: Float, contrast: Float) -> Unit
) : ImageAnalysis.Analyzer {
    
    // Thresholds for normalization
    private companion object {
        const val BRIGHTNESS_MAX = 255f
        const val FOCUS_THRESHOLD = 100f  // Laplacian variance threshold for "sharp"
        const val CONTRAST_MAX = 128f     // Half of possible range for normalization
    }
    
    override fun analyze(image: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(image)
            if (bitmap == null) {
                image.close()
                return
            }
            
            // Calculate metrics
            val brightness = calculateBrightness(bitmap)
            val focus = calculateFocus(bitmap)
            val contrast = calculateContrast(bitmap)
            
            // Normalize to 0.0-1.0 range
            val normalizedBrightness = (brightness / BRIGHTNESS_MAX).coerceIn(0f, 1f)
            val normalizedFocus = (focus / FOCUS_THRESHOLD).coerceIn(0f, 1f)
            val normalizedContrast = (contrast / CONTRAST_MAX).coerceIn(0f, 1f)
            
            // Update callback
            onQualityUpdate(normalizedBrightness, normalizedFocus, normalizedContrast)
            
        } finally {
            image.close()
        }
    }
    
    /**
     * Calculate average brightness (luminosity)
     * 
     * Samples pixels and computes mean intensity.
     * Higher values = brighter image.
     */
    private fun calculateBrightness(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val sampleSize = 10  // Sample every Nth pixel for performance
        
        var totalBrightness = 0L
        var pixelCount = 0
        
        for (y in 0 until height step sampleSize) {
            for (x in 0 until width step sampleSize) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                // Luminosity formula (weighted RGB)
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
     * Measures edge sharpness. Blurry images have low variance.
     * Higher values = sharper focus.
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
        
        // Apply Laplacian kernel (simplified 3x3)
        var laplacianSum = 0.0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
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
     * Measures spread of pixel values. Flat images have low contrast.
     * Higher values = more contrast.
     */
    private fun calculateContrast(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val sampleSize = 10
        
        // First pass: calculate mean
        var sum = 0L
        var count = 0
        for (y in 0 until height step sampleSize) {
            for (x in 0 until width step sampleSize) {
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
        for (y in 0 until height step sampleSize) {
            for (x in 0 until width step sampleSize) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val gray = (r + g + b) / 3
                val diff = gray - mean
                varianceSum += diff * diff
            }
        }
        val stdDev = kotlin.math.sqrt(varianceSum / count)
        return stdDev.toFloat()
    }
}

/**
 * Helper: Convert ImageProxy to Bitmap
 */
private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer
    
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    
    val nv21 = ByteArray(ySize + uSize + vSize)
    
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
    val imageBytes = out.toByteArray()
    
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}
