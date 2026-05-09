package com.jabauth.jabcode.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Size

/**
 * Immutable snapshot of a camera's capabilities
 * 
 * @property cameraId Camera identifier
 * @property characteristics Full CameraCharacteristics object
 * @property facing Lens facing direction (BACK, FRONT, EXTERNAL)
 * @property hardwareLevel Hardware support level
 * @property capabilities Available capabilities flags
 * @property maxResolution Maximum JPEG resolution
 * @property isLogicalMultiCamera Whether this is a logical multi-camera
 * @property physicalCameraIds Physical camera IDs (if logical multi-camera)
 */
data class CameraInfo(
    val cameraId: String,
    val characteristics: CameraCharacteristics?,
    val facing: Int,
    val hardwareLevel: Int,
    val capabilities: Set<Int>,
    val maxResolution: Size,
    val isLogicalMultiCamera: Boolean,
    val physicalCameraIds: Set<String>
) {
    /**
     * Human-readable facing direction
     */
    val facingName: String
        get() = when(facing) {
            CameraCharacteristics.LENS_FACING_BACK -> "BACK"
            CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }
    
    /**
     * Human-readable hardware level
     */
    val hardwareLevelName: String
        get() = when(hardwareLevel) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }
    
    /**
     * Check if specific capability is supported
     */
    fun hasCapability(capability: Int): Boolean = capabilities.contains(capability)
    
    /**
     * Convenience checks for common capabilities
     */
    val supportsRaw: Boolean
        get() = hasCapability(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
    
    val supportsManualSensor: Boolean
        get() = hasCapability(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
    
    val supportsManualPostProcessing: Boolean
        get() = hasCapability(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
}
