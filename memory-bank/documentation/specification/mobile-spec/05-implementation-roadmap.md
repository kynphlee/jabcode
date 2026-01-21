# JABCode Mobile Implementation Roadmap

**Duration:** 5-7 weeks  
**Team Size:** 1-2 developers  
**Delivery Model:** Iterative releases (Phase 1 → Phase 5)

---

## Roadmap Overview

```
Phase 1: Native C Layer (Weeks 1-2)
├── Create mobile_bridge.c
├── Remove image.c dependency
├── Write C unit tests
└── Verify desktop compilation

Phase 2: Android Integration (Week 3)
├── CMakeLists.txt for NDK
├── JNI/Swift-Java wrapper
├── Kotlin API layer
└── CameraX integration

Phase 3: iOS Integration (Week 4)
├── Swift Package/Xcode project
├── Swift C interop
├── Swift API wrapper
└── AVFoundation camera

Phase 4: Swift-Java Bridge (Week 5)
├── swift-java toolchain setup
├── Bidirectional interface
├── Memory marshalling
└── Cross-platform testing

Phase 5: Polish & Ship (Weeks 6-7)
├── Performance benchmarks
├── Documentation
├── CI/CD pipeline
└── Production release
```

---

## Phase 1: Native C Layer (Weeks 1-2)

### Goals

- ✅ Platform-agnostic C API ready
- ✅ All desktop dependencies removed
- ✅ C unit tests passing (>80% coverage)
- ✅ Desktop build verification

### Tasks

#### Week 1: Foundation

**Day 1-2: Create mobile_bridge.h/c**
```bash
# File: @/swift-java-wrapper/src/c/mobile_bridge.h
# File: @/swift-java-wrapper/src/c/mobile_bridge.c

- [ ] Define jab_mobile_encode_params struct
- [ ] Implement jabMobileEncodeCreate()
- [ ] Implement jabMobileDecode()
- [ ] Implement jabMobileEncodeFree(), jabMobileDataFree()
- [ ] Implement jabMobileGetLastError() (thread-local storage)
- [ ] Add validation: reject color_number=256, symbol_number>4
```

**Acceptance Criteria:**
- Compiles without errors on desktop (gcc/clang)
- No dependencies on image.c, libpng, libtiff
- All functions have error handling

**Day 3-4: Remove Desktop Dependencies**
```bash
# Update build system
- [ ] Create CMakeLists.txt excluding image.c
- [ ] Add MOBILE_BUILD flag to compilation
- [ ] Replace stdio.h printf with custom error reporting
- [ ] Verify libjabcode.a builds without PNG/TIFF linkage
```

**Acceptance Criteria:**
```bash
$ nm libjabcode.a | grep png
# Should return nothing

$ ldd libjabcode.so | grep png
# Should return nothing
```

**Day 5: Create Test Fixtures**
```bash
# Directory: @/swift-java-wrapper/test/fixtures/
- [ ] Generate test_4color_21x21.rgba (desktop encoder)
- [ ] Generate test_8color_25x25.rgba
- [ ] Generate test_damaged_10pct.rgba (corrupted bits)
- [ ] Create expected outputs (.txt files)
- [ ] Document fixture generation process
```

**Deliverable:**
```
test/fixtures/
├── input/
│   ├── test_4color_21x21.rgba (1,764 bytes)
│   ├── test_8color_25x25.rgba (2,500 bytes)
│   └── test_damaged_10pct.rgba
├── expected/
│   ├── decode_4color.txt ("Hello, World!")
│   └── decode_8color.txt
└── generate_fixtures.sh
```

#### Week 2: Testing & Verification

**Day 6-8: Write C Unit Tests**
```bash
# File: @/swift-java-wrapper/test/c/test_mobile_bridge.c
- [ ] test_encode_4color_basic()
- [ ] test_encode_rejects_256_color()
- [ ] test_encode_limits_symbols_to_4()
- [ ] test_decode_from_rgba_buffer()
- [ ] test_roundtrip_encode_decode()
- [ ] test_ldpc_error_correction()
- [ ] test_memory_cleanup()
```

