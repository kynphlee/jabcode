# Camera Pipeline Enhancement Discovery Plan

> **Goal:** Achieve the first successful JABCode decode from a screen-displayed barcode by systematically fixing the camera signal pipeline and geometry estimation.
>
> **Date:** 2026-05-15
> **Branch:** `swift-java-poc`
> **Tracker:** [CHECKSHEET.md](CHECKSHEET.md)

---

## Glossary

| Term | Definition |
|------|-----------|
| **AE** | Auto-Exposure -- camera algorithm that adjusts sensor exposure time and gain to target a brightness level |
| **AWB** | Auto White Balance -- camera algorithm that adjusts color temperature to neutralize color casts |
| **AF** | Auto-Focus -- camera algorithm that adjusts lens position for sharpness |
| **BT.709** | Standard color gamut for SDR content (equivalent to sRGB) |
| **BT.2020** | Wide color gamut for HDR content; covers 75.8% of visible spectrum vs BT.709's 35.9% |
| **Chroma subsampling** | Reduction of color channel resolution relative to luminance; JPEG uses 4:2:0 (half horizontal and vertical chroma resolution) |
| **EV** | Exposure Value -- logarithmic scale; +1 EV = 2x brighter |
| **FP** | Finder Pattern -- one of 4 corner markers in a JABCode symbol (FP0=black TL, FP1=black TR, FP2=yellow BR, FP3=cyan BL) |
| **HLG10** | Hybrid Log-Gamma 10-bit -- baseline HDR profile mandatory on Android 13+ 10-bit devices |
| **LDPC** | Low-Density Parity-Check -- error correction code used by JABCode |
| **module\_size** | Pixel width of one barcode module as measured by the finder pattern detector |
| **NV21** | Android's native YUV format with interleaved V-U chroma plane |
| **Perspective transform** | 4-point projective mapping from camera pixel coordinates to barcode module coordinates |
| **YUV\_420\_888** | Camera2's standard output format: full-resolution luminance (Y), half-resolution chrominance (U, V) |

---

## Architecture Impact Matrix

Each enhancement touches specific layers. **F** = Framework change, **D** = Diagnostic app change, **N** = Native C change.

| # | Enhancement | Framework jabcode-sdk | Framework ui-components | Diagnostic App | Native (detector.c) | Test Layer |
|---|------------|:--------------------:|:----------------------:|:--------------:|:-------------------:|:----------:|
| 01 | [YUV-to-Bitmap Pipeline](01-yuv-bitmap-pipeline.md) | **F** CameraUtils.kt | -- | -- | -- | Unit + Instrumented |
| 02 | [Capture Request Tuning](02-capture-request-tuning.md) | -- | **F** Camera2Preview.kt | **D** ScannerScreen.kt, SettingsRepository.kt | -- | Instrumented |
| 03 | [Geometry Estimation Fix](03-geometry-estimation.md) | -- | -- | -- | **N** detector.c | Verify via logcat trace |
| 04 | [HDR Capture Session](04-hdr-capture-session.md) | **F** CameraConfig.kt, StreamConfigValidator.kt | **F** Camera2Preview.kt | **D** SettingsScreen.kt | -- | Instrumented |
| 05 | [10-Bit Color Pipeline](05-ten-bit-pipeline.md) | **F** CameraUtils.kt, Camera2JABCodeAnalyzer.kt | **F** Camera2Preview.kt | -- | **N** decoder JNI bridge | Unit + Instrumented + Trace |
| 06 | [Image Quality Gating](06-image-quality-gating.md) | **F** ImageQualityAnalyzer.kt, Camera2JABCodeAnalyzer.kt | -- | **D** ScannerViewModel.kt | -- | Unit |

---

## File Reference (Current State)

### Framework: `framework/jabcode-sdk/src/main/java/com/jabauth/jabcode/`

| File | Role | Key Lines |
|------|------|-----------|
| `camera/CameraUtils.kt` | YUV-to-Bitmap conversion | L73-101: JPEG roundtrip (P0 target) |
| `camera/Camera2JABCodeAnalyzer.kt` | Frame acquisition + decode orchestration | L69-153: analyze() loop, acquireLatestImage |
| `camera/ImageQualityAnalyzer.kt` | Brightness/focus/contrast metrics | L78-88: analyze(), L163-198: Laplacian focus |
| `camera/config/CameraConfig.kt` | Config data class | L13-19: boolean flags only, no lock modes |
| `camera/StreamConfigValidator.kt` | Stream combination validation | L84-107: hardware level checks |
| `DecodeOptions.kt` | Decode timeouts and intervals | L17-28: 500ms analyze interval, 5s timeout |

### Framework: `framework/ui-components/src/main/java/com/jabauth/ui/scanner/`

| File | Role | Key Lines |
|------|------|-----------|
| `Camera2Preview.kt` | Camera2 composable with TextureView | L252-299: startRepeatingRequest (AE/AF/AWB config) |

### Diagnostic App: `diagnostic-app/src/main/java/com/jabauth/diagnostic/`

| File | Role | Key Lines |
|------|------|-----------|
| `ui/scanner/ScannerScreen.kt` | Scanner UI | L66-75: Camera2Preview call, exposureCompensation=+1 |
| `ui/scanner/ScannerViewModel.kt` | Scanner logic | L74-122: createAnalyzer, settings wiring |
| `data/SettingsRepository.kt` | Persistent settings | L41-47: Settings data class (no AWB/AE lock fields) |

### Native: `swift-java-wrapper/` + `src/jabcode/`

| File | Role | Key Lines |
|------|------|-----------|
| `src/jabcode/detector.c` | FP detection, perspective transform, sampling | Phase 2M edits at ~L2219-2290 |
| `src/jabcode/detector.h` | SCREEN\_DISPLAY\_MODE flag | L26: currently set to 1 |

---

## Deep-Dive Documents

| Priority | Document | Summary |
|----------|----------|---------|
| P0 | [01-yuv-bitmap-pipeline.md](01-yuv-bitmap-pipeline.md) | Replace lossy JPEG roundtrip with direct pixel conversion to preserve color fidelity |
| P1-P3 | [02-capture-request-tuning.md](02-capture-request-tuning.md) | Lock AWB/AE after convergence, switch AF mode, disable NR/edge enhancement |
| P4 | [03-geometry-estimation.md](03-geometry-estimation.md) | Replace unreliable FP module\_size with edge-derived square estimation |
| P5 | [04-hdr-capture-session.md](04-hdr-capture-session.md) | Enable 10-bit HDR capture for wider color separation |
| P6 | [05-ten-bit-pipeline.md](05-ten-bit-pipeline.md) | Full 10-bit data path from sensor to JNI decoder |
| P7 | [06-image-quality-gating.md](06-image-quality-gating.md) | Skip decode on blurry/dark frames to reduce wasted CPU cycles |

---

## Execution Order

```
Phase 3A: P0 + P1-P3 + P4  (camera signal + geometry -- independent, can parallelize)
Phase 3B: P7               (quality gating -- depends on P0 for accurate metrics)
Phase 3C: P5               (HDR session -- needs device capability check)
Phase 3D: P6               (10-bit pipeline -- depends on P5, largest effort)
```
