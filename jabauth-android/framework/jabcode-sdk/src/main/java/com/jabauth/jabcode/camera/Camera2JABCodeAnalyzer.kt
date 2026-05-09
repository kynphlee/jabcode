package com.jabauth.jabcode.camera

import android.media.Image
import android.media.ImageReader
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.DecodeResult
import com.jabauth.jabcode.JABCodeDecoder

/**
 * Camera2 analyzer for JABCode scanning
 * 
 * Provides low-level Camera2 integration with:
 * - ImageReader frame processing
 * - Frame throttling (prevents CPU overload)
 * - Automatic YUV→Bitmap conversion
 * - Optional quality metrics calculation
 * - JABCode decoding with timeout
 * - Result and error callbacks
 * 
 * **Usage:**
 * ```kotlin
 * val analyzer = Camera2JABCodeAnalyzer(
 *     decoder = jabCodeDecoder,
 *     options = DecodeOptions(timeout = 200L, analyzeIntervalMs = 500L),
 *     onDecodeSuccess = { result -> handleSuccess(result) },
 *     onDecodeFailure = { error -> handleError(error) },
 *     onQualityUpdate = { metrics -> updateUI(metrics) }
 * )
 * 
 * // Pass to Camera2Preview or ImageReader
 * imageReader.setOnImageAvailableListener({ reader ->
 *     analyzer.analyze(reader)
 * }, handler)
 * ```
 * 
 * @param decoder JABCode decoder instance
 * @param options Decode options (includes throttling and timeout)
 * @param onDecodeSuccess Callback when JABCode successfully decoded
 * @param onDecodeFailure Callback when decode error occurs
 * @param onQualityUpdate Optional callback for quality metrics (null = skip metrics)
 */
class Camera2JABCodeAnalyzer(
    private val decoder: JABCodeDecoder,
    private val options: DecodeOptions = DecodeOptions(),
    private val onDecodeSuccess: (DecodeResult) -> Unit,
    private val onDecodeFailure: (String) -> Unit,
    private val onQualityUpdate: ((ImageQualityAnalyzer.QualityMetrics) -> Unit)? = null
) {
    
    private val qualityAnalyzer = if (options.includeQualityMetrics && onQualityUpdate != null) {
        ImageQualityAnalyzer()
    } else {
        null
    }
    
    private var lastAnalyzedTimestamp = 0L
    
    /**
     * Analyze a frame from ImageReader
     * CRITICAL: Caller MUST close the Image after this returns
     */
    fun analyze(imageReader: ImageReader) {
        var image: Image? = null
        try {
            // Frame throttling - prevent CPU overload
            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastAnalyzedTimestamp < options.analyzeIntervalMs) {
                return
            }
            lastAnalyzedTimestamp = currentTimestamp
            
            // Acquire latest image
            image = imageReader.acquireLatestImage() ?: return
            
            // Convert Image to Bitmap
            val bitmap = CameraUtils.imageToBitmap(image)
            
            if (bitmap == null) {
                return
            }
            
            try {
                // Calculate quality metrics (if enabled)
                if (qualityAnalyzer != null && onQualityUpdate != null) {
                    val metrics = qualityAnalyzer.analyze(bitmap)
                    onQualityUpdate.invoke(metrics)
                }
                
                // Attempt JABCode decode
                val result = decoder.decode(bitmap, options)
                
                if (result != null) {
                    // Success - found JABCode
                    onDecodeSuccess(result)
                }
                // No else - null result is normal during scanning
                
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            // Decode error - report to callback
            onDecodeFailure("Decode error: ${e.message}")
        } finally {
            // CRITICAL: Close image to prevent buffer exhaustion
            image?.close()
        }
    }
}
