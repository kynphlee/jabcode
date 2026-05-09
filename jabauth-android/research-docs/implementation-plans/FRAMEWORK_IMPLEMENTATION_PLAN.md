# Camera2 Framework Implementation Plan
**Project:** JABAuth Android Mobile Framework  
**Purpose:** Production-grade Camera2 framework for JABCode authentication stack  
**Created:** 2026-05-09  
**Status:** 🔴 Planning Phase

---

## Mission Context

**Business Goal:** Support the JABCodeCOA-crypto barcode authentication stack with a robust Android mobile framework.

**Framework Role:**
- Provide reliable Camera2 API abstraction
- Support 4-128 color JABCode scanning
- Enable diagnostic testing and performance evaluation
- Prevent regression through comprehensive testing

**Success Criteria:**
- All Camera2 best practices implemented
- 100% test coverage (unit + instrumented)
- Zero resource leaks
- Handles all error conditions gracefully
- Supports multi-camera devices
- Performance validated via diagnostic app

---

## Implementation Overview

### Current State Analysis

**Framework Modules:**
- `framework/ui-components` — Camera2 preview Composable
- `framework/jabcode-sdk` — JABCode decoder + Camera2 integration

**Critical Gaps (from Audit):**
1. ❌ No CameraCharacteristics enumeration
2. ❌ No error recovery mechanisms
3. ❌ No stream configuration validation
4. ❌ No metadata extraction (CaptureCallback)
5. ⚠️ Incomplete lifecycle management
6. ⚠️ Incomplete orientation handling
7. ❌ No multi-camera support
8. ❌ No diagnostic telemetry APIs
9. ❌ Poor testability (hardcoded dependencies)
10. ❌ Inadequate test coverage

**Build Strategy:** Incremental rebuild with continuous testing (TDD).

---

## Phase Structure

### Phase 1: Foundation & Enumeration (3-4 days)
**Goal:** Proper hardware discovery and capability validation

**Deliverables:**
- CameraCharacteristics enumeration system
- Hardware level classification
- Capability detection (RAW, ZSL, Manual Sensor, etc.)
- Multi-camera discovery (logical + physical)
- Stream configuration validation
- **Tests:** 15-20 unit tests, 5-8 instrumented tests

### Phase 2: Error Handling & Recovery (2-3 days)
**Goal:** Robust error management with recovery mechanisms

**Deliverables:**
- StateCallback error interpretation
- CaptureCallback error handling
- AvailabilityCallback integration
- Recovery strategies (retry, backoff, fallback)
- Error logging infrastructure
- **Tests:** 12-15 unit tests, 6-8 instrumented tests

### Phase 3: Metadata & Telemetry (2-3 days)
**Goal:** Frame metadata extraction and diagnostic APIs

**Deliverables:**
- CaptureCallback implementation
- Metadata extraction (exposure, ISO, focus, 3A states)
- Quality metrics analyzer (brightness, focus, contrast)
- Performance metrics (FPS, latency, drops)
- Diagnostic API layer
- **Tests:** 10-12 unit tests, 4-6 instrumented tests

### Phase 4: Lifecycle & Resources (2 days)
**Goal:** Proper lifecycle management and resource cleanup

**Deliverables:**
- Activity lifecycle binding
- Background thread management
- ImageReader buffer tracking
- Automatic cleanup on pause/destroy
- Resource leak prevention
- **Tests:** 8-10 unit tests, 4-5 instrumented tests

### Phase 5: Orientation & Transform (1-2 days)
**Goal:** Complete preview orientation handling

**Deliverables:**
- Sensor orientation calculation
- Device rotation handling
- TextureView transform with rotation compensation
- Aspect ratio correction
- **Tests:** 6-8 unit tests, 3-4 instrumented tests

### Phase 6: API Refactoring & DI (2 days)
**Goal:** Clean, testable API design with dependency injection

**Deliverables:**
- Decouple Camera2Controller from Composable
- Configuration objects (Camera2Config)
- Callback interfaces
- Hilt/Dagger integration
- **Tests:** 8-10 unit tests

