# Camera Integration

JABCode scanning is sensitive to Camera2 session configuration. This
sub-doc captures the patterns that ship with the framework as of
2026-05-31. The original deeper reference is
[`framework/jabcode-sdk/docs/CAMERA_CONFIGURATION_GUIDE.md`](../../jabauth-android/framework/jabcode-sdk/docs/CAMERA_CONFIGURATION_GUIDE.md)
(PR #36); the patterns below are the consumer-app-facing summary.

## Camera selection

Use the framework's `CameraEnumerator.findCameraByFacing()` rather than
enumerating cameras manually. The implementation ranks back-facing
cameras by `INFO_SUPPORTED_HARDWARE_LEVEL` and prefers the highest tier.

**Preference order**: `LEVEL_3` → `FULL` → `LIMITED-with-MANUAL_SENSOR`
→ `LIMITED` → `LEGACY` (refuse).

On the reference device (Galaxy S25 / SM-S938U-16), camera 0 is
`LEVEL_3` and camera 2 is `LIMITED`. Both are back-facing with manual
focus; the meaningful difference is per-frame manual control
(guaranteed at `FULL`+) and YUV reprocessing (`LEVEL_3` only).

The `LEVEL_3` distinction matters for the AWB/AE convergence-lock
pattern below: `CONTROL_AWB_LOCK` and `CONTROL_AE_LOCK` are documented
to "only meaningfully apply when AWB_MODE / AE_MODE are AUTO" and
LIMITED-tier devices may not honor the lock consistently.

## AWB / AE convergence-lock (default ON)

The framework's `Camera2Preview` starts the capture session with
`CONTROL_AWB_MODE_AUTO` and `CONTROL_AE_MODE_ON`. Once the capture
callback observes BOTH `CONTROL_AWB_STATE = CONVERGED` and
`CONTROL_AE_STATE = CONVERGED` at least once, the repeating request is
reissued with `CONTROL_AWB_LOCK = true` and `CONTROL_AE_LOCK = true`.

This stabilizes the ISP's color-correction matrix and exposure
frame-to-frame, which directly improves JABCode metadata-read
classification stability.

## Manual WB override (opt-in escape hatch)

The convergence-lock has a known failure mode: if AWB converges to a
non-neutral scene white-point at the moment of lock, the locked
color-correction matrix applies a residual color cast to every
subsequent frame. The empirical signature is documented in
`docs/cassandra-register/H_nc2_decode_failure.md` (2026-05-30/31
section).

When a consumer app's empirical measurements show this signature
(metadata samples reading consistently as `rgb=5` (M) regardless of
fixture content), the **manual WB override** is the documented
escape hatch:

```kotlin
captureRequestBuilder.set(
    CaptureRequest.CONTROL_AWB_MODE,
    CaptureRequest.CONTROL_AWB_MODE_OFF
)
captureRequestBuilder.set(
    CaptureRequest.COLOR_CORRECTION_MODE,
    CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
)
captureRequestBuilder.set(
    CaptureRequest.COLOR_CORRECTION_GAINS,
    RggbChannelVector(1.0f, 1.0f, 1.0f, 1.0f)  // neutral; tune per device
)
captureRequestBuilder.set(
    CaptureRequest.COLOR_CORRECTION_TRANSFORM,
    /* identity 3×3 ColorSpaceTransform, see Camera2Preview.kt */
)
```

**Production posture**: OFF by default; opt-in via consumer-defined
Settings toggle or programmatic capture-context detection.

The empirical record (H_nc2 register entry, 2026-05-30/31) shows this
override lifts nc=2 PartI success from 0% to 33.75% on the reference
device when combined with Path β (see `diagnostic-controls.md`).

**Cross-Nc applicability is UNVERIFIED.** Re-baseline all Nc values
(0–7) your app supports with the override ON to confirm no regressions
before shipping it as a default.

## Tuning the override

If the override is enabled but the residual cast persists, tune the
`RggbChannelVector` to attenuate the over-amplified channel. The
H_nc2 investigation found B-amplification specifically, not G
under-capture — `RggbChannelVector(1.0f, 1.0f, 1.0f, 0.3f)` (B at 0.3×)
is the documented starting point for that signature. Other devices may
show different signatures; the raw-byte instrumentation pattern in
`diagnostic-controls.md` is the diagnostic tool for identifying the
specific cast direction.
