# Phase 1: Foundation & Enumeration
**Duration:** 3-4 days  
**Status:** 🔴 Not Started  
**Dependencies:** None (first phase)

---

## Phase Objectives

**Primary Goal:** Establish proper Camera2 hardware discovery and capability validation system.

**Why This Matters:**
- Current framework hardcodes `cameraIdList[0]` — fails on devices where back camera isn't first
- No validation of hardware levels — attempts FULL-level features on LIMITED/LEGACY devices
- No stream configuration validation — sessions fail on incompatible combinations

**Success Criteria:**
- ✅ Enumerate all cameras (front, back, external, logical multi-camera)
- ✅ Classify hardware levels (FULL, LIMITED, LEGACY, LEVEL_3, EXTERNAL)
- ✅ Detect capabilities (RAW, ZSL, MANUAL_SENSOR, etc.)
- ✅ Validate stream configurations before session creation
- ✅ 100% test coverage
- ✅ Works on LEGACY, LIMITED, and FULL devices

---

## Research Foundation

**Key Documents:**
- Android Camera2 Diagnostic Application Design Best Practices (Section 2: Device Capability Enumeration)
- Android Camera2 Common Pitfalls (Section 1: Capability Validation)

**Critical Principles:**

> "A diagnostic app must query `android.info.supportedHardwareLevel` to determine the baseline capabilities... Never assume that a specific feature is available across all devices."

> "Android devices exhibit significant camera hardware fragmentation. The Camera2 API guarantees support for specific combinations of output streams depending on the hardware level."

