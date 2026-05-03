package com.jabauth.jabcode

import com.jabauth.core.storage.SecureStorage
import org.json.JSONObject

/**
 * Manages camera calibration profiles for JABCode scanning
 *
 * Stores and retrieves optimal camera settings per device.
 * Uses SecureStorage to persist profiles across app restarts.
 */
class CalibrationManager(
    private val storage: SecureStorage,
    private val deviceModel: String
) {
    
    companion object {
        private const val KEY_CALIBRATION_PREFIX = "jabcode_calibration_"
        private const val KEY_DEFAULT_PROFILE = "jabcode_calibration_default"
    }
    
    /**
     * Save calibration profile for current device
     */
    fun saveProfile(profile: CalibrationProfile) {
        val key = KEY_CALIBRATION_PREFIX + deviceModel
        val json = profileToJson(profile)
        storage.putString(key, json)
    }
    
    /**
     * Load calibration profile for current device
     *
     * @return Profile if exists, null otherwise
     */
    fun loadProfile(): CalibrationProfile? {
        val key = KEY_CALIBRATION_PREFIX + deviceModel
        val json = storage.getString(key) ?: return null
        return jsonToProfile(json)
    }
    
    /**
     * Save default calibration profile (fallback)
     */
    fun saveDefaultProfile(profile: CalibrationProfile) {
        val json = profileToJson(profile)
        storage.putString(KEY_DEFAULT_PROFILE, json)
    }
    
    /**
     * Load default calibration profile
     */
    fun loadDefaultProfile(): CalibrationProfile? {
        val json = storage.getString(KEY_DEFAULT_PROFILE) ?: return null
        return jsonToProfile(json)
    }
    
    /**
     * Clear calibration profile for current device
     */
    fun clearProfile() {
        val key = KEY_CALIBRATION_PREFIX + deviceModel
        storage.remove(key)
    }
    
    /**
     * Clear all calibration profiles
     */
    fun clearAll() {
        // Note: SecureStorage doesn't provide key iteration
        // In production, maintain an index of stored profiles
        clearProfile()
        storage.remove(KEY_DEFAULT_PROFILE)
    }
    
    /**
     * Check if profile exists for current device
     */
    fun hasProfile(): Boolean {
        val key = KEY_CALIBRATION_PREFIX + deviceModel
        return storage.contains(key)
    }
    
    /**
     * Get profile or create default
     */
    fun getOrCreateDefault(): CalibrationProfile {
        return loadProfile() ?: CalibrationProfile(
            deviceModel = deviceModel,
            brightness = 0.5,
            contrast = 0.0,
            preferredColorMode = ColorMode.COLOR_8
        )
    }
    
    /**
     * Convert profile to JSON string
     */
    private fun profileToJson(profile: CalibrationProfile): String {
        val json = JSONObject()
        json.put("deviceModel", profile.deviceModel)
        json.put("brightness", profile.brightness)
        json.put("contrast", profile.contrast)
        json.put("focusDistance", profile.focusDistance ?: JSONObject.NULL)
        json.put("exposureCompensation", profile.exposureCompensation)
        json.put("preferredColorMode", profile.preferredColorMode.value)
        json.put("scanSuccessRate", profile.scanSuccessRate)
        json.put("lastUpdated", profile.lastUpdated)
        return json.toString()
    }
    
    /**
     * Parse profile from JSON string
     */
    private fun jsonToProfile(jsonString: String): CalibrationProfile? {
        return try {
            val json = JSONObject(jsonString)
            CalibrationProfile(
                deviceModel = json.getString("deviceModel"),
                brightness = json.getDouble("brightness"),
                contrast = json.getDouble("contrast"),
                focusDistance = if (json.isNull("focusDistance")) null else json.getDouble("focusDistance"),
                exposureCompensation = json.getDouble("exposureCompensation"),
                preferredColorMode = ColorMode.entries.first { it.value == json.getInt("preferredColorMode") },
                scanSuccessRate = json.getDouble("scanSuccessRate"),
                lastUpdated = json.getLong("lastUpdated")
            )
        } catch (e: Exception) {
            null
        }
    }
}
