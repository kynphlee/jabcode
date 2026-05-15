# 04 -- HDR Capture Session

> **Priority:** P5
> **Layer:** Framework -- `jabcode-sdk` + `ui-components` + Diagnostic App
> **Risk:** Medium (requires API 33+, device capability check, session reconfiguration)
> **Prerequisite:** P0 (YUV pipeline fix) should land first for accurate comparison

---

## Context

Standard Camera2 captures in 8-bit SDR (BT.709/sRGB). Android 13+ devices with 10-bit sensor output can use HDR profiles that provide:

- **10-bit color depth:** 1024 levels per channel vs 256 (4x precision)
- **BT.2020 color gamut:** 75.8% of visible spectrum vs BT.709's 35.9%
- **Better tone mapping:** Even when output is 8-bit Bitmap, the sensor's wider capture range produces less clipping and better color separation in the tone-mapped result

### Why This Matters for JABCode

| Color mode | Colors | Min separation (8-bit) | Min separation (10-bit) | Noise margin improvement |
|-----------|--------|:---------------------:|:----------------------:|:------------------------:|
| 8-color | 8 | \~85/channel | \~340/channel | **4x** |
| 16-color | 16 | \~64/channel | \~256/channel | **4x** |
| 32-color | 32 | \~32/channel | \~128/channel | **4x** |
| 64-color | 64 | \~16/channel | \~64/channel | **4x** |

## Current State

**No HDR configuration exists anywhere in the codebase.**

- `Camera2Preview.kt`: Uses `camera.createCaptureSession(listOf(surface, reader.surface), ...)` -- standard SDR session
- `CameraConfig.kt`: No HDR-related fields
- `StreamConfigValidator.kt`: No HDR stream validation
- `ImageReader`: Created with `ImageFormat.YUV_420_888` -- 8-bit only

## Fix

### Phase 1: Pragmatic HDR (tone-mapped to 8-bit)

Enable HDR on the capture session so the sensor captures in 10-bit, but the output ImageReader still uses `YUV_420_888`. The Camera2 framework tone-maps the 10-bit data down to 8-bit, but with **better highlight/shadow retention** than SDR capture.

This requires:
1. Check device supports `REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT`
2. Check HLG10 profile is available
3. Use `OutputConfiguration.setDynamicRangeProfile(DynamicRangeProfiles.HLG10)` on each output surface
4. Create session via `createCaptureSessionByOutputConfigurations()`

### CameraConfig.kt additions

```kotlin
data class CameraConfig(
    // ... existing fields ...
    val enableHdr: Boolean = false,
    val hdrProfile: Long = DynamicRangeProfiles.HLG10
)
```

### Camera2Preview.kt: HDR session creation

```kotlin
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createHdrCaptureSession(textureView: TextureView) {
    val camera = cameraDevice ?: return
    val reader = imageReader ?: return
    val texture = textureView.surfaceTexture
    texture?.setDefaultBufferSize(IMAGE_WIDTH, IMAGE_HEIGHT)
    val surface = Surface(texture)
    previewSurface = surface

    val previewConfig = OutputConfiguration(surface).apply {
        setDynamicRangeProfile(DynamicRangeProfiles.HLG10)
    }
    val readerConfig = OutputConfiguration(reader.surface).apply {
        setDynamicRangeProfile(DynamicRangeProfiles.HLG10)
    }

    camera.createCaptureSessionByOutputConfigurations(
        listOf(previewConfig, readerConfig),
        sessionCallback,
        backgroundHandler
    )
}
```

### Capability detection

```kotlin
fun isHdrSupported(cameraId: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false

    val chars = cameraManager.getCameraCharacteristics(cameraId)
    val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        ?: return false

    if (!capabilities.contains(
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT
    )) return false

    val profiles = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)
        ?: return false

    return profiles.supportedProfiles.contains(DynamicRangeProfiles.HLG10)
}
```

## TDD Plan

### Test 04.1: Device capability query (instrumented)

```
GIVEN the Samsung SM-S938U (Galaxy S25 Ultra, Android 16)
WHEN  isHdrSupported("0") is called for back camera
THEN  returns true (S25 Ultra supports 10-bit HDR)
AND   log includes supported profile list
```

### Test 04.2: HDR session creation (instrumented)

```
GIVEN HDR is supported and CameraConfig.enableHdr = true
WHEN  Camera2Preview opens camera
THEN  session is created via createCaptureSessionByOutputConfigurations
AND   OutputConfiguration has DynamicRangeProfiles.HLG10 set
AND   session.onConfigured callback fires (not onConfigureFailed)
```

### Test 04.3: Fallback to SDR when HDR unavailable

```
GIVEN a device that does NOT support 10-bit (mock CameraCharacteristics)
WHEN  Camera2Preview opens camera with CameraConfig.enableHdr = true
THEN  falls back to standard createCaptureSession
AND   logs warning: "HDR not supported, falling back to SDR"
```

### Test 04.4: Color comparison HDR vs SDR (instrumented)

```
GIVEN two capture sessions (one HDR, one SDR) scanning same static barcode
WHEN  FP center RGB values are logged
THEN  HDR session shows higher saturation at FP2(yellow) and FP3(cyan)
      (specifically: HDR FP3 cyan should show G-R > 30, vs SDR G-R < 10)
```

## Files Affected

| File | Change |
|------|--------|
| `framework/jabcode-sdk/.../config/CameraConfig.kt` | Add enableHdr, hdrProfile fields |
| `framework/jabcode-sdk/.../camera/CameraInfo.kt` | Add isHdrSupported() method |
| `framework/jabcode-sdk/.../camera/StreamConfigValidator.kt` | Add HDR stream combination validation |
| `framework/ui-components/.../Camera2Preview.kt` | Add createHdrCaptureSession(), HDR capability check |
| `diagnostic-app/.../data/SettingsRepository.kt` | Add HDR enable preference |
| `diagnostic-app/.../ui/settings/SettingsScreen.kt` | Add HDR toggle with capability indicator |
| `framework/jabcode-sdk/src/androidTest/...` | Tests 04.1-04.4 |

## Verification

Deploy with HDR enabled:

```bash
# Confirm HDR session
grep "DynamicRangeProfile\|HLG10\|HDR" logcat

# Compare FP center colors
grep "H3_SAMPLE: FP" logcat
# FP3(cyan) should show: R < G, R < B (cyan signature)
# vs SDR where FP3 reads: R ≈ G ≈ B (desaturated white)
```

## Device Note

The Samsung SM-S938U (Galaxy S25 Ultra) runs Android 16 (API 36), well above the API 33 minimum. The Exynos 2500 / Snapdragon 8 Elite sensor supports 10-bit output. HLG10 support is expected but must be confirmed via instrumented test 04.1.
