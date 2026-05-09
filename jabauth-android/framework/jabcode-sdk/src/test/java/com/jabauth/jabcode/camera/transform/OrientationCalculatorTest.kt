package com.jabauth.jabcode.camera.transform

import com.jabauth.jabcode.camera.CameraDeviceProfiler
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for OrientationCalculator
 * 
 * Tests preview rotation calculations
 */
class OrientationCalculatorTest {
    
    private val calculator = OrientationCalculator()
    
    @Test
    fun calculator_backCamera_portrait() {
        // Back camera, portrait, sensor orientation 90
        val rotation = calculator.calculatePreviewRotation(
            sensorOrientation = 90,
            deviceRotation = 0,
            cameraFacing = CameraDeviceProfiler.Facing.BACK
        )
        
        assertEquals(90, rotation)
    }
    
    @Test
    fun calculator_backCamera_landscape() {
        // Back camera, landscape, sensor orientation 90
        val rotation = calculator.calculatePreviewRotation(
            sensorOrientation = 90,
            deviceRotation = 90,
            cameraFacing = CameraDeviceProfiler.Facing.BACK
        )
        
        assertEquals(0, rotation)
    }
    
    @Test
    fun calculator_frontCamera_portrait() {
        // Front camera, portrait, sensor orientation 270
        val rotation = calculator.calculatePreviewRotation(
            sensorOrientation = 270,
            deviceRotation = 0,
            cameraFacing = CameraDeviceProfiler.Facing.FRONT
        )
        
        assertEquals(270, rotation)
    }
    
    @Test
    fun calculator_frontCamera_landscape() {
        // Front camera, landscape, sensor orientation 270
        val rotation = calculator.calculatePreviewRotation(
            sensorOrientation = 270,
            deviceRotation = 90,
            cameraFacing = CameraDeviceProfiler.Facing.FRONT
        )
        
        assertEquals(0, rotation)
    }
    
    @Test
    fun calculator_rotationNormalized() {
        // Ensure rotation is always 0-359
        val rotation = calculator.calculatePreviewRotation(
            sensorOrientation = 90,
            deviceRotation = 270,
            cameraFacing = CameraDeviceProfiler.Facing.BACK
        )
        
        assertTrue("Rotation should be 0-359", rotation in 0..359)
    }
}
