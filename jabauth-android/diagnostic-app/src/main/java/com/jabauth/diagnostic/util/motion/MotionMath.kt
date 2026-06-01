package com.jabauth.diagnostic.util.motion

import kotlin.math.sqrt

/**
 * Pure motion-vector math used by the sensor telemetry pipeline.
 *
 * Kept as a top-level object so the math is testable without Android
 * SensorManager and reusable across the live-telemetry path and any
 * offline analysis tooling.
 */
object MotionMath {

    /**
     * Euclidean norm of a 3D vector.
     *
     * For Sensor.TYPE_LINEAR_ACCELERATION this gives the magnitude of
     * device acceleration with gravity removed (the SDK handles gravity
     * subtraction internally). For Sensor.TYPE_GYROSCOPE this gives the
     * magnitude of angular velocity in rad/s.
     */
    fun magnitude(x: Float, y: Float, z: Float): Float =
        sqrt(x * x + y * y + z * z)
}
