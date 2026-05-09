package com.jabauth.jabcode.camera.transform

/**
 * Device physical orientation
 * 
 * @property rotationDegrees Rotation in degrees from portrait
 * @property isLandscape Whether this is a landscape orientation
 * @property isPortrait Whether this is a portrait orientation
 */
enum class DeviceOrientation(
    val rotationDegrees: Int,
    val isLandscape: Boolean,
    val isPortrait: Boolean
) {
    /** Portrait orientation (0 degrees) */
    PORTRAIT(
        rotationDegrees = 0,
        isLandscape = false,
        isPortrait = true
    ),
    
    /** Landscape orientation (90 degrees) */
    LANDSCAPE(
        rotationDegrees = 90,
        isLandscape = true,
        isPortrait = false
    ),
    
    /** Reverse portrait orientation (180 degrees) */
    PORTRAIT_REVERSE(
        rotationDegrees = 180,
        isLandscape = false,
        isPortrait = true
    ),
    
    /** Reverse landscape orientation (270 degrees) */
    LANDSCAPE_REVERSE(
        rotationDegrees = 270,
        isLandscape = true,
        isPortrait = false
    );
    
    companion object {
        /**
         * Get orientation from rotation degrees
         * 
         * Rounds to nearest 90-degree increment
         * 
         * @param rotation Rotation in degrees
         * @return Corresponding orientation
         */
        fun fromRotation(rotation: Int): DeviceOrientation {
            val normalized = ((rotation + 45) / 90 * 90) % 360
            return when (normalized) {
                0 -> PORTRAIT
                90 -> LANDSCAPE
                180 -> PORTRAIT_REVERSE
                270 -> LANDSCAPE_REVERSE
                else -> PORTRAIT // Fallback
            }
        }
    }
}
