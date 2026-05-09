package com.jabauth.jabcode.camera.transform

import com.jabauth.jabcode.camera.CameraDeviceProfiler

/**
 * Calculates preview orientation and rotation
 * 
 * Handles sensor orientation and device rotation compensation
 */
class OrientationCalculator {
    
    /**
     * Calculate preview rotation based on sensor and device orientation
     * 
     * @param sensorOrientation Camera sensor orientation (0, 90, 180, 270)
     * @param deviceRotation Current device rotation in degrees
     * @param cameraFacing Camera facing direction
     * @return Rotation degrees for preview (0-359)
     */
    fun calculatePreviewRotation(
        sensorOrientation: Int,
        deviceRotation: Int,
        cameraFacing: CameraDeviceProfiler.Facing
    ): Int {
        // For back camera: rotation = sensor - device
        // For front camera: rotation = (sensor + device) % 360
        val rotation = when (cameraFacing) {
            CameraDeviceProfiler.Facing.BACK -> {
                (sensorOrientation - deviceRotation + 360) % 360
            }
            CameraDeviceProfiler.Facing.FRONT -> {
                // Front camera is mirrored, use different formula
                (sensorOrientation + deviceRotation) % 360
            }
            CameraDeviceProfiler.Facing.EXTERNAL,
            CameraDeviceProfiler.Facing.UNKNOWN -> {
                // External/unknown cameras use same as back
                (sensorOrientation - deviceRotation + 360) % 360
            }
        }
        
        return rotation
    }
}