**Acceptance Criteria:**
```bash
$ ./test_suite
5 Tests 0 Failures 0 Ignored
PASS
```

**Day 9: ASAN/Valgrind Verification**
```bash
# Memory leak detection
- [ ] Build with -fsanitize=address
- [ ] Run all tests under ASAN
- [ ] Fix any leaks/overflows found
- [ ] Verify clean exit (no errors)
```

**Day 10: Desktop Build Smoke Test**
```bash
# Ensure changes don't break desktop
- [ ] Build desktop jabcodeWriter/jabcodeReader
- [ ] Run desktop tests
- [ ] Verify PNG I/O still works (for desktop)
- [ ] Tag as "Phase1-Complete"
```

### Milestones

- **Week 1 End:** mobile_bridge.c implemented, test fixtures ready
- **Week 2 End:** All C tests passing, ASAN clean, desktop unaffected

### Risk Mitigation

**Risk:** Existing desktop code has hidden dependencies  
**Mitigation:** Incremental testing, grep for png/tiff symbols

**Risk:** LDPC matrix caching breaks on mobile  
**Mitigation:** Test with multiple ECC levels, verify cache hits

---

## Phase 2: Android Integration (Week 3)

### Goals

- ✅ NDK build successful
- ✅ JNI/Swift-Java wrapper functional
- ✅ Basic encode/decode working on Android device
- ✅ CameraX integration prototype

### Tasks

**Day 11-12: NDK Build Setup**
```bash
# File: @/swift-java-wrapper/android/jni/CMakeLists.txt
- [ ] Configure CMake for NDK
- [ ] Add all C source files (except image.c)
- [ ] Set MOBILE_BUILD flag
- [ ] Enable ARM NEON for arm64-v8a
- [ ] Build for arm64-v8a, armeabi-v7a, x86_64
```

**Build Verification:**
```bash
$ cd swift-java-wrapper/android/jni/build
$ cmake .. -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-21
$ make
$ ls ../libs/arm64-v8a/libjabcode.so
# File should exist, ~300KB
```

**Day 13: JNI/Swift-Java Wrapper**
```bash
# Decision point: Traditional JNI or Swift-Java bridge?
# If Swift-Java:
- [ ] Set up swift-java toolchain
- [ ] Create Swift wrapper (JABCodeEncoder.swift, JABCodeDecoder.swift)
- [ ] Generate Java bindings
- [ ] Test from Kotlin

# If Traditional JNI:
- [ ] Write JNI wrapper functions (jabcode_jni.c)
- [ ] Create Java native method declarations
- [ ] Test from Kotlin
```

**Day 14: Kotlin API Layer**
```kotlin
// File: @/swift-java-wrapper/android/src/main/kotlin/JABCode.kt
- [ ] JABCodeEncoder class
- [ ] JABCodeDecoder class
- [ ] JABCodeError exception
- [ ] ColorMode enum
- [ ] EncodedResult data class
```

**Day 15: CameraX Integration**
```kotlin
// File: @/swift-java-wrapper/android/src/main/kotlin/CameraScanner.kt
- [ ] JABCodeScanner class
- [ ] ImageAnalysis.Analyzer implementation
- [ ] Decode throttling (2 fps)
- [ ] ROI extraction (center 60%)
- [ ] Auto-stop on success
```

**Day 15: Instrumented Tests**
```kotlin
// File: @/swift-java-wrapper/android/src/androidTest/kotlin/JABCodeTest.kt
- [ ] testNativeLibraryLoads()
- [ ] testEncodeBasic()
- [ ] testDecodeFromAsset()
- [ ] testRoundTrip()
- [ ] testCameraIntegration() (manual)
```

**Run on Device:**
```bash
$ ./gradlew connectedAndroidTest
# All tests should pass
```

