package com.jabauth.jabcode.camera.transform

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DeviceOrientation enum
 * 
 * Tests device orientation and rotation calculations
 */
class DeviceOrientationTest {
    
    @Test
    fun deviceOrientation_hasAllOrientations() {
        assertNotNull(DeviceOrientation.PORTRAIT)
        assertNotNull(DeviceOrientation.LANDSCAPE)
        assertNotNull(DeviceOrientation.PORTRAIT_REVERSE)
        assertNotNull(DeviceOrientation.LANDSCAPE_REVERSE)
    }
    
    @Test
    fun deviceOrientation_correctRotationDegrees() {
        assertEquals(0, DeviceOrientation.PORTRAIT.rotationDegrees)
        assertEquals(90, DeviceOrientation.LANDSCAPE.rotationDegrees)
        assertEquals(180, DeviceOrientation.PORTRAIT_REVERSE.rotationDegrees)
        assertEquals(270, DeviceOrientation.LANDSCAPE_REVERSE.rotationDegrees)
    }
    
    @Test
    fun deviceOrientation_isLandscape() {
        assertTrue(DeviceOrientation.LANDSCAPE.isLandscape)
        assertTrue(DeviceOrientation.LANDSCAPE_REVERSE.isLandscape)
        
        assertFalse(DeviceOrientation.PORTRAIT.isLandscape)
        assertFalse(DeviceOrientation.PORTRAIT_REVERSE.isLandscape)
    }
    
    @Test
    fun deviceOrientation_isPortrait() {
        assertTrue(DeviceOrientation.PORTRAIT.isPortrait)
        assertTrue(DeviceOrientation.PORTRAIT_REVERSE.isPortrait)
        
        assertFalse(DeviceOrientation.LANDSCAPE.isPortrait)
        assertFalse(DeviceOrientation.LANDSCAPE_REVERSE.isPortrait)
    }
    
    @Test
    fun deviceOrientation_fromRotation_portrait() {
        assertEquals(DeviceOrientation.PORTRAIT, DeviceOrientation.fromRotation(0))
        assertEquals(DeviceOrientation.PORTRAIT, DeviceOrientation.fromRotation(30)) // Rounds to 0
    }
    
    @Test
    fun deviceOrientation_fromRotation_landscape() {
        assertEquals(DeviceOrientation.LANDSCAPE, DeviceOrientation.fromRotation(90))
        assertEquals(DeviceOrientation.LANDSCAPE, DeviceOrientation.fromRotation(100)) // Rounds to 90
    }
    
    @Test
    fun deviceOrientation_fromRotation_reversePortrait() {
        assertEquals(DeviceOrientation.PORTRAIT_REVERSE, DeviceOrientation.fromRotation(180))
    }
    
    @Test
    fun deviceOrientation_fromRotation_reverseLandscape() {
        assertEquals(DeviceOrientation.LANDSCAPE_REVERSE, DeviceOrientation.fromRotation(270))
    }
}
