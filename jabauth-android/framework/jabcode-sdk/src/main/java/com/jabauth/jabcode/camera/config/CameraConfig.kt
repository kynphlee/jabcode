package com.jabauth.jabcode.camera.config

/**
 * Camera configuration settings
 * 
 * @property preferredCameraId Preferred camera ID to use (null = default back camera)
 * @property enableAutoFocus Enable auto-focus
 * @property enableAutoExposure Enable auto-exposure
 * @property enableAutoWhiteBalance Enable auto white balance
 * @property enableFlash Enable flash/torch
 * @property targetFps Target frames per second
 */
data class CameraConfig(
    val preferredCameraId: String? = null,
    val enableAutoFocus: Boolean = true,
    val enableAutoExposure: Boolean = true,
    val enableAutoWhiteBalance: Boolean = true,
    val enableFlash: Boolean = false,
    val targetFps: Int = 30
) {
    /**
     * Validate configuration
     * 
     * @return true if configuration is valid
     */
    fun isValid(): Boolean {
        return targetFps > 0 && targetFps <= 120
    }
    
    /**
     * Builder for CameraConfig
     */
    class Builder {
        private var preferredCameraId: String? = null
        private var enableAutoFocus: Boolean = true
        private var enableAutoExposure: Boolean = true
        private var enableAutoWhiteBalance: Boolean = true
        private var enableFlash: Boolean = false
        private var targetFps: Int = 30
        
        fun cameraId(id: String) = apply { this.preferredCameraId = id }
        fun enableAutoFocus(enable: Boolean) = apply { this.enableAutoFocus = enable }
        fun enableAutoExposure(enable: Boolean) = apply { this.enableAutoExposure = enable }
        fun enableAutoWhiteBalance(enable: Boolean) = apply { this.enableAutoWhiteBalance = enable }
        fun enableFlash(enable: Boolean) = apply { this.enableFlash = enable }
        fun targetFps(fps: Int) = apply { this.targetFps = fps }
        
        fun build() = CameraConfig(
            preferredCameraId = preferredCameraId,
            enableAutoFocus = enableAutoFocus,
            enableAutoExposure = enableAutoExposure,
            enableAutoWhiteBalance = enableAutoWhiteBalance,
            enableFlash = enableFlash,
            targetFps = targetFps
        )
    }
}
