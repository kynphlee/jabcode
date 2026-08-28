package com.jabauth.ui.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The preview surface must be allowed to be bigger than its parent.
 *
 * ## Why a source scan and not a real test
 *
 * `CentreCropSizing` has seven passing tests and every one of them is correct — it returns
 * 1316x2340 for a 1080x2340 window, and proves that covers without distorting. The bug was
 * entirely in the seam between that number and Compose: `Modifier.size` is subject to the
 * constraints coming down from the parent, so a request to be WIDER than the parent was silently
 * reduced to the parent's own width, and the camera buffer was stretched to fit instead of
 * cropped.
 *
 * Measured on an SM-S918U: the view laid out at exactly 1080x2340, against a computed 1316x2340.
 * Portrait's distortion was mild enough to pass for a normal preview; landscape's was not, so it
 * presented as "skews when I rotate" rather than "is always wrong".
 *
 * Catching that properly needs a Compose layout test with a real measure pass — `ui-test-junit4`
 * plus Robolectric, in a module that has neither. This asserts the one line that matters instead.
 * It is a tripwire, not a substitute: if a Compose harness lands here later, replace it with a
 * test that measures the rendered surface and asserts it exceeds its parent on one axis.
 */
class PreviewSizingContractTest {

    private val source = File("src/main/java/com/jabauth/ui/scanner/Camera2Preview.kt")

    @Test
    fun `the source under test was found`() {
        // Without this a wrong path makes the assertion below pass by scanning nothing.
        assertTrue("Camera2Preview.kt is not where this test expects it", source.isFile())
    }

    /**
     * The preview's own sizing modifier must ignore parent constraints.
     *
     * Scoped to the `AndroidView` that hosts the surface — other views in this file may legitimately
     * want to be constrained, and a blanket ban on `Modifier.size` would be a rule nobody could
     * follow.
     */
    @Test
    fun `the preview surface is sized with requiredSize, which ignores parent constraints`() {
        val text = source.readText()
        val sizingLines = text.lines().withIndex().filter { (_, l) ->
            l.contains("size.width.toDp()") && l.contains("Modifier.")
        }
        assertTrue(
            "no sizing modifier found for the preview surface — has the composable been " +
                "restructured? This guard is now blind and needs rewriting.",
            sizingLines.isNotEmpty(),
        )
        val offenders = sizingLines
            .filter { (_, l) -> !l.contains("requiredSize") }
            .map { (i, l) -> "L${i + 1}: ${l.trim()}" }
        assertEquals(
            "the preview surface is sized with a constraint-respecting modifier. It is\n" +
                "deliberately larger than its parent on one axis, so the parent will clamp it and\n" +
                "the camera buffer will be stretched rather than cropped. Use requiredSize.\n" +
                offenders.joinToString("\n"),
            emptyList<String>(), offenders,
        )
    }

    /**
     * The buffer geometry is pinned once, before the surface exists.
     *
     * Android answers a buffer-size change by RECREATING the surface. Setting it from inside
     * openCamera — which is reached FROM surfaceCreated — therefore fired surfaceDestroyed and
     * surfaceCreated, which called openCamera, which set it again: every layout pass tore down
     * and rebuilt the capture session, and rotating the phone visibly stalled while it did.
     *
     * The count is the assertion. A second call site anywhere reintroduces the loop, and it will
     * look like a stall rather than like a bug in this line.
     */
    @Test
    fun `the preview buffer size is set exactly once`() {
        val calls = source.readText().lines().withIndex()
            .filter { (_, l) -> l.contains("setFixedSize(") && !l.trimStart().startsWith("//") }
            .map { (i, l) -> "L${i + 1}: ${l.trim()}" }
        assertEquals(
            "setFixedSize must be called exactly once, where the SurfaceView is constructed.\n" +
                "Calling it after the surface exists recreates the surface and rebuilds the\n" +
                "capture session on every layout pass.\n" + calls.joinToString("\n"),
            1, calls.size,
        )
    }

    /**
     * And it must run BEFORE the callback that consumes the surface is registered, or the first
     * surfaceCreated still arrives against a default buffer.
     */
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

    /** The overflow only works if something cuts it. */
    @Test
    fun `the parent clips, or the surface spills across the screen`() {
        assertTrue(
            "clipToBounds is gone from the preview's parent — the surface overflows on purpose " +
                "and the overflow must be cut, not drawn",
            source.readText().contains("clipToBounds()"),
        )
    }
}
