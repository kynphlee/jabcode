package com.jabauth.diagnostic.util.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD tests for MotionStateTracker — the Android-independent state holder
 * that consumes sensor magnitude updates and reports stability.
 *
 * Designed so the production MotionTelemetryController can be thin Android
 * plumbing (SensorEventListener.onSensorChanged → tracker.onAccelReading)
 * while the load-bearing state logic stays unit-testable without Robolectric.
 */
class MotionStateTrackerTest {

    private fun newTracker(threshold: Float = 0.5f, requiredSamples: Int = 3): MotionStateTracker =
        MotionStateTracker(StabilityGate(threshold = threshold, requiredConsecutiveSamples = requiredSamples))

    @Test
    fun initialState_isZero() {
        val tracker = newTracker()
        assertEquals(0f, tracker.state.accelMagnitude, 0.0001f)
        assertEquals(0f, tracker.state.gyroMagnitude, 0.0001f)
        assertFalse("Fresh tracker must not be stable", tracker.isStable())
    }

    @Test
    fun accelReading_updatesAccelMagnitudeOnly() {
        val tracker = newTracker()
        tracker.onAccelReading(3f, 4f, 0f)
        assertEquals(5f, tracker.state.accelMagnitude, 0.0001f)
        assertEquals(0f, tracker.state.gyroMagnitude, 0.0001f)
    }

    @Test
    fun gyroReading_updatesGyroMagnitudeOnly() {
        val tracker = newTracker()
        tracker.onGyroReading(1f, 0f, 0f)
        assertEquals(0f, tracker.state.accelMagnitude, 0.0001f)
        assertEquals(1f, tracker.state.gyroMagnitude, 0.0001f)
    }

    @Test
    fun accelReadings_feedStabilityGate() {
        val tracker = newTracker(threshold = 0.5f, requiredSamples = 3)
        tracker.onAccelReading(0.1f, 0f, 0f)
        tracker.onAccelReading(0.05f, 0.1f, 0f)
        tracker.onAccelReading(0.1f, 0f, 0.1f)
        assertTrue("Three consecutive low-accel samples must trigger stability", tracker.isStable())
    }

    @Test
    fun gyroReadings_doNotFeedStabilityGate() {
        // Per design: stability gate watches accelerometer only (linear motion).
        // Gyro is logged for diagnostic correlation but doesn't gate decode.
        val tracker = newTracker(threshold = 0.5f, requiredSamples = 3)
        tracker.onGyroReading(0.1f, 0f, 0f)
        tracker.onGyroReading(0.1f, 0f, 0f)
        tracker.onGyroReading(0.1f, 0f, 0f)
        assertFalse("Stability gate must NOT be fed by gyro samples", tracker.isStable())
    }

    @Test
    fun motionAboveThreshold_breaksStability() {
        val tracker = newTracker(threshold = 0.5f, requiredSamples = 2)
        tracker.onAccelReading(0.1f, 0f, 0f)
        tracker.onAccelReading(0.1f, 0f, 0f)
        assertTrue(tracker.isStable())
        tracker.onAccelReading(3f, 0f, 0f)  // jolt
        assertFalse("Single above-threshold accel sample must break stability", tracker.isStable())
    }

    @Test
    fun stateExposes_latestMagnitudes_acrossUpdates() {
        val tracker = newTracker()
        tracker.onAccelReading(3f, 4f, 0f)
        tracker.onGyroReading(1f, 2f, 2f)
        assertEquals(5f, tracker.state.accelMagnitude, 0.0001f)
        assertEquals(3f, tracker.state.gyroMagnitude, 0.0001f)  // sqrt(1+4+4) = 3
        // Subsequent accel reading replaces, gyro persists
        tracker.onAccelReading(0f, 0f, 0f)
        assertEquals(0f, tracker.state.accelMagnitude, 0.0001f)
        assertEquals(3f, tracker.state.gyroMagnitude, 0.0001f)
    }
}
