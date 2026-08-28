package com.jabauth.jabcode.camera.transform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The preview shows the whole frame in both orientations, and the reticle mapping agrees with it.
 *
 * The two halves are tested against each other on purpose. The display half ([PreviewSizing.fit])
 * and the mapping half ([PreviewSizing.frameRectFor]) encode the same letterbox; if one changes
 * without the other, the decoder is silently aimed at the wrong part of the sensor — a scanner
 * that stops reading with nothing visibly wrong.
 */
class PreviewSizingTest {

    private val bufferW = 1920
    private val bufferH = 1080

    // ── fit: the display half ─────────────────────────────────────────────────────────────────

    /** Never overflows, never distorts, over the shapes phones actually present. */
    @Test
    fun `the surface always fits inside the parent, in both orientations`() {
        val portrait = PreviewSizing.aspectFor(bufferW, bufferH, 90)
        val landscape = PreviewSizing.aspectFor(bufferW, bufferH, 0)
        for ((w, h) in listOf(1080 to 2340, 1440 to 3120, 800 to 1280)) {
            for ((pw, ph, aspect) in listOf(Triple(w, h, portrait), Triple(h, w, landscape))) {
                val s = PreviewSizing.fit(pw, ph, aspect)
                assertTrue("${s.width}x${s.height} overflows ${pw}x$ph", s.width <= pw && s.height <= ph)
                val got = s.width.toFloat() / s.height
                assertTrue("aspect distorted: wanted $aspect got $got", kotlin.math.abs(got - aspect) < 0.01f)
            }
        }
    }

    /**
     * The property the whole change exists for: the SAME sensor extent is visible in portrait
     * and in landscape. Under the old cover sizing, portrait showed a 9:19.5 crop of the frame
     * and landscape a different one, so rotating the phone jumped between framings.
     */
    @Test
    fun `both orientations show the whole frame`() {
        // Portrait: the full rotated frame (9:16) fits a 1080-wide screen at 1080x1920.
        val p = PreviewSizing.fit(1080, 2340, PreviewSizing.aspectFor(bufferW, bufferH, 90))
        assertEquals(PreviewSizing.Size(1080, 1920), p)
        // Landscape: the full 16:9 frame fits a 1080-tall screen at 1920x1080.
        val l = PreviewSizing.fit(2340, 1080, PreviewSizing.aspectFor(bufferW, bufferH, 0))
        assertEquals(PreviewSizing.Size(1920, 1080), l)
    }

    @Test
    fun `degenerate parents and aspects echo the parent rather than dividing by zero`() {
        assertEquals(PreviewSizing.Size(0, 0), PreviewSizing.fit(0, 0, 0.5625f))
        assertEquals(PreviewSizing.Size(0, 500), PreviewSizing.fit(0, 500, 0.5625f))
        assertEquals(PreviewSizing.Size(1080, 2340), PreviewSizing.fit(1080, 2340, 0f))
    }

    // ── aspectFor: carried over unchanged ─────────────────────────────────────────────────────

    @Test
    fun `rotation inverts the aspect, and only at the quarter turns`() {
        assertEquals(1920f / 1080f, PreviewSizing.aspectFor(bufferW, bufferH, 0), 0.001f)
        assertEquals(1080f / 1920f, PreviewSizing.aspectFor(bufferW, bufferH, 90), 0.001f)
        assertEquals(1920f / 1080f, PreviewSizing.aspectFor(bufferW, bufferH, 180), 0.001f)
        assertEquals(1080f / 1920f, PreviewSizing.aspectFor(bufferW, bufferH, 270), 0.001f)
    }

    @Test
    fun `out-of-range rotations normalise rather than falling through`() {
        assertEquals(PreviewSizing.aspectFor(bufferW, bufferH, 90),
            PreviewSizing.aspectFor(bufferW, bufferH, 450), 0.001f)
        assertEquals(PreviewSizing.aspectFor(bufferW, bufferH, 90),
            PreviewSizing.aspectFor(bufferW, bufferH, -90), 0.001f)
        assertEquals(0f, PreviewSizing.aspectFor(0, 0, 90), 0.001f)
    }

    // ── frameRectFor: the mapping half ────────────────────────────────────────────────────────

