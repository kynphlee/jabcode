package com.jabauth.diagnostic.ui.scanner

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the reticle's pure geometry — the part that has to hold once
 * the scanner is free to rotate.
 *
 * Plain JUnit: `Rect` and `IntSize` are pure Kotlin value classes, no Android
 * runtime involved.
 */
class RoiReticleGeometryTest {

    // A tall phone and the same phone on its side, minus the bottom nav bar.
    private val portrait = IntSize(1080, 2130)
    private val landscape = IntSize(2340, 870)

    private fun Rect.fitsIn(box: IntSize): Boolean =
        left >= -0.5f && top >= -0.5f &&
            right <= box.width + 0.5f && bottom <= box.height + 0.5f

    // --- defaultRoi ---------------------------------------------------------

    @Test
    fun `default reticle is square and centred in portrait`() {
        val roi = defaultRoi(portrait)

        assertThat(roi.width).isWithin(0.01f).of(roi.height)
        assertThat(roi.center.x).isWithin(0.01f).of(portrait.width / 2f)
        assertThat(roi.center.y).isWithin(0.01f).of(portrait.height / 2f)
    }

    @Test
    fun `default reticle fits inside a landscape container`() {
        // The regression: sizing off the container WIDTH gave 0.82 * 2340 =
        // 1918 px of height in an 870 px-tall box, putting the top edge ~524 px
        // off-screen.
        val roi = defaultRoi(landscape)

        assertThat(roi.fitsIn(landscape)).isTrue()
        assertThat(roi.top).isAtLeast(0f)
    }

    @Test
    fun `default reticle is sized off the short edge in both orientations`() {
        assertThat(defaultRoi(portrait).width).isWithin(0.5f).of(1080 * 0.82f)
        assertThat(defaultRoi(landscape).width).isWithin(0.5f).of(870 * 0.82f)
    }

    @Test
    fun `default reticle fits a square container`() {
        val square = IntSize(1000, 1000)

        assertThat(defaultRoi(square).fitsIn(square)).isTrue()
    }

    // --- refitRoi -----------------------------------------------------------

    @Test
    fun `refit keeps a rotated reticle inside the new container`() {
        val refitted = refitRoi(defaultRoi(portrait), portrait, landscape)

        assertThat(refitted.fitsIn(landscape)).isTrue()
    }

    @Test
    fun `refit preserves the size relative to the short edge`() {
        // A reticle the user shrank to half the default.
        val small = defaultRoi(portrait).let {
            Rect(it.center.x - it.width / 4f, it.center.y - it.height / 4f,
                 it.center.x + it.width / 4f, it.center.y + it.height / 4f)
        }
        val scale = 870f / 1080f

        val refitted = refitRoi(small, portrait, landscape)

        assertThat(refitted.width).isWithin(0.5f).of(small.width * scale)
        assertThat(refitted.height).isWithin(0.5f).of(small.height * scale)
    }

    @Test
    fun `refit re-centres on the new container`() {
        val refitted = refitRoi(defaultRoi(portrait), portrait, landscape)

        assertThat(refitted.center.x).isWithin(0.01f).of(landscape.width / 2f)
        assertThat(refitted.center.y).isWithin(0.01f).of(landscape.height / 2f)
    }

    @Test
    fun `refit clamps a reticle that is still too large for the new container`() {
        // A user-stretched, nearly full-width portrait reticle.
        val wide = Rect(20f, 800f, 1060f, 1400f)

        val refitted = refitRoi(wide, portrait, landscape)

        assertThat(refitted.fitsIn(landscape)).isTrue()
    }

    @Test
    fun `refit round-trips back to roughly the original after two rotations`() {
        val original = defaultRoi(portrait)

        val there = refitRoi(original, portrait, landscape)
        val back = refitRoi(there, landscape, portrait)

        assertThat(back.width).isWithin(1f).of(original.width)
        assertThat(back.height).isWithin(1f).of(original.height)
    }

    @Test
    fun `refit from a degenerate container falls back to the default`() {
        val refitted = refitRoi(defaultRoi(portrait), IntSize.Zero, landscape)

        assertThat(refitted).isEqualTo(defaultRoi(landscape))
    }
}
