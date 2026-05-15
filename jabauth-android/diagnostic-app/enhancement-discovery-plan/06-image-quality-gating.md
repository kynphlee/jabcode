# 06 -- Image Quality Gating

> **Priority:** P7
> **Layer:** Framework -- `jabcode-sdk`
> **Risk:** Low (additive check before decode, no existing behavior modified)

---

## Context

The `Camera2JABCodeAnalyzer` currently sends every non-throttled frame to the decoder regardless of image quality. Blurry, dark, or low-contrast frames have zero chance of decoding successfully but consume 50-200ms of decode time each. Gating these frames saves CPU for frames that matter.

The `ImageQualityAnalyzer` already exists and calculates brightness, focus, and contrast metrics. It's wired into the analyzer but only for UI reporting -- the metrics are never used to skip decode.

## Current State

**File:** `framework/jabcode-sdk/.../camera/Camera2JABCodeAnalyzer.kt` L112-138

```kotlin
// Quality metrics are calculated but only reported to UI callback
if (qualityAnalyzer != null && onQualityUpdate != null) {
    val metrics = qualityAnalyzer.analyze(bitmap)
    onQualityUpdate.invoke(metrics)
}

// Decode always proceeds regardless of quality
val result = decoder.decode(bitmap, options)
```

**File:** `framework/jabcode-sdk/.../camera/ImageQualityAnalyzer.kt` L50-59

```kotlin
fun meetsThresholds(
    minBrightness: Float = 0.3f,
    minFocus: Float = 0.4f,
    minContrast: Float = 0.3f
): Boolean { ... }
```

The `meetsThresholds()` method exists but is never called in production code.

## Fix

Add a quality gate between metrics calculation and decode invocation:

```kotlin
// Calculate quality metrics
val metrics = qualityAnalyzer?.analyze(bitmap)
metrics?.let { onQualityUpdate?.invoke(it) }

// Quality gate: skip decode for frames that can't succeed
if (metrics != null && !metrics.meetsThresholds()) {
    Log.d(TAG, "Frame $frameCount: quality gate SKIP " +
          "(brightness=${metrics.brightness}, focus=${metrics.focus}, contrast=${metrics.contrast})")
    return
}

// Decode only quality-sufficient frames
val result = decoder.decode(bitmap, options)
```

### Threshold tuning for screen-displayed barcodes

Screen scanning has different characteristics than printed scanning:
- **Brightness:** Screen is typically bright (> 0.5), but reflections can cause dark patches
- **Focus:** Screen pixel grid can confuse AF; threshold should be lower than printed
- **Contrast:** Screen barcodes have high contrast by nature

Recommended thresholds for screen mode:
```kotlin
metrics.meetsThresholds(
    minBrightness = 0.2f,  // lower than default (screen can be dimmed)
    minFocus = 0.3f,       // lower than default (screen AF is harder)
    minContrast = 0.25f    // lower than default (screen has uniform backlight)
)
```

### Make thresholds configurable

Add to `DecodeOptions`:

```kotlin
data class DecodeOptions(
    // ... existing ...
    val qualityGateEnabled: Boolean = true,
    val minBrightness: Float = 0.3f,
    val minFocus: Float = 0.4f,
    val minContrast: Float = 0.3f
)
```

## TDD Plan

### Test 06.1: Synthetic blurry image rejected

```
GIVEN a Bitmap created by applying Gaussian blur (radius=20) to a barcode image
WHEN  ImageQualityAnalyzer.analyze() is called
THEN  metrics.focus < 0.3
AND   metrics.meetsThresholds(minFocus=0.3f) returns false
```

### Test 06.2: Quality gate skips decode

```
GIVEN Camera2JABCodeAnalyzer with qualityGateEnabled=true
AND   a mock ImageReader returning a synthetic dark frame (all pixels < 20)
WHEN  analyze() is called
THEN  decoder.decode() is NOT called
AND   log contains "quality gate SKIP"
```

### Test 06.3: Good quality passes gate

```
GIVEN Camera2JABCodeAnalyzer with qualityGateEnabled=true
AND   a mock ImageReader returning a well-lit, sharp synthetic frame
WHEN  analyze() is called
THEN  decoder.decode() IS called
AND   log does NOT contain "quality gate SKIP"
```

### Test 06.4: Gate disabled passes all frames

```
GIVEN Camera2JABCodeAnalyzer with qualityGateEnabled=false
AND   a dark blurry frame
WHEN  analyze() is called
THEN  decoder.decode() IS called regardless
```

## Files Affected

| File | Change |
|------|--------|
| `framework/jabcode-sdk/.../camera/Camera2JABCodeAnalyzer.kt` | Add quality gate check before decode |
| `framework/jabcode-sdk/.../DecodeOptions.kt` | Add qualityGateEnabled, threshold fields |
| `framework/jabcode-sdk/src/test/.../camera/Camera2JABCodeAnalyzerTest.kt` | Tests 06.2, 06.3, 06.4 |
| `framework/jabcode-sdk/src/test/.../camera/ImageQualityAnalyzerTest.kt` | Test 06.1 |

## Verification

Deploy and observe logcat during scanning:

```bash
# Count gated vs decoded frames
grep "quality gate SKIP" logcat | wc -l
grep "Starting decode attempt" logcat | wc -l

# Expect: gated frames during camera movement / AF hunting
# Expect: decode attempts only on stable, well-focused frames
```

Expected improvement: 30-50% fewer wasted decode attempts, reducing CPU load and allowing faster turnaround on good frames.
