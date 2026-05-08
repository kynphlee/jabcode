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
 * @property analyzeIntervalMs Minimum interval between camera frame analyses in ms (camera mode only)
 * @property includeQualityMetrics Whether to calculate quality metrics during camera scanning
 */
data class DecodeOptions(
    val scanRegion: Rect? = null,
    val maxSymbols: Int = 1,
    val timeout: Long = 5000L,
    val analyzeIntervalMs: Long = 500L,
    val includeQualityMetrics: Boolean = true
) {
    init {
        require(maxSymbols > 0) { "Max symbols must be positive" }
        require(timeout >= 0) { "Timeout must be non-negative" }
        require(analyzeIntervalMs >= 0) { "Analyze interval must be non-negative" }
    }
}
