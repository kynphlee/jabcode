package com.jabauth.jabcode

import android.graphics.Bitmap

/**
 * JABCode decoder interface
 *
 * Decodes JABCode barcode images into raw data.
 *
 * **Flow:** JABCode IMAGE (Bitmap) → DATA (ByteArray)
 *
 * Usage:
 * ```
 * val decoder = JABCodeDecoderImpl()
 * val cameraFrame: Bitmap = // From camera preview
 * val options = DecodeOptions(
 *     scanRegion = Rect(0, 0, 1920, 1080)
 * )
 * val result = decoder.decode(cameraFrame, options)
 * if (result != null) {
 *     val decodedText = result.asString()
 *     println("Decoded: $decodedText")
 * }
 * ```
 *
 * **Use Cases:**
 * - Scan authentication tokens from camera
 * - Extract certificates from printed JABCode
 * - Decode offline credentials
 */
interface JABCodeDecoder {

    /**
     * Decode a JABCode from an image
     *
     * Scans the image for JABCode barcode and extracts data.
     *
     * @param image Bitmap containing a JABCode (from camera, file, etc.)
     * @param options Decoding configuration
     * @return DecodeResult containing extracted data, or null if no JABCode found
     */
    fun decode(image: Bitmap, options: DecodeOptions = DecodeOptions()): DecodeResult?

    /**
     * Decode multiple JABCodes from an image
     *
     * Useful when image may contain multiple barcodes.
     *
     * @param image Bitmap to scan
     * @param options Decoding configuration
     * @return List of DecodeResults (empty if no JABCode found)
     */
    fun decodeMultiple(image: Bitmap, options: DecodeOptions = DecodeOptions()): List<DecodeResult>

    /**
     * Quick check if image likely contains a JABCode
     *
     * Fast pre-scan to avoid expensive decode operations.
     * May have false positives/negatives.
     *
     * @param image Bitmap to check
     * @return true if JABCode likely present, false otherwise
     */
    fun containsJABCode(image: Bitmap): Boolean

    /**
     * Get the last decode error message, if any.
     *
     * Returns the error string from the most recent failed decode attempt
     * (e.g. "No JABCode found in image" for status=0, "JABCode found but
     * not decodable" for status=1). Cleared on successful decode.
     *
     * Used by callers that need to attribute failures by category — the
     * analyzer layer uses this to surface failure mode through its
     * onDecodeFailure callback. Default impl returns null so test stubs
     * don't need to wire this; production impls should override.
     */
    fun getLastError(): String? = null
}

/**
 * Exception thrown when decoding fails
 */
class DecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)
