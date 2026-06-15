package com.jabauth.jabcode

import android.graphics.Bitmap
import com.jabauth.core.logging.Logger
import com.jabcode.JABCodeMobile

/**
 * Production implementation of JABCodeEncoder using the native JABCode library.
 *
 * Delegates to the shared [JABCodeMobile] JNI bridge — the same single
 * `libjabcode-mobile.so` surface the decoder uses (see [JABCodeDecoderImpl]).
 * The bridge's companion `init` loads the native library, so constructing it
 * here is what links the encoder to the .so; this class no longer loads the
 * library itself. Thread-safe and suitable for production use.
 */
class JABCodeEncoderImpl(
    private val logger: Logger? = null
) : JABCodeEncoder {

    companion object {
        // Capacity estimates based on JABCode spec
        // Actual capacity depends on error correction level and module size
        private const val BYTES_PER_MODULE_COLOR_2 = 0.125   // 1 bit
        private const val BYTES_PER_MODULE_COLOR_4 = 0.25    // 2 bits
        private const val BYTES_PER_MODULE_COLOR_8 = 0.375   // 3 bits
        private const val BYTES_PER_MODULE_COLOR_16 = 0.5    // 4 bits
        private const val BYTES_PER_MODULE_COLOR_32 = 0.625  // 5 bits
        private const val BYTES_PER_MODULE_COLOR_64 = 0.75   // 6 bits
        private const val BYTES_PER_MODULE_COLOR_128 = 0.875 // 7 bits
        private const val BYTES_PER_MODULE_COLOR_256 = 1.0   // 8 bits

        private const val TYPICAL_MODULES = 4096 // 64x64 symbol

        // Native encode defaults. JABCode symbol dimensions are determined by
        // payload + ECC, not by a requested pixel size, so we encode at a fixed
        // module size and scale to the caller's width/height afterward.
        private const val MOBILE_SYMBOL_NUMBER = 1
        private const val DEFAULT_MODULE_SIZE = 12
    }

    /** Shared native bridge; constructing it triggers the libjabcode-mobile load. */
    private val nativeBridge = JABCodeMobile()

    override fun encode(data: ByteArray, options: EncodeOptions): Bitmap {
        val startTime = System.currentTimeMillis()

        logger?.debug("Encoding JABCode", mapOf(
            "dataSize" to data.size,
            "width" to options.width,
            "height" to options.height,
            "colorMode" to options.colorMode.name
        ))

        try {
            val encoded = nativeBridge.nativeEncodeToBitmap(
                data = data,
                colorNumber = options.colorMode.value,
                eccLevel = options.errorCorrectionLevel,
                symbolNumber = MOBILE_SYMBOL_NUMBER,
                moduleSize = DEFAULT_MODULE_SIZE
            ) ?: throw EncodeException(
                "Native encode returned no bitmap: " +
                    (nativeBridge.nativeGetLastError() ?: "unknown error")
            )

            // Honor the caller's requested size. JABCode dimensions are fixed by
            // the module grid, so scale with nearest-neighbor (filter = false) to
            // keep module edges crisp and machine-readable. Recycle the native-
            // size bitmap once its pixels have been copied into the scaled one.
            val bitmap = if (options.width > 0 && options.height > 0 &&
                (encoded.width != options.width || encoded.height != options.height)) {
                Bitmap.createScaledBitmap(encoded, options.width, options.height, false)
                    .also { scaled -> if (scaled !== encoded) encoded.recycle() }
            } else {
                encoded
            }

            val encodeTime = System.currentTimeMillis() - startTime

            logger?.info("JABCode encoded successfully", mapOf(
                "dataSize" to data.size,
                "bitmapSize" to "${bitmap.width}x${bitmap.height}",
                "encodeTimeMs" to encodeTime
            ))

            return bitmap
        } catch (e: EncodeException) {
            // Already the right exception type (e.g. the null-bitmap case above);
            // rethrow as-is so the native error message is not double-wrapped.
            logger?.error("JABCode encoding failed", e, mapOf(
                "dataSize" to data.size,
                "colorMode" to options.colorMode.name
            ))
            throw e
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
            ColorMode.COLOR_16 -> BYTES_PER_MODULE_COLOR_16
            ColorMode.COLOR_32 -> BYTES_PER_MODULE_COLOR_32
            ColorMode.COLOR_64 -> BYTES_PER_MODULE_COLOR_64
            ColorMode.COLOR_128 -> BYTES_PER_MODULE_COLOR_128
            ColorMode.COLOR_256 -> BYTES_PER_MODULE_COLOR_256
        }

        // Account for error correction overhead
        val eccOverhead = 1.0 + (options.errorCorrectionLevel / 100.0)

        return (TYPICAL_MODULES * bytesPerModule / eccOverhead).toInt()
    }
}
