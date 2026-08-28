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
