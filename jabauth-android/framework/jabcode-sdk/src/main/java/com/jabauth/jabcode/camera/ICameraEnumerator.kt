package com.jabauth.jabcode.camera

/**
 * Interface for camera enumeration
 *
 * Enables testing and alternative implementations
 */
interface ICameraEnumerator {
    /**
     * Get all available camera profiles
     *
     * @return List of device profiles for all cameras
     */
    fun getAllCameraProfiles(): List<CameraDeviceProfiler.DeviceProfile>
}
