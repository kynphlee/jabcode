# 07 -- Adaptive Camera Intelligence

> **Priority:** P8-P10 (three layers, incremental)
> **Layer:** Framework -- `jabcode-sdk` + `ui-components` + Diagnostic App
> **Risk:** Low (L1), Medium (L2), Medium (L3) -- each layer is additive, no existing behavior removed
> **Prerequisites:** P1-P3 (capture request tuning fields must exist in CameraConfig)

---

## Context

Camera2 exposes dozens of configuration modes (AWB, AE, AF, NR, edge enhancement) that vary by device and are meaningless to most developers. The current diagnostic app hardcodes `AWB_MODE_AUTO` and relies on manual user intervention to change it. Phase 3A trace data shows AWB AUTO never converges when pointed at a screen -- the barcode's high-frequency color pattern confuses the algorithm into perpetual adjustment.

Screen displays emit a fixed ~6500K white point (sRGB D65 standard). Printed barcodes under fluorescent light sit at ~4000K. Outdoor printed barcodes at ~5500K. Each environment has an optimal AWB preset, but expecting users (or downstream developers) to know this is unreasonable.

The goal is a three-layer system where each layer builds on the previous:

1. **Discover** what the device supports
2. **Auto-set** based on what the environment needs
3. **Monitor** and adapt when conditions change

## Layer 1: Capability Discovery (P8)

### Current State

`CameraConfig.kt` accepts `awbMode: Int` but has no way to know which values are valid for the current device. `Camera2Preview.kt` blindly sets whatever mode is requested -- if unsupported, the camera HAL silently ignores it or falls back to AUTO.

No capability query exists anywhere in the codebase.

### Target State

A `CameraCapabilities` data class that queries device characteristics once at camera open and exposes validated mode lists.

### Implementation

```kotlin
// framework/jabcode-sdk/.../camera/CameraCapabilities.kt

data class CameraCapabilities(
    val cameraId: String,
    val hardwareLevel: Int,
    val supportedAwbModes: List<Int>,
    val supportedAfModes: List<Int>,
    val supportedNrModes: List<Int>,
    val supportedEdgeModes: List<Int>,
    val supportedAeRange: IntRange,
    val supports10Bit: Boolean,
    val supportsHlg10: Boolean,
    val sensorOrientation: Int,
    val maxZoom: Float
) {
    fun isAwbModeSupported(mode: Int): Boolean = mode in supportedAwbModes
    fun isAfModeSupported(mode: Int): Boolean = mode in supportedAfModes

    companion object {
        fun fromCharacteristics(
            cameraId: String,
            chars: CameraCharacteristics
        ): CameraCapabilities {
            val awbModes = chars.get(
                CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES
            )?.toList() ?: listOf(1) // fallback: AUTO only

            val afModes = chars.get(
                CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES
            )?.toList() ?: listOf(0) // fallback: OFF only

            val nrModes = chars.get(
                CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES
            )?.toList() ?: listOf(0)

            val edgeModes = chars.get(
                CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES
            )?.toList() ?: listOf(0)

            val aeRange = chars.get(
                CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE
            )?.let { it.lower..it.upper } ?: (0..0)

            val hwLevel = chars.get(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
            ) ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY

            // 10-bit / HDR detection (API 33+)
            val supports10Bit = if (Build.VERSION.SDK_INT >= 33) {
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                caps?.contains(
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT
                ) ?: false
            } else false

            val supportsHlg10 = if (Build.VERSION.SDK_INT >= 33 && supports10Bit) {
                val profiles = chars.get(
                    CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES
                )
                profiles?.supportedProfiles?.contains(
                    DynamicRangeProfiles.HLG10
                ) ?: false
            } else false

            return CameraCapabilities(
                cameraId = cameraId,
                hardwareLevel = hwLevel,
                supportedAwbModes = awbModes,
                supportedAfModes = afModes,
                supportedNrModes = nrModes,
                supportedEdgeModes = edgeModes,
                supportedAeRange = aeRange,
                supports10Bit = supports10Bit,
                supportsHlg10 = supportsHlg10,
                sensorOrientation = chars.get(
                    CameraCharacteristics.SENSOR_ORIENTATION
                ) ?: 0,
                maxZoom = chars.get(
                    CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM
                ) ?: 1.0f
            )
        }
    }
}
```

### Integration Points

- `Camera2Preview.kt`: Query capabilities at `openCamera()`, validate `CameraConfig` modes before applying
- `SettingsScreen.kt`: Display only supported AWB modes in the picker (gray out unsupported)
- `CameraConfig.kt`: Add `fun validateAgainst(caps: CameraCapabilities): CameraConfig` that clamps unsupported modes to nearest valid fallback

### AWB Mode Reference