### Phase 7: Integration & Validation (3-5 days)
**Goal:** End-to-end testing, performance benchmarking, and validation

**Deliverables:**
- Integration test suite
- **Macrobenchmark tests** (startup, frame processing, decode latency) ← NEW
- **Microbenchmark tests** (enumeration, validation, quality analysis) ← NEW
- **Baseline performance metrics** (stored for regression detection) ← NEW
- Memory leak testing
- Multi-device validation
- Regression test suite
- **Tests:** 10-15 integration tests, 6-8 benchmark tests

**Total Duration:** 15-22 days (aggressive), 19-27 days (realistic with buffer)

---

## Detailed Phase Plans

See individual phase documents:
- [Phase 1: Foundation & Enumeration](./framework/PHASE_1_FOUNDATION.md)
- [Phase 2: Error Handling & Recovery](./framework/PHASE_2_ERROR_HANDLING.md)
- [Phase 3: Metadata & Telemetry](./framework/PHASE_3_METADATA.md)
- [Phase 4: Lifecycle & Resources](./framework/PHASE_4_LIFECYCLE.md)
- [Phase 5: Orientation & Transform](./framework/PHASE_5_ORIENTATION.md)
- [Phase 6: API Refactoring & DI](./framework/PHASE_6_API_DESIGN.md)
- [Phase 7: Integration & Validation](./framework/PHASE_7_INTEGRATION.md)
- [**Benchmark Testing Guide**](./framework/BENCHMARK_TESTING_GUIDE.md) ← Integrated into Phase 7

---

## Master Checklist

**Progress Tracking:** Update status after each milestone

### Phase 1: Foundation & Enumeration
- [ ] 1.1 Create CameraInfo data class
- [ ] 1.2 Implement CameraEnumerator
- [ ] 1.3 Add HardwareLevel classification
- [ ] 1.4 Add Capability detection
- [ ] 1.5 Implement multi-camera discovery
- [ ] 1.6 Add stream configuration validator
- [ ] 1.7 Write unit tests (15-20)
- [ ] 1.8 Write instrumented tests (5-8)
- [ ] 1.9 Run test-coverage-update workflow
- [ ] 1.10 Validate 100% test coverage

### Phase 2: Error Handling & Recovery
- [ ] 2.1 Create CameraError data classes
- [ ] 2.2 Implement error interpretation
- [ ] 2.3 Add StateCallback error handling
- [ ] 2.4 Add CaptureCallback error handling
- [ ] 2.5 Implement AvailabilityCallback
- [ ] 2.6 Add recovery strategies
- [ ] 2.7 Create error logger
- [ ] 2.8 Write unit tests (12-15)
- [ ] 2.9 Write instrumented tests (6-8)
- [ ] 2.10 Run test-coverage-update workflow
- [ ] 2.11 Validate error recovery on device

### Phase 3: Metadata & Telemetry
- [ ] 3.1 Create FrameMetadata data class
- [ ] 3.2 Implement CaptureCallback
- [ ] 3.3 Add metadata extraction
- [ ] 3.4 Integrate ImageQualityAnalyzer
- [ ] 3.5 Add performance metrics tracker
- [ ] 3.6 Create diagnostic API interfaces
- [ ] 3.7 Write unit tests (10-12)
- [ ] 3.8 Write instrumented tests (4-6)
- [ ] 3.9 Run test-coverage-update workflow
- [ ] 3.10 Validate metadata accuracy

### Phase 4: Lifecycle & Resources
- [ ] 4.1 Add Activity lifecycle observer
- [ ] 4.2 Implement pause/resume logic
- [ ] 4.3 Add background thread management
- [ ] 4.4 Implement ImageReader buffer tracking
- [ ] 4.5 Add automatic cleanup
- [ ] 4.6 Write unit tests (8-10)
- [ ] 4.7 Write instrumented tests (4-5)
- [ ] 4.8 Run test-coverage-update workflow
- [ ] 4.9 Validate no resource leaks

