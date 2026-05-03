package com.jabauth.jabcode

import android.graphics.Rect

/**
 * Options for JABCode decoding
 *
 * Configures how to scan for JABCode in an image.
 *
 * @property scanRegion Optional region of image to scan (null = scan entire image)
 * @property maxSymbols Maximum number of JABCode symbols to find (default: 1)
 * @property timeout Maximum time to spend decoding in milliseconds (0 = no limit)
 */
data class DecodeOptions(
    val scanRegion: Rect? = null,
    val maxSymbols: Int = 1,
    val timeout: Long = 5000L
) {
    init {
        require(maxSymbols > 0) { "Max symbols must be positive" }
        require(timeout >= 0) { "Timeout must be non-negative" }
    }
}
