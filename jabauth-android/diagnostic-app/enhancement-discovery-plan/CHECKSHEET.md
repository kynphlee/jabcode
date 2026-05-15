# Enhancement Discovery Checksheet

> **Instructions:** Update status and date as each step completes. Mark TDD column only after test is written AND passing.
>
> Status key: `[ ]` pending | `[~]` in progress | `[x]` done | `[!]` blocked

---

## Phase 3A: Camera Signal + Geometry (Parallel)

### 01 -- YUV-to-Bitmap Pipeline (P0)

| Step | Task | Status | TDD | Date |
|------|------|:------:|:---:|------|
| 01.1 | Write unit test: YUV byte array with known RGB -> Bitmap -> verify pixel values | [ ] | [ ] | |
| 01.2 | Write unit test: compare JPEG-roundtrip vs direct-conversion color error | [ ] | [ ] | |
| 01.3 | Implement `yuv420ToBitmapDirect()` in CameraUtils.kt | [ ] | [ ] | |
| 01.4 | Instrumented test: Camera2 frame -> direct conversion -> verify no chroma loss | [ ] | [ ] | |
| 01.5 | Replace `yuv420ToBitmap()` call in production path | [ ] | [ ] | |
| 01.6 | Verify: deploy, capture logcat, confirm RGB values at FP centers show saturation | [ ] | [ ] | |

### 02 -- Capture Request Tuning (P1-P3)

| Step | Task | Status | TDD | Date |
|------|------|:------:|:---:|------|
| 02.1 | Add AWB/AE lock fields to CameraConfig.kt | [ ] | [ ] | |
| 02.2 | Add NR mode and edge mode fields to CameraConfig.kt | [ ] | [ ] | |
| 02.3 | Unit test: CameraConfig builder produces correct lock combinations | [ ] | [ ] | |
| 02.4 | Implement AE lock callback in Camera2Preview.kt (lock after 10 converged frames) | [ ] | [ ] | |
| 02.5 | Implement AWB lock in Camera2Preview.kt (DAYLIGHT preset or lock after convergence) | [ ] | [ ] | |
| 02.6 | Switch AF from CONTINUOUS\_PICTURE to CONTINUOUS\_VIDEO | [ ] | [ ] | |
| 02.7 | Set CONTROL\_NOISE\_REDUCTION\_MODE = OFF | [ ] | [ ] | |
| 02.8 | Set CONTROL\_EDGE\_MODE = OFF | [ ] | [ ] | |
| 02.9 | Add AWB mode selector to SettingsScreen.kt and SettingsRepository.kt | [ ] | [ ] | |
| 02.10 | Instrumented test: verify CaptureRequest contains expected keys after lock | [ ] | [ ] | |
| 02.11 | Verify: deploy, capture logcat, confirm stable exposure/color across 20+ frames | [ ] | [ ] | |

### 03 -- Geometry Estimation Fix (P4)

| Step | Task | Status | TDD | Date |
|------|------|:------:|:---:|------|
| 03.1 | Syntax-verify: gcc -fsyntax-only detector.c with proposed change | [ ] | [ ] | |
| 03.2 | Replace per-FP module\_size with edge-derived v\_ms in Phase 2M block | [ ] | [ ] | |
| 03.3 | Add sanity clamp: if per-FP ms within 30% of v\_ms, allow trapezoid correction | [ ] | [ ] | |
| 03.4 | Update H3\_GEOM log to include v\_ms and clamped values | [ ] | [ ] | |
| 03.5 | Build diagnostic app with native recompilation | [ ] | [ ] | |
| 03.6 | Deploy and capture logcat trace | [ ] | [ ] | |
| 03.7 | Verify: h\_ext0/edge ratio now 0.7-1.3 (not 0.3-4.2) | [ ] | [ ] | |
| 03.8 | Verify: FP center RGB samples show correct colors (black, black, yellow, cyan) | [ ] | [ ] | |

---

## Phase 3B: Quality Gating

### 06 -- Image Quality Gating (P7)

| Step | Task | Status | TDD | Date |
|------|------|:------:|:---:|------|
| 06.1 | Unit test: ImageQualityAnalyzer returns below-threshold for synthetic blurry image | [ ] | [ ] | |
| 06.2 | Add quality gate in Camera2JABCodeAnalyzer.analyze() before decode call | [ ] | [ ] | |
| 06.3 | Unit test: analyzer skips decode when quality below threshold | [ ] | [ ] | |
| 06.4 | Verify: deploy, confirm "quality gate skip" log messages for blurry frames | [ ] | [ ] | |

---

## Phase 3C: HDR Capture

### 04 -- HDR Capture Session (P5)

| Step | Task | Status | TDD | Date |
|------|------|:------:|:---:|------|
| 04.1 | Instrumented test: query device for DYNAMIC\_RANGE\_TEN\_BIT capability | [ ] | [ ] | |
| 04.2 | Instrumented test: query device for HLG10 profile support | [ ] | [ ] | |
| 04.3 | Add HDR enable flag to CameraConfig.kt | [ ] | [ ] | |
| 04.4 | Implement OutputConfiguration.setDynamicRangeProfile in Camera2Preview.kt | [ ] | [ ] | |
| 04.5 | Update StreamConfigValidator for HDR stream combinations | [ ] | [ ] | |
| 04.6 | Add HDR toggle to SettingsScreen.kt | [ ] | [ ] | |
| 04.7 | Verify: deploy, confirm 10-bit session established in logcat | [ ] | [ ] | |
| 04.8 | Verify: compare FP center RGB saturation HDR-on vs HDR-off | [ ] | [ ] | |

---

## Phase 3D: 10-Bit Pipeline

### 05 -- Full 10-Bit Color Pipeline (P6)

| Step | Task | Status | TDD | Date |
|------|------|:------:|:---:|------|
| 05.1 | Research: confirm SM-S938U supports YCBCR\_P010 format in ImageReader | [ ] | [ ] | |
| 05.2 | Implement `p010ToBitmap()` or `p010ToRgb10Buffer()` in CameraUtils.kt | [ ] | [ ] | |
| 05.3 | Unit test: synthetic P010 data -> 10-bit RGB values preserved | [ ] | [ ] | |
| 05.4 | Modify JNI bridge to accept 10-bit pixel data | [ ] | [ ] | |
| 05.5 | Modify detector.c color classification for 10-bit input | [ ] | [ ] | |
| 05.6 | Instrumented test: end-to-end 10-bit capture -> decode attempt | [ ] | [ ] | |
| 05.7 | Verify: compare color separation metrics 8-bit vs 10-bit | [ ] | [ ] | |

---

## Milestone Checkpoints

| Milestone | Criteria | Status | Date |
|-----------|----------|:------:|------|
| **M1: First decode** | Any single frame decodes successfully (result=1) | [ ] | |
| **M2: Reliable 8-color** | >50% of frames decode in 8-color mode | [ ] | |
| **M3: HDR baseline** | HDR capture session active, 10-bit tone-mapped to 8-bit | [ ] | |
| **M4: 16-color viable** | Any frame decodes 16-color from screen display | [ ] | |
| **M5: Full 10-bit** | End-to-end 10-bit pipeline operational | [ ] | |