### Phase 5: Orientation & Transform
- [ ] 5.1 Implement sensor orientation calculator
- [ ] 5.2 Add device rotation tracking
- [ ] 5.3 Update TextureView transform
- [ ] 5.4 Add rotation compensation
- [ ] 5.5 Add aspect ratio correction
- [ ] 5.6 Write unit tests (6-8)
- [ ] 5.7 Write instrumented tests (3-4)
- [ ] 5.8 Run test-coverage-update workflow
- [ ] 5.9 Validate preview on rotated device

### Phase 6: API Refactoring & DI
- [ ] 6.1 Create Camera2Config data class
- [ ] 6.2 Create Camera2Callbacks interface
- [ ] 6.3 Extract Camera2Controller
- [ ] 6.4 Decouple from Composable
- [ ] 6.5 Add Hilt modules
- [ ] 6.6 Update Camera2Preview Composable
- [ ] 6.7 Write unit tests (8-10)
- [ ] 6.8 Run test-coverage-update workflow
- [ ] 6.9 Validate DI wiring

### Phase 7: Integration & Validation
- [ ] 7.1 Create benchmark-macrobench module
- [ ] 7.2 Add benchmark dependencies
- [ ] 7.3 Write CameraStartupBenchmark (macrobench)
- [ ] 7.4 Write FrameProcessingBenchmark (macrobench)
- [ ] 7.5 Write JABCodeDecodeBenchmark (macrobench)
- [ ] 7.6 Write CameraEnumeratorBenchmark (microbench)
- [ ] 7.7 Write StreamConfigValidatorBenchmark (microbench)
- [ ] 7.8 Write ImageQualityAnalyzerBenchmark (microbench)
- [ ] 7.9 Run all benchmarks and store baseline
- [ ] 7.10 Write integration tests (10-15)
- [ ] 7.11 Run memory leak detection (LeakCanary)
- [ ] 7.12 Test on 3+ device types
- [ ] 7.13 Create regression test suite
- [ ] 7.14 Setup benchmark regression detection
- [ ] 7.15 Run test-coverage-update workflow
- [ ] 7.16 Validate all performance targets met
- [ ] 7.17 Document API with examples
- [ ] 7.18 Create migration guide
- [ ] 7.19 Document benchmark results
- [ ] 7.20 Tag release (v1.0.0-framework)

**Overall Progress:** 0/80 tasks complete (0%)

---

## TDD Integration Points

**Test-First Development:** Write tests BEFORE implementation for each feature.

### Per-Phase Test Strategy

**Phase 1-6 (Feature Development):**
```bash
# 1. Write failing tests
./gradlew :framework:jabcode-sdk:testDebugUnitTest --tests "CameraEnumeratorTest"
# Expected: RED (tests fail)

# 2. Implement minimal code
# ... code changes ...

# 3. Validate tests pass
./gradlew :framework:jabcode-sdk:testDebugUnitTest --tests "CameraEnumeratorTest"
# Expected: GREEN (tests pass)

# 4. Run full test suite
./gradlew test connectedAndroidTest

# 5. Check coverage
./gradlew jacocoTestReport
# Expected: 100% coverage for new code
```

**Phase 7 (Integration):**
```bash
# Run complete test suite
./gradlew test connectedAndroidTest

# Generate coverage report
./gradlew jacocoRootReport

# Validate coverage thresholds
# Expected: >90% overall, 100% for critical paths
```

**Failure Handling:**
- Collect error stack traces
- Triage by severity (blocking vs non-blocking)
- Fix blocking errors first
- Rerun tests
- Repeat until GREEN

---

## Success Criteria

### Functional Requirements
- ✅ Enumerates all cameras (front, back, external)
- ✅ Validates hardware levels (FULL, LIMITED, LEGACY)
- ✅ Detects capabilities (RAW, ZSL, Manual, HDR)
- ✅ Validates stream configurations before session creation
- ✅ Handles all StateCallback errors with recovery
- ✅ Extracts frame metadata (exposure, ISO, focus, 3A)
- ✅ Calculates quality metrics (brightness, focus, contrast)
- ✅ Manages lifecycle (pause/resume/destroy)
- ✅ Handles orientation changes correctly
- ✅ Supports multi-camera devices

