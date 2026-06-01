package com.jabauth.diagnostic.util.motion

/**
 * Stability gate — pure motion-throttling state machine.
 *
 * Accepts motion-magnitude samples and reports whether the device has been
 * stable for [requiredConsecutiveSamples] consecutive samples STRICTLY below
 * [threshold]. The "strictly below" semantics are conservative — a sample
 * exactly at threshold counts as motion (resets the counter).
 *
 * Bayesian Council session bc-2026-06-01-05 (Historian round): this is the
 * load-bearing throttling primitive. The "skip-on-motion" pattern is a
 * degenerate case of stability gate with requiredConsecutiveSamples=1.
 * Apple VisionKit's CMMotionManager stability gate, MLKit's frame-skip
 * heuristic, and Microsoft Power Apps' stability score all reduce to this
 * shape: count of consecutive low-motion samples.
 *
 * Threshold defaults reflect handheld-scanning practice: ~0.5 m/s² for
 * linear-acceleration magnitude is the typical "user has stopped moving"
 * boundary. Tune per device IMU noise floor.
 *
 * Thread-safety: NOT thread-safe. Caller must synchronise externally OR
 * confine instances to a single thread (typical: the camera analyzer thread
 * which is single-consumer).
 */
class StabilityGate(
    val threshold: Float,
    val requiredConsecutiveSamples: Int
) {
    init {
        require(requiredConsecutiveSamples >= 1) {
            "requiredConsecutiveSamples must be >= 1, got $requiredConsecutiveSamples"
        }
        require(threshold >= 0f) {
            "threshold must be non-negative, got $threshold"
        }
    }

    /** Current count of consecutive samples below threshold. Resets on any sample >= threshold. */
    var consecutiveLowSamples: Int = 0
        private set

    /**
     * Record a new motion-magnitude sample. Samples strictly below threshold
     * increment the consecutive counter; samples at or above threshold reset
     * it to zero.
     */
    fun addSample(magnitude: Float) {
        if (magnitude < threshold) {
            consecutiveLowSamples++
        } else {
            consecutiveLowSamples = 0
        }
    }

    /**
     * @return true iff the gate has accepted [requiredConsecutiveSamples]
     * consecutive samples strictly below threshold since the last reset.
     */
    fun isStable(): Boolean = consecutiveLowSamples >= requiredConsecutiveSamples
}
