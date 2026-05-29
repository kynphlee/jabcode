package com.jabauth.jabcode.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.DecodeResult
import com.jabauth.jabcode.JABCodeDecoder

/**
 * Standard CameraX analyzer for JABCode scanning
 * 
 * Provides production-ready camera integration with:
 * - Frame throttling (prevents CPU overload)
 * - Automatic YUV→Bitmap conversion
 * - Optional quality metrics calculation
 * - JABCode decoding with timeout
 * - Result and error callbacks
 * 
 * **Migrated from diagnostic app** (2026-05-07)
 * - Consolidates best practices from JABCodeAnalyzer.kt
 * - Replaces ad-hoc camera integration patterns
 * 
 * **Usage:**
 * ```kotlin
 * val analyzer = JABCodeCameraAnalyzer(
 *     decoder = jabCodeDecoder,
 *     options = DecodeOptions(timeout = 200L, analyzeIntervalMs = 500L),
 *     onDecodeSuccess = { result -> handleSuccess(result) },
 *     onDecodeFailure = { error -> handleError(error) },
 *     onQualityUpdate = { metrics -> updateUI(metrics) }
 * )
 * 
 * imageAnalysis.setAnalyzer(cameraExecutor, analyzer)
 * ```
 * 
 * @param decoder JABCode decoder instance
 * @param options Decode options (includes throttling and timeout)
 * @param onDecodeSuccess Callback when JABCode successfully decoded
 * @param onDecodeFailure Callback when decode error occurs
 * @param onQualityUpdate Optional callback for quality metrics (null = skip metrics)
 */
class JABCodeCameraAnalyzer(
    private val decoder: JABCodeDecoder,
    private val options: DecodeOptions = DecodeOptions(),
    private val onDecodeSuccess: (DecodeResult) -> Unit,
    private val onDecodeFailure: (String, Long) -> Unit,
    private val onQualityUpdate: ((ImageQualityAnalyzer.QualityMetrics) -> Unit)? = null
) : ImageAnalysis.Analyzer {
    
    private val qualityAnalyzer = if (options.includeQualityMetrics && onQualityUpdate != null) {
        ImageQualityAnalyzer()
    } else {
        null
    }
    
    private var lastAnalyzedTimestamp = 0L
    
    override fun analyze(image: ImageProxy) {
        try {
            // Frame throttling - prevent CPU overload
            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastAnalyzedTimestamp < options.analyzeIntervalMs) {
                image.close()
                return
            }
            lastAnalyzedTimestamp = currentTimestamp
            
            // Convert ImageProxy to Bitmap
            val bitmap = CameraUtils.imageProxyToBitmap(image)
            
            if (bitmap == null) {
                image.close()
                return
            }
            
            try {
                // Calculate quality metrics (if enabled)
                if (qualityAnalyzer != null && onQualityUpdate != null) {
                    val metrics = qualityAnalyzer.analyze(bitmap)
                    onQualityUpdate.invoke(metrics)
                }
                
                // Attempt JABCode decode
                val decodeStart = System.currentTimeMillis()
                val result = decoder.decode(bitmap, options)
                val decodeTime = System.currentTimeMillis() - decodeStart

                if (result != null) {
                    // Success - found JABCode
                    onDecodeSuccess(result)
                } else {
                    // Null result is a real failure outcome — surface it via
                    // the failure callback so upstream telemetry can categorize.
                    // Mirrors the Camera2 analyzer's fix (a873969).
                    val errorMsg = decoder.getLastError() ?: "No JABCode found"
                    onDecodeFailure(errorMsg, decodeTime)
                }
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            // Decode error - report to callback. Decode time unavailable;
            // pass 0L so the stats path excludes it from timing aggregates.
            onDecodeFailure("Decode error: ${e.message}", 0L)
        } finally {
            image.close()
        }
    }
}