| Constant | Value | Color Temp | Environment |
|---|---|---|---|
| OFF | 0 | Manual | Full manual color correction |
| AUTO | 1 | Adaptive | General purpose (current default) |
| INCANDESCENT | 2 | ~2700K | Warm tungsten bulbs |
| FLUORESCENT | 3 | ~4000K | Cool white fluorescent |
| WARM\_FLUORESCENT | 4 | ~3000K | Warm fluorescent |
| DAYLIGHT | 5 | ~5500K | Direct sunlight |
| CLOUDY\_DAYLIGHT | 6 | ~6500K | Overcast sky / **screen displays** |
| TWILIGHT | 7 | ~12000K | Twilight / blue hour |
| SHADE | 8 | ~7500K | Open shade |

### TDD Plan

#### Test 07.1: Capability query returns valid modes (instrumented)

```
GIVEN the SM-S938U back camera
WHEN  CameraCapabilities.fromCharacteristics() is called
THEN  supportedAwbModes contains at least AUTO (1) and DAYLIGHT (5)
AND   supportedAfModes contains CONTINUOUS_VIDEO (3)
AND   supportedAeRange spans at least -2..+2
AND   supports10Bit == true (expected for S25 Ultra)
```

#### Test 07.2: Unsupported mode falls back gracefully

```
GIVEN CameraCapabilities with supportedAwbModes = [0, 1, 5, 6]
AND   CameraConfig with awbMode = 7 (TWILIGHT, not in list)
WHEN  config.validateAgainst(capabilities) is called
THEN  returned config has awbMode = 1 (AUTO fallback)
AND   log contains warning about unsupported mode
```

#### Test 07.3: Settings UI shows only supported modes (instrumented)

```
GIVEN CameraCapabilities with supportedAwbModes = [0, 1, 2, 3, 5, 6]
WHEN  AWB mode picker is rendered in SettingsScreen
THEN  6 options are shown as selectable
AND   WARM_FLUORESCENT (4), TWILIGHT (7), SHADE (8) are grayed out or hidden
```

---

## Layer 2: Environment-Driven Auto-Set (P9)

### Current State

No environment sensing exists. The user must manually select AWB mode.

### Target State

An `EnvironmentAnalyzer` that reads ambient light sensor data and initial camera frames to recommend optimal camera settings at session start.

### Implementation Approach

```kotlin
// framework/jabcode-sdk/.../camera/EnvironmentAnalyzer.kt

class EnvironmentAnalyzer(
    private val sensorManager: SensorManager,
    private val capabilities: CameraCapabilities
) {
    data class EnvironmentReading(
        val ambientLux: Float,
        val estimatedColorTempK: Int,
        val isScreenSource: Boolean,
        val confidence: Float
    )

    data class RecommendedConfig(
        val awbMode: Int,
        val exposureCompensation: Int,
        val reason: String
    )

    fun analyze(
        ambientLux: Float,
        initialFrames: List<Bitmap>
    ): RecommendedConfig {
        val colorTemp = estimateColorTemperature(initialFrames)
        val isScreen = detectScreenSource(initialFrames)

        return when {
            isScreen -> RecommendedConfig(
                awbMode = 6, // CLOUDY_DAYLIGHT (~6500K = sRGB D65)
                exposureCompensation = 0,
                reason = "Screen display detected (D65 white point)"
            )
            ambientLux > 10000 -> RecommendedConfig(
                awbMode = 5, // DAYLIGHT
                exposureCompensation = -1,
                reason = "Bright outdoor (${ambientLux.toInt()} lux)"
            )
            ambientLux > 500 -> RecommendedConfig(
                awbMode = mapColorTempToAwb(colorTemp),
                exposureCompensation = 0,
                reason = "Indoor (${ambientLux.toInt()} lux, ~${colorTemp}K)"
            )
            else -> RecommendedConfig(
                awbMode = 1, // AUTO — low light, let camera decide
                exposureCompensation = 1,
                reason = "Low light (${ambientLux.toInt()} lux)"
            )
        }.let { rec ->
            if (capabilities.isAwbModeSupported(rec.awbMode)) rec
            else rec.copy(awbMode = 1, reason = rec.reason + " [mode unsupported, AUTO fallback]")
        }
    }
}
```

### Screen Source Detection

A screen-displayed barcode has distinctive characteristics vs printed:

- **Uniform backlight:** Low spatial frequency variation in non-barcode regions
- **High luminance:** Screen pixels are self-emitting, typically > 200 cd/m2
- **Pixel grid:** At close range, subpixel structure is visible as periodic high-frequency pattern
- **Color gamut:** Screen colors are more saturated than printed (especially cyan and yellow)

Detection heuristic: sample a 64x64 patch from the border region around the barcode. If luminance variance is low AND mean luminance is high AND color saturation is above threshold, classify as screen source.

