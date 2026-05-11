package com.jabauth.jabcode

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.jabauth.core.logging.Logger
import com.jabcode.JABCodeMobile
import java.nio.ByteBuffer

/**
 * Production implementation of JABCodeDecoder using native JABCode library
 *
 * Wraps JNI calls to libjabcode-mobile.so
 * Thread-safe and suitable for production use.
 */
class JABCodeDecoderImpl(
    private val logger: Logger? = null
) : JABCodeDecoder {

    companion object {
        private const val TAG = "JABCodeDecoder"
    }

    private val nativeBridge = JABCodeMobile()
    private var decodeCount = 0

    override fun decode(image: Bitmap, options: DecodeOptions): DecodeResult? {
        decodeCount++
        val startTime = System.currentTimeMillis()
        
        Log.d(TAG, "--- Decode #$decodeCount START ---")
        Log.d(TAG, "Bitmap: ${image.width}x${image.height}, config=${image.config}, hasAlpha=${image.hasAlpha()}")
        Log.d(TAG, "Options: timeout=${options.timeout}ms, scanRegion=${options.scanRegion}")
        
        logger?.debug("Decoding JABCode", mapOf(
            "imageSize" to "${image.width}x${image.height}",
            "scanRegion" to options.scanRegion?.toString(),
            "timeout" to options.timeout
        ))
        
        try {
            // Use nativeDecodeFromBitmap for simplicity
            Log.d(TAG, "Calling nativeDecodeFromBitmap()...")
            val decodedData = nativeBridge.nativeDecodeFromBitmap(image, options.timeout)
            
            if (decodedData == null) {
                val errorMsg = nativeBridge.nativeGetLastError() ?: "Unknown error"
                Log.e(TAG, "❌ Native decode FAILED: $errorMsg")
                logger?.error("Native decode failed: $errorMsg", null, mapOf(
                    "imageSize" to "${image.width}x${image.height}"
                ))
                return null
            }
            
            Log.d(TAG, "✅ Native decode SUCCESS: ${decodedData.size} bytes received")
            
            val decodeTime = System.currentTimeMillis() - startTime
            
            // Try to decode as string for logging
            val dataPreview = try {
                String(decodedData, Charsets.UTF_8).take(50)
            } catch (e: Exception) {
                "<binary data>"
            }
            
            Log.i(TAG, "Decoded data preview: \"$dataPreview\"${if (decodedData.size > 50) "..." else ""}")
            
            // Create result (native library doesn't return metadata yet)
            val result = DecodeResult(
                data = decodedData,
                colorMode = ColorMode.COLOR_8, // Default - actual mode not returned by native
                position = Rect(0, 0, image.width, image.height), // Full image
                decodeTimeMs = decodeTime
            )
            
            Log.i(TAG, "--- Decode #$decodeCount SUCCESS (${decodeTime}ms) ---")
            
            logger?.info("JABCode decoded successfully", mapOf(
                "dataSize" to result.data.size,
                "colorMode" to result.colorMode.name,
                "decodeTimeMs" to decodeTime
            ))
            
            return result
        } catch (e: Exception) {
            val decodeTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "--- Decode #$decodeCount EXCEPTION (${decodeTime}ms) ---", e)
            Log.e(TAG, "Exception: ${e.javaClass.simpleName}: ${e.message}")
            
            logger?.error("JABCode decoding failed", e, mapOf(
                "imageSize" to "${image.width}x${image.height}"
            ))
            return null
        }
    }

    override fun decodeMultiple(image: Bitmap, options: DecodeOptions): List<DecodeResult> {
        // Current implementation: single decode only
        // Native library would need enhancement for multi-symbol support
        val result = decode(image, options)
        return if (result != null) listOf(result) else emptyList()
    }

    override fun containsJABCode(image: Bitmap): Boolean {
        try {
            // Quick pre-scan by attempting decode with short timeout
            val quickOptions = DecodeOptions(timeout = 500L)
            return decode(image, quickOptions) != null
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Convert bitmap to RGBA buffer for native processing
     */
    private fun bitmapToRgbaBuffer(bitmap: Bitmap, region: Rect?): ByteArray {
        val width = region?.width() ?: bitmap.width
        val height = region?.height() ?: bitmap.height
        val x = region?.left ?: 0
        val y = region?.top ?: 0
        
        val buffer = ByteArray(width * height * 4)
        val byteBuffer = ByteBuffer.wrap(buffer)
        
        // Extract pixels from region
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, x, y, width, height)
        
        // Convert ARGB to RGBA
        for (pixel in pixels) {
            byteBuffer.put(((pixel shr 16) and 0xFF).toByte()) // R
            byteBuffer.put(((pixel shr 8) and 0xFF).toByte())  // G
            byteBuffer.put((pixel and 0xFF).toByte())          // B
            byteBuffer.put(((pixel shr 24) and 0xFF).toByte()) // A
        }
        
        return buffer
    }

}
