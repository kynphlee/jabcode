package com.jabauth.jabcode

import com.jabauth.core.storage.SecureStorage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for CalibrationManager
 * 
 * Requires Robolectric for JSONObject support
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalibrationManagerTest {

    private lateinit var storage: FakeSecureStorage
    private lateinit var manager: CalibrationManager

    @Before
    fun setup() {
        storage = FakeSecureStorage()
        manager = CalibrationManager(storage, "TestDevice")
    }
    
    /**
     * Fake SecureStorage for testing
     */
    private class FakeSecureStorage : SecureStorage {
        private val data = mutableMapOf<String, String>()
        
        override fun putString(key: String, value: String) {
            data[key] = value
        }
        
        override fun getString(key: String, defaultValue: String?): String? {
            return data[key] ?: defaultValue
        }
        
        override fun putInt(key: String, value: Int) {}
        override fun getInt(key: String, defaultValue: Int): Int = defaultValue
        override fun putBoolean(key: String, value: Boolean) {}
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        
        override fun contains(key: String): Boolean = data.containsKey(key)
        
        override fun remove(key: String) {
            data.remove(key)
        }
        
        override fun clear() {
            data.clear()
        }
    }

    @Test
    fun `saveProfile stores profile for device`() {
        val profile = CalibrationProfile(
            deviceModel = "TestDevice",
            brightness = 0.7,
            preferredColorMode = ColorMode.COLOR_4
        )
        
        manager.saveProfile(profile)
        
        val loaded = manager.loadProfile()
        assertNotNull(loaded)
        assertEquals("TestDevice", loaded!!.deviceModel)
        assertEquals(0.7, loaded.brightness, 0.001)
        assertEquals(ColorMode.COLOR_4, loaded.preferredColorMode)
    }

    @Test
    fun `loadProfile returns null when no profile exists`() {
        val loaded = manager.loadProfile()
        
        assertNull(loaded)
    }

    @Test
    fun `saveDefaultProfile stores fallback profile`() {
        val profile = CalibrationProfile(
            deviceModel = "Default",
            brightness = 0.5
        )
        
        manager.saveDefaultProfile(profile)
        
        val loaded = manager.loadDefaultProfile()
        assertNotNull(loaded)
        assertEquals("Default", loaded!!.deviceModel)
    }

    @Test
    fun `clearProfile removes device profile`() {
        val profile = CalibrationProfile(deviceModel = "TestDevice")
        manager.saveProfile(profile)
        
        manager.clearProfile()
        
        assertNull(manager.loadProfile())
    }

    @Test
    fun `hasProfile returns true when profile exists`() {
        assertFalse(manager.hasProfile())
        
        val profile = CalibrationProfile(deviceModel = "TestDevice")
        manager.saveProfile(profile)
        
        assertTrue(manager.hasProfile())
    }

    @Test
    fun `getOrCreateDefault returns saved profile if exists`() {
        val profile = CalibrationProfile(
            deviceModel = "TestDevice",
            brightness = 0.8
        )
        manager.saveProfile(profile)
        
        val result = manager.getOrCreateDefault()
        
        assertEquals(0.8, result.brightness, 0.001)
    }

    @Test
    fun `getOrCreateDefault creates new profile if none exists`() {
        val result = manager.getOrCreateDefault()
        
        assertEquals("TestDevice", result.deviceModel)
        assertEquals(0.5, result.brightness, 0.001)
    }

    @Test
    fun `profile roundtrip preserves all fields`() {
        val profile = CalibrationProfile(
            deviceModel = "TestDevice",
            brightness = 0.7,
            contrast = 0.3,
            focusDistance = 0.5,
            exposureCompensation = 1.0,
            preferredColorMode = ColorMode.COLOR_8,
            scanSuccessRate = 0.95,
            lastUpdated = 1234567890L
        )
        
        manager.saveProfile(profile)
        val loaded = manager.loadProfile()
        
        assertNotNull(loaded)
        assertEquals(profile.deviceModel, loaded!!.deviceModel)
        assertEquals(profile.brightness, loaded.brightness, 0.001)
        assertEquals(profile.contrast, loaded.contrast, 0.001)
        assertNotNull(loaded.focusDistance)
        assertEquals(profile.focusDistance!!, loaded.focusDistance!!, 0.001)
        assertEquals(profile.exposureCompensation, loaded.exposureCompensation, 0.001)
        assertEquals(profile.preferredColorMode, loaded.preferredColorMode)
        assertEquals(profile.scanSuccessRate, loaded.scanSuccessRate, 0.001)
        assertEquals(profile.lastUpdated, loaded.lastUpdated)
    }

    @Test
    fun `profile with null focusDistance serializes correctly`() {
        val profile = CalibrationProfile(
            deviceModel = "TestDevice",
            focusDistance = null
        )
        
        manager.saveProfile(profile)
        val loaded = manager.loadProfile()
        
        assertNotNull(loaded)
        assertNull(loaded!!.focusDistance)
    }

    @Test
    fun `clearAll removes all profiles`() {
        manager.saveProfile(CalibrationProfile(deviceModel = "TestDevice"))
        manager.saveDefaultProfile(CalibrationProfile(deviceModel = "Default"))
        
        manager.clearAll()
        
        assertNull(manager.loadProfile())
        assertNull(manager.loadDefaultProfile())
    }
}
