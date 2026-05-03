package com.jabauth.jabcode

import android.graphics.Bitmap

/**
 * JABCode encoder interface
 *
 * Encodes raw data into JABCode barcode images.
 *
 * **Flow:** DATA (ByteArray) → JABCode IMAGE (Bitmap)
 *
 * Usage:
 * ```
 * val encoder = JABCodeEncoderImpl()
 * val data = "Hello, JABAuth!".toByteArray()
 * val options = EncodeOptions(
 *     width = 512,
 *     height = 512,
 *     colorMode = ColorMode.COLOR_8
 * )
 * val jabcodeImage = encoder.encode(data, options)
 * // Result: Bitmap containing the JABCode barcode
 * ```
 *
 * **Use Cases:**
 * - Generate authentication tokens as JABCode
 * - Encode certificates for offline transfer
 * - Create QR-like codes with higher data capacity
 */
interface JABCodeEncoder {

    /**
     * Encode data into a JABCode image
     *
     * @param data Raw bytes to encode (text, binary, certificate, etc.)
     * @param options Encoding configuration
     * @return Bitmap containing the JABCode barcode
     * @throws EncodeException if encoding fails (data too large, invalid options, etc.)
     */
    fun encode(data: ByteArray, options: EncodeOptions = EncodeOptions()): Bitmap

    /**
     * Encode string data into a JABCode image
     *
     * Convenience method that converts string to UTF-8 bytes.
     *
     * @param data String to encode
     * @param options Encoding configuration
     * @return Bitmap containing the JABCode barcode
     */
    fun encodeString(data: String, options: EncodeOptions = EncodeOptions()): Bitmap {
        return encode(data.toByteArray(Charsets.UTF_8), options)
    }

    /**
     * Calculate maximum data capacity for given options
     *
     * Useful for checking if data will fit before encoding.
     *
     * @param options Encoding configuration
     * @return Maximum number of bytes that can be encoded
     */
    fun getMaxDataCapacity(options: EncodeOptions): Int

    /**
     * Check if data can be encoded with given options
     *
     * @param dataSize Size of data in bytes
     * @param options Encoding configuration
     * @return true if data will fit, false otherwise
     */
    fun canEncode(dataSize: Int, options: EncodeOptions): Boolean {
        return dataSize <= getMaxDataCapacity(options)
    }
}

/**
 * Exception thrown when encoding fails
 */
class EncodeException(message: String, cause: Throwable? = null) : Exception(message, cause)
