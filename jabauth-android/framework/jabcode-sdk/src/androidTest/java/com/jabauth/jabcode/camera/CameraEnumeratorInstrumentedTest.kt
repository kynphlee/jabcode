package com.jabauth.jabcode.camera

import android.hardware.camera2.CameraCharacteristics
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for CameraEnumerator
 * 
 * Runs on real Android device to test actual Camera2 hardware
 */
@RunWith(AndroidJUnit4::class)
class CameraEnumeratorInstrumentedTest {
    
    private lateinit var enumerator: CameraEnumerator
    
    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        enumerator = CameraEnumerator(context)
    }
    
    @Test
    fun getAllCameras_returnsNonEmptyList_onRealDevice() {
        val cameras = enumerator.getAllCameras()
        
        // Most devices have at least 1 camera
        assertTrue("Device should have at least one camera", cameras.isNotEmpty())
        
        // Verify all cameras have valid IDs
        cameras.forEach { camera ->
            assertNotNull("Camera ID should not be null", camera.cameraId)
            assertTrue("Camera ID should not be empty", camera.cameraId.isNotEmpty())
        }
    }
    
    @Test
    fun getAllCameras_returnsCamerasWithValidFacing() {
        val cameras = enumerator.getAllCameras()
        
        cameras.forEach { camera ->
            val validFacings = setOf(
                CameraCharacteristics.LENS_FACING_BACK,
                CameraCharacteristics.LENS_FACING_FRONT,
                CameraCharacteristics.LENS_FACING_EXTERNAL
            )
            assertTrue(
                "Camera facing should be valid: ${camera.facingName}",
                validFacings.contains(camera.facing)
            )
        }
    }
    
    @Test
    fun getAllCameras_returnsCamerasWithValidHardwareLevel() {
        val cameras = enumerator.getAllCameras()
        
        cameras.forEach { camera ->
            val validLevels = setOf(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL
            )
            assertTrue(
                "Hardware level should be valid: ${camera.hardwareLevelName}",
                validLevels.contains(camera.hardwareLevel)
            )
        }
    }
    
    @Test
    fun getAllCameras_returnsCamerasWithNonZeroResolution() {
        val cameras = enumerator.getAllCameras()
        
        cameras.forEach { camera ->
            assertTrue(
                "Camera ${camera.cameraId} should have non-zero width",
                camera.maxResolution.width > 0
            )
            assertTrue(
                "Camera ${camera.cameraId} should have non-zero height",
                camera.maxResolution.height > 0
            )
        }
    }
    
    @Test
    fun findCameraByFacing_findsBackCamera_whenExists() {
        val cameras = enumerator.getAllCameras()
        val hasBackCamera = cameras.any { it.facing == CameraCharacteristics.LENS_FACING_BACK }
        
        if (hasBackCamera) {
            val backCamera = enumerator.findCameraByFacing(CameraCharacteristics.LENS_FACING_BACK)
            
            assertNotNull("Back camera should be found", backCamera)
            assertEquals(
                "Found camera should be back-facing",
                CameraCharacteristics.LENS_FACING_BACK,
                backCamera?.facing
            )
        } else {
            // Skip if device has no back camera (rare but possible)
            val backCamera = enumerator.findCameraByFacing(CameraCharacteristics.LENS_FACING_BACK)
            assertNull("No back camera should be found on this device", backCamera)
        }
    }
    
    @Test
    fun findCameraByFacing_findsFrontCamera_whenExists() {
        val cameras = enumerator.getAllCameras()
        val hasFrontCamera = cameras.any { it.facing == CameraCharacteristics.LENS_FACING_FRONT }
        
        if (hasFrontCamera) {
            val frontCamera = enumerator.findCameraByFacing(CameraCharacteristics.LENS_FACING_FRONT)
            
            assertNotNull("Front camera should be found", frontCamera)
            assertEquals(
                "Found camera should be front-facing",
                CameraCharacteristics.LENS_FACING_FRONT,
                frontCamera?.facing
            )
        } else {
            // Skip if device has no front camera
            val frontCamera = enumerator.findCameraByFacing(CameraCharacteristics.LENS_FACING_FRONT)
            assertNull("No front camera should be found on this device", frontCamera)
        }
    }
    
    @Test
    fun findCamerasWithCapability_filtersCorrectly() {
        val allCameras = enumerator.getAllCameras()
        
        // Test with BACKWARD_COMPATIBLE capability (should be on all cameras)
        val backwardCompatibleCameras = enumerator.findCamerasWithCapability(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
        )
        
        assertTrue(
            "All cameras should have BACKWARD_COMPATIBLE capability",
            backwardCompatibleCameras.size == allCameras.size
        )
    }
    
    @Test
    fun findCamerasWithCapability_returnsSubset_forSpecificCapability() {
        val allCameras = enumerator.getAllCameras()
        
        // Test with RAW capability (not all cameras have this)
        val rawCameras = enumerator.findCamerasWithCapability(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
        )
        
        // RAW cameras should be subset of all cameras
        assertTrue(
            "RAW cameras should be subset of all cameras",
            rawCameras.size <= allCameras.size
        )
        
        // All returned cameras should actually have RAW capability
        rawCameras.forEach { camera ->
            assertTrue(
                "Camera ${camera.cameraId} should have RAW capability",
                camera.supportsRaw
            )
        }
    }
    
    @Test
    fun cameraInfo_hasNonNullCharacteristics() {
        val cameras = enumerator.getAllCameras()
        
        cameras.forEach { camera ->
            assertNotNull(
                "Camera ${camera.cameraId} should have non-null characteristics",
                camera.characteristics
            )
        }
    }
}
