package com.jabauth.jabcode.camera.transform

/**
 * Tells the analyzer which way up the frames are, and does so only when the answer changes.
 *
 * ## Why this is separate from the preview's display transform
 *
 * These two things lived in one function called `updateTransform`, which read as though its whole
 * job were making the picture look right. It was not: the same function silently owned the crop
 * the DECODER uses. Publishing the wrong rotation does not smudge the preview, it maps the
 * reticle onto the wrong region of the sensor frame and the scanner stops reading — a failure
 * with no visible cause on screen.
 *
 * Two consequences of the tangle worth naming, because they are the reason for this class:
 *
 *  - the rotation contract could not be tested. The publication lived inside a method taking a
 *    `TextureView`, so exercising it needed a view, a window and a camera;
 *  - it could not be reasoned about separately. Anyone changing how the preview is displayed —
 *    which is precisely what a move to `SurfaceView` is — had to notice they were also editing
 *    the decoder's input, from a function whose name gave no hint of it.
 *
 * The rotation ARITHMETIC is not here; that is [OrientationCalculator], which this leaves alone.
 * This owns only the question of when to speak.
 *
 * ## The de-duplication is the point
 *
 * A display change often leaves the camera-relative rotation untouched — rotating a phone
 * 180 degrees is the common case, and so is any change on a device whose sensor orientation
 * happens to cancel it out. Re-publishing an unchanged value would rebuild the analyzer's crop
 * for nothing, on every display event.
 *
 * Not thread-safe by design: it is driven from the surface-callback thread that owns the preview,
 * and adding synchronisation would suggest otherwise.
 */
class FrameRotationPublisher(private val onChange: (Int) -> Unit) {

    /**
     * Deliberately not 0. Zero is a legitimate rotation — the common one, in fact — so seeding
     * with it would swallow the first publication on any device already at 0 degrees and leave
     * the analyzer holding whatever default it started with.
     */
    private var last: Int = UNSET

    /** The last rotation published, or [UNSET] if nothing has been. Exposed for assertions. */
    val lastPublished: Int get() = last

    /** Publish [rotation] if it differs from the last one. Returns true when it was published. */
    fun publish(rotation: Int): Boolean {
        if (rotation == last) return false
        last = rotation
        onChange(rotation)
        return true
    }

    companion object {
        /** No rotation has been published. Outside the 0/90/180/270 the calculator can return. */
        const val UNSET: Int = -1
    }
}
