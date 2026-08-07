package com.jabauth.ui.scanner

import android.util.Size
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the two pure decisions `Camera2Preview` makes about geometry:
 * which analysis stream size to ask the camera for, and how far the analysis
 * frame has to be rotated to line up with the display.
 *
 * Robolectric only because `android.util.Size` is a real Android class; nothing
 * here touches a camera.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Camera2PreviewGeometryTest {

    private val sixteenByNine = 1920f / 1080f
    private val fallback = Size(1920, 1080)

    /** A realistic YUV_420_888 output-size list for a modern phone sensor. */
    private val typicalSupported = listOf(
        Size(4080, 3060), Size(4080, 2296), Size(3840, 2160), Size(3264, 2448),
        Size(2560, 1440), Size(1920, 1440), Size(1920, 1080), Size(1440, 1080),
        Size(1280, 720), Size(960, 720), Size(640, 480), Size(320, 240)
    )

    // --- chooseAnalysisSize -------------------------------------------------

    @Test
    fun `picks the largest 16-9 size at or below the width cap`() {
        val chosen = chooseAnalysisSize(typicalSupported, sixteenByNine, 1920, fallback)

        assertThat(chosen).isEqualTo(Size(1920, 1080))
    }

    @Test
    fun `never exceeds the width cap even when larger sizes exist`() {
        // 3840x2160 and 2560x1440 are both supported and both 16:9; the cap is
        // what keeps the analysis stream inside Camera2's guaranteed stream
        // combinations on LIMITED/LEGACY hardware.
        val chosen = chooseAnalysisSize(typicalSupported, sixteenByNine, 1920, fallback)

        assertThat(chosen.width).isAtMost(1920)
    }

    @Test
    fun `raising the cap lets a larger same-aspect size through`() {
        val chosen = chooseAnalysisSize(typicalSupported, sixteenByNine, 2560, fallback)

        assertThat(chosen).isEqualTo(Size(2560, 1440))
    }

    @Test
    fun `holds the preview aspect rather than taking a larger 4-3 size`() {
        // 1920x1440 is bigger in pixel count than 1920x1080 and within the cap.
        // It must still lose: the analyzer's ROI mapping assumes the analysis
        // frame and the preview cover an identical field of view.
        val chosen = chooseAnalysisSize(typicalSupported, sixteenByNine, 1920, fallback)

        assertThat(chosen.height).isEqualTo(1080)
    }

    @Test
    fun `accepts a macroblock-padded near-16-9 size such as 1920x1088`() {
        // 1920x1088 is 1.31% off 16:9. It must still count as a match, or a
        // device that advertises it instead of a clean 1080p would silently
        // fall back to 720p and lose the whole point of the change.
        val chosen = chooseAnalysisSize(
            listOf(Size(1920, 1088), Size(1280, 720)), sixteenByNine, 1920, fallback
        )

        assertThat(chosen).isEqualTo(Size(1920, 1088))
    }

    @Test
    fun `a 4-3 size is never mistaken for the preview aspect`() {
        // 1440x1080 is 25% off 16:9 — outside any sane tolerance — so it can
        // only be reached through the closest-aspect fallback, never the match.
        val chosen = chooseAnalysisSize(
            listOf(Size(1440, 1080), Size(1280, 720)), sixteenByNine, 1920, fallback
        )

        assertThat(chosen).isEqualTo(Size(1280, 720))
    }

    @Test
    fun `falls back to the closest aspect when nothing matches`() {
        val onlyFourThree = listOf(Size(1440, 1080), Size(640, 480), Size(320, 240))

        val chosen = chooseAnalysisSize(onlyFourThree, sixteenByNine, 1920, fallback)

        // All three are 4:3, so aspect distance ties and the largest wins.
        assertThat(chosen).isEqualTo(Size(1440, 1080))
    }

    @Test
    fun `returns the fallback when the camera advertises nothing`() {
        assertThat(chooseAnalysisSize(emptyList(), sixteenByNine, 1920, fallback))
            .isEqualTo(fallback)
    }

    @Test
    fun `returns the fallback when every size is above the cap`() {
        val onlyHuge = listOf(Size(3840, 2160), Size(4080, 3060))

        assertThat(chooseAnalysisSize(onlyHuge, sixteenByNine, 1920, fallback))
            .isEqualTo(fallback)
    }

    @Test
    fun `the negotiated size raises the module ceiling above the 720p one`() {
        // The regression this whole change exists to prevent: a 260x216-module
        // cascade needs ~5 px/module, i.e. >1300 x >1080 frame pixels. 720p
        // cannot express it on either axis.
        val chosen = chooseAnalysisSize(typicalSupported, sixteenByNine, 1920, fallback)

        assertThat(chosen.width / 5).isAtLeast(260)   // 384 modules
        assertThat(chosen.height / 5).isAtLeast(216)  // 216 modules
    }

    // --- relativeFrameRotation ---------------------------------------------

    @Test
    fun `back camera in natural portrait rotates the frame 90 degrees`() {
        assertThat(relativeFrameRotation(90, 0, isFrontFacing = false)).isEqualTo(90)
    }

    @Test
    fun `back camera in landscape needs no rotation`() {
        // This is the case the old formula got wrong: it added the display
        // rotation instead of subtracting it and returned 180 here, flipping
        // the analysis frame and mis-mapping the reticle.
        assertThat(relativeFrameRotation(90, 90, isFrontFacing = false)).isEqualTo(0)
    }

    @Test
    fun `back camera in the opposite landscape inverts the frame`() {
        assertThat(relativeFrameRotation(90, 270, isFrontFacing = false)).isEqualTo(180)
    }

    @Test
    fun `back camera upside down rotates 270 degrees`() {
        assertThat(relativeFrameRotation(90, 180, isFrontFacing = false)).isEqualTo(270)
    }

    @Test
    fun `front camera adds the display rotation instead of subtracting it`() {
        assertThat(relativeFrameRotation(270, 90, isFrontFacing = true)).isEqualTo(0)
        assertThat(relativeFrameRotation(270, 0, isFrontFacing = true)).isEqualTo(270)
    }

    @Test
    fun `result is always a non-negative multiple of 90 below 360`() {
        for (sensor in listOf(0, 90, 180, 270)) {
            for (surface in listOf(0, 90, 180, 270)) {
                for (front in listOf(true, false)) {
                    val r = relativeFrameRotation(sensor, surface, front)
                    assertThat(r).isAtLeast(0)
                    assertThat(r).isLessThan(360)
                    assertThat(r % 90).isEqualTo(0)
                }
            }
        }
    }

    @Test
    fun `agrees with the SDK OrientationCalculator it delegates to`() {
        // Guards the LENS_FACING -> Facing mapping, which is the only thing
        // this wrapper actually adds over the SDK's calculator.
        val calculator = com.jabauth.jabcode.camera.transform.OrientationCalculator()
        for (sensor in listOf(0, 90, 180, 270)) {
            for (surface in listOf(0, 90, 180, 270)) {
                assertThat(relativeFrameRotation(sensor, surface, isFrontFacing = false))
                    .isEqualTo(
                        calculator.calculatePreviewRotation(
                            sensor, surface,
                            com.jabauth.jabcode.camera.CameraDeviceProfiler.Facing.BACK
                        )
                    )
                assertThat(relativeFrameRotation(sensor, surface, isFrontFacing = true))
                    .isEqualTo(
                        calculator.calculatePreviewRotation(
                            sensor, surface,
                            com.jabauth.jabcode.camera.CameraDeviceProfiler.Facing.FRONT
                        )
                    )
            }
        }
    }

    @Test
    fun `the sign fix cannot change whether the preview swaps dimensions`() {
        // updateTransform() branches on relativeRotation % 180. The old and new
        // formulas differ by 2 * surfaceRotationDegrees, which is always 0 mod
        // 180 — so correcting the sign leaves the preview transform untouched.
        for (sensor in listOf(0, 90, 180, 270)) {
            for (surface in listOf(0, 90, 180, 270)) {
                val corrected = relativeFrameRotation(sensor, surface, isFrontFacing = false)
                val old = (sensor + surface) % 360
                assertThat(corrected % 180).isEqualTo(old % 180)
            }
        }
    }
}
