# Camera2 control audit — JABCode scanner pipeline

> Filed: 2026-05-27. Subject: the active camera plumbing in
> `jabauth-android/framework/ui-components/src/main/java/com/jabauth/ui/scanner/Camera2Preview.kt`
> and the SDK analyzer surface it feeds. Goal: identify the structural causes of
> the intermittent scanner lag observed during WS-5 verification and surface the
> Camera2 control mechanisms (Stream Use Cases, Low Light Boost AE, Extensions,
> HDR) that could improve it.

## Section 1 — Timeline of the WS-5 / WS-0 conversation arc

| # | Branch / commit                                | Layer                                      | What changed                                                                                                                                                                 | Empirical impact                                                            |
| - | ---------------------------------------------- | ------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| 1 | `claude/ws-5-color-metadata-v2`            → trunk | C library + JNI + Kotlin SDK             | Added `jabMobileDecodeCameraWithMeta` parallel function exposing decoded `Nc` via out-param                                                                              | SDK consumers can read `DecodeResult.colorMode` honestly                    |
| 2 | `claude/ws-5-with-meta-tdd`                → trunk | Tests across three layers                | 6 C-level TDD tests + 4 Kotlin pure-logic tests + 1 Android instrumented canary + Nc=3 fixture asset; wired as regression hard-gate #9                                       | Contract locked across layers                                               |
| 3 | `claude/ws-5-partII-strict-on-with-meta`   → trunk | `decoder.c` + bridge + tests + new doc   | Memory hygiene; thread-local strict flag; Option D (strict on WithMeta only); `__attribute__((deprecated))` on legacy entry point; Cassandra register entry filed            | Nc=3 mis-identification fixed; multi-frame test contract preserved          |
| 4 | `claude/ws-5-diag-logging-gate`            → trunk | `decoder.c` + `detector.c` + Kotlin JNI  | `JAB_DIAG_INFO` macro gated by thread-local `g_diag_verbose`; 8 chatty markers converted; `nativeSetDiagVerbose(Boolean)` toggle exposed                                     | **4.1× decode success rate** on Nc=3; **4.7× log volume reduction**         |
| 5 | `claude/ws-0-mode0-decoder-trigger-fix`     (local) | `detector.c` + new desktop test          | Replaced strict R==G==B with chroma-tolerance check (`MODE0_CHROMA_TOLERANCE=24`); new gate test #10                                                                          | 10/10 desktop assertions PASS; on-device verification ongoing               |

Two pieces of project memory captured from this arc:
- `feedback_stacked_pr_merge_race.md` — second recurrence of the auto-retarget bug; corrective-PR-from-topmost-branch is the recovery pattern.
- `project_jabcode_screen_vs_print_physics.md` — emissive-vs-reflective failure-mode asymmetry with citations from color science and camera sensor literature.

## Section 2 — Current camera architecture

The session-lifecycle code is **not** in the SDK's `camera/` module. It lives in the UI-components module that the diagnostic app consumes:

`jabauth-android/framework/ui-components/src/main/java/com/jabauth/ui/scanner/Camera2Preview.kt` (445 lines)

The SDK module's `Camera2JABCodeAnalyzer.kt` owns the *analyzer* surface (consumes `ImageReader` frames and calls the JABCode decoder); the UI-components `Camera2Preview` owns the *session* (opens camera, builds surfaces, dispatches requests).

CameraX dependencies are in the gradle file (`androidx.camera:camera-core`/`camera2`/`lifecycle`/`view`) but the active scanner uses Camera2 directly — CameraX is unused in this path today.

### Active session configuration (the empirical state)

