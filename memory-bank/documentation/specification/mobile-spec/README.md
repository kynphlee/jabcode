# JABCode Mobile Specification

**Target Platforms:** Android (NDK) & iOS  
**Implementation Approach:** Swift-Java Interop  
**Branch:** `swift-java-poc`  
**Implementation Root:** `/swift-java-wrapper/`

---

## Purpose

This folder contains **actionable specifications** for compiling, optimizing, and deploying the JABCode native library on mobile platforms. All documentation is based on:

- **ISO/IEC 23634:2022** JAB Code polychrome bar code symbology specification
- **Native C source code** analysis from `@/src/jabcode/`
- **Desktop optimization learnings** (LDPC matrix caching, palette fixes)
- **Mobile platform constraints** (memory, CPU, battery, camera integration)

---

## Documentation Structure

### Core Specifications

| Document | Purpose | Audience |
|----------|---------|----------|
| **[00-index.md](00-index.md)** | Quick reference, decision trees | All developers |
| **[01-native-compilation.md](01-native-compilation.md)** | C library build requirements | Build engineers |
| **[02-mobile-optimizations.md](02-mobile-optimizations.md)** | Performance tuning strategies | C/Native developers |
| **[03-tdd-benchmarks.md](03-tdd-benchmarks.md)** | Testing methodology | QA/Test engineers |
| **[04-swift-java-interop.md](04-swift-java-interop.md)** | Bridge architecture | Platform developers |
| **[05-implementation-roadmap.md](05-implementation-roadmap.md)** | Phased delivery plan | Project managers |

### Reference Materials

- **ISO-IEC-23634.txt** - Full JABCode specification (parent directory)
- **Desktop implementation** - `@/src/jabcode/` (proven algorithms)
- **Mobile research** - `@/memory-bank/research/swift-java-poc/` (exploratory)

---

## Quick Start Decision Tree

```
Are you new to this project?
├─ YES → Read 00-index.md first
│         Then: 05-implementation-roadmap.md
│
└─ NO  → What's your role?
          ├─ Native C developer → 01-native-compilation.md + 02-mobile-optimizations.md
          ├─ Android developer  → 04-swift-java-interop.md + 01-native-compilation.md (NDK section)
          ├─ iOS developer      → 04-swift-java-interop.md + 01-native-compilation.md (Xcode section)
          ├─ QA/Test engineer   → 03-tdd-benchmarks.md
          └─ Project manager    → 05-implementation-roadmap.md
```

---

## Critical Findings Summary

### ✅ Ready for Mobile

- **Core algorithms** - All ISO-compliant encoding/decoding logic is platform-agnostic
- **Error correction** - LDPC implementation optimized (matrix caching already in place)
- **Color modes** - 4, 8, 16, 32, 64, 128-color modes tested and working
- **Memory footprint** - Acceptable for mobile (<5MB with optimizations)

### ⚠️ Requires Modification

- **Image I/O** - Remove `image.c` dependency (libpng/libtiff are desktop-only)
- **Build system** - Replace Makefile with CMake (Android) and Xcode/SPM (iOS)
- **Logging** - Replace `stdio.h` printf with platform logging APIs

### ❌ Known Issues

- **256-color mode** - Malloc corruption, exclude from mobile builds
- **TIFF output** - Desktop-only feature, remove for mobile
- **Threading** - Current implementation not thread-safe, needs mobile-specific guards

---

## Mobile Platform Constraints

### Memory

- **Target:** <5MB peak memory during encode/decode
- **Strategy:** Use platform bitmap APIs, avoid buffer copies
- **ISO Consideration:** Symbol cascading (up to 61 symbols) - cap at 4 for mobile

### CPU

- **Target:** <50ms encode, <80ms decode (iPhone 12 / Pixel 5 baseline)
- **Strategy:** ARM NEON SIMD for LDPC syndrome checking, detector color classification
- **ISO Consideration:** Mask pattern evaluation (8 patterns) - early termination on mobile

### Battery

- **Target:** <1% battery per 100 scans
- **Strategy:** Matrix caching (reuse across operations), camera throttling
- **ISO Consideration:** Real-time camera processing - decode on capture, not continuous

---

## Implementation Phases

### Phase 1: Native C Layer (Weeks 1-2)
- Create platform-agnostic C API (`mobile_bridge.c`)
- Remove image I/O dependencies
- Write C unit tests (raw RGBA buffers)
- Enable MOBILE_BUILD conditional compilation

### Phase 2: Android Integration (Week 3)
- CMakeLists.txt for NDK
- JNI wrapper layer
- Kotlin API
- CameraX integration

### Phase 3: iOS Integration (Week 4)
- Swift Package / Xcode project
- Swift C interop
- Swift API wrapper
- AVFoundation camera

### Phase 4: Swift-Java Bridge (Week 5)
- swift-java toolchain setup
- Bidirectional interface
- Memory marshalling
- Cross-platform testing

### Phase 5: Polish & Ship (Weeks 6-7)
- Performance benchmarks
- Documentation
- CI/CD pipeline
- Production release

---

## Testing Philosophy

**Test-Driven Development (TDD) Requirements:**

1. **Write tests first** - Define expected behavior before implementation
2. **No platform dependencies in C tests** - Use raw buffers, not UIImage/Bitmap
3. **Shared test fixtures** - Same RGBA test data for Android and iOS parity
4. **Benchmark every commit** - Performance regressions caught immediately
5. **Coverage targets** - >80% C layer, >90% platform layers

**Reference:** See `03-tdd-benchmarks.md` for complete methodology

---

## Success Criteria

### Functional
- ✅ Encode/decode 4-color and 8-color modes
- ✅ Round-trip accuracy: 100% clean, >95% damaged
- ✅ Symbol sizes 21×21 to 49×49
- ✅ Error correction levels 1-7

### Performance
- ✅ Encode 100 chars: <50ms
- ✅ Decode clean code: <80ms
- ✅ Memory: <5MB peak
- ✅ Battery: <1% per 100 scans

### Quality
- ✅ Zero memory leaks (ASAN/Instruments verified)
- ✅ Thread-safe encoding
- ✅ Crash-free on all devices
- ✅ Test coverage >80%

---

## Questions? Next Steps?

1. **New to mobile development?** → Start with `05-implementation-roadmap.md`
2. **Ready to code?** → `01-native-compilation.md` + setup your toolchain
3. **Need architecture overview?** → `04-swift-java-interop.md`
4. **Want performance details?** → `02-mobile-optimizations.md`

**Last Updated:** 2026-01-16  
**Specification Version:** ISO/IEC 23634:2022  
**JABCode Library Version:** 2.0.0 (desktop baseline)
