# Camera Configuration Guide

Standard guidance for Android applications that integrate the JABCode SDK, derived from the H_nc2 / H_partI investigation on Galaxy S25 (2026-05-30) and prior camera-control audit work.

## TL;DR for SDK consumers

1. **Prefer hardware level `LEVEL_3`** (then `FULL`, then `LIMITED`) when picking a back-facing camera. Use `CameraEnumerator.findCameraByFacing(LENS_FACING_BACK)` from this SDK — its `maxByOrNull { hardwareLevel }` selection implements the preference.
2. **Lock AWB and AE after convergence.** Start the session with `CONTROL_AWB_MODE = AUTO` and `CONTROL_AE_MODE = ON`, observe `CONTROL_AWB_STATE` and `CONTROL_AE_STATE` in the capture callback, and once both reach `CONVERGED` reissue the repeating request with `CONTROL_AWB_LOCK = true` and `CONTROL_AE_LOCK = true`. Reference implementation: `framework/ui-components/.../Camera2Preview.kt`.
3. **Continuous-picture autofocus is the right default** (`CONTROL_AF_MODE_CONTINUOUS_PICTURE`). JABCode fixtures move with hand-held framing and continuous AF tracks better than triggered single-shot AF.
4. **Apply consistent exposure compensation across devices.** EV=0 by default; allow user adjustment for printed-fixture vs on-screen-fixture lighting differences.

## Why hardware level matters for JABCode

Camera2 classifies camera devices into four tiers (Android source: `INFO_SUPPORTED_HARDWARE_LEVEL_*`):

| Level | Per-frame manual control | RAW capture | YUV reprocessing | Monochrome capability |
|---|---|---|---|---|
| `LEGACY` | No | No | No | No |
| `LIMITED` | No (auto modes only) | No | No | No |
| `FULL` | Yes (exposure, ISO, AWB, AF) | Sometimes | No | No |
| `LEVEL_3` | Yes | Always | Yes (OPAQUE + YUV) | Yes |

For JABCode metadata decoding, the relevant capabilities are:

- **AWB lock + AE lock at the ISP level**: requires `FULL` or `LEVEL_3` per-frame manual control. On `LIMITED`-tier cameras, `CONTROL_AWB_LOCK` is technically settable but the underlying ISP may not honor it consistently; AWB drift persists frame-to-frame.
- **Monochrome capability flag**: a `LEVEL_3`-only signal that the sensor can produce a luminance-only stream bypassing the demosaic step. For Mode 0 (Nc=0) JABCode fixtures the demosaic step is the noise source most likely to perturb metadata reads — a monochrome capture path would materially improve Mode 0 reliability when implemented.
- **YUV reprocessing**: `LEVEL_3`-only. Allows the application to capture YUV, apply a custom 3×3 color-correction matrix in-pipeline, then feed the reprocessed YUV back to the decoder. The H_nc2 green-channel under-capture finding is a candidate for this approach — a fixed gain map on the green channel can shift the rgb=5 (Magenta) failure pattern back toward rgb=6 (Yellow) without touching the C decoder.

On the reference Galaxy S25, camera 0 is `LEVEL_3` and camera 2 is `LIMITED`. Both are back-facing with manual focus. The user-perceived "this works better" on camera 0 is directly traceable to these tier differences.

## The convergence-lock pattern (load-bearing)

This is the single highest-leverage Camera2 change for JABCode scanning quality.

### Why locked AWB/AE matters

JABCode metadata uses a small palette (`{K, C, Y}` for color modes, `{K, W}` for monochrome) and the decoder's per-module classifier (`decodeModuleNc` in `src/jabcode/decoder.c`) compares sampled RGB values against fixed thresholds. When AWB and AE are unlocked:

- Frame N captures a yellow module under one white-point estimate, RGB sampled correctly as `rgb=6` (Y).
- Frame N+1 captures the same module under a slightly different white-point estimate (AWB drift), and the green channel comes through weaker. RGB now classified as `rgb=5` (M = R+B, green missing).
- The PartI validity check (`{0, 3, 6}`) rejects rgb=5 → FAIL_STAGE=module_color → 66% PartI failure rate for nc2 on Galaxy S25.

The 2026-05-30 H_partI_unifies investigation captured this directly via `PartI_DIAG` markers: 79 of nc2's PartI failures show `module[0] rgb=5` and 57 show `rgb=7`, both attributable to AWB drift causing the camera's color-correction matrix to drift mid-session.

