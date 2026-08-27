# Plan — move the camera preview from `TextureView` to `SurfaceView`

**Status:** proposed, not started. **Owner module:** `framework/ui-components`.
**Blast radius:** display only — see "What is not at risk" before estimating.

## Why

The reader's camera preview is a `TextureView`, so every camera frame is a texture upload and a
composite on the render thread that is also drawing the HUD and the reticle. The platform camera
app gives its preview a dedicated `SurfaceView` layer, which the compositor handles without the
app being involved at all.

Confirmed at runtime, not inferred from source. `dumpsys SurfaceFlinger` shows the camera app
owning its own layer:

```
Layer [1343] SurfaceView[com.sec.android.app.camera/...]@0(BLAST)#1343
```

while jabauth-verify has no surface layer — its preview lives inside the activity's own window.

### What the stock camera can and cannot tell us

An earlier draft of this plan claimed the app renders 529 frames in 35 seconds against the stock
camera's 640, and called that a 17% shortfall. **That comparison was invalid and has been
withdrawn.**

Measured afterwards: the stock camera renders **0 frames** when held still, on both phones, while
plainly showing a live preview. Its preview never touches app-side rendering, so `gfxinfo` was
only ever counting its chrome — autofocus indicators and exposure readouts, which update when the
phone moves. The two numbers are different quantities:

| | what its frame count contains |
| --- | --- |
| jabauth-verify | UI **plus preview compositing** |
| stock camera | UI chrome only |

The stock camera is therefore evidence for the architectural claim — a `SurfaceView` preview costs
the hosting app nothing — and is **not** a valid control for frame rate or jank, because it is not
doing the work our app is doing.

### What the justification actually rests on

1. The architectural difference, verified in `SurfaceFlinger` on the device.
2. Its consequence: preview compositing competes with HUD drawing on one thread, which is the
   shape of the reported symptom (smooth when still, jittery when moved, worse the longer the
   scanner has been open).
3. A before-and-after on **our own app**, which is the only comparison where both arms do the same
   work. That measurement does not exist yet, and producing it is Phase 0.

This is worth stating plainly because the investigation that led here killed four hypotheses --
gradient scrims, thermal throttling, AE lock thrashing, motion-gate recomposition -- each of which
looked convincing until it was measured against something. A fifth belief that cannot be measured
is not an improvement on those.

## What is not at risk

Two facts narrow this far below what "replace the camera preview" usually implies. Both were
checked in the source before this plan was written.

**Decode cannot be affected.** Preview and analysis are separate streams with separate
`OutputConfiguration`s, both added as targets of one request
(`Camera2Preview.kt:839-883`). The analyzer reads `reader.surface`, an `ImageReader`, and never
touches the preview surface. Changing what the preview draws into cannot change what the decoder
sees.

**The rotation maths is already surface-independent and already tested.** `OrientationCalculator`
lives in `framework/jabcode-sdk` with 13 tests across `OrientationCalculatorTest` and
`DeviceOrientationTest`. The dangerous part — sensor-versus-display angle arithmetic, the
front-camera sign flip — is not being rewritten.

## What is actually changing

`updateTransform` (`Camera2Preview.kt:1279`) does two unrelated jobs in one function. Only the
second belongs to `TextureView`.

**Job 1 — publish the frame rotation.** Computes `relativeRotation` and calls `onFrameRotation`,
which drives `RoiSpec` and the analyzer's crop. Needs the display rotation and the camera
characteristics; nothing else. Carries over untouched, as does the `DisplayManager` listener that
catches the 180-degree flips a view resize does not report.

**Job 2 — apply a display matrix.** `matrix.setScale(...)` then
`matrix.postRotate(-surfaceRotationDegrees, ...)` then `textureView.setTransform(matrix)`. Two
things bundled:

- *centre-crop fill*, which exists only to undo `TextureView`'s default stretch-to-fit. Under
  `SurfaceView` this becomes a sizing decision rather than a transform.
- *rotation compensation*, which exists only because `TextureView` does not rotate its content
  with the display. A `SurfaceView` is part of the window and the compositor applies the buffer
  transform, so this does not get ported — it gets deleted.

Those two properties share a cause. The reason `TextureView` is slow (the app owns every frame) is
the same reason it needs a hand-built rotation matrix.

## Phases

Each phase ends at a gate. A phase that cannot pass its gate stops the plan rather than
proceeding on optimism.

### Phase 0 — establish OUR OWN baseline (no code)

Not a stock-camera comparison; see above for why that cannot work. The baseline has to be the
thing that will change, measured the way Phase 3 will measure it.

