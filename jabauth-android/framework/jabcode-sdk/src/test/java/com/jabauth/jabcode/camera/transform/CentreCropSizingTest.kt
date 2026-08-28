package com.jabauth.jabcode.camera.transform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The preview must cover its parent exactly, never letterbox, and never distort.
 *
 * These three properties used to live in a `Matrix` that only a device could exercise. They are
 * asserted here because the failure they guard against is invisible on screen: a preview that
 * letterboxes still looks like a camera, but the reticle then addresses regions of the view that
 * correspond to no part of the sensor frame, and the scanner stops decoding for no apparent
 * reason.
 */
class CentreCropSizingTest {

    // 1920x1080 is what the preview stream is pinned to.
    private val bufferW = 1920
    private val bufferH = 1080

    private fun assertCovers(parentW: Int, parentH: Int, aspect: Float) {
        val s = CentreCropSizing.cover(parentW, parentH, aspect)
        assertTrue(
            "size ${s.width}x${s.height} leaves a gap in a ${parentW}x$parentH parent",
            s.width >= parentW && s.height >= parentH,
        )
        // Aspect preserved within a pixel of rounding.
        val got = s.width.toFloat() / s.height
        assertTrue(
            "aspect distorted: wanted $aspect, got $got",
            kotlin.math.abs(got - aspect) < 0.01f,
        )
    }

    /** The property that matters, over the shapes a phone actually presents. */
    @Test
    fun `the surface always covers the parent, in both orientations`() {
        val portrait = CentreCropSizing.aspectFor(bufferW, bufferH, 90)
        val landscape = CentreCropSizing.aspectFor(bufferW, bufferH, 0)
        // Tall phone, square-ish tablet, wide phone — portrait and landscape each.
        for ((w, h) in listOf(1080 to 2340, 1440 to 3120, 800 to 1280)) {
            assertCovers(w, h, portrait)
            assertCovers(h, w, landscape)
        }
    }

    /**
     * A 9:16 preview in a 19.5:9 window is the case the old matrix existed for.
     *
     * The screen is the NARROWER shape (0.46 against the preview's 0.5625), so covering it means
     * pinning the height and spilling over the sides. The first draft of this test asserted the
     * opposite — width pinned, height overflowing — which is the intuition you get from picturing
     * a wide photo in a tall frame and forgetting that the preview has already been rotated.
     */
    @Test
    fun `a tall screen is covered by spilling over the sides`() {
        val aspect = CentreCropSizing.aspectFor(bufferW, bufferH, 90) // 1080/1920 = 0.5625
        val s = CentreCropSizing.cover(1080, 2340, aspect)
        assertEquals("height is the binding dimension here", 2340, s.height)
        assertTrue("width must exceed the screen so there are no bars", s.width > 1080)
    }

    /** Landscape, where the screen is the WIDER shape, so the spill goes the other way. */
    @Test
    fun `a wide screen is covered by spilling top and bottom`() {
        val aspect = CentreCropSizing.aspectFor(bufferW, bufferH, 0) // 16:9 = 1.778
        val s = CentreCropSizing.cover(2340, 1080, aspect)
        assertEquals("width is the binding dimension here", 2340, s.width)
        assertTrue("height must exceed the screen", s.height > 1080)
    }

    // ── the rotation half ─────────────────────────────────────────────────────────────────────

    /**
     * The mistake this guards: passing the sensor aspect straight through in portrait. The
     * preview would be rotated correctly by the compositor and then stretched into 16:9 in a
     * 9:16 window, which reads as a broken camera rather than a sizing bug.
     */
    @Test
    fun `rotation inverts the aspect, and only at the quarter turns`() {
        assertEquals(1920f / 1080f, CentreCropSizing.aspectFor(bufferW, bufferH, 0), 0.001f)
        assertEquals(1080f / 1920f, CentreCropSizing.aspectFor(bufferW, bufferH, 90), 0.001f)
        assertEquals(1920f / 1080f, CentreCropSizing.aspectFor(bufferW, bufferH, 180), 0.001f)
        assertEquals(1080f / 1920f, CentreCropSizing.aspectFor(bufferW, bufferH, 270), 0.001f)
    }

    /** 360 and beyond, and negatives, must land where their quarter turn does. */
    @Test
    fun `out-of-range rotations normalise rather than falling through`() {
        assertEquals(
            CentreCropSizing.aspectFor(bufferW, bufferH, 90),
            CentreCropSizing.aspectFor(bufferW, bufferH, 450), 0.001f,
        )
        assertEquals(
            CentreCropSizing.aspectFor(bufferW, bufferH, 90),
            CentreCropSizing.aspectFor(bufferW, bufferH, -90), 0.001f,
        )
    }

    // ── the degenerate first layout pass ──────────────────────────────────────────────────────

    /**
     * A parent with no area is the normal state during the first measure, before constraints are
     * known. It must not divide by zero, and must not invent a size that flashes on screen before
     * the real one arrives.
     */
    @Test
    fun `a zero-sized parent is returned unchanged`() {
        assertEquals(CentreCropSizing.Size(0, 0), CentreCropSizing.cover(0, 0, 0.5625f))
        assertEquals(CentreCropSizing.Size(0, 500), CentreCropSizing.cover(0, 500, 0.5625f))
        assertEquals(CentreCropSizing.Size(500, 0), CentreCropSizing.cover(500, 0, 0.5625f))
    }

    /** An unknown aspect — the camera has not opened yet — is not a reason to crash. */
    @Test
    fun `an unset aspect leaves the parent size alone`() {
        assertEquals(CentreCropSizing.Size(1080, 2340), CentreCropSizing.cover(1080, 2340, 0f))
        assertEquals(0f, CentreCropSizing.aspectFor(0, 0, 90), 0.001f)
    }
}
