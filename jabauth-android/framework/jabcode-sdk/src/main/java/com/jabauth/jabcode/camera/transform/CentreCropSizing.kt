package com.jabauth.jabcode.camera.transform

/**
 * How large a preview surface must be to fill its parent without distorting.
 *
 * ## Why filling, and not fitting
 *
 * A letterboxed preview would be simpler and is what most camera samples do. It cannot be used
 * here: the reticle publishes its rectangle as a fraction of the VIEW, and the analyzer maps that
 * fraction onto the sensor frame. Black bars would make part of the view correspond to no frame
 * at all, so a reticle dragged into a bar would crop a region the camera never saw — and the
 * failure would look like a decoder that stopped working rather than a layout mistake.
 *
 * So the surface is sized to cover the parent on both axes and the parent clips the overflow,
 * which is what the `TextureView` matrix achieved with `maxOf(scaleX, scaleY)`.
 *
 * ## Why this is arithmetic and not a matrix
 *
 * The same result used to be a `Matrix` handed to `TextureView.setTransform`, which could only be
 * exercised with a view, a window and a camera attached. Expressed as sizes it is a pure
 * function, so the cases that actually break — a rotation that swaps the axes, a parent narrower
 * than the buffer, a zero-sized parent during the first layout pass — are testable at the desk.
 */
object CentreCropSizing {

    /** A measured size in pixels. */
    data class Size(val width: Int, val height: Int)

    /**
     * The smallest size with [aspect] that covers a [parentWidth] x [parentHeight] box.
     *
     * @param aspect width divided by height, ALREADY accounting for rotation — see
     *   [aspectFor]. Passing the unrotated sensor aspect in portrait is the mistake this
     *   parameter's name exists to prevent.
     */
    fun cover(parentWidth: Int, parentHeight: Int, aspect: Float): Size {
        // A parent with no area happens on the first layout pass, before constraints are known.
        // Returning it unchanged lets the view measure to nothing and be re-measured, rather than
        // dividing by zero or inventing a size that briefly flashes on screen.
        if (parentWidth <= 0 || parentHeight <= 0 || aspect <= 0f) {
            return Size(parentWidth.coerceAtLeast(0), parentHeight.coerceAtLeast(0))
        }
        val parentAspect = parentWidth.toFloat() / parentHeight
        return if (parentAspect < aspect) {
            // Parent is the narrower shape: match its height and overflow horizontally.
            Size(Math.round(parentHeight * aspect), parentHeight)
        } else {
            Size(parentWidth, Math.round(parentWidth / aspect))
        }
    }

    /**
     * The aspect to display a [bufferWidth] x [bufferHeight] camera buffer at.
     *
     * The camera always writes in sensor orientation. When the display sits at 90 or 270 degrees
     * relative to the sensor the axes swap on screen, so the aspect inverts. Nothing rotates the
     * pixels here — a `SurfaceView`'s buffer transform is the compositor's job — but the size the
     * view asks for has to anticipate the result, or a correctly rotated preview is stretched into
     * the wrong shape.
     *
     * @param relativeRotationDegrees the value published to the analyzer, 0/90/180/270.
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
}
