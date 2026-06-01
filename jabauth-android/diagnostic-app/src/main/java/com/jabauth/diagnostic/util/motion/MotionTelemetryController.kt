package com.jabauth.diagnostic.util.motion

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Android-coupled wrapper around [MotionStateTracker]. Registers
 * SensorManager listeners for LINEAR_ACCELERATION and GYROSCOPE and
 * dispatches readings into the tracker.
 *
 * Lifecycle: call [start] when the scanner activates (e.g., from
 * ScannerViewModel.init) and [stop] from onCleared/onPause to release
 * sensor resources.
 *
 * Per Bayesian Council session bc-2026-06-01-05 (Heisenberg envelope):
 * sample rate is SENSOR_DELAY_UI (~16 Hz / 60ms) — well below the
 * camera analyzer rate (~30 Hz / 33ms) so sensor callbacks do not
 * contend with the camera-thread binder queue.
 *
 * No-op gracefully when:
 * - Device has no SensorManager (extremely rare)
 * - Either sensor is unavailable (returns null from getDefaultSensor)
 *
 * Load-bearing motion-state logic is in [MotionStateTracker] and tested
 * independently of Android. This class is thin glue.
 */
class MotionTelemetryController(
    application: Application,
    val tracker: MotionStateTracker = MotionStateTracker(
        StabilityGate(threshold = DEFAULT_THRESHOLD, requiredConsecutiveSamples = DEFAULT_STABILITY_SAMPLES)
    )
) {
    private val sensorManager: SensorManager? =
        application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val accelSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val gyroSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_LINEAR_ACCELERATION ->
                    tracker.onAccelReading(event.values[0], event.values[1], event.values[2])
                Sensor.TYPE_GYROSCOPE ->
                    tracker.onGyroReading(event.values[0], event.values[1], event.values[2])
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    init {
        Log.i(
            TAG,
            "MotionTelemetryController init: sensorManager=${sensorManager != null} " +
            "accel=${accelSensor != null} gyro=${gyroSensor != null}"
        )
    }

    /** Register sensor listeners. Idempotent — calling twice has no effect beyond logging. */
    fun start() {
        val sm = sensorManager ?: return
        accelSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        gyroSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        Log.i(TAG, "Motion sensors registered (SENSOR_DELAY_UI)")
    }

    /** Unregister sensor listeners. Safe to call multiple times. */
    fun stop() {
        sensorManager?.unregisterListener(listener)
        Log.i(TAG, "Motion sensors unregistered")
    }

    /** Current motion state snapshot. */
    fun currentState(): MotionState = tracker.state

    /** Stability-gate verdict — true iff [DEFAULT_STABILITY_SAMPLES] consecutive low-motion samples. */
    fun isStable(): Boolean = tracker.isStable()

    companion object {
        private const val TAG = "MotionTelemetryController"

        /**
         * Stability threshold for linear acceleration in m/s². Tuned for
         * handheld scanning: ~0.5 m/s² is the typical "user has stopped
         * moving" boundary on modern IMUs (Samsung Galaxy S25 noise floor
         * is roughly 0.05 m/s², so 0.5 is 10× headroom).
         */
        const val DEFAULT_THRESHOLD = 0.5f

        /**
         * Number of consecutive low-motion samples before stability is
         * declared. At SENSOR_DELAY_UI (~60ms), 3 samples = ~180ms of
         * stillness — matches the user's natural "I've settled" duration.
         */
        const val DEFAULT_STABILITY_SAMPLES = 3
    }
}
