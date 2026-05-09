package com.jabauth.jabcode.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Size

/**
 * Enumerates and classifies Camera2 hardware
 * 
 * Handles:
 * - Logical vs physical cameras
 * - Hardware level detection
 * - Capability discovery
 * - Multi-camera systems
 */
class CameraEnumerator(private val context: Context) {
    
    private val cameraManager: CameraManager = 
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    
    /**
     * Get all available cameras
     * 
     * @return List of CameraInfo for all detected cameras
     */
    fun getAllCameras(): List<CameraInfo> {
        return cameraManager.cameraIdList.mapNotNull { cameraId ->
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                createCameraInfo(cameraId, characteristics)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Find camera by facing direction
     * 
     * @param facing Desired facing (LENS_FACING_BACK, LENS_FACING_FRONT)
     * @param minHardwareLevel Minimum hardware level required
     * @return CameraInfo or null if not found
     */
    fun findCameraByFacing(
        facing: Int,
        minHardwareLevel: Int = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
    ): CameraInfo? {
        return getAllCameras()
            .filter { it.facing == facing }
            .filter { it.hardwareLevel >= minHardwareLevel }
            .maxByOrNull { it.hardwareLevel }
    }
    
    /**
     * Find camera with specific capability
     * 
     * @param capability Required capability
     * @return List of cameras with that capability
     */
    fun findCamerasWithCapability(capability: Int): List<CameraInfo> {
        return getAllCameras().filter { it.hasCapability(capability) }
    }
    
    /**
     * Create CameraInfo from characteristics
     */
    private fun createCameraInfo(
        cameraId: String,
        characteristics: CameraCharacteristics
    ): CameraInfo {
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            ?: CameraCharacteristics.LENS_FACING_BACK
        
        val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
        
        val capabilitiesArray = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?: intArrayOf()
        val capabilities = capabilitiesArray.toSet()
        
        val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = streamConfigMap?.getOutputSizes(android.graphics.ImageFormat.JPEG) ?: emptyArray()
        val maxResolution = jpegSizes.maxByOrNull { it.width * it.height }
            ?: Size(0, 0)
        
        val isLogicalMultiCamera = if (Build.VERSION.SDK_INT >= 28) {
            capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
        } else {
            false
        }
        
        val physicalCameraIds = if (isLogicalMultiCamera && Build.VERSION.SDK_INT >= 28) {
            characteristics.physicalCameraIds
        } else {
            emptySet()
        }
        
        return CameraInfo(
            cameraId = cameraId,
            characteristics = characteristics,
            facing = facing,
            hardwareLevel = hardwareLevel,
            capabilities = capabilities,
            maxResolution = maxResolution,
            isLogicalMultiCamera = isLogicalMultiCamera,
            physicalCameraIds = physicalCameraIds
        )
    }
}
