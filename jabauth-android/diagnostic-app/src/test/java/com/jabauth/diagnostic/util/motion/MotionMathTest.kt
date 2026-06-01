package com.jabauth.diagnostic.util.motion

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TDD tests for MotionMath — pure motion-vector math used by the sensor
 * telemetry pipeline.
 *
 * Kept as a top-level pure function so the StabilityGate state machine
 * can be tested independently of Android's SensorManager and so the
 * computation is reusable in both the live telemetry path (per-frame
 * magnitude logging) and in any future offline analysis script.
 */
class MotionMathTest {

    @Test
    fun magnitude_ofZeroVector_isZero() {
        assertEquals(0f, MotionMath.magnitude(0f, 0f, 0f), 0.0001f)
    }

    @Test
    fun magnitude_ofUnitX_isOne() {
        assertEquals(1f, MotionMath.magnitude(1f, 0f, 0f), 0.0001f)
    }

    @Test
    fun magnitude_ofUnitY_isOne() {
        assertEquals(1f, MotionMath.magnitude(0f, 1f, 0f), 0.0001f)
    }

    @Test
    fun magnitude_ofUnitZ_isOne() {
        assertEquals(1f, MotionMath.magnitude(0f, 0f, 1f), 0.0001f)
    }

    @Test
    fun magnitude_of345Triple_is5() {
        // Classic Pythagorean triple — proves Euclidean norm correctness.
        assertEquals(5f, MotionMath.magnitude(3f, 4f, 0f), 0.0001f)
    }

    @Test
    fun magnitude_ignoresSignOfComponents() {
        // Euclidean norm is invariant under sign flips.
        val pos = MotionMath.magnitude(1f, 2f, 3f)
        val neg = MotionMath.magnitude(-1f, -2f, -3f)
        val mixed = MotionMath.magnitude(1f, -2f, 3f)
        assertEquals(pos, neg, 0.0001f)
        assertEquals(pos, mixed, 0.0001f)
    }

    @Test
    fun magnitude_handlesRealisticAccelValues() {
        // Real Android LINEAR_ACCELERATION values are typically in [-10, 10] m/s²
        // after gravity subtraction. Spot-check a realistic "user shaking" value.
        // sqrt(2² + 2² + 1²) = sqrt(9) = 3
        assertEquals(3f, MotionMath.magnitude(2f, 2f, 1f), 0.0001f)
    }
}