### Milestones

- **Day 12:** NDK build produces libjabcode.so
- **Day 14:** Kotlin API functional, unit tests pass
- **Day 15:** Camera integration working on physical device

### Deliverables

```
swift-java-wrapper/android/
├── jni/
│   ├── CMakeLists.txt
│   └── build/libs/arm64-v8a/libjabcode.so
├── src/
│   ├── main/kotlin/JABCode.kt
│   └── androidTest/kotlin/JABCodeTest.kt
└── build.gradle
```

---

## Phase 3: iOS Integration (Week 4)

### Goals

- ✅ Swift Package compiles
- ✅ Swift wrapper functional
- ✅ Basic encode/decode on iOS simulator/device
- ✅ AVFoundation camera integration

### Tasks

**Day 16-17: Swift Package Setup**
```bash
# File: @/swift-java-wrapper/ios/Package.swift
- [ ] Define JABCodeNative target (C sources)
- [ ] Define JABCode target (Swift wrapper)
- [ ] Exclude image.c from build
- [ ] Set MOBILE_BUILD flag
- [ ] Add swift-java dependency (if using)
```

**Build Verification:**
```bash
$ cd swift-java-wrapper/ios
$ swift build
# Should complete without errors
```

**Day 18: Swift Wrapper**
```swift
// Files: @/swift-java-wrapper/ios/Sources/JABCode/
- [ ] JABCodeEncoder.swift
- [ ] JABCodeDecoder.swift
- [ ] JABCodeError.swift
- [ ] EncodedResult.swift
- [ ] ColorMode enum
```

**Day 19: AVFoundation Camera**
```swift
// File: @/swift-java-wrapper/ios/Sources/JABCode/CameraScanner.swift
- [ ] JABCodeScanner class
- [ ] AVCaptureVideoDataOutputSampleBufferDelegate
- [ ] CVPixelBuffer → RGBA conversion
- [ ] Decode throttling (2 fps)
- [ ] ROI extraction
```

**Day 20: XCTest Suite**
```swift
// File: @/swift-java-wrapper/ios/Tests/JABCodeTests/
- [ ] EncoderTests.swift (testEncodeBasic, testRoundTrip)
- [ ] DecoderTests.swift (testDecodeFromFixture)
- [ ] PerformanceTests.swift (benchmarks)
- [ ] CameraScannerTests.swift (requires device)
```

**Run Tests:**
```bash
$ swift test
# All tests pass

# Performance measurement
$ swift test --filter PerformanceTests
# XCTest reports: encode 45ms, decode 70ms
```

### Milestones

- **Day 17:** Swift Package builds successfully
- **Day 19:** Camera scanner working on physical iPhone
- **Day 20:** All XCTests passing, performance meets targets

### Deliverables

```
swift-java-wrapper/ios/
├── Sources/
│   └── JABCode/
│       ├── JABCodeEncoder.swift
│       ├── JABCodeDecoder.swift
│       └── CameraScanner.swift
├── Tests/
│   └── JABCodeTests/
│       ├── EncoderTests.swift
│       └── PerformanceTests.swift
└── Package.swift
```

---

## Phase 4: Swift-Java Bridge (Week 5)

### Goals

- ✅ Swift-Java toolchain operational
- ✅ Bidirectional Swift ↔ Java calls working
- ✅ Android app uses Swift wrapper (not JNI)
- ✅ Cross-platform parity verified

### Tasks

**Day 21-22: Toolchain Setup**
```bash
- [ ] Install swift-java toolchain
- [ ] Configure for Android target
- [ ] Test basic Swift → Java call
- [ ] Test basic Java → Swift call
- [ ] Verify memory marshalling works
```

**Day 23: Annotate Swift Code**
```swift
// Update Swift wrapper with Java annotations
- [ ] Add @JavaClass to all classes
- [ ] Add @JavaMethod to all public methods
- [ ] Add @JavaField to all public properties
- [ ] Add @JavaConstructor to initializers
```

