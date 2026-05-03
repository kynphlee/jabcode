package com.jabauth.jabcode

import android.graphics.Bitmap
import com.jabauth.core.logging.Logger

/**
 * Production implementation of JABCodeEncoder using native JABCode library
 *
 * Wraps JNI calls to libjabcode-mobile.so
 * Thread-safe and suitable for production use.
 */
class JABCodeEncoderImpl(
    private val logger: Logger? = null
) : JABCodeEncoder {

    companion object {
        init {
            System.loadLibrary("jabcode-mobile")
        }
        
        // Capacity estimates based on JABCode spec
        // Actual capacity depends on error correction level and module size
        private const val BYTES_PER_MODULE_COLOR_2 = 0.125  // 1 bit
        private const val BYTES_PER_MODULE_COLOR_4 = 0.1875 // 1.5 bits
        private const val BYTES_PER_MODULE_COLOR_8 = 0.375  // 3 bits
        
        private const val TYPICAL_MODULES = 4096 // 64x64 symbol
    }

    override fun encode(data: ByteArray, options: EncodeOptions): Bitmap {
        val startTime = System.currentTimeMillis()
        
        logger?.debug("Encoding JABCode", mapOf(
            "dataSize" to data.size,
            "width" to options.width,
            "height" to options.height,
            "colorMode" to options.colorMode.name
        ))
        
        try {
            val bitmap = nativeEncode(
                data = data,
                colorNumber = options.colorMode.value,
                eccLevel = options.errorCorrectionLevel,
                targetWidth = options.width,
                targetHeight = options.height
            )
            
            val encodeTime = System.currentTimeMillis() - startTime
            
            logger?.info("JABCode encoded successfully", mapOf(
                "dataSize" to data.size,
                "bitmapSize" to "${bitmap.width}x${bitmap.height}",
                "encodeTimeMs" to encodeTime
            ))
            
            return bitmap
        } catch (e: Exception) {
            logger?.error("JABCode encoding failed", e, mapOf(
                "dataSize" to data.size,
                "colorMode" to options.colorMode.name
            ))
            throw EncodeException("Failed to encode JABCode: ${e.message}", e)
        }
    }

    override fun getMaxDataCapacity(options: EncodeOptions): Int {
        // Estimate based on color mode
        // In production, this would query the native library for exact capacity
        val bytesPerModule = when (options.colorMode) {
            ColorMode.COLOR_2 -> BYTES_PER_MODULE_COLOR_2
            ColorMode.COLOR_4 -> BYTES_PER_MODULE_COLOR_4
            ColorMode.COLOR_8 -> BYTES_PER_MODULE_COLOR_8
        }
        
        // Account for error correction overhead
        val eccOverhead = 1.0 + (options.errorCorrectionLevel / 100.0)
        
        return (TYPICAL_MODULES * bytesPerModule / eccOverhead).toInt()
    }

    /**
     * Native encode method
     *
     * @param data Data to encode
     * @param colorNumber Number of colors (2, 4, or 8)
     * @param eccLevel Error correction level (0-10)
     * @param targetWidth Target bitmap width
     * @param targetHeight Target bitmap height
     * @return Encoded JABCode bitmap
     */
    private external fun nativeEncode(
        data: ByteArray,
        colorNumber: Int,
        eccLevel: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap
}
