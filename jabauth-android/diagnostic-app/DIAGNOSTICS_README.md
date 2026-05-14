# Diagnostic-App Camera Diagnostics

**Last Updated:** May 14, 2026  
**Status:** Active Development

---

## Overview

Camera diagnostics tools for testing and tuning JABCode screen display compatibility. These are **testing-specific components** maintained in the diagnostic-app, not production framework features.

---

## Components

### `CameraDiagnosticLogger`

**Location:** `diagnostic-app/src/main/java/com/jabauth/diagnostic/diagnostics/CameraDiagnosticLogger.kt`

**Purpose:** Collect camera frame metadata for statistical analysis of screen display compatibility.

**Key Metrics:**
- **Exposure Time** - Target: 33.3ms (1/30 sec) to average 2+ refresh cycles at 60Hz/120Hz
- **ISO Sensitivity** - Monitor noise in low light conditions
- **Frame Rate** - Detect rolling shutter interference patterns
- **AE Convergence** - Auto-exposure stability indicator

**Usage Example:**
```kotlin
val diagnosticLogger = CameraDiagnosticLogger()

// In Camera2 capture callback
override fun onCaptureCompleted(session: CameraCaptureSession, 
                               request: CaptureRequest,
                               result: CaptureResult) {
    diagnosticLogger.logFrame(result)
}

// After test session
diagnosticLogger.printStatistics()
diagnosticLogger.computeOptimalSettings()
diagnosticLogger.reset()
```

---

## Architecture Rationale

### Why Diagnostic-App Level?

**Separation of Concerns:**
- ✅ Framework stays lean (no diagnostic code bloat in production builds)
- ✅ Clear signal to consumers: "This is a testing tool, not a production API"
- ✅ Independent versioning (diagnostic features don't drive framework releases)
- ✅ Testing-specific optimizations (can add Perplexity search, YOLO analysis, etc.)

**Framework Remains Minimal:**
- Framework provides **data access** via `MetadataExtractor` (general-purpose utility)
- Diagnostic-app adds **analysis layer** for testing scenarios
- Production apps importing framework don't ship unused diagnostic code

---

## Integration with Framework

**Framework Dependency:**
```kotlin
// Diagnostic-app imports framework metadata utilities
implementation(project(":framework:jabcode-sdk"))

// Uses MetadataExtractor from framework
import com.jabauth.jabcode.camera.metadata.MetadataExtractor
import com.jabauth.jabcode.camera.metadata.FrameMetadata
```

**Data Flow:**
```
Camera2 CaptureResult
    ↓
MetadataExtractor (framework)    ← General-purpose utility
    ↓
FrameMetadata (framework)        ← Data structure only
    ↓
CameraDiagnosticLogger (app)     ← Analysis & logging
    ↓
Statistics & Recommendations     ← Testing insights
```

---

## Phase 2: Screen Display Tuning

**Objective:** Use diagnostic data to compute optimal camera settings for screen display compatibility.

**Current Status:**
1. ✅ Data collection implemented (`CameraDiagnosticLogger`)
2. ✅ Statistical analysis (avg/min/max exposure, ISO, FPS)
3. ✅ Optimal settings computation (recommendations for exposure time, frame rate)
4. ⏳ **Pending:** UI toggle integration for runtime control
5. ⏳ **Pending:** Screen-aware validation mode (adaptive `crossCheckColor` tolerance)

**Next Steps:**
- Wire `CameraDiagnosticLogger` into diagnostic-app scanner UI
- Add toggle for enabling/disabling diagnostics
- Collect real-world data from screen display tests
- Use statistics to tune Phase 2B screen-aware color validation

---

## Future Extensions

**Planned Enhancements:**
- Real-time metrics display in UI (live exposure/FPS overlay)
- Export diagnostics to CSV for external analysis
- Integration with Phase 2B screen-aware validation modes
- Correlation analysis (screen refresh rate vs. detection success rate)
- Perplexity/YOLO integration for advanced image analysis

---

## Migration History

**May 14, 2026** - Migrated `CameraDiagnosticLogger` from `framework/jabcode-sdk` to `diagnostic-app`

**Rationale:**
- Camera diagnostics are testing tools, not production framework features
- Keeps framework lean and focused on core capabilities
- Enables diagnostic-app to evolve independently without framework version bumps
- Framework retains `MetadataExtractor` as a general-purpose utility

**Files Moved:**
- `framework/jabcode-sdk/.../diagnostics/CameraDiagnosticLogger.kt` → `diagnostic-app/.../diagnostics/CameraDiagnosticLogger.kt`

**Files Retained in Framework:**
- `framework/jabcode-sdk/.../metadata/MetadataExtractor.kt` (general-purpose utility)
- `framework/jabcode-sdk/.../metadata/FrameMetadata.kt` (data structure)

---

**Maintained By:** JABAuth Diagnostic Team  
**Framework Version Compatibility:** 1.0.0+
