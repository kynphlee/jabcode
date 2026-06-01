package com.jabauth.diagnostic.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.jabauth.diagnostic.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// DataStore delegate - must be top-level property
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "diagnostic_settings")

/**
 * Settings Repository - Persistent configuration storage
 * 
 * Uses DataStore for reactive, type-safe settings persistence
 */
class SettingsRepository(private val context: Context) {
    
    companion object {
        // Preference keys
        private val DECODE_TIMEOUT = intPreferencesKey("decode_timeout")
        private val ANALYZE_INTERVAL = intPreferencesKey("analyze_interval")
        private val AUTO_FOCUS = booleanPreferencesKey("auto_focus")
        private val DEBUG_LOGGING = booleanPreferencesKey("debug_logging")
        private val PREFERRED_COLOR_MODE = intPreferencesKey("preferred_color_mode")
        private val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        private val MOTION_TELEMETRY = booleanPreferencesKey("motion_telemetry")
        private val MOTION_THROTTLING = booleanPreferencesKey("motion_throttling")

        // Default values
        const val DEFAULT_DECODE_TIMEOUT = 200
        const val DEFAULT_ANALYZE_INTERVAL = 500
        const val DEFAULT_AUTO_FOCUS = true

        /**
         * Default for the Settings "Haptic Feedback" toggle.
         *
         * Sourced from BuildConfig.DEFAULT_HAPTIC_ENABLED. Currently TRUE
         * across all variants — scan-success vibration is standard PoS
         * scanner UX and end users expect it on by default.
         */
        val DEFAULT_HAPTIC_FEEDBACK: Boolean = BuildConfig.DEFAULT_HAPTIC_ENABLED

        /**
         * Default for the Settings "Debug Logging" toggle.
         *
         * Sourced from BuildConfig.DEFAULT_DEBUG_LOGGING_ENABLED so the
         * default varies per build variant:
         *
         * - debug + release: FALSE (production-grade default; users opt in
         *   via Settings if they need verbose markers)
         * - benchmark: TRUE (diagnostic builds — installBenchmark
         *   uninstalls + reinstalls the APK, wiping the DataStore each
         *   time, so defaulting to FALSE forces a manual Settings toggle
         *   flip after every build to see [PartI_DIAG] markers fire.
         *   Stopping that recurring waste is the entire reason this
         *   variant-specific default exists.)
         *
         * Not declared `const` because BuildConfig fields are not Kotlin
         * compile-time constants — they're generated at build time by AGP.
         */
        val DEFAULT_DEBUG_LOGGING: Boolean = BuildConfig.DEFAULT_DEBUG_LOGGING_ENABLED

        const val DEFAULT_PREFERRED_COLOR_MODE = -1  // -1 = Auto

        /**
         * Default for the "Motion Telemetry" toggle. Sourced from
         * BuildConfig.DEFAULT_MOTION_TELEMETRY_ENABLED — defaults ON in
         * benchmark + test variants so SENSOR_SNAPSHOT markers surface
         * automatically. Bayesian Council bc-2026-06-01-05 (Sherlock):
         * sensor data is diagnostic-cheap when default-on; the marginal
         * cost is two log lines per decode attempt.
         */
        val DEFAULT_MOTION_TELEMETRY: Boolean = BuildConfig.DEFAULT_MOTION_TELEMETRY_ENABLED

        /**
         * Default for the "Motion Throttling" toggle. When true, the
         * camera analyzer skips decode attempts during motion above the
         * stability threshold. Bayesian Council bc-2026-06-01-05
         * (Historian): MLKit / VisionKit / Power Apps reader all use this
         * pattern; the JABCode decoder is more expensive per attempt so
         * the savings are larger.
         */
        val DEFAULT_MOTION_THROTTLING: Boolean = BuildConfig.DEFAULT_MOTION_THROTTLING_ENABLED
    }
    
    /**
     * Settings data class
     */
    data class Settings(
        val decodeTimeout: Int = DEFAULT_DECODE_TIMEOUT,
        val analyzeInterval: Int = DEFAULT_ANALYZE_INTERVAL,
        val autoFocus: Boolean = DEFAULT_AUTO_FOCUS,
        val debugLogging: Boolean = DEFAULT_DEBUG_LOGGING,
        val preferredColorMode: Int? = null,  // null = Auto
        val hapticFeedback: Boolean = DEFAULT_HAPTIC_FEEDBACK,
        val motionTelemetryEnabled: Boolean = DEFAULT_MOTION_TELEMETRY,
        val motionThrottlingEnabled: Boolean = DEFAULT_MOTION_THROTTLING
    )
    
    /**
     * Read settings as Flow
     */
    val settingsFlow: Flow<Settings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            Settings(
                decodeTimeout = preferences[DECODE_TIMEOUT] ?: DEFAULT_DECODE_TIMEOUT,
                analyzeInterval = preferences[ANALYZE_INTERVAL] ?: DEFAULT_ANALYZE_INTERVAL,
                autoFocus = preferences[AUTO_FOCUS] ?: DEFAULT_AUTO_FOCUS,
                debugLogging = preferences[DEBUG_LOGGING] ?: DEFAULT_DEBUG_LOGGING,
                preferredColorMode = preferences[PREFERRED_COLOR_MODE]?.takeIf { it != -1 },
                hapticFeedback = preferences[HAPTIC_FEEDBACK] ?: DEFAULT_HAPTIC_FEEDBACK,
                motionTelemetryEnabled = preferences[MOTION_TELEMETRY] ?: DEFAULT_MOTION_TELEMETRY,
                motionThrottlingEnabled = preferences[MOTION_THROTTLING] ?: DEFAULT_MOTION_THROTTLING
            )
        }
    
    /**
     * Update decode timeout
     */
    suspend fun updateDecodeTimeout(timeout: Int) {
        context.dataStore.edit { preferences ->
            preferences[DECODE_TIMEOUT] = timeout
        }
    }
    
    /**
     * Update analyze interval
     */
    suspend fun updateAnalyzeInterval(interval: Int) {
        context.dataStore.edit { preferences ->
            preferences[ANALYZE_INTERVAL] = interval
        }
    }
    
    /**
     * Update auto-focus setting
     */
    suspend fun updateAutoFocus(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_FOCUS] = enabled
        }
    }
    
    /**
     * Update debug logging
     */
    suspend fun updateDebugLogging(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DEBUG_LOGGING] = enabled
        }
    }
    
    /**
     * Update preferred color mode
     */
    suspend fun updatePreferredColorMode(colorMode: Int?) {
        context.dataStore.edit { preferences ->
            preferences[PREFERRED_COLOR_MODE] = colorMode ?: -1
        }
    }

    /**
     * Update haptic feedback setting
     */
    suspend fun updateHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAPTIC_FEEDBACK] = enabled
        }
    }

    /**
     * Update motion telemetry setting (SENSOR_SNAPSHOT log markers per
     * decode attempt; diagnostic-only).
     */
    suspend fun updateMotionTelemetry(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MOTION_TELEMETRY] = enabled
        }
    }

    /**
     * Update motion throttling setting (camera analyzer skips decode
     * attempts during motion above stability threshold).
     */
    suspend fun updateMotionThrottling(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MOTION_THROTTLING] = enabled
        }
    }
    
    /**
     * Reset all settings to defaults
     */
    suspend fun resetToDefaults() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
