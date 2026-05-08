package com.jabauth.diagnostic.ui.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.JABCodeDecoder
import com.jabauth.jabcode.camera.CameraUtils
import com.jabauth.jabcode.camera.ImageQualityAnalyzer

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
    
    private val qualityAnalyzer = ImageQualityAnalyzer()
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
            // Convert ImageProxy to Bitmap using framework utility
            val bitmap = CameraUtils.imageProxyToBitmap(image)
            
            if (bitmap != null) {
                // Calculate quality metrics using framework analyzer
                val metrics = qualityAnalyzer.analyze(bitmap)
                onQualityUpdate(metrics.brightness, metrics.focus, metrics.contrast)
                
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
    
}