### Color Temperature Estimation

From a neutral (white/gray) region of the frame:

```
correlated_color_temp = f(R_mean / B_mean)
```

A simple lookup table maps the R/B ratio to approximate Kelvin:

| R/B Ratio | Approx Temp | AWB Mode |
|---|---|---|
| > 1.4 | < 3000K | INCANDESCENT (2) |
| 1.2 - 1.4 | 3000-4000K | WARM\_FLUORESCENT (4) |
| 1.0 - 1.2 | 4000-5000K | FLUORESCENT (3) |
| 0.85 - 1.0 | 5000-6000K | DAYLIGHT (5) |
| 0.7 - 0.85 | 6000-7000K | CLOUDY\_DAYLIGHT (6) |
| < 0.7 | > 7000K | SHADE (8) |

### TDD Plan

#### Test 07.4: Screen source detection (unit)

```
GIVEN a synthetic Bitmap simulating screen display:
      - uniform high luminance (Y > 200) in border region
      - high color saturation in barcode region
WHEN  EnvironmentAnalyzer.detectScreenSource() is called
THEN  returns true
AND   confidence > 0.8
```

#### Test 07.5: Printed barcode detection (unit)

```
GIVEN a synthetic Bitmap simulating printed barcode:
      - non-uniform luminance (paper texture variance > 10)
      - lower color saturation
WHEN  EnvironmentAnalyzer.detectScreenSource() is called
THEN  returns false
```

#### Test 07.6: Color temperature estimation (unit)

```
GIVEN a synthetic white patch with R=200, G=200, B=240 (cool/blue)
WHEN  estimateColorTemperature() is called
THEN  returns value in range 6000-7000K
AND   mapColorTempToAwb() returns CLOUDY_DAYLIGHT (6)
```

#### Test 07.7: End-to-end recommendation (instrumented)

```
GIVEN a running camera session pointed at a screen
WHEN  EnvironmentAnalyzer.analyze() is called with first 5 frames
THEN  recommendation.awbMode == CLOUDY_DAYLIGHT (6)
AND   recommendation.isScreenSource == true
AND   recommendation.reason contains "Screen display"
```

---

## Layer 3: Session Monitor (P10)

### Current State

No runtime monitoring. Camera settings are fixed for the duration of a session.

### Target State

A `SessionMonitor` that continuously evaluates environment metrics during scanning and surfaces reconfiguration suggestions when conditions drift. Initially surfaces suggestions to the user; future refinement converges to fully autonomous adaptation without human intervention.

### Implementation Approach

```kotlin
// framework/jabcode-sdk/.../camera/SessionMonitor.kt

class SessionMonitor(
    private val environmentAnalyzer: EnvironmentAnalyzer,
    private val currentConfig: CameraConfig,
    private val onSuggestion: (ConfigSuggestion) -> Unit
) {
    data class ConfigSuggestion(
        val suggestedConfig: CameraConfig,
        val reason: String,
        val confidence: Float,
        val metric: EnvironmentDelta
    )

    data class EnvironmentDelta(
        val luxChange: Float,
        val colorTempChangeK: Int,
        val sourceTypeChanged: Boolean
    )

    private val readingHistory = ArrayDeque<EnvironmentReading>(maxCapacity = 30)
    private var lastSuggestionTime = 0L
    private val suggestionCooldownMs = 10_000L
    private val driftThreshold = 0.3f

    fun onFrame(bitmap: Bitmap, ambientLux: Float) {
        val reading = environmentAnalyzer.quickAnalyze(bitmap, ambientLux)
        readingHistory.addLast(reading)

        if (readingHistory.size < 10) return
        if (System.currentTimeMillis() - lastSuggestionTime < suggestionCooldownMs) return

        val recent = readingHistory.takeLast(5)
        val baseline = readingHistory.take(5)

        val delta = computeDelta(baseline, recent)
        if (delta.significance > driftThreshold) {
            val recommendation = environmentAnalyzer.analyze(
                ambientLux = recent.map { it.ambientLux }.average().toFloat(),
                initialFrames = emptyList()
            )

            if (recommendation.awbMode != currentConfig.awbMode) {
                lastSuggestionTime = System.currentTimeMillis()
                onSuggestion(ConfigSuggestion(
                    suggestedConfig = currentConfig.copy(awbMode = recommendation.awbMode),
                    reason = recommendation.reason,
                    confidence = delta.significance,
                    metric = delta
                ))
            }
        }
    }
}
```

### Evolution Path: Suggestion → Autonomous

The initial implementation surfaces suggestions via a non-intrusive UI element (snackbar or floating chip). The evolution toward autonomous operation:

