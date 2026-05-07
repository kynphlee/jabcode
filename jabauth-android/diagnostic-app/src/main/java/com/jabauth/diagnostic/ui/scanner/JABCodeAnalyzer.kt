package com.jabauth.diagnostic.ui.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.JABCodeDecoder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * JABCode camera frame analyzer
 * 
 * Processes camera frames from CameraX and attempts to decode JABCodes.
 * Integrates with ScannerViewModel to update UI state and quality metrics.
 * 
 * **Flow:**
 * Camera Frame → Convert to Bitmap → JABCode Decode → Parse Result → Update ViewModel
 * 
 * Phase 3 Day 5: Real JABCode SDK Integration
 */
class JABCodeAnalyzer(
    private val decoder: JABCodeDecoder,
    private val onDecodeSuccess: (String, Long) -> Unit,
    private val onDecodeFailure: (String) -> Unit,
    private val onQualityUpdate: (brightness: Float, focus: Float, contrast: Float) -> Unit
) : ImageAnalysis.Analyzer {
    
    private var lastAnalyzedTimestamp = 0L
    private val analyzeIntervalMs = 500L // Throttle to 2 FPS to prevent buffer overflow
    
    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        
        // Throttle analysis to avoid overwhelming CPU
        if (currentTimestamp - lastAnalyzedTimestamp < analyzeIntervalMs) {
            image.close()
            return
        }
        
        lastAnalyzedTimestamp = currentTimestamp
        
        try {
            // Convert ImageProxy to Bitmap
            val bitmap = image.toBitmap()
            
            if (bitmap != null) {
                // Calculate quality metrics
                val quality = analyzeImageQuality(bitmap)
                onQualityUpdate(quality.brightness, quality.focus, quality.contrast)
                
                // Attempt decode with short timeout to prevent blocking
                val startTime = System.currentTimeMillis()
                val result = decoder.decode(bitmap, DecodeOptions(timeout = 200L))
                val decodeTime = System.currentTimeMillis() - startTime
                
                if (result != null) {
                    // Success - decode found JABCode
                    val decodedData = result.asString()
                    onDecodeSuccess(decodedData, decodeTime)
                } else {
                    // No JABCode found in frame (normal during scanning)
                    // Don't call onDecodeFailure - just continue scanning
                }
                
                bitmap.recycle()
            }
        } catch (e: Exception) {
            // Decode error - report to ViewModel
            onDecodeFailure("Decode error: ${e.message}")
        } finally {
            image.close()
        }
    }
    
    /**
     * Convert ImageProxy to Bitmap
     * 
     * Supports YUV_420_888 and RGBA_8888 formats from CameraX
     */
    private fun ImageProxy.toBitmap(): Bitmap? {
        return when (format) {
            ImageFormat.YUV_420_888 -> yuv420ToBitmap()
            else -> null
        }
    }
    
    /**
     * Convert YUV_420_888 image to Bitmap
     */
    private fun ImageProxy.yuv420ToBitmap(): Bitmap? {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)
        
        // Copy UV planes (interleaved for NV21)
        var uvIndex = ySize
        for (i in 0 until uSize) {
            nv21[uvIndex++] = vBuffer.get(i)
            nv21[uvIndex++] = uBuffer.get(i)
        }
        
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    
    /**
     * Analyze image quality metrics
     * 
     * Returns normalized (0.0-1.0) values for:
     * - Brightness: Average luminance
     * - Focus: Edge sharpness (Laplacian variance)
     * - Contrast: Standard deviation of luminance
     */
    private fun analyzeImageQuality(bitmap: Bitmap): ImageQuality {
        val width = bitmap.width.coerceAtMost(100) // Downsample for performance
        val height = bitmap.height.coerceAtMost(100)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, false)
        
        var totalBrightness = 0.0
        val luminanceValues = mutableListOf<Float>()
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = scaled.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                // Calculate luminance (perceived brightness)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toFloat()
                luminanceValues.add(luminance)
                totalBrightness += luminance
            }
        }
        
        val avgBrightness = (totalBrightness / (width * height)) / 255.0
        
        // Calculate contrast (standard deviation)
        val mean = luminanceValues.average().toFloat()
        val variance = luminanceValues.map { (it - mean) * (it - mean) }.average()
        val contrast = (Math.sqrt(variance) / 255.0).coerceIn(0.0, 1.0)
        
        // Simple focus estimate (Laplacian variance approximation)
        var edgeStrength = 0.0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = scaled.getPixel(x, y) and 0xFF
                val right = scaled.getPixel(x + 1, y) and 0xFF
                val down = scaled.getPixel(x, y + 1) and 0xFF
                val edge = Math.abs(center - right) + Math.abs(center - down)
                edgeStrength += edge
            }
        }
        val focus = (edgeStrength / (width * height) / 255.0).coerceIn(0.0, 1.0)
        
        scaled.recycle()
        
        return ImageQuality(
            brightness = avgBrightness.toFloat(),
            focus = focus.toFloat(),
            contrast = contrast.toFloat()
        )
    }
    
    private data class ImageQuality(
        val brightness: Float,
        val focus: Float,
        val contrast: Float
    )
}
