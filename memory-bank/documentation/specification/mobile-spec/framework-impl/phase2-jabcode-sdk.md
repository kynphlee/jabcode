# Phase 2: :jabcode-sdk Module - Native Wrapper

**Duration:** 1 week (5 working days)  
**Dependencies:** :core  
**Status:** ⬜ Not Started

---

## Overview

Wraps the native JABCode library with a Kotlin API, providing:
- Encoding/decoding for 6 color modes (4, 8, 16, 32, 64, 128)
- Calibration profile management
- Performance tracking

**Coverage Target:** 85%+ (35 tests)

---

## Implementation Plan

### **Day 1-2: JABCodeEncoder**

**TDD Approach:**
1. Write 12 tests (6 modes × 2 scenarios each)
2. Implement `JABCodeEncoder` class
3. Run tests → Coverage ≥ 85%

**Test Template:**
```kotlin
@Test
fun `encode 4-color mode with ECC level 3`() {
    val encoder = JABCodeEncoder()
    val params = EncodeParams(colorNumber = 4, eccLevel = 3)
    val result = encoder.encode("Test data", params)
    
    assertNotNull(result)
    assertTrue(result.width > 0)
    assertTrue(result.height > 0)
}
```

**Implementation:**
```kotlin
class JABCodeEncoder(
    private val logger: Logger
) {
    fun encode(data: String, params: EncodeParams): EncodeResult? {
        return try {
            val nativeResult = JABCodeMobile.encode(data, params)
            logger.info("JABCodeEncoder", "Encoded ${data.length} bytes")
            nativeResult
        } catch (e: Exception) {
            logger.error("JABCodeEncoder", "Encoding failed", e)
            null
        }
    }
}
```

---

### **Day 3: JABCodeDecoder**

**TDD Approach:**
1. Write 12 tests (6 modes × 2 scenarios)
2. Implement `JABCodeDecoder` class
3. Run tests → Coverage ≥ 85%

**Test Template:**
```kotlin
@Test
fun `decode 8-color mode roundtrip`() {
    val encoder = JABCodeEncoder()
    val decoder = JABCodeDecoder()
    
    val originalData = "Hello JABAuth"
    val encoded = encoder.encode(originalData, EncodeParams(8, 3))
    val decoded = decoder.decode(encoded!!, 8, 3)
    
    assertEquals(originalData, decoded)
}
```

---

### **Day 4: Calibration Manager**

**TDD Approach:**
1. Write 6 tests (load, save, clear, validate)
2. Implement `CalibrationManager`
3. Run tests → Coverage ≥ 85%

**Key Tests:**
```kotlin
@Test
fun `save calibration profile to storage`() {
    val profile = CalibrationProfile(
        printer = PrinterInfo("HP", "LaserJet"),
        colorOffsets = ColorOffsets(r = 0.95f, g = 1.0f, b = 1.05f)
    )
    
    val result = calibrationManager.save(profile)
    assertTrue(result)
    assertTrue(calibrationManager.hasActiveProfile())
}

@Test
fun `load calibration applies to native library`() {
    calibrationManager.load(profileId)
    verify(jabCodeMobile).loadCalibration(any())
}
```

---

### **Day 5: Performance Tracking & Phase Completion**

**Deliverables:**
1. `PerformanceTracker` with 5 tests
2. Complete test suite (35 tests)
3. Run `/test-coverage-update` workflow
4. Documentation

**Coverage Check:**
```bash
./gradlew :jabcode-sdk:jacocoTestReport
# Expected: 85%+ coverage
```

**Success Criteria:**
- ✅ All 35 tests pass
- ✅ 85%+ coverage
- ✅ All 6 color modes work
- ✅ Performance metrics collected

---

## Module Structure

```
:jabcode-sdk/src/main/java/com/jabauth/jabcode/
├── JABCodeEncoder.kt
├── JABCodeDecoder.kt
├── CalibrationManager.kt
├── PerformanceTracker.kt
├── models/
│   ├── EncodeParams.kt
│   ├── EncodeResult.kt
│   ├── CalibrationProfile.kt
│   └── PerformanceMetrics.kt
└── native/
    └── JABCodeMobile.kt (wrapper for JNI)
```

---

## Test Matrix

| Color Mode | Encode Tests | Decode Tests | Roundtrip | Total |
|------------|--------------|--------------|-----------|-------|
| 4-color | 2 | 2 | 1 | 5 |
| 8-color | 2 | 2 | 1 | 5 |
| 16-color | 2 | 2 | 1 | 5 |
| 32-color | 2 | 2 | 1 | 5 |
| 64-color | 2 | 2 | 1 | 5 |
| 128-color | 2 | 2 | 1 | 5 |
| Calibration | - | - | - | 6 |
| Performance | - | - | - | 5 |
| **TOTAL** | **12** | **12** | **6** | **41** |

---

**Last Updated:** 2026-05-02  
**Next:** Phase 3 (:jabauth-client)
