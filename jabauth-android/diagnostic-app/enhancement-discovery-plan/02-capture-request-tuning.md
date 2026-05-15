# 02 -- Capture Request Tuning

> **Priority:** P1-P3 (AE/AWB lock, AF mode, NR/edge disable)
> **Layer:** Framework -- `ui-components` + Diagnostic App
> **Risk:** Low (additive CaptureRequest keys, no existing behavior removed)

---

## Context

The Camera2 `CaptureRequest` controls how the sensor captures each frame. The current configuration uses all-auto modes with no locks, causing frame-to-frame fluctuation in exposure, white balance, and focus that destabilizes color classification.

## Current State

**File:** `framework/ui-components/.../Camera2Preview.kt` L252-299

```kotlin
// Current CaptureRequest configuration:
CONTROL_AF_MODE         = CONTINUOUS_PICTURE    // aggressive focus hunting
CONTROL_AE_MODE         = ON                    // auto, never locks
CONTROL_AE_COMPENSATION = +1 EV                 // slight brightness boost
CONTROL_AWB_MODE        = AUTO                  // continuous color correction
// NOT SET:
// CONTROL_NOISE_REDUCTION_MODE  (defaults to device preference)
// CONTROL_EDGE_MODE             (defaults to device preference)
// CONTROL_AE_LOCK               (never locks)
// CONTROL_AWB_LOCK              (never locks)
```

**File:** `framework/jabcode-sdk/.../config/CameraConfig.kt` L13-19

```kotlin
data class CameraConfig(
    val enableAutoFocus: Boolean = true,       // no lock option
    val enableAutoExposure: Boolean = true,    // no lock option
    val enableAutoWhiteBalance: Boolean = true, // no preset option
    val enableFlash: Boolean = false,
    val targetFps: Int = 30
)
```

## Gaps

### Gap 1: AE never locks

When pointed at a bright screen, AE oscillates between frames as the barcode's bright/dark modules cause metering instability. Each frame may have slightly different exposure, shifting all RGB values up or down by 5-15 units.

**Evidence from trace:** FP0(black) reads RGB(28,25,30) in one frame and RGB(50,43,49) in another -- 22-unit swing in a "stable" black region.

### Gap 2: AWB set to AUTO

AWB AUTO continuously re-evaluates color temperature. A screen's \~6500K backlight is stable, but AWB doesn't know that -- it adjusts for every bright/dark ratio change as the barcode moves. This shifts hue by 5-10 units per channel between frames.

**Evidence from trace:** FP3(cyan) should be constant but reads RGB(239,237,240) in one frame and RGB(255,253,255) in another. The near-equal R=G=B values suggest AWB is neutralizing the cyan tint entirely.

### Gap 3: AF in CONTINUOUS\_PICTURE mode

CONTINUOUS\_PICTURE mode is designed for photography -- it aggressively hunts for the sharpest possible focus, temporarily defocusing to re-evaluate. During these defocus moments, module edges blur, inflating measured `module_size`.

### Gap 4: Default noise reduction and edge enhancement

Device-default NR smooths pixel values across boundaries. Edge enhancement adds ringing artifacts at high-contrast transitions. Both corrupt the precise color values at module centers.

## Fix

### CameraConfig.kt additions

```kotlin
data class CameraConfig(
    // ... existing fields ...
    val awbMode: Int = CaptureRequest.CONTROL_AWB_MODE_AUTO,
    val aeLockAfterConvergence: Boolean = false,
    val awbLockAfterConvergence: Boolean = false,
    val convergenceFrameCount: Int = 10,
    val noiseReductionMode: Int = CaptureRequest.NOISE_REDUCTION_MODE_OFF,
    val edgeMode: Int = CaptureRequest.EDGE_MODE_OFF
)
```

### Camera2Preview.kt: AE/AWB lock via CaptureCallback

```kotlin
private var aeConvergedCount = 0
private var awbConvergedCount = 0

private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
    override fun onCaptureCompleted(
        session: CameraCaptureSession,
        request: CaptureRequest,
        result: TotalCaptureResult
    ) {
        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
        val awbState = result.get(CaptureResult.CONTROL_AWB_STATE)

        if (aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
            aeConvergedCount++
            if (aeConvergedCount >= CONVERGENCE_THRESHOLD && !aeLocked) {
                lockAE()
            }
        }
        // Similar for AWB
    }
}
```

### startRepeatingRequest additions

```kotlin
requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CONTINUOUS_VIDEO)
requestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, OFF)
requestBuilder.set(CaptureRequest.EDGE_MODE, OFF)
// Pass captureCallback to setRepeatingRequest for lock tracking
session.setRepeatingRequest(requestBuilder.build(), captureCallback, backgroundHandler)
```

## TDD Plan

### Test 02.1: CameraConfig builder

```
GIVEN CameraConfig.Builder()
WHEN  .awbMode(DAYLIGHT).aeLockAfterConvergence(true).build()
THEN  config.awbMode == CONTROL_AWB_MODE_DAYLIGHT
AND   config.aeLockAfterConvergence == true
AND   config.isValid() == true
```

### Test 02.2: CaptureRequest key verification (instrumented)

```
GIVEN a Camera2 session with tuned CameraConfig
WHEN  capture request is built
THEN  request.get(CONTROL_AF_MODE) == CONTINUOUS_VIDEO
AND   request.get(NOISE_REDUCTION_MODE) == OFF
AND   request.get(EDGE_MODE) == OFF
```

### Test 02.3: AE lock after convergence (instrumented)

```
GIVEN a running capture session with aeLockAfterConvergence=true, threshold=10
WHEN  10 consecutive frames report AE_STATE_CONVERGED
THEN  the next CaptureRequest includes CONTROL_AE_LOCK = true
```

### Test 02.4: AWB mode preset (instrumented)

```
GIVEN CameraConfig with awbMode=DAYLIGHT
WHEN  capture request is built
THEN  request.get(CONTROL_AWB_MODE) == CONTROL_AWB_MODE_DAYLIGHT
```

## Files Affected

| File | Change |
|------|--------|
| `framework/jabcode-sdk/.../config/CameraConfig.kt` | Add awbMode, lock fields, NR/edge fields |
| `framework/ui-components/.../Camera2Preview.kt` | Add CaptureCallback for lock tracking, apply new config keys |
| `diagnostic-app/.../data/SettingsRepository.kt` | Add AWB mode preference key |
| `diagnostic-app/.../ui/settings/SettingsScreen.kt` | Add AWB mode picker UI |
| `diagnostic-app/.../ui/scanner/ScannerScreen.kt` | Pass AWB mode to Camera2Preview |
| `framework/jabcode-sdk/src/test/.../config/CameraConfigTest.kt` | Test 02.1 |
| `framework/jabcode-sdk/src/androidTest/.../camera/` | Tests 02.2, 02.3, 02.4 |

## Verification

After deployment, capture 30 seconds of logcat while scanning a static barcode:

1. **AE stability:** After initial convergence, `CONTROL_AE_STATE` should show LOCKED, not CONVERGED/SEARCHING
2. **AWB stability:** FP3(cyan) RGB values should vary by < 5 units across 20 consecutive frames (currently varies by > 20)
3. **Module\_size stability:** FP0 `module_size` values should cluster tightly (currently ranges 20-126px)
