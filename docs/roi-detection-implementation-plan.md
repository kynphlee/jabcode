# ROI Detection — Implementation Plan & Design Wireframes

Filed: 2026-05-27 in response to WS-camera Bayesian Council Session 6 verdict. Reviewing source: [`docs/camera-control-audit.md`](camera-control-audit.md), [`H_mode0_partI_decode_failure.md`](cassandra-register/H_mode0_partI_decode_failure.md), `project_jabcode_screen_vs_print_physics.md` (memory).

---

## 0. Executive Summary

Council Session 6 unanimously rejected "implement the H_AC hybrid ROI detector now" and unanimously endorsed "ROI detection is potentially high-leverage but conditional on empirical measurement." The verdict: a **four-PR sequenced plan, measurement-first**, plus parallel UX work.

### 0.1 Product positioning (resolves Session 7 Q3)

The SDK's primary use case is **continuous scanning** — the user holds the camera over a JABCode, the SDK detects and decodes it in the background without explicit user trigger. This is contrasted with the tap-to-decode pattern (user explicitly indicates intent to scan) that Prometheus's reframe in Session 7 raised as an alternative.

**Decision: continuous scan is the primary mode.** All four PRs (1-4) are therefore load-bearing rather than speculative. The decision is anchored by the SDK's eventual deployment targets — authentication kiosks, library-checkout terminals, document-verification flows — where the user's expectation is "hold the camera near the code, scan happens automatically." Tap-to-decode remains a future opt-in mode but not the headline UX.

This positioning increases the Kano tier of the default heuristic detector from Performance to **Must-be**: a continuous-scan SDK that occasionally engages tracking on a non-JABCode is worse than the current behavior. Adversarial test fixtures (§9.4) are therefore required for PR 3, not optional.

| PR | Title | Effort | Empirical question it answers |
|----|-------|--------|-------------------------------|
| 1 | Manual pinch-zoom verification | ~1–2 days | Does crop-region/zoom actually unlock high-Nc and Mode 0 decoding on this device? |
| 2 | SDK `setCropRegion(Rect?)` foundation API | ~1 day | (No new behavior; just exposes the mechanism) |
| 3 | `ROIDetector` interface + heuristic default | ~3–5 days | Can the existing FP detector logic identify a JABCode at downscaled resolution? |
| 4 | SEARCH/TRACKING state machine | ~3–5 days | Does automatic search-then-track outperform manual zoom? |
| UX | Module-size telemetry + "move closer" hint | ~1 day | Can the user solve the resolution problem before software needs to? |

**Decision gate**: PRs 2–4 are conditional on PR 1's empirical outcome. If pinch-zoom DOES NOT unlock decodes that fail at 1×, ROI detection is solving the wrong problem and the entire chain stops; resources pivot to slave-decode investigation (`H_partI_clean_data_failure`, `H_mode0_partI_decode_failure`). If pinch-zoom DOES unlock decodes, the chain executes in order.

---

## 1. The Empirical Question (PR 1)

### 1.1 Why this PR exists