**Day 24: Generate Java Bindings**
```bash
$ swift build --target JABCode --java-bindings
# Generates: build/java/com/jabcode/mobile/*.java

- [ ] Verify Java classes generated
- [ ] Check method signatures match Swift
- [ ] Test compilation in Android project
```

**Day 25: Integration Testing**
```kotlin
// Android app using Swift-Java
- [ ] Replace JNI wrapper with Swift-Java
- [ ] Update imports to generated Java classes
- [ ] Run all instrumented tests
- [ ] Compare performance vs JNI baseline
```

**Day 25: Cross-Platform Parity**
```bash
# Ensure Android and iOS produce identical outputs
- [ ] Run parity test on Android
- [ ] Run parity test on iOS
- [ ] Compare output hashes
- [ ] Fix any discrepancies
```

### Milestones

- **Day 22:** Swift-Java toolchain functional
- **Day 24:** Java bindings generated and compiling
- **Day 25:** Parity tests pass, Android uses Swift wrapper

### Deliverables

- Swift-Java integration working
- Single Swift codebase serving both platforms
- Performance overhead <10% vs direct JNI

### Fallback Plan

**If Swift-Java fails:**
- Abort Phase 4
- Continue with separate JNI (Android) + Swift C interop (iOS)
- Accept 2× maintenance burden
- Document decision in ADR (Architecture Decision Record)

---

## Phase 5: Polish & Ship (Weeks 6-7)

### Goals

- ✅ Performance targets met
- ✅ Documentation complete
- ✅ CI/CD pipeline operational
- ✅ Production-ready release

### Week 6: Performance & Optimization

**Day 26-27: Benchmarking**
```bash
# Android
- [ ] Run Jetpack Benchmark on Pixel 5
- [ ] Measure encode 100 chars: target <50ms
- [ ] Measure decode clean: target <80ms
- [ ] Measure memory footprint: target <5MB
- [ ] Profile with Android Profiler

# iOS
- [ ] Run XCTest performance on iPhone 12
- [ ] Measure encode 100 chars: target <50ms
- [ ] Measure decode clean: target <80ms
- [ ] Measure battery impact: target <1% per 100 ops
- [ ] Profile with Instruments
```

**Day 28: Optimization (if needed)**
```bash
# If targets not met:
- [ ] Add ARM NEON syndrome checking (02-mobile-optimizations.md)
- [ ] Optimize detector color classification
- [ ] Implement mask pattern early termination
- [ ] Add fast path for clean data
- [ ] Re-benchmark
```

**Day 29: Memory Leak Audit**
```bash
# Android
- [ ] Run LeakCanary on sample app
- [ ] Check for leaked Activities, Fragments
- [ ] Verify buffer pool releases memory

# iOS
- [ ] Run Instruments → Leaks
- [ ] Check for retain cycles
- [ ] Verify ARC cleanup works
```

**Day 30: Battery Testing**
```bash
# Android
- [ ] Use Battery Historian
- [ ] Measure 100 scans with camera
- [ ] Verify <1% battery drain

# iOS
- [ ] Use Xcode Energy Log
- [ ] Measure 100 scans with camera
- [ ] Verify <1% battery drain
```

### Week 7: Documentation & Release

**Day 31-32: Documentation**
```bash
# User-facing docs
- [ ] README.md (quick start, installation)
- [ ] API.md (Kotlin/Swift API reference)
- [ ] EXAMPLES.md (code samples)
- [ ] TROUBLESHOOTING.md (common issues)

# Developer docs
- [ ] ARCHITECTURE.md (system design)
- [ ] BUILD.md (compilation instructions)
- [ ] CONTRIBUTING.md (how to contribute)
- [ ] CHANGELOG.md (version history)
```

