package com.jabauth.jabcode

/**
 * Camera calibration profile for JABCode scanning
 *
 * Stores optimal settings for reliable JABCode decoding on a specific device.
 * Profiles can be learned over time or manually configured.
 *
 * @property deviceModel Device identifier (e.g., "Pixel 7", "Galaxy S23")
 * @property brightness Optimal brightness level (0.0-1.0)
 * @property contrast Optimal contrast adjustment (-1.0 to 1.0)
 * @property focusDistance Optimal focus distance in meters (null = auto-focus)
 * @property exposureCompensation Exposure compensation (-2.0 to 2.0)
 * @property preferredColorMode Most reliable color mode for this device
 * @property scanSuccessRate Historical success rate (0.0-1.0)
 * @property lastUpdated Timestamp of last profile update (millis since epoch)
 */
data class CalibrationProfile(
    val deviceModel: String,
    val brightness: Double = 0.5,
    val contrast: Double = 0.0,
    val focusDistance: Double? = null,
    val exposureCompensation: Double = 0.0,
    val preferredColorMode: ColorMode = ColorMode.COLOR_8,
    val scanSuccessRate: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    init {
        require(brightness in 0.0..1.0) { "Brightness must be 0.0-1.0" }
        require(contrast in -1.0..1.0) { "Contrast must be -1.0 to 1.0" }
        require(focusDistance == null || focusDistance > 0) { "Focus distance must be positive" }
        require(exposureCompensation in -2.0..2.0) { "Exposure compensation must be -2.0 to 2.0" }
        require(scanSuccessRate in 0.0..1.0) { "Success rate must be 0.0-1.0" }
    }
    
    /**
     * Create updated profile with new success rate
     */
    fun withSuccessRate(rate: Double): CalibrationProfile {
        return copy(
            scanSuccessRate = rate,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * Check if profile is stale (older than 30 days)
     */
    fun isStale(): Boolean {
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() - lastUpdated > thirtyDaysMs
    }
}
