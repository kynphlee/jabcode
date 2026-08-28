package com.jabauth.ui.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source tripwires for the preview's two load-bearing orderings and its sizing mode.
 *
 * These are scans, not layout tests — catching them properly needs a Compose harness this module
 * does not have. Each one exists because the mistake it guards was actually shipped, looked like
 * a stall or a skew on the device, and gave no hint of the line that caused it.
 */
class PreviewSizingContractTest {

    private val source = File("src/main/java/com/jabauth/ui/scanner/Camera2Preview.kt")

    @Test
    fun `the source under test was found`() {
        // Without this a wrong path makes every scan below pass by scanning nothing.
        assertTrue("Camera2Preview.kt is not where this test expects it", source.isFile())
    }

    /**
     * The preview buffer size is set exactly once, where the SurfaceView is constructed.
     *
     * Android answers a buffer-geometry change by RECREATING the surface. A second call site —
     * the original bug had two, both reached from surfaceCreated — re-fires surfaceDestroyed/
     * surfaceCreated on every layout pass, tearing down the capture session each time. It
     * presents as a rotation stall, not as a fault in the offending line.
     */
    @Test
    fun `the preview buffer size is set exactly once`() {
        val calls = source.readText().lines().withIndex()
            .filter { (_, l) -> l.contains("setFixedSize(") && !l.trimStart().startsWith("//") }
            .map { (i, l) -> "L${i + 1}: ${l.trim()}" }
        assertEquals(
            "setFixedSize must be called exactly once, in the SurfaceView factory.\n" +
                calls.joinToString("\n"),
            1, calls.size,
        )
    }

    /** ...and before the callback that consumes the surface is registered. */
    @Test
    fun `the buffer size is pinned before the surface callback is registered`() {
        val text = source.readText()
        val fixed = text.indexOf("setFixedSize(")
        val callback = text.indexOf("holder.addCallback(")
        assertTrue("could not locate both setFixedSize and addCallback", fixed > 0 && callback > 0)
        assertTrue(
            "setFixedSize runs after addCallback, so the first surfaceCreated fires against a " +
                "default buffer and the geometry changes underneath it",
            fixed < callback,
        )
    }

    /**
     * The preview is sized with PreviewSizing.fit — the whole frame, letterboxed, the same
     * sensor extent in both orientations.
     *
     * The two designs this replaces both shipped and both failed on watched frame extracts:
     * cover-fill jumped between a portrait crop and a landscape crop on every rotation, and
     * animating that jump smeared wrong-aspect distortion across 800ms. Reintroducing either
     * looks like an innocent sizing tweak here and like a framing glitch on the device — and it
     * silently invalidates the reticle mapping in cropToRoi, which assumes fit's letterbox.
     */
    @Test
    fun `the preview is sized by fit, not cover and not an animated aspect`() {
        val text = source.readText()
        assertTrue(
            "PreviewSizing.fit( is gone from Camera2Preview — if the sizing mode is changing " +
                "on purpose, cropToRoi's un-letterbox mapping must change with it",
            text.contains("PreviewSizing.fit("),
        )
        assertTrue(
            "an animated aspect is back in the preview; the last one rendered as 800ms of " +
                "distortion because intermediate aspects match neither orientation",
            !text.contains("animateFloatAsState"),
        )
    }
}