**Day 33: CI/CD Pipeline**
```yaml
# .github/workflows/mobile-ci.yml
- [ ] Android build job (NDK + Gradle)
- [ ] iOS build job (Swift build)
- [ ] Run unit tests on both platforms
- [ ] Run instrumented tests (Android emulator)
- [ ] Generate code coverage reports
- [ ] Upload artifacts (AAR, framework)
```

**Day 34: Release Preparation**
```bash
# Version tagging
- [ ] Bump version to 1.0.0
- [ ] Update CHANGELOG.md
- [ ] Tag release in git

# Android
- [ ] Generate release AAR
- [ ] Sign with release keystore
- [ ] Publish to Maven Central (optional)

# iOS
- [ ] Create XCFramework
- [ ] Archive with release configuration
- [ ] Publish to CocoaPods (optional)
```

**Day 35: Production Release**
```bash
- [ ] GitHub Release with binaries
- [ ] Update documentation site
- [ ] Announce on project channels
- [ ] Monitor for issues
```

### Milestones

- **Week 6 End:** Performance targets met, no memory leaks
- **Week 7 End:** Documentation complete, CI/CD running, v1.0.0 released

### Deliverables

```
Release Artifacts:
├── jabcode-android-1.0.0.aar (Android library)
├── JABCode.xcframework (iOS framework)
├── Documentation/ (GitHub Pages)
└── Release Notes (GitHub Release)
```

---

## Success Criteria

### Functional Requirements

- [x] Encode/decode 4-color and 8-color modes
- [x] Round-trip accuracy: 100% for clean codes
- [x] Error correction: >95% for damaged codes (10% error)
- [x] Symbol sizes: 21×21 to 49×49 supported
- [x] ECC levels: 1-7 functional

### Performance Requirements

| Metric | Target | Android (Pixel 5) | iOS (iPhone 12) | Status |
|--------|--------|-------------------|-----------------|--------|
| Encode 100 chars | <50ms | ___ms | ___ms | ⏳ |
| Decode clean | <80ms | ___ms | ___ms | ⏳ |
| Memory peak | <5MB | ___MB | ___MB | ⏳ |
| Battery/100 scans | <1% | ___%  | ___% | ⏳ |

### Quality Requirements

- [x] C layer test coverage: >80%
- [x] Platform layer coverage: >90%
- [x] Zero memory leaks (ASAN/Instruments verified)
- [x] Thread-safe encoding (concurrent tests pass)
- [x] Crash-free on all target devices

### Platform Requirements

**Android:**
- [x] Supports API 21+ (Android 5.0+)
- [x] ABIs: arm64-v8a, armeabi-v7a, x86_64
- [x] ProGuard/R8 compatible
- [x] CameraX integration working

**iOS:**
- [x] Supports iOS 13.0+
- [x] Swift 5.9+
- [x] CocoaPods and SPM support
- [x] AVFoundation integration working

---

## Risk Assessment

### High Risk

**Risk:** Swift-Java toolchain too immature  
**Probability:** 30%  
**Impact:** High (blocks Phase 4)  
**Mitigation:** Fallback to traditional JNI (2 weeks delay)  
**Owner:** Tech Lead

**Risk:** Performance targets not met  
**Probability:** 20%  
**Impact:** Medium (requires optimization)  
**Mitigation:** ARM NEON, fast paths (1 week delay)  
**Owner:** Performance Engineer

### Medium Risk

**Risk:** 256-color mode crash not fixable  
**Probability:** 50%  
**Impact:** Low (document as unsupported)  
**Mitigation:** None needed, already excluded  
**Owner:** N/A

**Risk:** Camera integration issues on old devices  
**Probability:** 40%  
**Impact:** Medium (affects user experience)  
**Mitigation:** Graceful degradation, min API 21  
**Owner:** Mobile Developer

### Low Risk

**Risk:** Memory leaks in native code  
**Probability:** 10%  
**Impact:** High (blocker)  
**Mitigation:** ASAN testing every commit  
**Owner:** QA Engineer

