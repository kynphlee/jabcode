# 05 -- Full 10-Bit Color Pipeline

> **Priority:** P6
> **Layer:** Framework + Native (end-to-end)
> **Risk:** High (touches every layer from sensor to JNI decoder)
> **Prerequisites:** P0 (YUV fix), P5 (HDR session)

---

## Context

Phase 04 enables HDR capture but tone-maps to 8-bit for the existing pipeline. This phase extends the data path to preserve full 10-bit precision from sensor to the JABCode decoder, enabling reliable decode of 32-color and 64-color modes from screen displays.

## Current State

```
Sensor (10/12-bit) -> ISP -> YUV_420_888 (8-bit) -> CameraUtils -> Bitmap (8-bit ARGB_8888) -> JNI -> decoder
```

Every stage truncates to 8-bit. The 10-bit data captured by the sensor is lost before it reaches the decoder.

## Target State

```
Sensor (10/12-bit) -> ISP -> YCBCR_P010 (10-bit) -> CameraUtils -> 10-bit RGB buffer -> JNI -> decoder
```

### Key Format: YCBCR\_P010

- Android's 10-bit equivalent of YUV\_420\_888
- Each Y sample: 16 bits (10 significant + 6 padding)
- Each UV sample: 16 bits (10 significant + 6 padding)
- Available on devices with `REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT`

## Fix

### Step 1: ImageReader format change

```kotlin
// In Camera2Preview.kt
val imageFormat = if (hdrEnabled && isP010Supported()) {
    ImageFormat.YCBCR_P010  // 10-bit
} else {
    ImageFormat.YUV_420_888  // 8-bit fallback
}

imageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, imageFormat, 2)
```

### Step 2: P010-to-RGB10 conversion in CameraUtils

```kotlin
fun p010ToRgb10Buffer(image: Image): ShortArray {
    // P010: Y plane has 16-bit samples (10 significant bits, MSB-aligned)
    // UV plane has interleaved 16-bit U,V pairs

    val yPlane = image.planes[0]
    val uvPlane = image.planes[1]  // interleaved U,V

    val yBuffer = yPlane.buffer.asShortBuffer()
    val uvBuffer = uvPlane.buffer.asShortBuffer()

    val pixels = ShortArray(width * height * 3)  // R10, G10, B10 per pixel

    for (row in 0 until height) {
        for (col in 0 until width) {
            val y10 = (yBuffer[row * yRowStride/2 + col].toInt() and 0xFFFF) shr 6
            val uvIdx = (row / 2) * (uvRowStride / 2) + (col / 2) * 2
            val u10 = (uvBuffer[uvIdx].toInt() and 0xFFFF) shr 6
            val v10 = (uvBuffer[uvIdx + 1].toInt() and 0xFFFF) shr 6

            // BT.2020 YUV to RGB conversion (10-bit)
            val y = y10 - 64
            val u = u10 - 512
            val v = v10 - 512

            val r = ((1192 * y + 1836 * v) shr 10).coerceIn(0, 1023)
            val g = ((1192 * y - 218 * u - 547 * v) shr 10).coerceIn(0, 1023)
            val b = ((1192 * y + 2166 * u) shr 10).coerceIn(0, 1023)

            val idx = (row * width + col) * 3
            pixels[idx] = r.toShort()
            pixels[idx + 1] = g.toShort()
            pixels[idx + 2] = b.toShort()
        }
    }
    return pixels
}
```

### Step 3: JNI bridge modification

Current JNI bridge accepts `Bitmap` (8-bit ARGB). Need to add an overload that accepts a `ShortArray` (10-bit RGB):

```c
JNIEXPORT jint JNICALL Java_com_jabcode_JABCodeMobile_decodeRgb10(
    JNIEnv *env, jobject obj,
    jshortArray pixelData, jint width, jint height)
{
    jshort *pixels = (*env)->GetShortArrayElements(env, pixelData, NULL);
    // Create jab_bitmap with 16-bit channels or scale to 8-bit with better mapping
    // ...
}
```

### Step 4: detector.c color classification

The current color classifier uses 8-bit thresholds. For 10-bit input, either:
- **Option A:** Scale 10-bit to 8-bit with optimal mapping at the JNI boundary (simpler)
- **Option B:** Modify classifier thresholds for 10-bit range (more precise, more invasive)

**Recommended:** Option A for initial implementation, Option B as a follow-up.

## TDD Plan

### Test 05.1: Device P010 support (instrumented)

```
GIVEN the SM-S938U back camera
WHEN  StreamConfigurationMap.getOutputSizes(ImageFormat.YCBCR_P010) is queried
THEN  returns non-null list containing at least 1920x1080
```

### Test 05.2: P010-to-RGB10 conversion accuracy

```
GIVEN a synthetic P010 buffer encoding pure red (Y=219, U=457, V=707 in 10-bit)
WHEN  p010ToRgb10Buffer() is called
THEN  output R10 ≈ 940 (±10), G10 ≈ 64 (±10), B10 ≈ 64 (±10)
      (BT.2020 10-bit full-range red)
```

### Test 05.3: 10-bit to 8-bit scaling preserves separation

```
GIVEN 8 JABCode palette colors encoded as 10-bit P010 blocks
WHEN  converted to 10-bit RGB, then scaled to 8-bit
THEN  pairwise Euclidean distance between all 8 colors > 80
      (i.e., no color pair collapses due to quantization)
```

### Test 05.4: End-to-end 10-bit decode (instrumented)

```
GIVEN HDR session with P010 ImageReader
WHEN  scanning a known 8-color JABCode
THEN  decode succeeds (result=1) OR color values at FP centers are measurably better than 8-bit path
```

## Files Affected

| File | Change |
|------|--------|
| `framework/ui-components/.../Camera2Preview.kt` | P010 ImageReader format option |
| `framework/jabcode-sdk/.../camera/CameraUtils.kt` | Add p010ToRgb10Buffer() |
| `framework/jabcode-sdk/.../camera/Camera2JABCodeAnalyzer.kt` | Handle 10-bit path alongside 8-bit |
| `framework/jabcode-sdk/.../JABCodeDecoderImpl.kt` | Add decodeRgb10() JNI call |
| `swift-java-wrapper/jni/jabcode_jni.c` | Add decodeRgb10 native method |
| `src/jabcode/decoder.c` or JNI bridge | 10-bit to 8-bit scaling |
| Tests across all layers | As specified above |

## Verification

```bash
# Confirm P010 ImageReader
grep "YCBCR_P010\|P010\|10-bit" logcat

# Compare color separation
# Extract H3_SAMPLE FP center values and compute pairwise distances
# 10-bit path should show larger distances than 8-bit path
```

## Risk Mitigation

- **Feature flag:** Gate behind `CameraConfig.enable10BitPipeline` (default false)
- **Fallback:** If P010 not supported or session fails, fall back to YUV\_420\_888
- **Gradual rollout:** Implement Option A (10-to-8 scaling at JNI) first, measure improvement, then decide on Option B
