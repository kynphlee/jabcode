# Migration: Camera Diagnostics to Diagnostic-App

**Date:** May 14, 2026  
**Type:** Architecture Improvement  
**Impact:** Framework size reduction, clearer separation of concerns

---

## Summary

Migrated `CameraDiagnosticLogger` from `framework/jabcode-sdk` to `diagnostic-app` to maintain clear separation between production framework features and testing-specific diagnostic tools.

---

## Changes

### Files Moved

**From:**
```
framework/jabcode-sdk/src/main/java/com/jabauth/jabcode/camera/diagnostics/
└── CameraDiagnosticLogger.kt
```

**To:**
```
diagnostic-app/src/main/java/com/jabauth/diagnostic/diagnostics/
└── CameraDiagnosticLogger.kt
```

### Files Retained in Framework

The following remain in `framework/jabcode-sdk` as general-purpose utilities:

```
framework/jabcode-sdk/src/main/java/com/jabauth/jabcode/camera/metadata/
├── MetadataExtractor.kt         ← Used by CameraDiagnosticLogger
├── FrameMetadata.kt             ← Data structure for camera metadata
├── ImageQualityMetrics.kt
├── PerformanceMetrics.kt
└── PerformanceTracker.kt
```

### New Documentation

- **`diagnostic-app/DIAGNOSTICS_README.md`** - Guide for using camera diagnostics in testing

---

## Rationale

### Problem

Original placement in `framework/jabcode-sdk`:
- ❌ Production apps importing framework get unused diagnostic code
- ❌ Diagnostic features drive framework version bumps
- ❌ Unclear signal: "Is this a production API or testing tool?"
- ❌ Framework bloat (testing code in production modules)

### Solution

Move to `diagnostic-app`:
- ✅ **Clear separation** - Testing tools live in testing app
- ✅ **Lean framework** - No diagnostic bloat in production builds
- ✅ **Independent evolution** - diagnostic-app features don't bump framework version
- ✅ **Explicit intent** - "This is for diagnostic purposes only"

---

## Architecture

### Dependency Flow

```
diagnostic-app
    ↓ (imports)
framework/jabcode-sdk
    ↓ (provides)
MetadataExtractor + FrameMetadata
    ↓ (used by)
CameraDiagnosticLogger (diagnostic-app)
```

### Code Example

```kotlin
// diagnostic-app uses framework utilities
import com.jabauth.jabcode.camera.metadata.MetadataExtractor
import com.jabauth.jabcode.camera.metadata.FrameMetadata

class CameraDiagnosticLogger {
    private val metadataExtractor = MetadataExtractor()  // Framework utility
    
    fun logFrame(result: CaptureResult) {
        val metadata = metadataExtractor.extract(result)  // Framework API
        // Diagnostic-app-specific analysis and logging
    }
}
```

---

## Impact Assessment

### Framework Consumers

✅ **No breaking changes** - `CameraDiagnosticLogger` was never part of public API
- Framework still provides `MetadataExtractor` and `FrameMetadata` for advanced users
- Production apps never needed diagnostic logging

### Diagnostic-App

✅ **Cleaner architecture** - Testing tools in testing app
- Can evolve diagnostics independently (add Perplexity, YOLO, etc.)
- No framework rebuild required for diagnostic feature changes

### Build System

✅ **Verified successful** - Build passes with new structure
- `./gradlew :diagnostic-app:assembleDebug` - **SUCCESS**
- All framework modules compile cleanly
- No broken dependencies

---

## Migration Checklist

- [x] Create `CameraDiagnosticLogger` in diagnostic-app package
- [x] Update import statement (`com.jabauth.diagnostic.diagnostics`)
- [x] Remove old file from framework/jabcode-sdk
- [x] Verify framework metadata utilities remain intact
- [x] Test build succeeds
- [x] Create documentation (`DIAGNOSTICS_README.md`)
- [x] Create migration record (this file)
- [ ] Update any referencing documentation
- [ ] Wire into diagnostic-app UI (Phase 2, next session)

---

## Next Steps (Phase 2)

1. **UI Integration** - Add toggle in diagnostic-app scanner screen
2. **Data Collection** - Run screen display tests with diagnostics enabled
3. **Statistical Analysis** - Compute optimal camera settings from collected data
4. **Phase 2B Implementation** - Use insights for screen-aware color validation

---

## Verification

**Build Status:** ✅ PASS
```bash
./gradlew :diagnostic-app:assembleDebug
# Result: BUILD SUCCESSFUL
```

**Framework Integrity:** ✅ VERIFIED
```
framework/jabcode-sdk/camera/metadata/
├── MetadataExtractor.kt   ← Present
├── FrameMetadata.kt       ← Present
└── (other utilities)      ← Intact
```

**Diagnostic-App Structure:** ✅ CREATED
```
diagnostic-app/diagnostics/
├── CameraDiagnosticLogger.kt   ← Migrated successfully
└── DIAGNOSTICS_README.md       ← Documentation added
```

---

## References

- **Original Discussion:** Checkpoint 45 - May 14, 2026
- **Architecture Rationale:** DIAGNOSTICS_README.md
- **Framework Dependency Guide:** FRAMEWORK_DEPENDENCY_GUIDE.md

---

**Reviewed By:** AI Assistant (JARVIS Mode)  
**Approved For:** Development Branch  
**Breaking Changes:** None
