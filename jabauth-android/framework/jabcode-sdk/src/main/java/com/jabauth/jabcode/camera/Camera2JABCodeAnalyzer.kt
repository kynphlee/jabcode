package com.jabauth.jabcode.camera

import android.media.Image
import android.media.ImageReader
import android.util.Log
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
    
    companion object {
        private const val TAG = "Camera2JABCodeAnalyzer"
    }
    
    private val qualityAnalyzer = if (options.includeQualityMetrics && onQualityUpdate != null) {
        ImageQualityAnalyzer()
    } else {
        null
    }
    
    private var lastAnalyzedTimestamp = 0L
    private var frameCount = 0
    private var decodeAttempts = 0
    
    /**
     * Analyze a frame from ImageReader
     * CRITICAL: Caller MUST close the Image after this returns
     */
    fun analyze(imageReader: ImageReader) {
        var image: Image? = null
        try {
            frameCount++
            
            // Frame throttling - prevent CPU overload
            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastAnalyzedTimestamp < options.analyzeIntervalMs) {
                Log.v(TAG, "Frame $frameCount throttled (interval: ${currentTimestamp - lastAnalyzedTimestamp}ms < ${options.analyzeIntervalMs}ms)")
                return
            }
            lastAnalyzedTimestamp = currentTimestamp
            
            Log.d(TAG, "=== Analyzing frame $frameCount ===")
            
            // Acquire latest image
            image = imageReader.acquireLatestImage()
            if (image == null) {
                Log.w(TAG, "Frame $frameCount: No image available from ImageReader")
                return
            }
            
            Log.d(TAG, "Frame $frameCount: Acquired image ${image.width}x${image.height}, format=${image.format}")
            
            // Convert Image to Bitmap
            val bitmapStart = System.currentTimeMillis()
            val bitmap = CameraUtils.imageToBitmap(image)
            val bitmapTime = System.currentTimeMillis() - bitmapStart
            
            if (bitmap == null) {
                Log.e(TAG, "Frame $frameCount: Bitmap conversion failed")
                return
            }
            
            Log.d(TAG, "Frame $frameCount: Bitmap created ${bitmap.width}x${bitmap.height} (${bitmapTime}ms)")
            
            try {
                // Calculate quality metrics (if enabled)
                if (qualityAnalyzer != null && onQualityUpdate != null) {
                    val qualityStart = System.currentTimeMillis()
                    val metrics = qualityAnalyzer.analyze(bitmap)
                    val qualityTime = System.currentTimeMillis() - qualityStart
                    Log.v(TAG, "Frame $frameCount: Quality metrics - brightness=${metrics.brightness}, " +
                               "contrast=${metrics.contrast}, focus=${metrics.focus} (${qualityTime}ms)")
                    onQualityUpdate.invoke(metrics)
                }
                
                // Attempt JABCode decode
                decodeAttempts++
                Log.d(TAG, "Frame $frameCount: Starting decode attempt #$decodeAttempts (timeout=${options.timeout}ms)")
                
                val decodeStart = System.currentTimeMillis()
                val result = decoder.decode(bitmap, options)
                val decodeTime = System.currentTimeMillis() - decodeStart
                
                if (result != null) {
                    // Success - found JABCode
                    Log.i(TAG, "Frame $frameCount: ✅ JABCode DETECTED! Data size=${result.data.size} bytes, " +
                               "colorMode=${result.colorMode}, decodeTime=${decodeTime}ms")
                    onDecodeSuccess(result)
                } else {
                    Log.v(TAG, "Frame $frameCount: No JABCode found (decode took ${decodeTime}ms)")
                }
                // No else - null result is normal during scanning
                
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            // Decode error - report to callback
            Log.e(TAG, "Frame $frameCount: ❌ Decode exception: ${e.javaClass.simpleName}: ${e.message}", e)
            onDecodeFailure("Decode error: ${e.message}")
        } finally {
            // CRITICAL: Close image to prevent buffer exhaustion
            image?.close()
            Log.v(TAG, "Frame $frameCount: Image closed")
        }
    }
}
