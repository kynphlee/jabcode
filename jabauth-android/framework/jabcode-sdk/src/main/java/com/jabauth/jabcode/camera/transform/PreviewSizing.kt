package com.jabauth.jabcode.camera.transform

/**
 * The preview's geometry: how large it draws, and how the reticle's view coordinates map onto
 * the sensor frame. One object because they are two halves of the same decision — a display
 * choice made here without updating the mapping silently mis-aims the decoder.
 *
 * ## Why the preview FITS its parent instead of covering it
 *
 * It used to centre-crop-fill (`cover`), which shows a different slice of the sensor per
 * orientation — a tall 9:19.5 crop in portrait, a wide one in landscape — so rotating the phone
 * visibly JUMPED between two framings. An attempt to animate that jump made it worse: the
 * intermediate aspects match neither orientation, so the animation was 800ms of visible
 * distortion. Both were confirmed by watching frame extracts, not by statistics.
 *
 * The platform camera, watched the same way, does neither. Its chrome flips in a single frame
 * and its preview shows the SAME sensor extent in both orientations, so there is no framing
 * change to hide and the world in the viewfinder stays continuous. This object now does what it
 * does: [fit] shows the whole frame, letterboxed, identical extent in both orientations.
 * Rotation becomes a non-event — the bars swap sides and nothing else changes.
 *
 * A side effect that suits a scanner: the portrait field of view WIDENS, because the full 16:9
 * frame is visible instead of a 9:19.5 crop of it.
 *
 * ## The mapping half
 *
 * The reticle publishes its rectangle as fractions of the VIEW. Under cover those mapped onto a
 * crop of the frame; under fit the frame occupies a centred sub-rect of the view and the bars
 * correspond to no frame at all. [frameRectFor] is that un-letterboxing, and it is the half that
 * mis-aims the decoder if it drifts from [fit] — which is why they live in one file with the
 * tests asserting them against each other.
 */
object PreviewSizing {

    /** A measured size in pixels. */
    data class Size(val width: Int, val height: Int)

    /**
     * The largest size with [aspect] that fits INSIDE a [parentWidth] x [parentHeight] box.
     *
     * @param aspect width over height, ALREADY accounting for rotation — see [aspectFor].
     */
    fun fit(parentWidth: Int, parentHeight: Int, aspect: Float): Size {
        // No area yet (first layout pass) or no aspect yet (camera not open): echo the parent
        // rather than divide by zero or invent a size that flashes before the real one.
        if (parentWidth <= 0 || parentHeight <= 0 || aspect <= 0f) {
            return Size(parentWidth.coerceAtLeast(0), parentHeight.coerceAtLeast(0))
        }
        val parentAspect = parentWidth.toFloat() / parentHeight
        return if (parentAspect < aspect) {
            // Parent is the narrower shape: the width binds, bars top and bottom.
            Size(parentWidth, Math.round(parentWidth / aspect))
        } else {
            Size(Math.round(parentHeight * aspect), parentHeight)
        }
    }

    /**
     * The aspect to display a [bufferWidth] x [bufferHeight] camera buffer at.
     *
     * The camera writes in sensor orientation; at 90/270 the axes swap on screen. Nothing here
     * rotates pixels — the compositor does that — but the size the view asks for has to
     * anticipate the result.
     */
    fun aspectFor(bufferWidth: Int, bufferHeight: Int, relativeRotationDegrees: Int): Float {
        if (bufferWidth <= 0 || bufferHeight <= 0) return 0f
        val swapped = Math.floorMod(relativeRotationDegrees, 180) != 0
        return if (swapped) {
            bufferHeight.toFloat() / bufferWidth
        } else {
            bufferWidth.toFloat() / bufferHeight
        }
    }

    /**
     * Map a rectangle given in VIEW fractions onto FRAME fractions, under [fit] letterboxing.
     *
     * @param viewAspect the reticle container's width over height.
     * @param frameAspect the displayed frame's width over height (after rotation — the same
     *   number [fit] was called with).
     * @return `[left, top, right, bottom]` as fractions of the frame, clamped to it — or null
     *   when the rectangle lies entirely in the bars and addresses no frame at all. Callers
     *   treat null as "no usable ROI" and fall back to the full frame; inventing a sliver at
     *   the frame's edge would aim the decoder at content the user never framed.
     *
     * When the aspects are equal there are no bars and this is the identity — asserted in the
     * tests, because that is the case every screen-shaped device exercises constantly.
     */
    fun frameRectFor(
        viewAspect: Float,
        frameAspect: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): FloatArray? {
        if (viewAspect <= 0f || frameAspect <= 0f) return null

        // The frame's centred sub-rect of the view, in view fractions.
        val frameW: Float
        val frameH: Float
        if (frameAspect > viewAspect) {
            // Frame relatively wider than the view: spans full width, bars top and bottom.
            frameW = 1f
            frameH = viewAspect / frameAspect
        } else {
            frameW = frameAspect / viewAspect
            frameH = 1f
        }
        val x0 = (1f - frameW) / 2f
        val y0 = (1f - frameH) / 2f

        // Un-letterbox, then clamp to the frame.
        val l = ((left - x0) / frameW).coerceIn(0f, 1f)
        val t = ((top - y0) / frameH).coerceIn(0f, 1f)
        val r = ((right - x0) / frameW).coerceIn(0f, 1f)
        val b = ((bottom - y0) / frameH).coerceIn(0f, 1f)
        if (r - l <= 0f || b - t <= 0f) return null
        return floatArrayOf(l, t, r, b)
    }
}