---

## Resource Requirements

### Personnel

- **1 Mobile Developer:** Native C, Android NDK, iOS Swift
- **0.5 QA Engineer:** Test automation, device testing
- **0.25 DevOps:** CI/CD setup

**Total:** 1.75 FTE for 7 weeks

### Hardware

- **Android Device:** Google Pixel 5 or equivalent (ARM64)
- **iOS Device:** iPhone 12 or newer
- **Optional:** Android emulator (slower, acceptable for CI)

### Tools

- **Android Studio** (free)
- **Xcode** (free, requires macOS)
- **CMake** (free)
- **Swift toolchain** (free)
- **swift-java** (open source)
- **GitHub Actions** (free tier sufficient)

---

## Iteration Plan

### Sprint 1 (Weeks 1-2): Foundation
- **Goal:** Native C layer ready
- **Demo:** C unit tests passing, ASAN clean
- **Review:** Desktop build still works

### Sprint 2 (Week 3): Android
- **Goal:** Android app encodes/decodes
- **Demo:** Live camera scan on Pixel 5
- **Review:** Performance benchmarks

### Sprint 3 (Week 4): iOS
- **Goal:** iOS app encodes/decodes
- **Demo:** Live camera scan on iPhone 12
- **Review:** Cross-platform parity

### Sprint 4 (Week 5): Integration
- **Goal:** Swift-Java bridge or fallback to JNI
- **Demo:** Single codebase serving both platforms
- **Review:** Maintenance plan

### Sprint 5 (Weeks 6-7): Ship
- **Goal:** Production release
- **Demo:** v1.0.0 on GitHub, docs live
- **Review:** Retrospective, lessons learned

---

## Post-Launch Plan

### Maintenance (Months 1-3)

- Monitor crash reports (Firebase Crashlytics)
- Fix critical bugs within 48 hours
- Performance tuning based on telemetry
- Documentation updates

### Future Enhancements (Months 4-6)

- 16-color, 32-color mode optimization
- Batch encode/decode API
- QR code fallback detection
- Multi-symbol cascade support (>4 symbols)

### Long-Term Vision (Year 2)

- React Native/Flutter wrappers
- Web Assembly port (browser support)
- Hardware acceleration (GPU, NPU)
- Real-time video stream decoding

---

## Lessons Learned (To Be Filled)

_Document key learnings after each phase:_

**Phase 1:**
- What went well?
- What could be improved?
- Unexpected challenges?

**Phase 2:**
- ...

---

## Contact & Support

**Technical Lead:** [Name]  
**Email:** [email]  
**Slack:** #jabcode-mobile  
**Wiki:** https://wiki.internal/jabcode

**Office Hours:** Tuesdays 2-3pm UTC for Q&A

---

## Appendix: Quick Command Reference

### Build Commands

```bash
# Phase 1: C unit tests
cd swift-java-wrapper/test/c
gcc -o test_suite *.c -I../../src/jabcode/include -L../../build -ljabcode -lm
./test_suite

# Phase 2: Android NDK
cd swift-java-wrapper/android/jni/build
cmake .. -DANDROID_ABI=arm64-v8a
make
./gradlew connectedAndroidTest

# Phase 3: iOS Swift
cd swift-java-wrapper/ios
swift build
swift test

# Phase 4: Swift-Java
swift build --target JABCode --java-bindings

# Phase 5: Release
./scripts/release.sh --version 1.0.0
```

### Testing Commands

```bash
# C tests with ASAN
gcc -fsanitize=address -g -o test_suite *.c -ljabcode
./test_suite

# Android benchmarks
./gradlew connectedAndroidTest -P androidx.benchmark.output.enable=true

# iOS performance
swift test --filter PerformanceTests

# Cross-platform parity
./scripts/verify_parity.sh
```

---

**Last Updated:** 2026-01-16  
**Version:** 1.0  
**Status:** Ready for implementation