| Setting                    | Value                                                  | Source                                                  |
| -------------------------- | ------------------------------------------------------ | ------------------------------------------------------- |
| Session creation API       | `createCaptureSession(List<Surface>, ...)` — deprecated | `Camera2Preview.kt:232`                                 |
| Request template           | `TEMPLATE_PREVIEW`                                     | `Camera2Preview.kt:258`                                 |
| `CONTROL_AE_MODE`          | `CONTROL_AE_MODE_ON` (basic auto-exposure)             | `Camera2Preview.kt:272`                                 |
| `CONTROL_AF_MODE`          | `CONTINUOUS_PICTURE` (or `OFF` conditionally)          | `Camera2Preview.kt:264`                                 |
| `CONTROL_AWB_MODE`         | `AUTO`                                                 | `Camera2Preview.kt:289`                                 |
| Stream Use Case            | **NOT SET** — defaults to 0 (`DEFAULT`)                | logcat: `streamUseCase 0`                               |
| Surface types              | TextureView (preview) + ImageReader YUV (analysis)     | `Camera2Preview.kt:185`                                 |
| Analysis stream resolution | 1920×1080 YUV_420_888                                  | logcat                                                  |
| Preview surface            | TextureView (discouraged in Google's own docs)         | `Camera2Preview.kt:160` signature                       |
| Repeating request          | Yes — correct usage                                    | `Camera2Preview.kt:293`                                 |
| Single-shot requests       | None                                                   | (good — preserves frame cadence)                        |

## Section 3 — Root-cause analysis of intermittent lag

Five distinct issues with the current configuration plausibly contribute to the observed lag, ordered by expected impact:

### Issue A — No `streamUseCase` on output configurations *(highest leverage)*

The session uses the older `createCaptureSession(List<Surface>, ...)` overload, which means surfaces aren't wrapped in `OutputConfiguration` objects, which means `streamUseCase` cannot be specified. The logcat confirms `streamUseCase 0` (i.e., `DEFAULT`). Per Google's [Capture sessions and requests](https://developer.android.com/media/camera/camera2/capture-sessions-requests) doc, stream use cases let the HAL "tune sensor mode, frame rates, and processing based on use case." DEFAULT means **no hardware optimization hint**, forcing the HAL into a generic profile.

For a preview + concurrent ImageReader analysis pipeline, the right use case is `SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW_VIDEO_STILL` — the multi-purpose hint that signals "sustained scanning, optimize for steady frame rate over peak quality."

Mitigation: switch to `createCaptureSessionByOutputConfigurations(List<OutputConfiguration>, ...)` with explicit per-surface `streamUseCase`. Available on Android 13 (API 33)+. Fall back to the current path on earlier API levels. This is the single highest-leverage change identified.

### Issue B — Analysis stream at full preview resolution

Logcat confirms the YUV_420_888 ImageReader stream is at 1920×1080 — same as the preview. The JABCode is at most a 21×21 module symbol at master level; at the encoder default of 12 pixels per module the full code fits in roughly a 250×250 region. We are shipping ~8× more pixels per frame to the analysis pipeline than the decoder can use, plus all the per-frame YUV→ARGB bitmap conversion overhead.

Mitigation: reduce the analysis stream to 640×480 or 720×720 while keeping the preview at 1920×1080. Per-stream sizing is supported on every stream combination table for LIMITED hardware level and above (see [Multiple camera streams simultaneously](https://developer.android.com/media/camera/camera2/multiple-camera-streams-simultaneously)).

### Issue C — TextureView preview surface

The Camera2 docs note TextureView is "discouraged for maintainability" — it goes through an extra GL composition path that adds latency vs SurfaceView. The cost is not large per frame, but it accumulates over a long scanning session.

Mitigation: switch `Camera2Preview` to use SurfaceView. Cosmetic-feeling change but eliminates the GL composition step from every frame.

### Issue D — AE/AWB/AF re-converging on every session creation

The capture session is created with `CONTROL_AE_MODE_ON` and `CONTROL_AWB_MODE_AUTO` but **not locked**. Every time the user starts or stops scanning, the camera has to re-converge auto-exposure, auto-white-balance, and auto-focus. From the WS-5 traces, AE convergence visibly takes 200–500 ms post-session-creation; the JABCode detector is running on those un-converged frames and likely failing on them.

Mitigation: after a JABCode is detected at least once in a session, lock AE/AWB (`CONTROL_AE_LOCK = true`, `CONTROL_AWB_LOCK = true`). Subsequent frames in the same scanning session then have consistent capture conditions and the decoder gets stable color palettes to match against. This connects directly to the existing "Adaptive camera intelligence" project memory.

### Issue E — ImageReader buffer count and listener blocking

`Camera2Preview` uses the default ImageReader `maxImages` (typically 2). Under load — when the analyzer takes >50 ms to decode a frame — the reader backlog fills and the camera pipeline starts dropping frames. The dropped-frame pattern is the textbook symptom of intermittent lag.

Mitigation: increase `maxImages` to 4 (we have memory headroom for ~30 MB of YUV buffers at our stream resolution), use `acquireLatestImage()` in the analyzer (drops queued frames automatically), and verify the listener's `Image` is closed promptly on every code path including decode failures.

## Section 4 — Low Light Boost AE Mode applicability

The Low Light Boost AE Mode is a single-flag, opt-in feature on Android 15+ (API 35). The [official documentation](https://developer.android.com/media/camera/lowlight/low-light-boost-ae) explicitly lists "scanning QR codes in low light" as a primary use case. It does NOT combine multiple frames (so the preview stream stays a live continuous stream — no shutter delay). It DOES continuously brighten the live preview by adjusting exposure dynamically, with OEMs using a "moon icon" indicator to show it is active.

### Direct relevance to JABCode scanning

1. **Dim ambient scanning of prints** — the WS-5 verification showed Nc=3 print scanning worked, Nc=4+ on print failed. Some of the failure mode is exposure-related; LLB AE would expose more aggressively without compromising temporal stability.
2. **Mode 0 (Nc=0) decoder failure currently under investigation** — if chroma noise scales with low light, LLB AE would reduce noise by exposing longer, which would *also* loosen the `MODE0_CHROMA_TOLERANCE` constraint we are tuning.

### Trade-off to verify

The doc warns color rendering may differ from non-boosted captures. For high-Nc color JABCodes where palette discrimination matters, verify color fidelity is preserved before adopting unconditionally.

### Detection code

```kotlin
val supported = characteristics
    .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
    ?.contains(CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY)
    ?: false
```

The Galaxy S25 is Android 15 — almost certainly supports this.

### Active-state observation for telemetry

```kotlin
if (result.get(CaptureResult.CONTROL_LOW_LIGHT_BOOST_STATE)
    == CameraMetadata.CONTROL_LOW_LIGHT_BOOST_STATE_ACTIVE) {
    // LLB active — surface indicator in diagnostic HUD
}
```

A natural addition to the diagnostic app's existing HUD next to the scan counter.

## Section 5 — Camera2 Extensions API applicability

Camera2 Extensions are device-vendor-implemented enhancement modes (Night, HDR, Auto, Bokeh, Face Retouch) that operate at a HIGHER level than the raw Camera2 API. They use `CameraExtensionSession` instead of `CameraCaptureSession`.

### Two caveats that disqualify Extensions for live scanning

1. **Extensions trade processing latency for image quality.** They're designed for one-shot still capture (a "shutter button" pattern), not continuous low-latency analysis streams. For a JABCode scanner running 5–30 fps of analysis frames, the per-frame quality-enhancement cost is structurally wrong.

2. **Extensions surface preset, non-tunable behavior.** "Settings are preset by OEM; minimal fine-tuning available." If we want a specific exposure / AWB strategy for JABCode optimization (locked AE/AWB after first detection), Extensions takes that control away from us.

### Recommendation

Skip Extensions for live scanning. If we ever add a "high-quality still capture for diagnostics" path (e.g., a "tap-to-capture for manual analysis" feature), HDR Extension would be the right pick because it preserves color detail under high-DR conditions. Night and Auto Extensions would degrade scan latency.

### Detection code (still worth implementing for diagnostic enumeration)

```kotlin
val extensionChars = cameraManager.getCameraExtensionCharacteristics(cameraId)
val supportedExtensions = extensionChars.supportedExtensions
val canDoHdr = supportedExtensions.contains(EXTENSION_HDR)
val canDoNight = supportedExtensions.contains(EXTENSION_NIGHT)
```

Belongs in `framework/jabcode-sdk/src/main/java/com/jabauth/jabcode/camera/CameraEnumerator.kt` as a one-time capability scan. The diagnostic app HUD should *report* available extensions even when it does not apply them.

## Section 6 — Other findings

### HDR video capture (`setDynamicRangeProfile(HLG10)`, API 33+)

[Documentation](https://developer.android.com/media/camera/camera2/hdr-video-capture) is clear that HDR's primary benefit is "preserving detail in high-contrast scenes." HDR does NOT directly address the camera-sensor saturation issue observed when scanning bright screens at high Nc. Reason: HDR widens the *output* dynamic range, but the sensor's *input* range — where saturation actually happens — is unchanged. HDR could theoretically preserve more headroom if the pipeline were built for 10-bit output, but the JABCode decoder operates on 8-bit input regardless. Skip HDR for the current scope.

### Multi-stream best practices

For our use (preview + analysis), the LIMITED hardware level (Galaxy S25 exceeds this at LEVEL_3) supports `PRIV PREVIEW + YUV PREVIEW`, which is what we already do. The optimization opportunity is per-stream sizing (Issue B) and stream-use-case hints (Issue A), not stream count.

### Android-Image-Cropper library

A user-driven crop-selection UI library, not a region-of-interest detector. Useful if we ever want a "manual crop to barcode region" interaction, but it provides no automated JABCode region detection. Skip unless a tap-to-crop UI mode is explicitly required.

## Section 7 — Recommended workstreams (prioritized)

| # | Workstream                                                                                                                                  | Effort     | Expected impact                                                                  |
| - | ------------------------------------------------------------------------------------------------------------------------------------------- | ---------- | -------------------------------------------------------------------------------- |
| 1 | **Session creation modernization** — `createCaptureSessionByOutputConfigurations` + explicit `streamUseCase` per surface                  | 1–2 days   | High — comparable in magnitude to the diag-gate (4×-ish on success rate)         |
| 2 | **Analysis stream resolution reduction** — 1920×1080 → 640×480 or 720×720 for the YUV ImageReader                                          | <1 day     | High — drops per-frame bitmap conversion cost by ~8×                             |
| 3 | **Low Light Boost AE adoption** — detect capability, opt-in when present, surface activation state in diagnostic HUD                       | <1 day     | Medium-high — unblocks dim-light scanning; may relax Mode 0 tolerance margin     |
| 4 | **AE/AWB lock-after-first-detection** — lock once a JABCode has been detected; release on scanner exit                                     | 1–2 days   | Medium — eliminates AE convergence drift mid-session                             |
| 5 | **TextureView → SurfaceView** preview surface                                                                                              | <1 day     | Low-medium — eliminates GL composition latency                                   |
| 6 | **ImageReader buffer count + `acquireLatestImage`** hygiene                                                                                | <1 day     | Medium — eliminates queued-frame backlog under decode-time spikes                |
| 7 | **Camera capability enumeration** including extensions and AE modes for diagnostic app HUD                                                 | 1 day      | Diagnostic value — informs every other decision                                  |

Suggested sequencing:
- 1 + 2 together (they touch the same code).
- Then 6 (independent ImageReader hygiene).
- Then 3 + 4 together (both are runtime camera-control strategy).
- Then 7 (the HUD additions).
- Then 5 (cosmetic-feel cleanup).

Each is independently testable against the Galaxy S25 in the same APK-deploy-and-scan loop established during WS-5.

## Section 8 — Sources

User-provided primary references:

- [Camera2 Capture Sessions and Requests — developer.android.com](https://developer.android.com/media/camera/camera2/capture-sessions-requests)
- [Camera2 Camera Enumeration — developer.android.com](https://developer.android.com/media/camera/camera2/camera-enumeration)
- [Low Light Boost AE Mode — developer.android.com](https://developer.android.com/media/camera/lowlight/low-light-boost-ae)
- [Camera2 Extensions API — developer.android.com](https://developer.android.com/media/camera/camera2/extensions-api)
- [Multiple Camera Streams Simultaneously — developer.android.com](https://developer.android.com/media/camera/camera2/multiple-camera-streams-simultaneously)
- [HDR Video Capture — developer.android.com](https://developer.android.com/media/camera/camera2/hdr-video-capture)
- [Android-Image-Cropper — GitHub](https://github.com/CanHub/Android-Image-Cropper)

Related Android docs (cross-referenced):

- [CameraCharacteristics — Android Reference](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics)
- [OutputConfiguration.setStreamUseCase — Android Reference](https://developer.android.com/reference/android/hardware/camera2/params/OutputConfiguration#setStreamUseCase(long))
- [CameraExtensionSession — Android Reference](https://developer.android.com/reference/android/hardware/camera2/CameraExtensionSession)

In-repo prior research (the work that informed this audit):

- `jabauth-android/research-docs/Android Camera2 Diagnostic Application Design Best Practices.md`
- `jabauth-android/research-docs/Android Camera2 Diagnostic Application_ Common Pitfalls and Avoidance Strategies.md`
- `jabauth-android/research-docs/framework-and-diagnostic-app-audit/FRAMEWORK_AUDIT.md`

Related project memory (loaded into future conversations automatically):

- `project_jabcode_screen_vs_print_physics.md` — emissive-vs-reflective failure-mode asymmetry; informs why screen capture saturates at high backlight
- `project_adaptive_camera_intelligence.md` — vision for runtime camera-mode adaptation; this audit gives that vision concrete first steps
- `project_ws0_mode0_status.md` — encoder side of Mode 0 already merged; the trigger fix in `claude/ws-0-mode0-decoder-trigger-fix` is the decoder-side counterpart
