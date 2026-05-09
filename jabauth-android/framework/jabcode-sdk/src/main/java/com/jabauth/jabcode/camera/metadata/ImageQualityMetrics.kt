package com.jabauth.jabcode.camera.metadata

/**
 * Image quality analysis metrics
 * 
 * @property brightness Average pixel brightness (0-255 scale)
 * @property contrast Contrast ratio (0-1, higher is more contrast)
 * @property sharpness Sharpness/focus score (0-1, higher is sharper)
 * @property isWellExposed Whether image exposure is acceptable
 * @property isInFocus Whether image appears in focus
 * @property qualityScore Overall quality score (0-1, higher is better)
 */
data class ImageQualityMetrics(
    val brightness: Float,
    val contrast: Float,
    val sharpness: Float,
    val isWellExposed: Boolean,
    val isInFocus: Boolean,
    val qualityScore: Float
) {
    init {
        require(brightness in 0f..255f) { "Brightness must be in range [0, 255]" }
        require(contrast in 0f..1f) { "Contrast must be in range [0, 1]" }
        require(sharpness in 0f..1f) { "Sharpness must be in range [0, 1]" }
        require(qualityScore in 0f..1f) { "Quality score must be in range [0, 1]" }
    }
}
