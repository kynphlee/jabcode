# 03 -- Geometry Estimation Fix

> **Priority:** P4
> **Layer:** Native -- `detector.c`
> **Risk:** Medium (modifies FP position estimation, core decode path)

---

## Context

When only 2 of 4 finder patterns are detected (FP0=black top-left, FP3=cyan bottom-left), the detector estimates FP1 and FP2 positions using the left edge vector and a perpendicular extension. Phase 2M introduced per-FP `module_size` scaling to create a trapezoid estimation that models perspective foreshortening.

## Current State

**File:** `src/jabcode/detector.c` \~L2219-2247

Phase 2M computes:
```c
h_extent_fp0 = fp_span * fps[0].module_size;   // 14 * FP0's module_size
h_extent_fp3 = fp_span * fps[3].module_size;   // 14 * FP3's module_size
```

Then places FP1 and FP2 at perpendicular distance `h_extent_fp0` and `h_extent_fp3` from FP0 and FP3 respectively.

## Gap

The `module_size` field from FP detection is **directionally dependent** -- it measures the horizontal projection of finder pattern bar widths, not the actual module size. When the barcode is rotated or viewed at an angle:

| Left edge (px) | Expected ms (edge/14) | FP0 ms (actual) | Ratio | h\_ext0/edge |
|---|---|---|---|---|
| 435.0 | 31.1 | 126.2 | **4.06x** | 4.06 |
| 460.0 | 32.9 | 52.6 | 1.60x | 1.60 |
| 952.5 | 68.0 | 20.3 | **0.30x** | 0.30 |
| 736.5 | 52.6 | 35.2 | 0.67x | 0.67 |

Expected ratio: 0.85-1.15 for moderate perspective. Actual range: **0.30-4.17**.

### Consequence

FP1 placed 4x too far right -> perspective transform creates extremely distorted mapping -> bottom half of sampled image maps to background -> module 0 reads color 7 (white) -> metadata fails -> LDPC fails.

Sampled FP center RGB values confirm the distortion:
- FP2(yellow) \[21,21\]: RGB(24,18,24) -- reads **dark gray** instead of yellow
- FP3(cyan) \[21,3\]: RGB(239,237,240) -- reads **white** instead of cyan

## Fix

Replace per-FP `module_size` with edge-derived module size. Allow per-FP values only as a bounded correction factor.

```c
// Phase 2N: Edge-derived perpendicular estimation
jab_float v_ms = left_edge_len / fp_span;  // reliable module size

// Only trust per-FP module_size if within 30% of v_ms
jab_float h_ms0 = fps[0].module_size;
jab_float h_ms3 = fps[3].module_size;

jab_float ratio0 = (v_ms > 0) ? h_ms0 / v_ms : 1.0f;
jab_float ratio3 = (v_ms > 0) ? h_ms3 / v_ms : 1.0f;

if (ratio0 < 0.7f || ratio0 > 1.3f) h_ms0 = v_ms;
if (ratio3 < 0.7f || ratio3 > 1.3f) h_ms3 = v_ms;

jab_float h_extent_fp0 = fp_span * h_ms0;
jab_float h_extent_fp3 = fp_span * h_ms3;

JAB_REPORT_INFO(("H3_GEOM: Phase2N: v_ms=%.1f, h_ms0=%.1f(ratio=%.2f%s), h_ms3=%.1f(ratio=%.2f%s)",
    v_ms, h_ms0, ratio0, (ratio0<0.7f||ratio0>1.3f)?" CLAMPED":"",
    h_ms3, ratio3, (ratio3<0.7f||ratio3>1.3f)?" CLAMPED":""))
```

### Effect

With current trace data, ALL 27 frames would clamp to v\_ms (square estimation) because no frame has a ratio within 0.7-1.3. This produces h\_ext0/edge = 1.0 for all frames -- a reliable square that doesn't wildly distort the perspective transform.

## TDD Plan

### Test 03.1: Syntax verification

```
GIVEN the modified detector.c
WHEN  compiled with gcc -fsyntax-only (without MOBILE_BUILD)
THEN  no syntax errors
```

### Test 03.2: Trace verification (logcat)

```
GIVEN the deployed diagnostic app with Phase 2N
WHEN  scanning a v1 JABCode for 10 seconds
THEN  H3_GEOM log shows "CLAMPED" for >90% of frames
AND   h_ext0/edge ratio is between 0.7 and 1.3 for all frames
```

### Test 03.3: FP center color verification (logcat)

```
GIVEN frames that reach Stage3 (sampleSymbol OK)
WHEN  H3_SAMPLE lines are examined
THEN  FP0(black) center: R<60, G<60, B<60
AND   FP1(black) center: R<60, G<60, B<60
AND   FP2(yellow) center: R>150, G>150, B<100 (or at minimum not dark gray)
AND   FP3(cyan) center: R<100, G>150, B>150 (or at minimum not white)
```

### Test 03.4: Side size accuracy

```
GIVEN the v1 21x21 barcode
WHEN  calculateSideSize is logged
THEN  >80% of frames compute 21x21 (currently only 46%)
```

## Files Affected

| File | Change |
|------|--------|
| `src/jabcode/detector.c` | Replace Phase 2M h\_extent with edge-derived + bounded correction |
| `src/jabcode/detector.c` | Update log messages to Phase2N format |

## Verification

Deploy, scan for 10 seconds, grep logcat:

```bash
grep "H3_GEOM: Phase2N" logcat | head -20    # confirm new log format
grep "CLAMPED" logcat | wc -l                 # expect >90% of geometry lines
grep "H3_SAMPLE: FP" logcat                   # check FP center colors
grep "calculateSideSize" logcat               # check 21x21 accuracy
grep "result=1" logcat                        # check for first successful decode
```
