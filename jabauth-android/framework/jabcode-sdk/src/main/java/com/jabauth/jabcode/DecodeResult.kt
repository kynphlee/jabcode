package com.jabauth.jabcode

import android.graphics.Rect

/**
 * Result of JABCode decoding operation
 *
 * Contains the decoded data and metadata about the barcode.
 *
 * @property data Raw decoded bytes
 * @property colorMode Color mode detected in the barcode
 * @property position Bounding rectangle of the barcode in the source image
 * @property decodeTimeMs Time taken to decode in milliseconds
 */
data class DecodeResult(
    val data: ByteArray,
    val colorMode: ColorMode,
    val position: Rect,
    val decodeTimeMs: Long
) {
    /**
     * Decode data as UTF-8 string
     */
    fun asString(): String = String(data, Charsets.UTF_8)
    
    /**
     * Check if decode result contains expected data
     */
    fun contains(substring: String): Boolean = asString().contains(substring)
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecodeResult

        if (!data.contentEquals(other.data)) return false
        if (colorMode != other.colorMode) return false
        if (position != other.position) return false
        if (decodeTimeMs != other.decodeTimeMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + colorMode.hashCode()
        result = 31 * result + position.hashCode()
        result = 31 * result + decodeTimeMs.hashCode()
        return result
    }
}