**Reference Implementation:**
- [Camera2 Samples - HdrViewfinder](https://github.com/android/camera-samples/tree/main/HdrViewfinder) — Shows proper enumeration
- [CameraX Source](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:camera/camera-camera2/src/main/java/androidx/camera/camera2/internal/Camera2CameraInfo.java) — Industry-standard patterns

---

## Implementation Tasks

### Task 1.1: Create CameraInfo Data Class

**File:** `framework/jabcode-sdk/src/main/java/com/jabauth/jabcode/camera/CameraInfo.kt`

**Purpose:** Encapsulate camera metadata for easy access.

**Implementation:**
```kotlin
package com.jabauth.jabcode.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Size

/**
 * Immutable snapshot of a camera's capabilities
 * 
 * @property cameraId Camera identifier
 * @property characteristics Full CameraCharacteristics object
 * @property facing Lens facing direction (BACK, FRONT, EXTERNAL)
 * @property hardwareLevel Hardware support level
 * @property capabilities Available capabilities flags
 * @property maxResolution Maximum JPEG resolution
 * @property isLogicalMultiCamera Whether this is a logical multi-camera
 * @property physicalCameraIds Physical camera IDs (if logical multi-camera)
 */
data class CameraInfo(
    val cameraId: String,
    val characteristics: CameraCharacteristics,
    val facing: Int,
    val hardwareLevel: Int,
    val capabilities: Set<Int>,
    val maxResolution: Size,
    val isLogicalMultiCamera: Boolean,
    val physicalCameraIds: Set<String>
) {
    /**
     * Human-readable facing direction
     */
    val facingName: String
        get() = when(facing) {
            CameraCharacteristics.LENS_FACING_BACK -> "BACK"
            CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }
    
    /**
     * Human-readable hardware level
     */
    val hardwareLevelName: String
        get() = when(hardwareLevel) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }
    
    /**
     * Check if specific capability is supported
     */
    fun hasCapability(capability: Int): Boolean = capabilities.contains(capability)
    
    /**
     * Convenience checks for common capabilities
     */
    val supportsRaw: Boolean
        get() = hasCapability(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
    
    val supportsManualSensor: Boolean
        get() = hasCapability(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
    
    val supportsManualPostProcessing: Boolean
        get() = hasCapability(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
}
```

**Tests Required:**
- `CameraInfoTest.kt`:
  - `facingName_returnsCorrectString_forAllFacings()`
  - `hardwareLevelName_returnsCorrectString_forAllLevels()`
  - `hasCapability_returnsFalse_whenNotPresent()`
  - `hasCapability_returnsTrue_whenPresent()`
  - `supportsRaw_returnsFalse_whenCapabilityMissing()`
  - `supportsRaw_returnsTrue_whenCapabilityPresent()`

---

### Task 1.2: Implement CameraEnumerator

**File:** `framework/jabcode-sdk/src/main/java/com/jabauth/jabcode/camera/CameraEnumerator.kt`

**Purpose:** Discover and classify all cameras on the device.

**Implementation:**
```kotlin
package com.jabauth.jabcode.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Size

/**
 * Enumerates and classifies Camera2 hardware
 * 
 * Handles:
 * - Logical vs physical cameras
 * - Hardware level detection
 * - Capability discovery
 * - Multi-camera systems
 */
class CameraEnumerator(private val context: Context) {
    
    private val cameraManager: CameraManager = 
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    
    /**
     * Get all available cameras
     * 
     * @return List of CameraInfo for all detected cameras
     */
    fun getAllCameras(): List<CameraInfo> {
        return cameraManager.cameraIdList.mapNotNull { cameraId ->
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                createCameraInfo(cameraId, characteristics)
            } catch (e: Exception) {
                // Camera not accessible, skip
                null
            }
        }
    }
    
    /**
     * Find camera by facing direction
     * 
     * @param facing Desired facing (LENS_FACING_BACK, LENS_FACING_FRONT)
     * @param minHardwareLevel Minimum hardware level required
     * @return CameraInfo or null if not found
     */
    fun findCameraByFacing(
        facing: Int,
        minHardwareLevel: Int = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
    ): CameraInfo? {
        return getAllCameras()
            .filter { it.facing == facing }
            .filter { it.hardwareLevel >= minHardwareLevel }
            .maxByOrNull { it.hardwareLevel } // Prefer higher level
    }
    
    /**
     * Find camera with specific capability
     * 
     * @param capability Required capability
     * @return List of cameras with that capability
     */
    fun findCamerasWithCapability(capability: Int): List<CameraInfo> {
        return getAllCameras().filter { it.hasCapability(capability) }
    }
    
    /**
     * Create CameraInfo from characteristics
     */
    private fun createCameraInfo(
        cameraId: String,
        characteristics: CameraCharacteristics
    ): CameraInfo {
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            ?: CameraCharacteristics.LENS_FACING_BACK
        
        val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
        
        val capabilitiesArray = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?: intArrayOf()
        val capabilities = capabilitiesArray.toSet()
        
        // Get maximum JPEG resolution
        val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = streamConfigMap?.getOutputSizes(android.graphics.ImageFormat.JPEG) ?: emptyArray()
        val maxResolution = jpegSizes.maxByOrNull { it.width * it.height }
            ?: Size(0, 0)
        
        // Check for logical multi-camera (API 28+)
        val isLogicalMultiCamera = if (Build.VERSION.SDK_INT >= 28) {
            capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
        } else {
            false
        }
        
        val physicalCameraIds = if (isLogicalMultiCamera && Build.VERSION.SDK_INT >= 28) {
            characteristics.physicalCameraIds
        } else {
            emptySet()
        }
        
        return CameraInfo(
            cameraId = cameraId,
            characteristics = characteristics,
            facing = facing,
            hardwareLevel = hardwareLevel,
            capabilities = capabilities,
            maxResolution = maxResolution,
            isLogicalMultiCamera = isLogicalMultiCamera,
            physicalCameraIds = physicalCameraIds
        )
    }
}
```

**Tests Required:**
- `CameraEnumeratorTest.kt` (Unit):
  - `getAllCameras_returnsNonEmptyList_onRealDevice()` — Instrumented
  - `findCameraByFacing_returnsBackCamera_whenExists()` — Instrumented
  - `findCameraByFacing_returnsNull_whenNotFound()` — Unit (mock)
  - `findCamerasWithCapability_filtersCorrectly()` — Unit (mock)
  
- `CameraEnumeratorInstrumentedTest.kt` (Instrumented):
  - `enumerator_findsAllCameras_onActualDevice()`
  - `enumerator_detectsHardwareLevels_correctly()`
  - `enumerator_detectsCapabilities_correctly()`
  - `enumerator_handlesLogicalMultiCamera_onAPI28Plus()`

---

### Task 1.3: Add Stream Configuration Validator

**File:** `framework/jabcode-sdk/src/main/java/com/jabauth/jabcode/camera/StreamConfigValidator.kt`

**Purpose:** Validate stream combinations before creating capture session.

**Implementation:**
```kotlin
package com.jabauth.jabcode.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size
import android.graphics.ImageFormat

/**
 * Validates Camera2 stream configurations
 * 
 * Prevents session configuration failures by checking:
 * - Hardware level guarantees
 * - Format support
 * - Size support
 * - Stream combination validity
 */
class StreamConfigValidator {
    
    data class StreamConfig(
        val format: Int,
        val size: Size,
        val isInput: Boolean = false
    )
    
    data class ValidationResult(
        val isValid: Boolean,
        val reason: String? = null
    )
    
    /**
     * Validate stream configuration for a camera
     * 
     * @param cameraInfo Camera to validate against
     * @param streams List of desired streams
     * @return ValidationResult indicating if configuration is supported
     */
    fun validate(
        cameraInfo: CameraInfo,
        streams: List<StreamConfig>
    ): ValidationResult {
        // Check each stream is individually supported
        for (stream in streams) {
            if (!isStreamSupported(cameraInfo, stream)) {
                return ValidationResult(
                    isValid = false,
                    reason = "Stream ${stream.format}@${stream.size} not supported"
                )
            }
        }
        
        // Check combination is guaranteed by hardware level
        val combinationValid = isStreamCombinationSupported(cameraInfo, streams)
        if (!combinationValid) {
            return ValidationResult(
                isValid = false,
                reason = "Stream combination not guaranteed for ${cameraInfo.hardwareLevelName} hardware level"
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Check if single stream is supported
     */
    private fun isStreamSupported(
        cameraInfo: CameraInfo,
        stream: StreamConfig
    ): Boolean {
        val streamConfigMap = cameraInfo.characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        ) ?: return false
        
        val supportedSizes = streamConfigMap.getOutputSizes(stream.format) ?: return false
        return supportedSizes.contains(stream.size)
    }
    
    /**
     * Check if stream combination is guaranteed by hardware level
     * 
     * Based on Camera2 API guarantees:
     * - LEGACY: 1 PRIV stream only
     * - LIMITED: PRIV + JPEG or PRIV + YUV
     * - FULL: PRIV + PRIV + JPEG or PRIV + YUV + JPEG
     */
    private fun isStreamCombinationSupported(
        cameraInfo: CameraInfo,
        streams: List<StreamConfig>
    ): Boolean {
        when (cameraInfo.hardwareLevel) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> {
                // LEGACY: Maximum 1 stream
                return streams.size <= 1
            }
            
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> {
                // LIMITED: 2 streams max (PRIV + JPEG or PRIV + YUV)
                if (streams.size > 2) return false
                
                // Check guaranteed combinations
                val hasPriv = streams.any { it.format == ImageFormat.PRIVATE }
                val hasJpeg = streams.any { it.format == ImageFormat.JPEG }
                val hasYuv = streams.any { it.format == ImageFormat.YUV_420_888 }
                
                return (hasPriv && hasJpeg) || (hasPriv && hasYuv)
            }
            
            else -> {
                // FULL, LEVEL_3: More permissive, assume valid
                return streams.size <= 3
            }
        }
    }
    
    companion object {
        /**
         * Common stream configurations for JABCode scanning
         */
        fun previewPlusAnalysisConfig(
            previewSize: Size = Size(1280, 720),
            analysisSize: Size = Size(1280, 720)
        ): List<StreamConfig> = listOf(
            StreamConfig(ImageFormat.PRIVATE, previewSize),  // TextureView
            StreamConfig(ImageFormat.YUV_420_888, analysisSize)  // ImageReader
        )
    }
}
```

**Tests Required:**
- `StreamConfigValidatorTest.kt`:
  - `validate_returnsValid_forSupportedStream()`
  - `validate_returnsInvalid_forUnsupportedFormat()`
  - `validate_returnsInvalid_forUnsupportedSize()`
  - `validate_returnsInvalid_forLegacyMultiStream()`
  - `validate_returnsValid_forLimitedTwoStream()`
  - `validate_returnsInvalid_forLimitedThreeStream()`
  - `previewPlusAnalysisConfig_returnsCorrectFormats()`

---

## TDD Workflow

**For Each Task:**

### 1. Write Failing Tests (RED)
```bash
# Create test file first
touch framework/jabcode-sdk/src/test/java/com/jabauth/jabcode/camera/CameraInfoTest.kt

# Write tests that fail
@Test
fun facingName_returnsBack_forBackCamera() {
    val cameraInfo = CameraInfo(
        cameraId = "0",
        characteristics = mockCharacteristics,
        facing = CameraCharacteristics.LENS_FACING_BACK,
        // ... other params
    )
    
    assertEquals("BACK", cameraInfo.facingName)  // Will fail - not implemented yet
}

# Run tests (expect FAILURE)
./gradlew :framework:jabcode-sdk:testDebugUnitTest --tests "CameraInfoTest"
```

### 2. Implement Minimal Code (GREEN)
```bash
# Implement just enough to pass
data class CameraInfo(...) {
    val facingName: String
        get() = when(facing) {
            CameraCharacteristics.LENS_FACING_BACK -> "BACK"
            // ... rest
        }
}

# Run tests (expect SUCCESS)
./gradlew :framework:jabcode-sdk:testDebugUnitTest --tests "CameraInfoTest"
```

### 3. Refactor & Repeat
```bash
# Clean up code, add edge cases, repeat for next test
```

### 4. Phase Completion
```bash
# Run ALL tests
./gradlew test

# Check coverage
./gradlew jacocoTestReport

# Expected: 100% coverage for Phase 1 code
```

---

## Progress Narrative Template

**After completing each task, update progress narrative:**

```markdown
### Task 1.1: CameraInfo Data Class

**Date:** 2026-05-XX  
**Status:** ✅ Complete

**Implementation:**
- Created CameraInfo.kt with all required fields
- Added convenience properties (facingName, hardwareLevelName)
- Added capability check methods

**Tests:**
- 6/6 unit tests passing
- Coverage: 100%

**Challenges:**
- None

**Next:** Task 1.2 (CameraEnumerator)
```

---

## Acceptance Criteria

**Phase 1 is complete when:**

- ✅ All 10 checklist items marked complete
- ✅ 15-20 unit tests written and passing
- ✅ 5-8 instrumented tests written and passing
- ✅ 100% code coverage for new code
- ✅ No regression in existing tests
- ✅ Code reviewed and approved
- ✅ API documented with KDoc
- ✅ Progress narrative updated

**Validation:**
```bash
# Run full test suite
./gradlew test connectedAndroidTest

# Generate coverage report
./gradlew jacocoTestReport

# Expected output:
# - All tests GREEN
# - Coverage: 100% for camera/*.kt (Phase 1 files)
```

---

**Ready to begin Phase 1, sir?**