| Phase | Behavior | Trigger |
|---|---|---|
| **3a: Suggest** | Snackbar: "Lighting changed — switch to DAYLIGHT?" | User taps to apply |
| **3b: Suggest + Auto** | Setting: "Auto-apply camera suggestions" (default off) | User opts in |
| **3c: Autonomous** | Seamless mid-session reconfiguration | Confidence > 0.9 AND no decode in progress |

Phase 3c requires careful engineering:
- Reconfiguration must happen between frames (not mid-decode)
- AE/AWB locks must be released, allowed to reconverge, then re-locked
- A brief "recalibrating" indicator tells the user why scanning paused
- If the new config produces worse results (decode rate drops), auto-revert

### Diagnostic App UI

```
┌─────────────────────────────────────────┐
│  Scanner                          ⚙️    │
│ ┌─────────────────────────────────────┐ │
│ │                                     │ │
│ │         [Camera Preview]            │ │
│ │                                     │ │
│ └─────────────────────────────────────┘ │
│                                         │
│  AWB: CLOUDY_DAYLIGHT (auto)  ☀️ 6500K  │
│  AE: LOCKED ✅  AWB: LOCKED ✅          │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │ 💡 Lighting changed — darker now.   │ │
│ │    Switch to AUTO mode?    [Apply]  │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

### TDD Plan

#### Test 07.8: Drift detection triggers suggestion (unit)

```
GIVEN SessionMonitor with baseline readings at 6500K / 400 lux
WHEN  10 consecutive readings arrive at 3000K / 100 lux
THEN  onSuggestion callback fires
AND   suggestion.suggestedConfig.awbMode == INCANDESCENT (2)
AND   suggestion.confidence > 0.5
```

#### Test 07.9: Cooldown prevents suggestion spam (unit)

```
GIVEN SessionMonitor that just fired a suggestion
WHEN  another drift is detected within 10 seconds
THEN  onSuggestion does NOT fire
AND   fires after cooldown expires
```

#### Test 07.10: Autonomous revert on regression (unit)

```
GIVEN SessionMonitor in autonomous mode (Phase 3c)
AND   auto-applied AWB change from CLOUDY_DAYLIGHT to INCANDESCENT
WHEN  decode success rate drops from 60% to 0% over next 20 frames
THEN  auto-reverts to previous config (CLOUDY_DAYLIGHT)
AND   logs "Auto-revert: decode regression detected"
```

---

## Files Affected

| File | Layer | Change |
|---|---|---|
| `framework/jabcode-sdk/.../camera/CameraCapabilities.kt` | L1 | **New:** capability query data class |
| `framework/jabcode-sdk/.../camera/config/CameraConfig.kt` | L1 | Add `validateAgainst(CameraCapabilities)` |
| `framework/ui-components/.../Camera2Preview.kt` | L1 | Query capabilities at openCamera, validate config |
| `diagnostic-app/.../ui/settings/SettingsScreen.kt` | L1 | AWB picker shows only supported modes |
| `framework/jabcode-sdk/.../camera/EnvironmentAnalyzer.kt` | L2 | **New:** environment sensing and recommendation |
| `framework/jabcode-sdk/.../camera/SessionMonitor.kt` | L3 | **New:** continuous monitoring and suggestion engine |
| `diagnostic-app/.../ui/scanner/ScannerScreen.kt` | L3 | Suggestion snackbar UI |
| `diagnostic-app/.../ui/scanner/ScannerViewModel.kt` | L2-L3 | Wire analyzer and monitor into scan lifecycle |
| Tests across all layers | L1-L3 | As specified above (07.1-07.10) |

## Execution Order

```
Layer 1 (P8):  CameraCapabilities + validation + settings UI
               └── Prerequisite for L2 and L3
               └── Can ship independently, immediate value

Layer 2 (P9):  EnvironmentAnalyzer + auto-set at session start
               └── Depends on L1 for mode validation
               └── Requires ambient light sensor access

Layer 3 (P10): SessionMonitor + suggestion UI + autonomous evolution
               └── Depends on L2 for environment analysis
               └── 3a (suggest) → 3b (opt-in auto) → 3c (autonomous)
```

## Verification

### Layer 1

```bash
# Confirm capability query
grep "CameraCapabilities" logcat
# Should list all supported AWB/AF/NR/edge modes at camera open

# Confirm mode validation
# Set an unsupported AWB mode in settings → verify log warning and AUTO fallback
```

### Layer 2

```bash
# Point at screen, check recommendation
grep "EnvironmentAnalyzer" logcat
# Should show: "Screen display detected (D65 white point)" → CLOUDY_DAYLIGHT

# Point at printed barcode under fluorescent light
# Should show: "Indoor (500 lux, ~4000K)" → FLUORESCENT
```

### Layer 3

```bash
# Start scanning at a screen, then move to a window
grep "SessionMonitor\|ConfigSuggestion" logcat
# Should show drift detection and suggestion after environment change
```
