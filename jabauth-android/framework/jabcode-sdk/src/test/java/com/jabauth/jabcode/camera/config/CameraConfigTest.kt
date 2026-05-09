package com.jabauth.jabcode.camera.config

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CameraConfig data class
 * 
 * Tests camera configuration properties and builder
 */
class CameraConfigTest {
    
    @Test
    fun config_hasDefaultValues() {
        val config = CameraConfig()
        
        assertNull(config.preferredCameraId) // null = use default camera
        assertTrue(config.enableAutoFocus)
        assertTrue(config.enableAutoExposure)
        assertTrue(config.enableAutoWhiteBalance)
        assertFalse(config.enableFlash)
        assertEquals(30, config.targetFps)
    }
    
    @Test
    fun config_builderCreatesConfig() {
        val config = CameraConfig.Builder()
            .cameraId("0")
            .targetFps(60)
            .enableFlash(true)
            .build()
        
        assertEquals("0", config.preferredCameraId)
        assertEquals(60, config.targetFps)
        assertTrue(config.enableFlash)
    }
    
    @Test
    fun config_copyWorks() {
        val original = CameraConfig(
            preferredCameraId = "1",
            targetFps = 60
        )
        
        val modified = original.copy(enableFlash = true)
        
        assertEquals("1", modified.preferredCameraId)
        assertEquals(60, modified.targetFps)
        assertTrue(modified.enableFlash)
    }
    
    @Test
    fun config_validatesFps() {
        val invalidConfig = CameraConfig(targetFps = -1)
        assertFalse(invalidConfig.isValid())
        
        val validConfig = CameraConfig(targetFps = 30)
        assertTrue(validConfig.isValid())
    }
    
    @Test
    fun config_builderChainsCorrectly() {
        val config = CameraConfig.Builder()
            .cameraId("0")
            .enableAutoFocus(false)
            .enableAutoExposure(false)
            .enableAutoWhiteBalance(false)
            .targetFps(60)
            .build()
        
        assertEquals("0", config.preferredCameraId)
        assertFalse(config.enableAutoFocus)
        assertFalse(config.enableAutoExposure)
        assertFalse(config.enableAutoWhiteBalance)
        assertEquals(60, config.targetFps)
    }
}
