package com.jabauth.jabcode

import android.graphics.Bitmap
import android.graphics.Rect
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

    private val nativeBridge = JABCodeMobile()

    override fun decode(image: Bitmap, options: DecodeOptions): DecodeResult? {
        val startTime = System.currentTimeMillis()
        
        logger?.debug("Decoding JABCode", mapOf(
            "imageSize" to "${image.width}x${image.height}",
            "scanRegion" to options.scanRegion?.toString(),
            "timeout" to options.timeout
        ))
        
        try {
            // Use nativeDecodeFromBitmap for simplicity
            val decodedData = nativeBridge.nativeDecodeFromBitmap(image, options.timeout)
                ?: return null
            
            val decodeTime = System.currentTimeMillis() - startTime
            
            // Create result (native library doesn't return metadata yet)
            val result = DecodeResult(
                data = decodedData,
                colorMode = ColorMode.COLOR_8, // Default - actual mode not returned by native
                position = Rect(0, 0, image.width, image.height), // Full image
                decodeTimeMs = decodeTime
            )
            
            logger?.info("JABCode decoded successfully", mapOf(
                "dataSize" to result.data.size,
                "colorMode" to result.colorMode.name,
                "decodeTimeMs" to decodeTime
            ))
            
            return result
        } catch (e: Exception) {
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
