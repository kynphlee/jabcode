package com.jabauth.jabcode

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CalibrationProfile
 */
class CalibrationProfileTest {

    @Test
    fun `default profile has valid values`() {
        val profile = CalibrationProfile(deviceModel = "TestDevice")
        
        assertEquals("TestDevice", profile.deviceModel)
        assertEquals(0.5, profile.brightness, 0.001)
        assertEquals(0.0, profile.contrast, 0.001)
        assertNull(profile.focusDistance)
        assertEquals(0.0, profile.exposureCompensation, 0.001)
        assertEquals(ColorMode.COLOR_8, profile.preferredColorMode)
        assertEquals(0.0, profile.scanSuccessRate, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `brightness below 0 throws exception`() {
        CalibrationProfile(deviceModel = "Test", brightness = -0.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `brightness above 1 throws exception`() {
        CalibrationProfile(deviceModel = "Test", brightness = 1.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `contrast below -1 throws exception`() {
        CalibrationProfile(deviceModel = "Test", contrast = -1.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `contrast above 1 throws exception`() {
        CalibrationProfile(deviceModel = "Test", contrast = 1.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative focus distance throws exception`() {
        CalibrationProfile(deviceModel = "Test", focusDistance = -1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero focus distance throws exception`() {
        CalibrationProfile(deviceModel = "Test", focusDistance = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `exposure compensation below -2 throws exception`() {
        CalibrationProfile(deviceModel = "Test", exposureCompensation = -2.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `exposure compensation above 2 throws exception`() {
        CalibrationProfile(deviceModel = "Test", exposureCompensation = 2.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `success rate below 0 throws exception`() {
        CalibrationProfile(deviceModel = "Test", scanSuccessRate = -0.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `success rate above 1 throws exception`() {
        CalibrationProfile(deviceModel = "Test", scanSuccessRate = 1.1)
    }

    @Test
    fun `withSuccessRate creates updated profile`() {
        val profile = CalibrationProfile(
            deviceModel = "Test",
            scanSuccessRate = 0.5
        )
        
        val updated = profile.withSuccessRate(0.8)
        
        assertEquals(0.8, updated.scanSuccessRate, 0.001)
        assertTrue(updated.lastUpdated >= profile.lastUpdated)
    }

    @Test
    fun `isStale returns false for recent profile`() {
        val profile = CalibrationProfile(
            deviceModel = "Test",
            lastUpdated = System.currentTimeMillis()
        )
        
        assertFalse(profile.isStale())
    }

    @Test
    fun `isStale returns true for old profile`() {
        val thirtyOneDaysAgo = System.currentTimeMillis() - (31L * 24 * 60 * 60 * 1000)
        val profile = CalibrationProfile(
            deviceModel = "Test",
            lastUpdated = thirtyOneDaysAgo
        )
        
        assertTrue(profile.isStale())
    }

    @Test
    fun `profile with all valid edge values succeeds`() {
        val profile = CalibrationProfile(
            deviceModel = "Test",
            brightness = 0.0,
            contrast = -1.0,
            focusDistance = 0.001,
            exposureCompensation = -2.0,
            scanSuccessRate = 1.0
        )
        
        assertEquals(0.0, profile.brightness, 0.001)
        assertEquals(-1.0, profile.contrast, 0.001)
    }
}
