package com.jabauth.jabcode.camera

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The pixel arithmetic behind [ImageQualityAnalyzer], over a plain array.
 *
 * ## Why it is not in the analyzer
 *
 * It was, and it could only be exercised with an Android `Bitmap` — so the numbers this produces
 * were never checked by anything. That mattered more than it sounds: one of the three metrics had
 * been saturating to a constant since it was written, and the cost of computing it was 76% of the
 * scanner's per-frame budget. Neither fact is visible without being able to run the maths at a
 * desk over an image you constructed.
 *
 * Everything here takes an ARGB `IntArray` and returns numbers. No Android types.
 *
 * ## Sampling
 *
 * All three metrics walk a grid of every [step]-th pixel. Previously two of them did and the third
 * silently did not, which is where the frame budget went: on a 989x989 crop the sampled metrics
 * touch about 9,800 pixels each and the unsampled one touched 978,121 — and it reached them
 * through `Bitmap.getPixel`, one JNI crossing apiece.
 *
 * These are scene statistics — average luminosity, spread, edge energy. They are used to decide
 * whether the exposure has shifted by more than a factor of 1.6. A grid sample answers that
 * question to far more precision than the decision needs.
 */
internal object QualityMath {

    /** Raw, un-normalised statistics. Normalisation belongs to the caller's thresholds. */
    data class Raw(
        /** Mean perceived luminosity, 0..255. */
        val brightness: Float,
        /** Variance of the Laplacian — edge energy. Unbounded; see [ImageQualityAnalyzer]. */
        val focusVariance: Float,
        /** Standard deviation of grey intensity, 0..255. */
        val contrast: Float,
    )

    /** Perceived luminosity of one ARGB pixel, 0..255. */
    private fun luma(p: Int): Float {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /** Flat grey of one ARGB pixel — the average the focus and contrast passes have always used. */
    private fun grey(p: Int): Int {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return (r + g + b) / 3
    }

    /**
     * All three metrics in ONE pass over the sampled grid.
     *
     * The original made three separate passes — two of them over the same sampled points, for
     * mean and then deviation. Contrast here uses the sum-of-squares identity instead, so the
     * grid is walked once. On the numbers that reach a 1.6x exposure comparison the difference is
     * nil, and it removes a whole traversal.
     *
     * @param step sample every step-th pixel on both axes. 1 walks everything.
     */
    fun analyse(pixels: IntArray, width: Int, height: Int, step: Int): Raw {
        if (width <= 0 || height <= 0 || pixels.isEmpty()) return Raw(0f, 0f, 0f)
        val s = step.coerceAtLeast(1)

        var lumaSum = 0.0
        var greySum = 0.0
        var greySqSum = 0.0
        var n = 0

        var y = 0
        while (y < height) {
            val row = y * width
            var x = 0
            while (x < width) {
                val p = pixels[row + x]
                lumaSum += luma(p)
                val g = grey(p)
                greySum += g
                greySqSum += g.toDouble() * g
                n++
                x += s
            }
            y += s
        }
        if (n == 0) return Raw(0f, 0f, 0f)

        val meanGrey = greySum / n
        // Var(X) = E[X^2] - E[X]^2. coerceAtLeast guards the floating-point case where a flat
        // image makes the two terms differ by a negative epsilon.
        val variance = (greySqSum / n - meanGrey * meanGrey).coerceAtLeast(0.0)

        return Raw(
            brightness = (lumaSum / n).toFloat(),
            focusVariance = laplacianVariance(pixels, width, height, s),
            contrast = sqrt(variance).toFloat(),
        )
    }

    /**
     * Edge energy, as the variance of a Laplacian evaluated on the sampled grid.
     *
     * The neighbours are a full [step] away rather than adjacent, so this measures edge energy at
     * the sampling scale rather than at the pixel scale. That is a different number from the
     * original's per-pixel version — necessarily, since the original could not be afforded — and
     * it is the reason [ImageQualityAnalyzer] does not pretend the two are interchangeable.
     */
    private fun laplacianVariance(pixels: IntArray, width: Int, height: Int, step: Int): Float {
        var sum = 0.0
        var n = 0
        var y = step
        while (y < height - step) {
            var x = step
            while (x < width - step) {
                val c = grey(pixels[y * width + x])
                val l = abs(
                    8 * c
                        - grey(pixels[(y - step) * width + (x - step)])
                        - grey(pixels[(y - step) * width + x])
                        - grey(pixels[(y - step) * width + (x + step)])
                        - grey(pixels[y * width + (x - step)])
                        - grey(pixels[y * width + (x + step)])
                        - grey(pixels[(y + step) * width + (x - step)])
                        - grey(pixels[(y + step) * width + x])
                        - grey(pixels[(y + step) * width + (x + step)])
                )
                sum += l.toDouble() * l
                n++
                x += step
            }
            y += step
        }
        return if (n == 0) 0f else (sum / n).toFloat()
    }
}
