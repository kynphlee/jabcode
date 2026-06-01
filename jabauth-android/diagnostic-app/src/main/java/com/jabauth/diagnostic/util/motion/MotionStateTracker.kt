package com.jabauth.diagnostic.util.motion

/**
 * Latest motion snapshot — accelerometer and gyroscope magnitudes.
 *
 * Updated by [MotionStateTracker] from raw sensor readings. Consumed by
 * ScannerViewModel for the SENSOR_SNAPSHOT log markers and by HUD overlays
 * for the "DETECTING — hold steady" indicator.
 *
 * @property accelMagnitude Euclidean norm of linear acceleration in m/s²
 *   (gravity already removed by Sensor.TYPE_LINEAR_ACCELERATION).
 * @property gyroMagnitude Euclidean norm of angular velocity in rad/s.
 */
data class MotionState(
    val accelMagnitude: Float = 0f,
    val gyroMagnitude: Float = 0f
)

/**
 * Android-independent motion-state tracker. Feeds the [StabilityGate] from
 * accelerometer readings (linear motion) and tracks gyroscope readings for
 * diagnostic correlation only.
 *
 * Design note (per Bayesian Council session bc-2026-06-01-05): the gate
 * watches accelerometer ONLY because pinch-zoom and physical positioning
 * are dominated by linear motion. Gyroscope readings are useful for
 * post-hoc analysis (e.g., correlating rotational instability with PartII
 * palette-learning failures at Nc=6) but do not gate decode attempts —
 * that would over-suppress decodes during natural wrist rotation.
 *
 * Thread-safety: NOT thread-safe. Confine to a single thread or guard
 * externally. In production this lives on the main thread (sensor
 * callbacks) and is read from the camera-analyzer thread; if contention
 * becomes a concern, swap [state] to a volatile or use a StateFlow wrapper
 * at the MotionTelemetryController layer.
 */
class MotionStateTracker(
    private val stabilityGate: StabilityGate
) {
    var state: MotionState = MotionState()
        private set

    /**
     * Record a linear-accelerometer reading. Updates [state.accelMagnitude]
     * and feeds the [StabilityGate] with the magnitude.
     */
    fun onAccelReading(x: Float, y: Float, z: Float) {
        val mag = MotionMath.magnitude(x, y, z)
        state = state.copy(accelMagnitude = mag)
        stabilityGate.addSample(mag)
    }

    /**
     * Record a gyroscope reading. Updates [state.gyroMagnitude] only —
     * does NOT feed the StabilityGate (see class doc for rationale).
     */
    fun onGyroReading(x: Float, y: Float, z: Float) {
        val mag = MotionMath.magnitude(x, y, z)
        state = state.copy(gyroMagnitude = mag)
    }

    /** @return whether the StabilityGate currently reports stable. */
    fun isStable(): Boolean = stabilityGate.isStable()
}
