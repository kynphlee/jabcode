package com.jabauth.jabcode.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * The scene statistics behind the scanner's exposure decisions.
 *
 * These had no tests at all, which is how a metric that saturated to a constant on every real
 * image survived — while costing 451ms per frame, 76% of the scanner's budget, against 52ms for
 * the decode it was assisting.
 *
 * The question these answer is not "is the arithmetic the same as before" — it cannot be, because
 * the previous version could not be afforded. It is "does sampling still answer the question the
 * caller actually asks", which is whether exposure has shifted by a factor of 1.6.
 */
class QualityMathTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun flat(w: Int, h: Int, v: Int) = IntArray(w * h) { argb(v, v, v) }

    /** A vertical ramp: dark at the top, bright at the bottom. */
    private fun ramp(w: Int, h: Int) = IntArray(w * h) { i ->
        val v = (i / w) * 255 / (h - 1)
        argb(v, v, v)
    }

    /** Alternating light and dark blocks — the closest thing here to a symbol. */
    private fun checker(w: Int, h: Int, cell: Int) = IntArray(w * h) { i ->
        val on = ((i % w) / cell + (i / w) / cell) % 2 == 0
        val v = if (on) 235 else 20
        argb(v, v, v)
    }

    // ── the property that matters: sampling preserves the answer ──────────────────────────────

    /**
     * The whole justification for sampling. Brightness and contrast from a 1-in-10 grid must
     * match a full walk closely enough that a 1.6x exposure comparison reaches the same verdict.
     */
    @Test
    fun `sampling agrees with a full scan on the metrics that are consumed`() {
        val w = 400
        val h = 400
        for ((name, px) in listOf("ramp" to ramp(w, h), "checker" to checker(w, h, 8))) {
            val full = QualityMath.analyse(px, w, h, 1)
            val sampled = QualityMath.analyse(px, w, h, 10)
            assertEquals("$name brightness drifted under sampling", full.brightness, sampled.brightness, 4f)
            assertEquals("$name contrast drifted under sampling", full.contrast, sampled.contrast, 6f)
        }
    }

    /**
     * Stated as the decision the value actually feeds, not as a tolerance in the abstract: the AE
     * re-arm fires when brightness moves by 1.6x, so sampling must not move it anywhere near that.
     */
    @Test
    fun `sampling never moves brightness far enough to flip the AE decision`() {
        val w = 320
        val h = 320
        val rng = Random(7)
        repeat(20) {
            val px = IntArray(w * h) { val v = rng.nextInt(256); argb(v, v, v) }
            val full = QualityMath.analyse(px, w, h, 1).brightness
            val sampled = QualityMath.analyse(px, w, h, 10).brightness
            val ratio = if (full > 0f) sampled / full else 1f
            assertTrue(
                "sampled brightness was ${"%.3f".format(ratio)}x the full scan — the AE re-arm " +
                    "threshold is 1.6x, so this is uncomfortably close",
                abs(ratio - 1f) < 0.1f,
            )
        }
    }

    // ── the metrics themselves ────────────────────────────────────────────────────────────────

    @Test
    fun `a flat image has no contrast and no edges`() {
        val r = QualityMath.analyse(flat(100, 100, 128), 100, 100, 10)
        assertEquals(128f, r.brightness, 1f)
        assertEquals("a uniform field has zero spread", 0f, r.contrast, 0.01f)
        assertEquals("a uniform field has no edge energy", 0f, r.focusVariance, 0.01f)
    }

    @Test
    fun `brightness tracks the scene`() {
        assertEquals(0f, QualityMath.analyse(flat(50, 50, 0), 50, 50, 5).brightness, 0.5f)
        assertEquals(255f, QualityMath.analyse(flat(50, 50, 255), 50, 50, 5).brightness, 0.5f)
        assertEquals(128f, QualityMath.analyse(flat(50, 50, 128), 50, 50, 5).brightness, 0.5f)
    }

    /** Luminosity is weighted, not a flat average — green must count for more than blue. */
    @Test
    fun `brightness uses perceived luminosity, not a flat mean`() {
        val green = QualityMath.analyse(IntArray(100) { argb(0, 200, 0) }, 10, 10, 1).brightness
        val blue = QualityMath.analyse(IntArray(100) { argb(0, 0, 200) }, 10, 10, 1).brightness
        assertTrue("green should read brighter than blue at equal intensity", green > blue * 3)
    }

    @Test
    fun `contrast separates a flat field from a checkerboard`() {
        val flatC = QualityMath.analyse(flat(200, 200, 128), 200, 200, 10).contrast
        val checkC = QualityMath.analyse(checker(200, 200, 20), 200, 200, 10).contrast
        assertTrue("a checkerboard must read as higher contrast than a flat field", checkC > flatC + 50f)
    }

    @Test
    fun `edge energy separates a blurred ramp from hard edges`() {
        val rampF = QualityMath.analyse(ramp(200, 200), 200, 200, 10).focusVariance
        val checkF = QualityMath.analyse(checker(200, 200, 20), 200, 200, 10).focusVariance
        assertTrue("hard edges must carry more edge energy than a smooth gradient", checkF > rampF)
    }

    /**
     * The bug this file exists to make visible.
     *
     * `ImageQualityAnalyzer` normalises focus as `variance / 100` and clamps to 0..1. A squared
     * Laplacian variance on any real scene runs to thousands, so the published `focus` has been
     * pinned at exactly 1.0 since it was written — which is what the device showed: `focus=1.0`
     * on every frame, forever.
     *
     * This asserts the RAW value is large, documenting why the normalised one is useless. It is
     * deliberately not a fix: the threshold needs calibrating against field decode rates, which is
     * a different piece of work, and inventing a constant here would replace a visible constant
     * with an invisible guess.
     */
    @Test
    fun `raw edge energy dwarfs the normalisation constant, which is why focus reads 1_0`() {
        val v = QualityMath.analyse(checker(200, 200, 20), 200, 200, 10).focusVariance
        assertTrue(
            "raw variance was $v; the analyzer divides by 100 and clamps, so anything above " +
                "100 publishes as exactly 1.0",
            v > 100f,
        )
    }

    // ── degenerate inputs ─────────────────────────────────────────────────────────────────────

    @Test
    fun `empty and zero-sized inputs return zeroes rather than dividing by nothing`() {
        assertEquals(0f, QualityMath.analyse(IntArray(0), 0, 0, 10).brightness, 0f)
        assertEquals(0f, QualityMath.analyse(flat(10, 10, 200), 0, 10, 10).brightness, 0f)
        assertEquals(0f, QualityMath.analyse(flat(10, 10, 200), 10, 0, 10).brightness, 0f)
    }

    /** A step larger than the image must still sample the one pixel it can reach. */
    @Test
    fun `a step larger than the image still produces a reading`() {
        val r = QualityMath.analyse(flat(8, 8, 200), 8, 8, 100)
        assertEquals(200f, r.brightness, 1f)
    }

    @Test
    fun `a step of zero is treated as one rather than looping forever`() {
        val r = QualityMath.analyse(flat(16, 16, 90), 16, 16, 0)
        assertEquals(90f, r.brightness, 1f)
    }
}