Every prior camera-control workstream (#1+2, #6, #3) had empirical evidence of the problem before code was written. ROI detection has only physics-derived prediction (more pixels per module → better high-Nc decoding). PR 1 closes that discipline gap.

### 1.2 Hypothesis being tested

> Manually zooming the camera (via pinch gesture) on a JABCode that currently fails at default 1× zoom should unlock the decode if and only if effective module-pixel ratio is the load-bearing bottleneck.

### 1.3 What to instrument

Add pinch-zoom to `Camera2Preview.kt` via the standard `ScaleGestureDetector` + `SimpleOnScaleGestureListener` pattern, driving `SCALER_CROP_REGION` on the active repeating request.

### 1.4 Implementation skeleton

```kotlin
// Camera2Preview.kt — new fields
private var currentZoomRatio: Float = 1.0f
private var maxDigitalZoom: Float = 1.0f
private val activeArraySize: Rect?
    get() = cameraCharacteristics
        ?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

// In openCamera() after fetching characteristics
maxDigitalZoom = characteristics
    .get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
Log.i(TAG, "Max digital zoom: ${maxDigitalZoom}x")

// New gesture detector wired into the TextureView's onTouchListener
private val scaleGestureDetector = ScaleGestureDetector(context,
    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newZoom = (currentZoomRatio * detector.scaleFactor)
                .coerceIn(1.0f, maxDigitalZoom)
            if (newZoom != currentZoomRatio) {
                currentZoomRatio = newZoom
                applyCropRegion(newZoom)
            }
            return true
        }
    })

// Compute and apply the crop region from a zoom ratio
private fun applyCropRegion(zoomRatio: Float) {
    val active = activeArraySize ?: return
    val centerX = active.centerX()
    val centerY = active.centerY()
    val halfW = (active.width()  / (2.0f * zoomRatio)).toInt()
    val halfH = (active.height() / (2.0f * zoomRatio)).toInt()
    // Align to 4-pixel boundaries per Camera2 best practice
    val alignedW = (halfW / 4) * 4
    val alignedH = (halfH / 4) * 4
    val crop = Rect(centerX - alignedW, centerY - alignedH,
                    centerX + alignedW, centerY + alignedH)
    cachedCropRegion = crop
    rebuildRepeatingRequest()  // re-issue with the new SCALER_CROP_REGION
    Log.i(TAG, "Zoom -> ${zoomRatio}x, crop=$crop")
}
```

### 1.5 Diagnostic markers

Two new logcat lines to enable verification:

```
Camera2Controller: Max digital zoom: 8.0x
Camera2Controller: Zoom -> 2.0x, crop=Rect(960, 540 - 2880, 1620)
```

Combined with the existing FAIL_ATTR/DECODE_OK markers, this lets us correlate decode success rate against zoom level cleanly.

### 1.6 Verification protocol

Same fixture set: Nc=3, Nc=4, Nc=5, Nc=6, Nc=7 on screen and print. Three scans per fixture:
- Scan A: 1× zoom (baseline, identical to current behavior)
- Scan B: 2× zoom (representative of typical user-initiated zoom)
- Scan C: max zoom or where the JABCode visually fills the frame

Expected outcomes form a decision tree:
- **A fails, B/C succeeds**: ROI hypothesis confirmed; proceed to PR 2-4
- **A and B fail, C succeeds**: ROI alone insufficient; combine with other workstreams (slave-decode)
- **A/B/C all fail**: Bottleneck is downstream of resolution; abandon ROI; pivot to slave-decode investigation

### 1.6.1 Per-fixture decision rules (resolves Session 7 Q1 — matrix-shaped outcomes)

PR 1's outcome is expected to be matrix-shaped, not binary. Per-fixture pass criteria:

| Fixture | Pass criterion | Rationale |
|---------|----------------|-----------|
| Nc=3 print | At baseline (Scan A) decode rate ≥ 60%, AND zoom (B or C) bumps rate by ≥ 10 percentage points | The current floor (~60% from session-modernization) is the comparison baseline; ROI should additively improve it |
| Nc=4 print | Baseline 0%, AND ANY of B/C produces ≥ 20% success rate | Nc=4 print is the historical 0% case; even modest ROI improvement is a strong signal |
| Nc=5/6/7 print | Baseline ~0%, AND C produces ≥ 10% success rate at max usable zoom | These have multiple compounding bottlenecks (gamut, dot gain, slave-decode); ROI alone may not be sufficient |
| Nc=0 (Mode 0) screen | Baseline 0% (gated by `H_mode0_partI_decode_failure`), AND zoom does NOT change baseline | Confirms Mode 0 bottleneck is downstream of resolution — independent verification of the hypothesis on that register entry |
| Nc=3-7 screen | Baseline already 75%+ on most, AND zoom does not regress them | No regression check; screen mode already works well |

**Gate-pass interpretation**:
- **≥ 3 of the above fixtures pass their criterion** → PRs 2-4 proceed
- **1-2 fixtures pass** → PR 2 ships (setCropRegion mechanism is defensible regardless); PRs 3+4 receive separate council review with the partial data
- **0 fixtures pass** → entire chain stops; resources pivot to slave-decode investigation (`H_partI_clean_data_failure`, `H_mode0_partI_decode_failure`)

### 1.7 Cross-reference to Android best practices

- [`SCALER_CROP_REGION` API reference](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#SCALER_CROP_REGION) — coordinate system is SENSOR_INFO_ACTIVE_ARRAY_SIZE
- [`SCALER_AVAILABLE_MAX_DIGITAL_ZOOM`](http://www.java2s.com/example/java-api/android/hardware/camera2/cameracharacteristics/scaler_available_max_digital_zoom-0.html) — device's maximum zoom ratio
- [Pinch-to-zoom pattern with `ScaleGestureDetector`](http://android-er.blogspot.com/2016/05/implement-pinch-to-zoom-with.html) — standard listener pattern
- Boundary alignment: round crop dimensions to multiples of 2 or 4 to avoid HAL rounding

### 1.8 Risks (from Cassandra Round 1)

- **Pinch gestures interact with TextureView's transform**: when transform is applied, gesture coordinates may need translation. Mitigation: implement gesture detection on a transparent overlay view, not the TextureView directly.
- **`SCALER_CROP_REGION` not supported on LEGACY hardware level**: device check before applying; fall through to no-zoom on unsupported devices.

---

## 2. SDK `setCropRegion(Rect?)` Foundation API (PR 2)

### 2.1 Why this PR exists

Expose `SCALER_CROP_REGION` as a public SDK API. No detector yet; just the mechanism. Honors Robin Hood's principle from Session 6: "SDK provides the mechanism; consuming app provides the policy."

### 2.2 API design

The SDK adds a single public method on the `JABCodeScanner` interface (or equivalent):

```kotlin
package com.jabauth.jabcode.scanner

interface JABCodeScanner {
    // ... existing methods ...

    /**
     * Set the camera's crop region (digital zoom equivalent).
     *
     * Coordinates are in the camera's active sensor array space
     * (see CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE).
     * The HAL will scale the cropped sensor region to fill the
     * configured analysis stream resolution — giving more
     * effective pixels per module for the JABCode being scanned.
     *
     * Pass null to clear the crop region (return to full-frame).
     *
     * Available API levels: 21+ (LIMITED hardware level or above).
     * On LEGACY devices, calls to this method are silently ignored
     * with a single warning log line.
     *
     * @param region The desired crop, in sensor active-array coordinates,
     *               or null to clear.
     */
    fun setCropRegion(region: Rect?)

    /**
     * Get the maximum digital zoom ratio for the current camera.
     * Returns 1.0f on LEGACY devices or before the camera has been opened.
     */
    fun getMaxDigitalZoom(): Float

    /**
     * Convenience: set the crop region centered on the active array
     * with the given zoom ratio. Equivalent to computing the centered
     * sub-rect of active_array_size shrunk by zoomRatio and calling
     * setCropRegion(rect).
     *
     * @param zoomRatio Clamped to [1.0f, getMaxDigitalZoom()].
     */
    fun setZoomRatio(zoomRatio: Float)
}
```

### 2.3 What this PR does NOT do

- Does not add an ROI detector
- Does not implement search/tracking
- Does not change scanner state machine
- Does not introduce any new dependency

This is purely **plumbing**: a public surface for crop control.

### 2.4 Cross-reference

- [`OutputConfiguration` Camera2 reference](https://developer.android.com/reference/android/hardware/camera2/params/OutputConfiguration) — already used in `Camera2Preview.kt`
- [`CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL`](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#INFO_SUPPORTED_HARDWARE_LEVEL) — for the LEGACY device check

---

## 3. `ROIDetector` Interface + Heuristic Default (PR 3) — Design Wireframes

### 3.1 Why this PR exists

After PR 2 exposes the mechanism, PR 3 ships a default policy: a callable `ROIDetector` that consumers can substitute (MLKit, custom TFLite, their own heuristic) and a heuristic implementation that ships in the SDK.

### 3.2 Interface contract

```kotlin
package com.jabauth.jabcode.scanner.roi

/**
 * Detects the bounding box of a JABCode (or candidate region) within
 * a camera frame, returning a Rect in the bitmap's pixel coordinates.
 *
 * Implementations may vary by speed/accuracy tradeoff:
 *   - Heuristic (default): downsamples + runs FP detection at low res
 *   - ML-based (consumer-supplied): MLKit, TFLite, custom
 *
 * Implementations MUST be thread-safe; the SDK calls detect() from
 * the camera analysis thread.
 */
interface ROIDetector {
    /**
     * Detect a JABCode candidate region in [bitmap].
     *
     * @return The bounding box of the candidate in [bitmap]'s pixel
     *         coordinate space, or null if no candidate was found.
     *         A returned Rect should include reasonable margin (15-25%)
     *         around the actual JABCode to tolerate jitter and
     *         partial occlusion.
     */
    fun detect(bitmap: Bitmap): Rect?

    /**
     * Suggested downsample factor for the input bitmap. Implementations
     * can hint that they perform best at certain resolutions; the
     * SDK uses this to pre-scale bitmaps before invocation.
     *
     * @return Power-of-two divisor (1 = full resolution, 2 = half,
     *         4 = quarter, etc.). Default 1.
     */
    fun preferredDownsampleFactor(): Int = 1

    /**
     * Suggested max age (frames) before re-running detection in
     * TRACKING mode. After this many decode failures, the state
     * machine should re-run detection rather than blindly trust the
     * previous crop region. Default 5 frames.
     */
    fun maxTrackingStaleness(): Int = 5
}
```

### 3.3 Default heuristic implementation — class diagram

```
┌─────────────────────────────────────────────────────────────────┐
│  HeuristicJabCodeROIDetector : ROIDetector                       │
├─────────────────────────────────────────────────────────────────┤
│  - downsampleFactor: Int = 4    // 1280x720 -> 320x180          │
│  - minFpCount: Int = 3          // accept 3 or 4 corner FPs     │
│  - bboxMarginPct: Float = 0.20f // 20% margin around bbox        │
├─────────────────────────────────────────────────────────────────┤
│  + detect(bitmap): Rect?                                         │
│    1. Downsample bitmap to ~320x180                              │
│    2. Convert to grayscale + binarize                            │
│    3. Run lightweight FP detector (port of detector.c logic)     │
│    4. If ≥ 3 FPs found in plausible quadrilateral:               │
│         a. Compute bounding box from FP positions                │
│         b. Scale bbox back to original bitmap coordinates        │
│         c. Apply 20% margin in all directions                    │
│         d. Return bbox                                           │
│    5. Otherwise return null                                      │
└─────────────────────────────────────────────────────────────────┘
```

### 3.4 Reused decoder primitives

The heuristic detector REUSES existing JABCode FP detection logic by exposing a public C function:

```c
// New in src/jabcode/detector.h
typedef struct {
    jab_int32 x;
    jab_int32 y;
    jab_float module_size;
    jab_int32 type;       // FP0/FP1/FP2/FP3
    jab_int32 confidence; // 0-100
} jab_fp_candidate;

/**
 * Lightweight FP candidate enumeration: runs the FP scanner without
 * the full master-symbol detection. Returns up to max_candidates
 * FP positions ordered by confidence.
 *
 * @return Number of candidates found (0 if none).
 */
jab_int32 jabFindFpCandidates(
    jab_bitmap* bitmap,
    jab_fp_candidate* candidates,
    jab_int32 max_candidates
);
```

This function is then bridged through JNI for Kotlin access:

```kotlin
// New in JABCodeMobile.kt
external fun nativeFindFpCandidates(
    bitmap: Bitmap,
    maxCandidates: Int
): IntArray?  // Returns [x0,y0,ms0,type0,conf0, x1,...] or null
```

### 3.5 Sequence diagram — SEARCH mode invocation

```
┌─────────┐    ┌──────────────────┐    ┌───────────────┐    ┌────────────┐
│Analyzer │    │Camera2JABCode    │    │ROIDetector    │    │JABCodeMobile│
│         │    │Analyzer          │    │(default)      │    │JNI          │
└────┬────┘    └────────┬─────────┘    └──────┬────────┘    └──────┬─────┘
     │                  │                     │                    │
     │ frame N          │                     │                    │
     │─────────────────▶│                     │                    │
     │                  │ detect(bitmap)      │                    │
     │                  │────────────────────▶│                    │
     │                  │                     │ downsample 1/4     │
     │                  │                     │                    │
     │                  │                     │ findFpCandidates   │
     │                  │                     │───────────────────▶│
     │                  │                     │ candidates (3-4)   │
     │                  │                     │◀───────────────────│
     │                  │                     │ compute bbox       │
     │                  │  Rect?              │ scale to original  │
     │                  │◀────────────────────│                    │
     │                  │                     │                    │
     │                  │ if non-null:        │                    │
     │                  │   transition to     │                    │
     │                  │   TRACKING mode     │                    │
     │                  │                     │                    │
```

### 3.6 Risks (Cassandra)

- **Downsampled FP detection may miss small JABCodes**: if the JABCode is < 80 px in the original frame, downsampling to /4 leaves < 20 px which can't be reliably FP-detected. Mitigation: if no FPs found at /4, retry at /2 before giving up. Cost: ~2x detection time on miss path, no cost on hit path.
- **False positives on visually similar content**: any image with 3-4 colored corner squares could falsely trigger. Mitigation: confidence filtering on FP candidates; require the candidates to fit a plausible quadrilateral aspect ratio.

### 3.7 Cross-reference

- [Bayer demosaicing constraints](https://en.wikipedia.org/wiki/Demosaicing) — downsample by power-of-two for clean color reproduction
- Existing reuse: `src/jabcode/detector.c::findMasterSymbol` (4035 lines reachable)

---

## 4. SEARCH/TRACKING State Machine (PR 4) — Design Wireframes

### 4.1 Why this PR exists

PR 2 ships the mechanism, PR 3 ships the detector. PR 4 wires them together into an automatic scanning state machine that dynamically engages the crop-region zoom when a JABCode is detected.

### 4.2 State diagram

```
                  ┌──────────────────────────┐
                  │   SEARCH                  │
       ┌─────────▶│   - Full-sensor crop      │◀─────────┐
       │          │   - 1280x720 stream       │          │
       │          │   - Run ROIDetector each  │          │
       │          │     analysis frame        │          │
       │          │   - SDK consumer sees     │          │
       │          │     "scanning" state      │          │
       │          └──────────┬───────────────┘          │
       │  staleCount         │                          │
       │  >= maxStaleness    │ detect() returns         │
       │                     │ non-null Rect            │
       │                     ▼                          │
       │          ┌──────────────────────────┐          │
       │          │   TRACKING                │          │
       │          │   - SCALER_CROP_REGION    │          │
       │          │     set to detected bbox  │          │
       │          │     (with margin)         │          │
       │          │   - 1280x720 stream is    │          │
       │          │     now JUST the JABCode  │          │
       │          │   - Run full JABCode      │          │
       │          │     decoder each frame    │          │
       │          │   - SDK consumer sees     │          │
       │          │     "tracking" state      │          │
       │          └──────────┬───────────────┘          │
       │                     │                          │
       └─────────────────────┘                          │
                  (decode fails N consecutive times)    │
                                                        │
                  ┌───────────────────────────────┐    │
                  │   SUSPENDED (consumer opt-in)  │    │
                  │   - User dwell on successful   │    │
                  │     scan; keep current crop    │    │
                  │   - Resume on user action      │────┘
                  └────────────────────────────────┘
```

### 4.3 State transitions

| From       | To         | Trigger                                  | Action                                            |
|------------|------------|------------------------------------------|---------------------------------------------------|
| SEARCH     | TRACKING   | `ROIDetector.detect()` returns non-null  | `setCropRegion(bbox + margin)`; reset staleCount |
| TRACKING   | SEARCH     | Decode failed N consecutive frames       | `setCropRegion(null)`; increment session retry   |
| TRACKING   | SUSPENDED  | Decode succeeded AND consumer set dwell  | Stop analysis until consumer calls `resume()`    |
| SUSPENDED  | SEARCH     | Consumer calls `resume()`                | `setCropRegion(null)`                            |

### 4.4 Consumer-visible state callback

```kotlin
package com.jabauth.jabcode.scanner

enum class ScannerState {
    IDLE,        // not actively scanning
    SEARCHING,   // looking for a JABCode in the full frame
    TRACKING,    // JABCode found; cropped to its region, decoding
    SUSPENDED,   // successful decode; awaiting consumer's resume()
    ERROR
}

interface JABCodeScanner {
    // ... existing methods ...

    /**
     * Observe scanner state transitions. The SDK posts on the main
     * thread by default; specify [executor] for a different thread.
     */
    fun observeState(executor: Executor? = null, listener: (ScannerState) -> Unit)
}
```

### 4.5 Verification protocol (Heisenberg's Round 2)

To disentangle SCALER_CROP_REGION effects from AE-region effects (per Heisenberg's contamination concern), the verification protocol REQUIRES three conditions per fixture:

| Condition | SCALER_CROP_REGION | CONTROL_AE_REGIONS | Purpose |
|---|---|---|---|
| A | Full frame | Full frame | Pre-this-PR baseline |
| B | Full frame | Restricted to JABCode bbox | Isolates AE-region effect |
| C | JABCode bbox | Full frame (or unset) | Isolates crop-region effect |
| D | JABCode bbox | JABCode bbox (default behavior) | Combined effect |

Decode rates compared (A, B, C, D) tell us how much improvement comes from each lever independently.

#### Interpretation rules (resolves Session 7 Q6 — Heisenberg's methodology)

Each comparison isolates a single variable:

| Comparison | Isolated effect | Reading the result |
|------------|-----------------|--------------------|
| A → B | AE-region effect only | If decode rate jumps A→B, AE region restriction was helping all along; the audit's workstream #4 (AE/AWB lock) is the correct lever |
| A → C | Crop-region effect only (resolution boost) | If decode rate jumps A→C, ROI/zoom is the load-bearing variable; PR 4's full state machine is justified |
| A → D | Combined effect | Confirms the additive case; should exceed both B and C if effects are independent |
| C → D | Marginal value of AE-region on top of crop | If D ≈ C, AE-region adds little; if D > C significantly, the combination matters |

Each effect is then graded against decode-rate-per-attempt (the OK/(OK+FAIL) ratio measured per PID per fixture), NOT total attempt count, to avoid the "longer scan = more attempts" confounder.

### 4.6 Risks (Cassandra Round 1)

- **Worst case: flaky TRACKING mode that loses the viewfinder.** If state machine gets stuck in TRACKING with a stale crop, the user thinks "the camera is broken." Mitigation: aggressive staleness detection (5 frames max), prominent SDK telemetry for state transitions, fallback button in diagnostic-app UI to force SEARCH.
- **AE re-convergence at SEARCH→TRACKING transition**: when crop region changes, AE/AWB re-meter. Brief flicker possible. Mitigation: only trigger AE recalculation if exposure compensation differs by >0.5 EV; otherwise preserve AE state across transitions.

### 4.7 Cross-reference

- [`CONTROL_AE_REGIONS`](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AE_REGIONS) — Heisenberg's noted lever for explicit AE region control
- [`MeteringRectangle`](https://developer.android.com/reference/android/hardware/camera2/params/MeteringRectangle) — used to specify metering areas
- Hardware level check: `CONTROL_MAX_REGIONS_AE` must be ≥ 1 for region-based AE

---

## 5. Parallel UX Work — Module-Size Telemetry + "Move Closer" Hint

### 5.1 Why this works in parallel

Prometheus's reframe from Session 6: maybe the problem is solved by users holding the phone closer, not by software detecting and cropping. This UX hint can ship alongside the camera-side work and will benefit users immediately regardless of PR 1's outcome.

### 5.2 What to surface in the UI

The decoder already produces `module_size` in pixels (visible in FAIL_ATTR markers like `module_size=27.61`). Surface this to the UI:

```kotlin
data class FrameQualityHint(
    val moduleSize: Float,
    val suggestion: ScannerSuggestion
)

enum class ScannerSuggestion {
    OK,                  // module_size ≥ 12 px — comfortable scanning
    MOVE_CLOSER,         // module_size 4-11 px — readable but borderline
    HOLD_STEADY,         // detection succeeding intermittently
    MOVE_FARTHER,        // module_size > 60 px — JABCode fills the frame
    BRIGHTER_LIGHT,      // low brightness detected (uses existing quality metric)
    NONE
}
```

A simple HUD overlay in the diagnostic app can render the suggestion as a chip above the viewfinder. Module-size thresholds derived from the decoder's empirical 3-px minimum and the WS-5 print-trace observations that 17-20 px is barely-decodable.

### 5.3 Risk

False alarms — a user holding the phone perfectly may get spurious "MOVE_CLOSER" hints if a single frame happens to have low module_size due to motion blur. Mitigation: debounce hints across 3-5 frames before showing the suggestion.

---

## 6. Sequencing & Dependencies Summary

```
┌───────────────────────────────────────────────────────────────────┐
│  Trunk (post-WS-camera #3)                                         │
│  ├─ PR 1  Pinch-zoom verification        ← Decision gate           │
│  │   │                                                              │
│  │   ├──[results negative]──> PIVOT to slave-decode investigation  │
│  │   │                                                              │
│  │   └──[results positive]──> PROCEED to PR 2                      │
│  │                                                                  │
│  ├─ PR 2  setCropRegion(Rect?) SDK API                              │
│  │                                                                  │
│  ├─ PR 3  ROIDetector + heuristic default                           │
│  │                                                                  │
│  └─ PR 4  SEARCH/TRACKING state machine                             │
│                                                                     │
│  In parallel (any time):                                            │
│  ├─ Workstream #4 (AE/AWB lock) — independent of ROI                │
│  ├─ Workstream T3 (allocation churn) — independent of ROI           │
│  └─ UX module-size telemetry + "move closer" hint                   │
└───────────────────────────────────────────────────────────────────┘
```

PR 1 can run in parallel with audit workstream #4. PRs 2–4 should land after #4 (which also touches AE/AWB and would otherwise conflict).

### 6.1 CameraX migration consideration (resolves Session 7 Q4)

The audit notes CameraX dependencies are declared in gradle but unused; the active scanner uses direct Camera2. CameraX has built-in ROI/zoom abstractions (`CameraControl.setZoomRatio`, `ImageAnalysis` use-case with `setTargetResolution`) that would do most of PRs 2-4's work automatically.

**Decision: stay on direct Camera2 for ROI work.** Rationale:

- We have substantial recent investment in Camera2-specific code (session modernization in `Camera2Preview.kt:232`, maxImages tuning, LLB AE wiring) — discarding it for CameraX would invalidate the empirical data we've gathered
- CameraX's abstractions are convenient but opinionated; our state machine (SEARCH/TRACKING with explicit transitions) wants per-frame control that CameraX flattens away
- Migration would require re-validating every camera-control improvement on the new substrate
- The team's accumulated knowledge is concentrated in the direct Camera2 path

**Future opt-in**: a CameraX migration could be considered as a separate workstream after the ROI feature stabilizes, IF it materially simplifies multi-camera or extension support. Not now.

---

## 7. Cross-References to Android Best Practices

| Area | Best practice reference |
|---|---|
| Camera2 zoom API | [`SCALER_CROP_REGION`](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#SCALER_CROP_REGION) — coordinate system is the active sensor array |
| Max zoom query | [`SCALER_AVAILABLE_MAX_DIGITAL_ZOOM`](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) |
| Pinch gestures | [`ScaleGestureDetector`](https://developer.android.com/reference/android/view/ScaleGestureDetector) + `SimpleOnScaleGestureListener` |
| AE region control | [`CONTROL_AE_REGIONS`](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AE_REGIONS) + [`MeteringRectangle`](https://developer.android.com/reference/android/hardware/camera2/params/MeteringRectangle) |
| Max AE regions | [`CONTROL_MAX_REGIONS_AE`](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#CONTROL_MAX_REGIONS_AE) |
| Hardware level | [`INFO_SUPPORTED_HARDWARE_LEVEL`](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#INFO_SUPPORTED_HARDWARE_LEVEL) — LEGACY devices must be detected and fall through |
| Reference apps | [pinguo-yuyidong/Camera2 DisplayFragment.java](https://github.com/pinguo-yuyidong/Camera2/blob/master/app/src/main/java/us/yydcdut/androidltest/ui/DisplayFragment.java) — pinch-to-zoom reference impl |
| Boundary alignment | Crop dimensions should be even or aligned to 4-pixel boundaries to avoid HAL rounding |

---

## 8. Risk Register (Compiled from Council Session 6)

| ID | Risk | Source | Mitigation |
|----|------|--------|------------|
| R1 | Manual zoom doesn't actually help | Sherlock | PR 1 is the test; if negative, abort the chain |
| R2 | State machine flakiness — stuck in TRACKING | Cassandra | Aggressive staleness detection; SDK state telemetry |
| R3 | AE re-convergence on crop change | Heisenberg | Preserve AE state across transitions; verification protocol disentangles |
| R4 | False ROI positives | Cassandra | Confidence + quadrilateral plausibility filter |
| R5 | SDK over-opinionation | Robin Hood | Ship interface + default; consumer can override |
| R6 | UX over-reliance on hints | Prometheus | Debounce, threshold tuning |
| R7 | LEGACY hardware level lacks SCALER_CROP_REGION | Best practices | Detect and fall through to full-frame on LEGACY |
| R8 | TextureView transform interaction with gesture coords | Best practices | Implement gesture on overlay view, not TextureView directly |

---

## 9. Verification Methodology Summary

All four PRs share a common verification skeleton:

- **PR 1**: A/B/C zoom comparison per fixture; per-fixture decision rules per §1.6.1
- **PR 2**: API-only PR; no on-device test; unit-test the coordinate transform helpers
- **PR 3**: Two-pronged — (a) synthetic positive fixtures: JABCodes at various positions/sizes; (b) **adversarial negative fixtures** per §9.4 below; ROIDetector must correctly identify positives AND reject negatives
- **PR 4**: Heisenberg's 4-condition protocol (A/B/C/D in §4.5) per fixture, interpreted via the rules in §4.5.1

### 9.4 Adversarial fixtures for PR 3 (per Kano + Session 7)

Because §0.1 places the default ROI detector at Must-be tier, PR 3 cannot ship without explicit false-positive verification. The detector must REJECT (return null) on each of the following synthetic test images:

| Fixture | Description | Why it's a confusable |
|---------|-------------|-----------------------|
| `rubiks_cube.png` | Photo of a Rubik's cube on neutral background | 9 colored squares; FP-candidate detector may find 4 in corner-like positions |
| `magazine_4corners.png` | Magazine cover with 4 corner color-blocks | The classic FP-confusion case |
| `qr_code.png` | A QR code (not a JABCode) | Has 3 finder patterns; tests rejection of related-but-wrong barcode formats |
| `data_matrix.png` | A Data Matrix barcode | Different finder pattern structure; should reject |
| `aztec_code.png` | An Aztec barcode | Bullseye center, no corner FPs; should reject |
| `random_blocks.png` | 4 randomly-positioned colored squares on grey | Forms a quadrilateral but not an FP arrangement |
| `dark_scene.png` | A dimly-lit scene with no barcode | Tests behavior under low-signal input |
| `colorful_clutter.png` | A busy multi-color image with no barcode | Tests behavior under high-noise input |

PR 3 must pass ≥ 95% of adversarial fixtures (reject correctly) AND ≥ 90% of positive fixtures (detect correctly within 20% margin). These thresholds are first-pass — refinement after PR 3 lands.

Adversarial fixtures live at `src/jabcode/test/data/roi-adversarial/` and are checked into the repo with the PR.

---

## 10. Sources

Primary references:

- [Camera2 SCALER_CROP_REGION (java2s example)](http://www.java2s.com/example/java-api/android/hardware/camera2/capturerequest/scaler_crop_region-0.html)
- [Camera2 SCALER_AVAILABLE_MAX_DIGITAL_ZOOM (java2s example)](http://www.java2s.com/example/java-api/android/hardware/camera2/cameracharacteristics/scaler_available_max_digital_zoom-0.html)
- [pinguo-yuyidong/Camera2 — DisplayFragment.java](https://github.com/pinguo-yuyidong/Camera2/blob/master/app/src/main/java/us/yydcdut/androidltest/ui/DisplayFragment.java)
- [OpenCamera Camera2Controller](https://github.com/almalence/OpenCamera/blob/master/src/com/almalence/opencam/cameracontroller/Camera2Controller.java)
- [Zoomable Camera2Preview gist](https://gist.github.com/siralam/1c4000a5af069ddb366705edd33ebeea)
- [Pinch-to-zoom with ScaleGestureDetector — Android-er](http://android-er.blogspot.com/2016/05/implement-pinch-to-zoom-with.html)
- [Kotlin pinch recognition — Techotopia](https://www.techotopia.com/index.php/Kotlin_-_Implementing_Custom_Gesture_and_Pinch_Recognition_on_Android)
- [Camera2 ControlMaxRegionsAe (Microsoft Learn)](https://learn.microsoft.com/en-us/dotnet/api/android.hardware.camera2.cameracharacteristics.controlmaxregionsae)
- [Camera2 ControlAeRegions (Microsoft Learn)](https://learn.microsoft.com/en-us/dotnet/api/android.hardware.camera2.capturerequest.controlaeregions)
- [Dynamsoft barcode detection on Android Camera2](https://www.dynamsoft.com/codepool/android-barcode-detection-fast-moving-object.html)
- [Camera2 Focus Areas — Google example issue](https://github.com/googlearchive/android-Camera2Basic/issues/19)
- [Android CameraMetadata reference](https://developer.android.com/reference/android/hardware/camera2/CameraMetadata.html)

In-repo cross-references:

- [`docs/camera-control-audit.md`](camera-control-audit.md) — the audit that catalyzed this design work
- [`docs/cassandra-register/H_mode0_partI_decode_failure.md`](cassandra-register/H_mode0_partI_decode_failure.md) — open hypothesis this work targets
- [`docs/cassandra-register/H_partI_clean_data_failure.md`](cassandra-register/H_partI_clean_data_failure.md) — open hypothesis this work targets
- `project_jabcode_screen_vs_print_physics.md` (memory) — physics rationale for module-pixel ratio being load-bearing
- `project_camera2_control_audit.md` (memory) — audit summary
