# 01 -- YUV-to-Bitmap Pipeline Fix

> **Priority:** P0 (highest impact, unblocks color fidelity)
> **Layer:** Framework -- `jabcode-sdk`
> **Risk:** Medium (core conversion path, affects all consumers)

---

## Context

Every camera frame passes through `CameraUtils.yuv420ToBitmap()` before reaching the JABCode decoder. This function is the sole entry point for pixel data into the decode pipeline.

JABCode's 8-color palette relies on distinguishing colors with at least \~85 units of separation per RGB channel. Any conversion that degrades color precision directly reduces decode success probability.

## Current State

**File:** `framework/jabcode-sdk/src/main/java/com/jabauth/jabcode/camera/CameraUtils.kt` L73-101

```
Camera2 YUV_420_888 frame
  -> Extract Y, U, V planes
  -> Build NV21 byte array (manual V-U interleave)
  -> YuvImage(NV21)
  -> compressToJpeg(quality=100)  <-- LOSSY STEP
  -> BitmapFactory.decodeByteArray  <-- JPEG decompress
  -> Bitmap (ARGB_8888)
```

### Problems

1. **JPEG chroma subsampling (4:2:0):** Even at quality 100, JPEG halves chroma (U/V) resolution in both dimensions. A 1920x1080 frame has 1920x1080 luma but only 960x540 chroma. Module boundaries in JABCode are \~30-50px; chroma is blurred across 2px at each boundary.

2. **DCT quantization artifacts:** JPEG's 8x8 DCT blocks introduce rounding errors at block boundaries. For barcode modules that don't align with 8x8 grids, color values shift by 2-8 units per channel.

3. **Double conversion overhead:** NV21 -> JPEG compress -> JPEG decompress is CPU-expensive. At 1920x1080, this takes 15-25ms per frame -- a significant fraction of the 500ms analyze interval.

4. **UV interleave bug risk:** The manual V-U interleave loop at L89-93 iterates `uSize` times but indexes into both `vBuffer` and `uBuffer` assuming identical sizes and pixel strides. If `pixelStride != 1` (common on many devices), this produces corrupted chroma.

## Fix

Replace the JPEG roundtrip with direct YUV-to-RGB pixel conversion using Android's `ScriptIntrinsicYuvToRGB` (RenderScript) or manual conversion with proper plane stride handling.

### Option A: RenderScript (fastest, but deprecated API 31+)

```kotlin
private fun yuv420ToBitmapRenderScript(image: Image): Bitmap {
    val rs = RenderScript.create(context)
    val yuvAlloc = Allocation.createSized(rs, Element.U8(rs), yuvSize)
    val rgbAlloc = Allocation.createFromBitmap(rs, outputBitmap)
    val script = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
    script.setInput(yuvAlloc)
    script.forEach(rgbAlloc)
    rgbAlloc.copyTo(outputBitmap)
}
```

### Option B: Direct pixel conversion (portable, no deprecation risk)

```kotlin
private fun yuv420ToBitmapDirect(image: Image): Bitmap {
    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yRowStride = yPlane.rowStride
    val uvRowStride = uPlane.rowStride
    val uvPixelStride = uPlane.pixelStride

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)

    for (row in 0 until height) {
        for (col in 0 until width) {
            val yIdx = row * yRowStride + col
            val uvIdx = (row / 2) * uvRowStride + (col / 2) * uvPixelStride

            val y = (yPlane.buffer[yIdx].toInt() and 0xFF) - 16
            val u = (uPlane.buffer[uvIdx].toInt() and 0xFF) - 128
            val v = (vPlane.buffer[uvIdx].toInt() and 0xFF) - 128

            var r = (1.164 * y + 1.596 * v).toInt()
            var g = (1.164 * y - 0.813 * v - 0.391 * u).toInt()
            var b = (1.164 * y + 2.018 * u).toInt()

            r = r.coerceIn(0, 255)
            g = g.coerceIn(0, 255)
            b = b.coerceIn(0, 255)

            pixels[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}
```

**Recommended: Option B** -- no deprecated APIs, handles pixelStride correctly, produces identical RGB values on every device.

## TDD Plan

### Test 01.1: Known-color YUV round-trip

```
GIVEN a synthetic YUV_420_888 byte array encoding pure red (Y=76, U=84, V=255)
WHEN  yuv420ToBitmapDirect() is called
THEN  the output Bitmap pixel at center reads RGB(254-255, 0-1, 0-1)
      (tolerance +-2 for integer rounding)
```

### Test 01.2: JPEG vs Direct color error comparison

```
GIVEN the same synthetic YUV input with 8 JABCode palette colors as 8x8 blocks
WHEN  both yuv420ToBitmap() [old] and yuv420ToBitmapDirect() [new] are called
THEN  max per-channel error for old method > 4
AND   max per-channel error for new method <= 2
```

### Test 01.3: pixelStride handling

```
GIVEN a synthetic YUV buffer with uvPixelStride=2 (interleaved UV planes)
WHEN  yuv420ToBitmapDirect() is called
THEN  output colors match expected values (no green/purple tint from swapped U/V)
```

### Test 01.4: Instrumented camera frame test

```
GIVEN a live Camera2 frame from ImageReader
WHEN  yuv420ToBitmapDirect() is called
THEN  output Bitmap is non-null, dimensions match ImageReader config
AND   first pixel RGB values are within reasonable range (not all-zero, not all-255)
```

## Files Affected

| File | Change |
|------|--------|
| `framework/jabcode-sdk/.../camera/CameraUtils.kt` | Add `yuv420ToBitmapDirect()`, update `imageToBitmap()` to call it |
| `framework/jabcode-sdk/.../camera/CameraUtils.kt` | Deprecate `yuv420ToBitmap()` (keep for fallback) |
| `framework/jabcode-sdk/src/test/.../camera/CameraUtilsTest.kt` | New: unit tests 01.1, 01.2, 01.3 |
| `framework/jabcode-sdk/src/androidTest/.../camera/CameraUtilsInstrumentedTest.kt` | New: test 01.4 |

## Verification

After deployment, capture logcat and check `H3_SAMPLE` diagnostic lines:
- FP0(black) should read RGB values < (50, 50, 50)
- FP3(cyan) should show R < 100, G > 150, B > 150 (not white/gray as currently seen)
- Data modules should show saturated colors, not desaturated gray-band values