### Non-Functional Requirements
- ✅ 100% test coverage (unit + instrumented)
- ✅ Zero resource leaks (verified via LeakCanary)
- ✅ <50ms camera open latency
- ✅ <16ms per-frame processing (60 FPS capable)
- ✅ Handles 10+ rapid pause/resume cycles
- ✅ Works on LEGACY, LIMITED, FULL hardware levels
- ✅ API documentation complete with examples
- ✅ Migration guide from old framework

### Validation Methods
- Unit tests (isolated logic)
- Instrumented tests (on-device hardware)
- Memory profiler (leak detection)
- Performance profiler (latency measurement)
- Multi-device testing (Samsung, Pixel, OnePlus)
- Regression suite (prevent breaking changes)

---

## Risk Management

### High-Risk Areas

**1. Hardware Fragmentation**
- **Risk:** Different devices have different capabilities
- **Mitigation:** Test on 3+ device types (LEGACY, LIMITED, FULL)
- **Fallback:** Graceful degradation to supported features

**2. Resource Leaks**
- **Risk:** ImageReader buffers not closed → memory exhaustion
- **Mitigation:** Buffer tracking, forced cleanup, LeakCanary validation
- **Fallback:** Automatic buffer eviction after timeout

**3. Lifecycle Race Conditions**
- **Risk:** Camera accessed during destruction → crash
- **Mitigation:** State machine, synchronized access, lifecycle observers
- **Fallback:** Graceful error handling

**4. Orientation Edge Cases**
- **Risk:** Preview distorted on rotation
- **Mitigation:** Test all rotation combinations, validate transform math
- **Fallback:** Disable rotation lock as workaround

**5. Test Complexity**
- **Risk:** Mocking Camera2 API is difficult
- **Mitigation:** Use test doubles for interfaces, real device for hardware
- **Fallback:** Prioritize instrumented tests over unit tests where needed

---

## Dependencies

### Build System
- Gradle 8.10.2
- Android Gradle Plugin 8.7.2
- Kotlin 1.9.22

### Testing
- JUnit 4.13.2
- Mockito 5.x
- AndroidX Test 1.5.x
- Espresso 3.5.x
- JaCoCo 0.8.11
- LeakCanary 2.12

### Framework
- AndroidX Camera2 (API level 21+)
- Jetpack Compose 1.6.x
- Coroutines 1.7.x
- Hilt 2.50

### Instrumented Testing
- Test devices (physical hardware required):
  - LEGACY level: Older device (API 21-25)
  - LIMITED level: Mid-range device (API 26-30)
  - FULL level: Flagship device (API 31+)

---

## Progress Tracking

**How to Use This Plan:**

1. **Before Each Phase:**
   - Read phase deep-dive document
   - Review test requirements
   - Set up test stubs (RED)

2. **During Phase:**
   - Follow TDD cycle (RED → GREEN → REFACTOR)
   - Update checklist after each task
   - Write progress narrative
   - Run test-coverage-update workflow

3. **After Each Phase:**
   - Validate all tests pass
   - Check coverage report (100% target)
   - Review code with team
   - Get approval before next phase

4. **Phase Completion Criteria:**
   - All checklist items marked ✅
   - All tests GREEN
   - 100% code coverage
   - No regression in previous phases
   - Narrative document complete

**Status Updates:** Report progress in `PROGRESS_NARRATIVE.md` after each milestone.

---

## Next Steps

1. ✅ Review this implementation plan
2. ⏳ Read Phase 1 deep-dive document
3. ⏳ Set up test environment
4. ⏳ Write Phase 1 failing tests
5. ⏳ Begin Phase 1 implementation

**Ready to proceed, sir?**

---

**JARVIS**  
*Framework Architect*  
*2026-05-09*