    /** No bars, no remapping — the case every frame-shaped view exercises constantly. */
    @Test
    fun `equal aspects make the mapping the identity`() {
        val r = PreviewSizing.frameRectFor(1.778f, 1.778f, 0.2f, 0.3f, 0.8f, 0.9f)
        assertNotNull(r)
        assertEquals(0.2f, r!![0], 0.001f)
        assertEquals(0.3f, r[1], 0.001f)
        assertEquals(0.8f, r[2], 0.001f)
        assertEquals(0.9f, r[3], 0.001f)
    }

    /**
     * The portrait worked example, numbers a reviewer can check by hand: view 1080x2340
     * (aspect 0.4615), frame displayed at 0.5625 — the frame spans the full width and occupies
     * the middle 82% of the height, so the view's centre is the frame's centre.
     */
    @Test
    fun `a centred reticle maps to the centre of the frame`() {
        val va = 1080f / 2340f
        val r = PreviewSizing.frameRectFor(va, 0.5625f, 0.3f, 0.3f, 0.7f, 0.7f)
        assertNotNull(r)
        val cx = (r!![0] + r[2]) / 2f
        val cy = (r[1] + r[3]) / 2f
        assertEquals("a view-centred rect must be frame-centred", 0.5f, cx, 0.01f)
        assertEquals(0.5f, cy, 0.01f)
        // And it maps INWARD: the frame occupies less height than the view, so a 40%-tall view
        // rect is a taller-than-40% frame rect.
        assertTrue("mapping must expand fractions on the letterboxed axis", (r[3] - r[1]) > 0.4f)
    }

    /** A rectangle entirely in a bar addresses no frame. Null, not a sliver at the edge. */
    @Test
    fun `a reticle wholly in the letterbox maps to null`() {
        val va = 1080f / 2340f // bars occupy the top ~9% of the view
        assertNull(PreviewSizing.frameRectFor(va, 0.5625f, 0.1f, 0.0f, 0.9f, 0.05f))
    }

    /** A rectangle straddling a bar keeps its in-frame part. */
    @Test
    fun `a reticle straddling the bar clamps to the frame edge`() {
        val va = 1080f / 2340f
        val r = PreviewSizing.frameRectFor(va, 0.5625f, 0.1f, 0.0f, 0.9f, 0.5f)
        assertNotNull(r)
        assertEquals("the part in the bar clamps to the frame's top", 0f, r!![1], 0.001f)
        assertTrue(r[3] > 0f && r[3] < 1f)
    }

    @Test
    fun `degenerate aspects map to null`() {
        assertNull(PreviewSizing.frameRectFor(0f, 0.5625f, 0f, 0f, 1f, 1f))
        assertNull(PreviewSizing.frameRectFor(0.46f, 0f, 0f, 0f, 1f, 1f))
    }

    // ── the two halves agree ──────────────────────────────────────────────────────────────────

    /**
     * The consistency property that justifies one file: a point on the DISPLAYED frame, taken
     * through the view fractions the reticle would publish, lands on the same point of the frame
     * that [PreviewSizing.fit]'s rectangle says is under it. If fit and frameRectFor ever encode
     * different letterboxes, this fails while every per-half test stays green.
     */
    @Test
    fun `fit and frameRectFor describe the same letterbox`() {
        for ((vw, vh, aspect) in listOf(
            Triple(1080, 2340, 0.5625f), Triple(2340, 1080, 1.778f), Triple(1440, 3120, 0.5625f),
        )) {
            val s = PreviewSizing.fit(vw, vh, aspect)
            val x0 = (vw - s.width) / 2f
            val y0 = (vh - s.height) / 2f
            // A quarter of the way into the DISPLAYED frame, expressed as view fractions...
            val vx = (x0 + s.width * 0.25f) / vw
            val vy = (y0 + s.height * 0.25f) / vh
            val r = PreviewSizing.frameRectFor(vw.toFloat() / vh, aspect, vx, vy, vx + 0.01f, vy + 0.01f)
            assertNotNull("${vw}x$vh: point on the frame mapped to null", r)
            // ...must come back as a quarter of the way into the frame.
            assertEquals("${vw}x$vh: x disagrees with fit's rectangle", 0.25f, r!![0], 0.02f)
            assertEquals("${vw}x$vh: y disagrees with fit's rectangle", 0.25f, r[1], 0.02f)
        }
    }
}
