package com.jabauth.diagnostic.util

import android.app.Application
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Haptic feedback controller for scan-success cues.
 *
 * Fires a single short vibration pulse on each successful JABCode decode
 * when the user has enabled the Settings "Haptic Feedback" toggle. The
 * 50ms pulse at DEFAULT_AMPLITUDE matches the commercial PoS barcode
 * scanner convention for a "scan succeeded" tactile cue.
 *
 * API split: Android 12+ (API 31, S) uses VibratorManager; older API
 * levels fall back to the deprecated single Vibrator system service.
 * Both paths produce identical vibration output for a one-shot pulse.
 *
 * Holds an Application reference (not Activity), so the controller is
 * safe to instantiate from an AndroidViewModel — no leak risk.
 *
 * No-op gracefully when:
 * - Device has no vibrator (hasVibrator() == false)
 * - VibratorManager / Vibrator system service is unavailable
 * - VIBRATE permission is missing (silently fails per Vibrator contract)
 */
class HapticFeedbackController(application: Application) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        application.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Vibrator::class.java)
    }

    init {
        val available = vibrator?.hasVibrator() == true
        Log.i(
            TAG,
            "HapticFeedbackController init: API=${Build.VERSION.SDK_INT} " +
            "vibrator=${if (available) "available" else "unavailable"}"
        )
    }

    /**
     * Fire a single short pulse to signal scan success. Safe to call at
     * high frequency (e.g., from a continuous-scan onDecodeSuccess
     * callback) — VibrationEffect.createOneShot is lightweight and the
     * system handles overlapping requests gracefully.
     */
    fun pulseSuccess() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(
            VibrationEffect.createOneShot(
                PULSE_DURATION_MS,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }

    companion object {
        private const val TAG = "HapticFeedbackController"
        private const val PULSE_DURATION_MS = 50L
    }
}