- Both phones, both states: held still, and moved continuously. Movement matters — every reading
  taken on a desk showed near-zero jank while the reported fault was happening.
- Minimum three samples per cell, interleaved. Single pairs produced two wrong conclusions during
  the investigation behind this plan.
- Record frames rendered, jank percentage, p95, p99 and GPU p90 for every sample, plus whether
  the app was freshly installed. Readings taken immediately after an `adb install` ran 9-15% janky
  and settled to ~2% once ART had compiled; that artifact accounted for most of the variance
  originally mistaken for signal.

**Gate:** a table with a median and a spread for each cell, on both devices. If the spread swamps
the difference Phase 3 hopes to show, say so now and either widen the sample or abandon the plan
-- do not proceed to a change whose benefit the instrument cannot resolve.

### Phase 1 — split `updateTransform` (pure refactor)

Separate Job 1 from Job 2. Job 1 becomes a function taking display rotation and characteristics
and returning the rotation to publish; Job 2 stays `TextureView`-specific and calls it.

No behaviour change. This phase has standalone value: the function currently reads as "update the
transform" while silently owning the analyzer's ROI correctness, and that conflation is why the
rotation contract is hard to reason about.

**Gate:** the 13 existing orientation tests pass unchanged; a new test asserts the published
rotation for the four display rotations against both sensor orientations; on-device decode still
succeeds against a known symbol.

### Phase 2 — swap the surface

- `AndroidView { SurfaceView(ctx) }` with `SurfaceHolder.Callback`. **Not**
  `AndroidExternalSurface` — that needs Compose foundation 1.6.0+ and this module is pinned at
  `COMPOSE_VERSION=1.5.4` (`gradle.properties`). Raising Compose is a separate change and must not
  be smuggled into this one.
- `setZOrderMediaOverlay(true)` so the HUD and reticle stay above the preview.
- Size the surface to the preview aspect and let the parent clip; delete the matrix.
- The composable's public signature does not change, so no call site moves.

**Gate:** preview is visible, right way up, and correctly cropped in all four display rotations,
on both back and front cameras, on both phones.

### Phase 3 — verify against the Phase 0 baseline

- Interleaved A/B of the `TextureView` and `SurfaceView` builds, minimum three samples each,
  alternating so thermal drift and scene changes hit both arms equally. One pair of runs is not a
  result; that error was made twice in the investigation that produced this plan.
- Decode success rate measured before and after against a fixed symbol at a fixed distance.

**Gate:** a measurable improvement against the Phase 0 baseline on the SM-S918U, outside that
cell's recorded spread, **and** decode success rate no worse. A smoother preview that reads fewer
symbols is a regression, not a fix. The comparison is our app against our app; the stock camera
appears nowhere in this gate.

### Phase 4 — re-vendor into the reader

`jabauth-verify-android` consumes this as a vendored AAR with a provenance guard.

- `./gradlew :framework:ui-components:assembleRelease`
- Copy into `app/libs/`, regenerate the `.provenance` file **deriving every value**
  (`git rev-parse`, `sha256sum`, `stat`), never transcribing.
- `:app:validateVendoredAars`, full JVM suite, install, walk the scanner.

**Gate:** provenance validates, suite green with zero skipped, scanner walks on both phones.

## Risks

| Risk | Why it matters | Mitigation |
| --- | --- | --- |
| Z-ordering | `SurfaceView` punches through the window; the HUD and reticle can end up behind it | `setZOrderMediaOverlay(true)`, checked visually in Phase 2's gate |
| `sensorOrientation == 0` | A real branch in the current code, for Chromebook-class devices we cannot test on | Keep the branch; cover it in the Phase 1 unit test rather than on hardware |
| Front camera | Sensor and display rotations add rather than subtract | Explicit case in the Phase 2 gate |
| Rounded corners / animation | `SurfaceView` cannot be transformed like a view | The preview fills the screen and is never animated; if that changes, this decision needs revisiting |
| Other consumers | `diagnostic-app` and any studio Android surface use the same composable | Signature unchanged; build all consumers before merging |

## Rollback

The composable's signature does not change, so reverting is one file in `ui-components` plus a
re-vendor. Phase 1 is independently valuable and would be kept even if Phases 2 and 3 are
abandoned.

## What would falsify the premise

If Phase 0's spread swamps any plausible improvement, or Phase 3 shows no movement outside that
spread, then `TextureView` was not the constraint and the remaining cost is elsewhere — most likely the per-frame YUV-to-Bitmap conversion in the analysis
path, which this plan does not touch. Say so and stop, rather than continuing to tune a surface
that was never the problem.
