package com.jabauth.jabcode.camera

import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CameraInfo data class
 * 
 * Testing:
 * - Facing direction name conversion
 * - Hardware level name conversion
 * - Capability checking
 * - Convenience properties
 */
class CameraInfoTest {
    
    @Test
    fun facingName_returnsBack_forBackCamera() {
        val cameraInfo = CameraInfo(
            cameraId = "0",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_BACK,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
            capabilities = emptySet(),
            maxResolution = Size(1920, 1080),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertEquals("BACK", cameraInfo.facingName)
    }
    
    @Test
    fun facingName_returnsFront_forFrontCamera() {
        val cameraInfo = CameraInfo(
            cameraId = "1",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_FRONT,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = emptySet(),
            maxResolution = Size(1280, 720),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertEquals("FRONT", cameraInfo.facingName)
    }
    
    @Test
    fun facingName_returnsExternal_forExternalCamera() {
        val cameraInfo = CameraInfo(
            cameraId = "2",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_EXTERNAL,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL,
            capabilities = emptySet(),
            maxResolution = Size(640, 480),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertEquals("EXTERNAL", cameraInfo.facingName)
    }
    
    @Test
    fun facingName_returnsUnknown_forInvalidFacing() {
        val cameraInfo = CameraInfo(
            cameraId = "99",
            characteristics = null,
            facing = 999,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
            capabilities = emptySet(),
            maxResolution = Size(800, 600),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertEquals("UNKNOWN", cameraInfo.facingName)
    }
    
    @Test
    fun hardwareLevelName_returnsLegacy_forLegacyHardware() {
        val cameraInfo = CameraInfo(
            cameraId = "0",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_BACK,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
            capabilities = emptySet(),
            maxResolution = Size(1920, 1080),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertEquals("LEGACY", cameraInfo.hardwareLevelName)
    }
    
    @Test
    fun hardwareLevelName_returnsLimited_forLimitedHardware() {
        val cameraInfo = CameraInfo(
            cameraId = "0",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_BACK,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = emptySet(),
            maxResolution = Size(1920, 1080),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertEquals("LIMITED", cameraInfo.hardwareLevelName)
    }
    
    @Test
    fun hardwareLevelName_returnsFull_forFullHardware() {
        val cameraInfo = CameraInfo(
            cameraId = "0",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_BACK,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
            capabilities = emptySet(),
            maxResolution = Size(1920, 1080),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertEquals("FULL", cameraInfo.hardwareLevelName)
    }
    
    @Test
    fun hardwareLevelName_returnsLevel3_forLevel3Hardware() {
        val cameraInfo = CameraInfo(
            cameraId = "0",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_BACK,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3,
            capabilities = emptySet(),
            maxResolution = Size(3840, 2160),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertEquals("LEVEL_3", cameraInfo.hardwareLevelName)
    }
    
    @Test
    fun hardwareLevelName_returnsExternal_forExternalHardware() {
        val cameraInfo = CameraInfo(
            cameraId = "2",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_EXTERNAL,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL,
            capabilities = emptySet(),
            maxResolution = Size(1280, 720),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertEquals("EXTERNAL", cameraInfo.hardwareLevelName)
    }
    
    @Test
    fun hardwareLevelName_returnsUnknown_forInvalidLevel() {
        val cameraInfo = CameraInfo(
            cameraId = "0",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_BACK,
            hardwareLevel = 999,
            capabilities = emptySet(),
            maxResolution = Size(1920, 1080),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertEquals("UNKNOWN", cameraInfo.hardwareLevelName)
    }
    
    @Test
    fun hasCapability_returnsFalse_whenCapabilityNotPresent() {
        val cameraInfo = CameraInfo(
            cameraId = "0",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_BACK,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = setOf(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
            ),
            maxResolution = Size(1920, 1080),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertFalse(cameraInfo.hasCapability(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW))
    }
    
    @Test
    fun hasCapability_returnsTrue_whenCapabilityPresent() {
        val cameraInfo = CameraInfo(
            cameraId = "0",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_BACK,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
            capabilities = setOf(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE,
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
            ),
            maxResolution = Size(1920, 1080),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertTrue(cameraInfo.hasCapability(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW))
    }
    
    @Test
    fun supportsRaw_returnsFalse_whenCapabilityMissing() {
        val cameraInfo = CameraInfo(
            cameraId = "0",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_BACK,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = setOf(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
            ),
            maxResolution = Size(1920, 1080),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertFalse(cameraInfo.supportsRaw)
    }
    
    @Test
    fun supportsRaw_returnsTrue_whenCapabilityPresent() {
        val cameraInfo = CameraInfo(
            cameraId = "0",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_BACK,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
            capabilities = setOf(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE,
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
            ),
            maxResolution = Size(1920, 1080),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertTrue(cameraInfo.supportsRaw)
    }
    
    @Test
    fun supportsManualSensor_returnsFalse_whenCapabilityMissing() {
        val cameraInfo = CameraInfo(
            cameraId = "0",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_BACK,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = setOf(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
            ),
            maxResolution = Size(1920, 1080),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertFalse(cameraInfo.supportsManualSensor)
    }
    
    @Test
    fun supportsManualSensor_returnsTrue_whenCapabilityPresent() {
        val cameraInfo = CameraInfo(
            cameraId = "0",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_BACK,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
            capabilities = setOf(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE,
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
            ),
            maxResolution = Size(1920, 1080),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertTrue(cameraInfo.supportsManualSensor)
    }
    
    @Test
    fun supportsManualPostProcessing_returnsFalse_whenCapabilityMissing() {
        val cameraInfo = CameraInfo(
            cameraId = "0",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_BACK,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = setOf(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
            ),
            maxResolution = Size(1920, 1080),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertFalse(cameraInfo.supportsManualPostProcessing)
    }
    
    @Test
    fun supportsManualPostProcessing_returnsTrue_whenCapabilityPresent() {
        val cameraInfo = CameraInfo(
            cameraId = "0",
            characteristics = null,
            facing = CameraCharacteristics.LENS_FACING_BACK,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
            capabilities = setOf(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE,
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING
            ),
            maxResolution = Size(1920, 1080),
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet()
        )
        
        assertTrue(cameraInfo.supportsManualPostProcessing)
    }
}