### Implementation pattern

```kotlin
// 1. State on the camera controller (NOT in the capture callback closure)
private var awbHasConverged: Boolean = false
private var aeHasConverged: Boolean = false
private var convergenceLocksApplied: Boolean = false
private var activeRepeatingSurface: Surface? = null

// 2. Repeating request with optional lock
private fun startRepeatingRequest(surface: Surface, applyConvergenceLocks: Boolean = false) {
    val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
    requestBuilder.addTarget(surface)
    requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
    requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
    requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
    if (applyConvergenceLocks) {
        requestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true)
        requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true)
    }
    activeRepeatingSurface = surface
    session.setRepeatingRequest(requestBuilder.build(), captureCallback, backgroundHandler)
}

// 3. Capture callback that latches convergence and re-triggers
private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
    override fun onCaptureCompleted(
        session: CameraCaptureSession,
        request: CaptureRequest,
        result: TotalCaptureResult
    ) {
        if (convergenceLocksApplied) return  // one-shot

        val awbState = result.get(CaptureResult.CONTROL_AWB_STATE)
        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
        if (awbState == CameraMetadata.CONTROL_AWB_STATE_CONVERGED) awbHasConverged = true
        if (aeState == CameraMetadata.CONTROL_AE_STATE_CONVERGED) aeHasConverged = true

        if (awbHasConverged && aeHasConverged) {
            convergenceLocksApplied = true
            val s = activeRepeatingSurface ?: return
            backgroundHandler?.post {
                startRepeatingRequest(s, applyConvergenceLocks = true)
            }
        }
    }
}
```

### Caveats

- **AWB lock is only meaningful when `CONTROL_AWB_MODE` is `AUTO`.** This is the Camera2 contract per official documentation. Preset modes (`DAYLIGHT`, `INCANDESCENT`, etc.) are fixed by themselves and don't need locking — but they don't adapt to actual lighting, so they're a poor fit for scanning in varied environments.
- **AE_STATE_FLASH_REQUIRED** can be accepted as "AE has converged" — the algorithm has decided, it just thinks flash would help. For barcode scanning, locking at the no-flash converged value is usually correct.
- **Reset on session restart.** When the camera session restarts (e.g., user navigates away and back), reset the latch flags. AWB/AE state will need to reconverge against the new scene.
- **AF and zoom interactions.** Changes to `SCALER_CROP_REGION` (pinch zoom) and AF re-trigger don't invalidate AWB/AE locks per the Camera2 contract, but on some OEM ISPs the lock may need to be reasserted after a zoom change. Worth empirical verification per device.

## Hardware-level selection pattern

```kotlin
// Recommended
val cameraInfo = CameraEnumerator(context).findCameraByFacing(
    facing = CameraCharacteristics.LENS_FACING_BACK
    // minHardwareLevel default = LIMITED for broad compatibility;
    // findCameraByFacing's maxByOrNull selection still prefers higher tiers.
)

// Anti-pattern — DON'T do this
val cameraId = cameraManager.cameraIdList.first {
    val ch = cameraManager.getCameraCharacteristics(it)
    ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
}
// `first` picks index 0, which on Galaxy S25 happens to be LEVEL_3,
// but on other OEMs the index ordering can place a LIMITED-tier camera
// at index 0 with a FULL/LEVEL_3 camera at higher indices. Always
// select by capability, not by enumeration order.
```

## Cross-references

- `docs/cassandra-register/H_nc2_decode_failure.md` — the bug this guidance addresses
- `docs/cassandra-register/H_mode0_partI_decode_failure.md` — Mode 0 monochrome path, which could benefit from LEVEL_3's monochrome capture capability when implemented
- `project_camera2_control_audit.md` (memory) — 2026-05-27 audit naming LLB AE Mode YES and 5 lag root causes; the convergence-lock pattern compounds with the audit's existing workstream recommendations
- `framework/ui-components/.../Camera2Preview.kt` — reference implementation
- `framework/jabcode-sdk/.../camera/CameraEnumerator.kt::findCameraByFacing` — the hardware-level-preference selector
- Android docs: [Camera2 overview](https://developer.android.com/training/camera2) — official Camera2 framework documentation
- Android docs: [CaptureRequest](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest) — `CONTROL_AWB_LOCK`, `CONTROL_AE_LOCK` constants
- Android docs: [Camera version support](https://source.android.com/docs/core/camera/versioning) — hardware-level definitions
