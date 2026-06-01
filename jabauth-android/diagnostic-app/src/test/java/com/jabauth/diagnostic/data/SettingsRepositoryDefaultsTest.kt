package com.jabauth.diagnostic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD tests for the Settings data class defaults.
 *
 * Pure data-class verification — no DataStore, no Robolectric. The
 * DataStore-backed read/write behaviour is exercised end-to-end by the
 * diagnostic-app's instrumented tests and by the runtime BuildConfig
 * default mechanism (verified in the benchmark variant build).
 *
 * Per Bayesian Council session bc-2026-06-01-05: motion telemetry and
 * throttling default ON in benchmark variant so the validation scan
 * surfaces motion signals without manual Settings toggling.
 */
class SettingsRepositoryDefaultsTest {

    @Test
    fun settings_defaults_includeMotionTelemetry() {
        val settings = SettingsRepository.Settings()
        // Default must come from BuildConfig — at test compile time this is
        // the test-variant default which mirrors the benchmark default (true).
        assertTrue(
            "motionTelemetryEnabled must default to true (benchmark/test variant)",
            settings.motionTelemetryEnabled
        )
    }

    @Test
    fun settings_defaults_includeMotionThrottling() {
        val settings = SettingsRepository.Settings()
        assertTrue(
            "motionThrottlingEnabled must default to true (benchmark/test variant)",
            settings.motionThrottlingEnabled
        )
    }

    @Test
    fun settings_existingDefaults_unchanged() {
        // Regression guard — the new fields must not perturb existing defaults.
        val settings = SettingsRepository.Settings()
        assertEquals(SettingsRepository.DEFAULT_DECODE_TIMEOUT, settings.decodeTimeout)
        assertEquals(SettingsRepository.DEFAULT_ANALYZE_INTERVAL, settings.analyzeInterval)
        assertEquals(SettingsRepository.DEFAULT_AUTO_FOCUS, settings.autoFocus)
        assertEquals(SettingsRepository.DEFAULT_HAPTIC_FEEDBACK, settings.hapticFeedback)
    }
}
