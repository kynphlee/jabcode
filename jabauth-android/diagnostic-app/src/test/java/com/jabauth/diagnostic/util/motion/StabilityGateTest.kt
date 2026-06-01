package com.jabauth.diagnostic.util.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD tests for StabilityGate — the pure motion-throttling state machine.
 *
 * The gate accepts motion-magnitude samples and reports whether the device
 * has been stable for N consecutive samples below threshold. Used by
 * MotionThrottle to decide whether to allow a decode attempt.
 *
 * Per Bayesian Council session bc-2026-06-01-05 (Historian round):
 * stability gate is the load-bearing throttling mechanism. Skip-on-motion
 * (#1) is a degenerate case of stability gate with N=1.
 */
class StabilityGateTest {

    @Test
    fun newGate_isNotStable() {
        val gate = StabilityGate(threshold = 0.5f, requiredConsecutiveSamples = 3)
        assertFalse("New gate with no samples must not report stable", gate.isStable())
    }

    @Test
    fun oneLowSample_isNotStable() {
        val gate = StabilityGate(threshold = 0.5f, requiredConsecutiveSamples = 3)
        gate.addSample(0.1f)
        assertFalse("Single low sample must not satisfy N=3 requirement", gate.isStable())
    }

    @Test
    fun threeConsecutiveLowSamples_isStable() {
        val gate = StabilityGate(threshold = 0.5f, requiredConsecutiveSamples = 3)
        gate.addSample(0.1f)
        gate.addSample(0.2f)
        gate.addSample(0.05f)
        assertTrue("Three consecutive low samples must report stable", gate.isStable())
    }

    @Test
    fun highSample_resetsCounter() {
        val gate = StabilityGate(threshold = 0.5f, requiredConsecutiveSamples = 3)
        gate.addSample(0.1f)
        gate.addSample(0.2f)
        gate.addSample(2.0f)  // above threshold — should reset
        assertFalse("High sample must reset the consecutive counter", gate.isStable())
        gate.addSample(0.1f)
        gate.addSample(0.1f)
        assertFalse("Only 2 consecutive lows after reset — not yet stable", gate.isStable())
        gate.addSample(0.1f)
        assertTrue("3 consecutive lows after reset — stable again", gate.isStable())
    }

    @Test
    fun sampleAtExactThreshold_isHigh() {
        // Boundary condition: sample == threshold should count as motion (conservative).
        val gate = StabilityGate(threshold = 0.5f, requiredConsecutiveSamples = 2)
        gate.addSample(0.5f)
        gate.addSample(0.5f)
        assertFalse("Samples AT threshold are not 'below' — must reset", gate.isStable())
    }

    @Test
    fun stabilityCountReflectsCurrentRun() {
        val gate = StabilityGate(threshold = 0.5f, requiredConsecutiveSamples = 3)
        assertEquals(0, gate.consecutiveLowSamples)
        gate.addSample(0.1f)
        assertEquals(1, gate.consecutiveLowSamples)
        gate.addSample(0.2f)
        assertEquals(2, gate.consecutiveLowSamples)
        gate.addSample(2.0f)  // reset
        assertEquals(0, gate.consecutiveLowSamples)
        gate.addSample(0.1f)
        assertEquals(1, gate.consecutiveLowSamples)
    }

    @Test
    fun skipOnMotion_equivalentToN1() {
        // Stability gate with N=1 is the degenerate "skip-on-motion" case.
        val gate = StabilityGate(threshold = 0.5f, requiredConsecutiveSamples = 1)
        gate.addSample(2.0f)
        assertFalse(gate.isStable())
        gate.addSample(0.1f)
        assertTrue("With N=1, single low sample is enough", gate.isStable())
        gate.addSample(2.0f)
        assertFalse("Single high sample again disables", gate.isStable())
    }

    @Test
    fun requireZeroSamplesIsNotPermitted() {
        // Guard against invalid configuration — N=0 would always be "stable".
        try {
            StabilityGate(threshold = 0.5f, requiredConsecutiveSamples = 0)
            org.junit.Assert.fail("requiredConsecutiveSamples=0 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun negativeThresholdIsNotPermitted() {
        try {
            StabilityGate(threshold = -0.1f, requiredConsecutiveSamples = 3)
            org.junit.Assert.fail("Negative threshold must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
