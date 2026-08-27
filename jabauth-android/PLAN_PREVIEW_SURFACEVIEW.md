# Plan — move the camera preview from `TextureView` to `SurfaceView`

**Status:** proposed, not started. **Owner module:** `framework/ui-components`.
**Blast radius:** display only — see "What is not at risk" before estimating.

## Why

The reader's scanner renders fewer frames than the device's own camera app, on the same phone,
in the same 35-second window, pointed at the same room.

| SM-S918U (Snapdragon 8 Gen 2) | Surface | Janky | p95 | p99 | Frames / 35s |
| --- | --- | --- | --- | --- | --- |
| Samsung camera | `SurfaceView` | 0.47% | 8 ms | 11 ms | **640** |
| jabauth-verify | `TextureView` | 2.46% | 10 ms | 17 ms | **529** |

The jank percentages are close enough to argue about. The frame counts are not: 18.3 fps against
15.1 fps, a 17% shortfall against a control running on the same hardware at the same moment. That
is what a user reports as "jitter when I move the phone", and it is why a 2.46% jank reading kept
being dismissed as acceptable — the number that mattered was the one nobody was reading.

Confirmed at runtime, not inferred from source. `dumpsys SurfaceFlinger` shows the camera app
owning a dedicated layer:

```
Layer [1343] SurfaceView[com.sec.android.app.camera/...]@0(BLAST)#1343
```

while jabauth-verify has no surface layer at all — the preview is a `TextureView` composited
inside the activity's own window, so every camera frame is a texture upload and a composite on the
render thread that is also drawing the HUD and the reticle.

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

### Phase 0 — establish the target on both devices (no code)

The measurement above exists for one phone. The SM-S938U (8 Elite) has never been compared against
its own stock camera, and an earlier "fix" on that device was declared verified on a single
unreplicated sample and later disproved. Do not repeat that.

- Run the stock-camera control on SM-S938U, same protocol: 35 s, moving, `dumpsys gfxinfo`.
- Record frames rendered, not only jank percentage.

**Gate:** a recorded frame-count delta for both phones. If the S938U matches its stock camera, the
change is worth doing but is an older-device fix, and should be scheduled as such rather than
sold as a general performance win.

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

### Phase 3 — verify against the control

- Interleaved A/B against the stock camera, minimum three samples each, alternating so thermal
  drift and scene changes hit both arms equally. One pair of runs is not a result; that error was
  made twice in the investigation that produced this plan.
- Decode success rate measured before and after against a fixed symbol at a fixed distance.

**Gate:** frame count within 5% of the stock camera on both phones, **and** decode success rate no
worse than the `TextureView` baseline. A smoother preview that reads fewer symbols is a
regression, not a fix.

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

If Phase 0 shows the SM-S938U already matching its stock camera, and Phase 3 shows the S918U
still short of its control after the swap, then `TextureView` was not the constraint and the
remaining cost is elsewhere — most likely the per-frame YUV-to-Bitmap conversion in the analysis
path, which this plan does not touch. Say so and stop, rather than continuing to tune a surface
that was never the problem.
