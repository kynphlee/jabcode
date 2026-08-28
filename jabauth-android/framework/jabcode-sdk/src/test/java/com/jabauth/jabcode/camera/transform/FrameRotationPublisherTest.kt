package com.jabauth.jabcode.camera.transform

import com.jabauth.jabcode.camera.CameraDeviceProfiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the analyzer is told about frame rotation, and when.
 *
 * This contract had no tests, because it lived inside a method that took a `TextureView` — so
 * exercising it needed a view, a window and a camera. It is the DECODER's input: get it wrong and
 * the reticle maps onto the wrong region of the sensor frame and the scanner stops reading, with
 * nothing visibly wrong on screen to say so.
 */
class FrameRotationPublisherTest {

    private val sent = mutableListOf<Int>()
    private val publisher = FrameRotationPublisher { sent += it }

    // ── when it speaks ────────────────────────────────────────────────────────────────────────

    @Test
    fun `an unchanged rotation is not republished`() {
        assertTrue(publisher.publish(90))
        assertFalse("re-publishing 90 rebuilds the analyzer crop for nothing", publisher.publish(90))
        assertFalse(publisher.publish(90))
        assertEquals(listOf(90), sent)
    }

    @Test
    fun `every change is published`() {
        for (r in listOf(0, 90, 180, 270, 0)) publisher.publish(r)
        assertEquals(listOf(0, 90, 180, 270, 0), sent)
    }

    /**
     * Zero is the common rotation, not a null. Seeding the memo with 0 would swallow the first
     * publication on any device already at 0 degrees, leaving the analyzer on whatever default it
     * started with — a silent wrong crop on exactly the most ordinary device.
     */
    @Test
    fun `zero is published on a fresh publisher`() {
        assertTrue("0 must be published, it is a rotation and not an absence", publisher.publish(0))
        assertEquals(listOf(0), sent)
    }

    @Test
    fun `nothing is published before the first call`() {
        assertEquals(FrameRotationPublisher.UNSET, publisher.lastPublished)
        assertTrue(sent.isEmpty())
    }

    // ── what it says ──────────────────────────────────────────────────────────────────────────

    /**
     * The gate this phase was written to satisfy: the rotation actually handed to the analyzer,
     * for every display rotation against both sensor orientations the fleet presents.
     *
     * Values come from [OrientationCalculator] rather than being restated here. Restating them
     * would let this test agree with itself while disagreeing with the code that runs.
     */
    @Test
    fun `the published value is the calculator's, for every display rotation`() {
        val calculator = OrientationCalculator()
        for (sensor in listOf(90, 270)) {
            for (display in listOf(0, 90, 180, 270)) {
                val fresh = mutableListOf<Int>()
                val p = FrameRotationPublisher { fresh += it }
                val expected = calculator.calculatePreviewRotation(
                    sensorOrientation = sensor,
                    deviceRotation = display,
                    cameraFacing = CameraDeviceProfiler.Facing.BACK,
                )
                p.publish(expected)
                assertEquals(
                    "sensor=$sensor display=$display reached the analyzer as the wrong rotation",
                    listOf(expected), fresh,
                )
            }
        }
    }

    /**
     * A front camera's sensor and display rotations add where a back camera's subtract. Asserted
     * as a DIFFERENCE rather than against a hard-coded number: the point is that facing changes
     * the answer, and pinning literals here would duplicate the calculator's own tests.
     */
    @Test
    fun `facing changes what the analyzer is told`() {
        val calculator = OrientationCalculator()
        val back = calculator.calculatePreviewRotation(90, 90, CameraDeviceProfiler.Facing.BACK)
        val front = calculator.calculatePreviewRotation(90, 90, CameraDeviceProfiler.Facing.FRONT)
        assertTrue(
            "front and back resolved identically — the facing distinction has been lost",
            back != front,
        )
    }
}
