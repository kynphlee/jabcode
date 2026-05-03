package com.jabauth.jabcode

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Test implementation of JABCodeDecoder for unit testing
 *
 * Decodes mock JABCode images created by TestJABCodeEncoderImpl.
 * Retrieves data from TestDataStore instead of analyzing pixels.
 */
class TestJABCodeDecoderImpl : JABCodeDecoder {

    override fun decode(image: Bitmap, options: DecodeOptions): DecodeResult? {
        val startTime = System.currentTimeMillis()
        
        // Retrieve data from test store
        val encodedData = TestDataStore.retrieve(image) ?: return null
        
        // Apply scan region if specified
        val position = options.scanRegion ?: Rect(0, 0, image.width, image.height)
        
        val decodeTime = System.currentTimeMillis() - startTime
        
        return DecodeResult(
            data = encodedData.data,
            colorMode = encodedData.colorMode,
            position = position,
            decodeTimeMs = decodeTime
        )
    }

    override fun decodeMultiple(image: Bitmap, options: DecodeOptions): List<DecodeResult> {
        val result = decode(image, options)
        return if (result != null) listOf(result) else emptyList()
    }

    override fun containsJABCode(image: Bitmap): Boolean {
        return TestDataStore.retrieve(image) != null
    }
}
