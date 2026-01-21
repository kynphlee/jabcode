# JABCode Mobile Implementation - Quick Reference

**Last Updated:** 2026-01-16  
**Status:** Specification Complete, Implementation Pending

---

## At a Glance

```
JABCode Mobile Stack:
┌─────────────────────────────────────────┐
│ Platform Layer (Kotlin/Swift)          │  ← Camera, UI, File I/O
├─────────────────────────────────────────┤
│ Swift-Java Bridge                       │  ← Bidirectional interop
├─────────────────────────────────────────┤
│ JABCode Native C (NO image.c)          │  ← Core algorithms
└─────────────────────────────────────────┘
```

---

## Critical Dependencies

### ✅ Keep (Standard C)
- `stdlib.h`, `string.h`, `math.h`
- `encoder.c`, `decoder.c`, `ldpc.c`, `detector.c`
- `binarizer.c`, `mask.c`, `sample.c`, `transform.c`
- `interleave.c`, `pseudo_random.c`

### ❌ Remove (Desktop-only)
- `image.c` - Replace with platform bitmap APIs
- `libpng`, `libtiff` - Not available on mobile
- `stdio.h` printf - Replace with platform logging

### ➕ Add (Mobile-specific)
- `mobile_bridge.c` - Platform-agnostic C API
- CMakeLists.txt (Android) or Package.swift (iOS)
- ARM NEON optimizations (optional but recommended)

---

## Color Mode Selection

| Mode | Colors | Mobile Recommendation | Use Case |
|------|--------|----------------------|----------|
| **1** | 4 | ⭐⭐⭐ **BEST** | Air-gapped file transfer, poor lighting |
| **2** | 8 | ⭐⭐ Standard | QR code replacement, general use |
| **3** | 16 | ⭐ Advanced | High-quality cameras only |
| **4** | 32 | ⭐ Advanced | High-quality cameras only |
| **5** | 64 | ⚠️ Expert | Excellent lighting required |
| **6** | 128 | ✅ Working | Tested but overkill for mobile |
| **7** | 256 | ❌ **BROKEN** | Do not use (malloc corruption) |

**Default for Mobile:** Use mode 1 (4-color) for maximum reliability.

---

## Performance Targets

### Baseline Devices
- **Android:** Google Pixel 5 (Snapdragon 765G)
- **iOS:** iPhone 12 (A14 Bionic)

### Target Metrics

| Operation | Target (ms) | Memory (MB) | Battery (%/100 ops) |
|-----------|-------------|-------------|---------------------|
| **Encode 100 chars** | < 50 | < 2 | < 0.5 |
| **Decode clean code** | < 80 | < 3 | < 0.5 |
| **Decode damaged (10% error)** | < 150 | < 4 | < 1.0 |
| **Peak memory footprint** | N/A | < 5 | N/A |

---

## Build Commands Quick Reference

### Android (NDK)
```bash
# From project root
cd swift-java-wrapper/android
./gradlew assembleDebug

# Manual NDK build
ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk
```

### iOS (Xcode)
```bash
# Swift Package
swift build

# Xcode build
xcodebuild -scheme JABCode -configuration Debug

# Run tests
swift test
```

### Desktop Testing (C only)
```bash
# From src/jabcode/
make clean
make CFLAGS="-O0 -g -DMOBILE_BUILD -fsanitize=address"

# Run unit tests
./test_encoder_mobile
./test_decoder_mobile
```

---

## API Quick Reference

### C Native API (mobile_bridge.h)

```c
// Encode
jab_encode* jabEncodeCreate(
    jab_byte* rgba_buffer,    // Raw RGBA pixel data
    jab_int32 width,          // Image width
    jab_int32 height,         // Image height
    jab_char* data,           // Data to encode
    jab_int32 data_length,    // Data length in bytes
    jab_int32 color_mode      // 1=4-color, 2=8-color, etc.
);

// Decode
jab_data* jabDecode(
    jab_byte* rgba_buffer,    // Raw RGBA pixel data
    jab_int32 width,          // Image width
    jab_int32 height          // Image height
);

// Cleanup
void jabEncodeFree(jab_encode* enc);
void jabDataFree(jab_data* data);
```

### Swift API (JABCodeEncoder.swift)

```swift
import JABCode

// Encode
let encoder = JABCodeEncoder(colorMode: .quaternary)
let encoded: Data = try encoder.encode("Hello, World!")

// Decode
let decoder = JABCodeDecoder()
let decoded: String = try decoder.decode(imageData)
```

### Kotlin/Java API (via Swift-Java)

```kotlin
import com.jabcode.mobile.JABCodeNative

// Encode
val encoder = JABCodeNative()
val encoded: ByteArray = encoder.encode("Hello, World!", colorMode = 4)

// Decode
val decoded: String = encoder.decode(imageData)
```

---

## Common Issues & Solutions

### Issue 1: Undefined reference to `png_*` functions
**Cause:** Trying to link against libpng  
**Solution:** Remove `image.c` from build, use `mobile_bridge.c`

### Issue 2: Crash during encode/decode
**Cause:** 256-color mode malloc corruption  
**Solution:** Restrict `color_number <= 128` in your API layer

### Issue 3: Poor decode accuracy
**Cause:** Camera image quality or wrong color mode  
**Solution:** Use 4-color mode, ensure good lighting, preprocess image

### Issue 4: Slow performance
**Cause:** Missing ARM NEON optimizations  
**Solution:** Enable NEON in CMakeLists.txt (`LOCAL_ARM_NEON := true`)

### Issue 5: Memory leaks
**Cause:** Not calling `jabEncodeFree()` or `jabDataFree()`  
**Solution:** Always free C structs, use RAII wrappers in Swift/Kotlin

---

## Test Coverage Requirements

### C Layer (>80% coverage)
- Unit tests for all public functions in `mobile_bridge.c`
- LDPC error correction edge cases
- Color palette generation (4, 8, 16, 32, 64, 128 colors)
- Finder pattern detection
- Masking/demasking round-trip

### Platform Layer (>90% coverage)
- Android: Instrumented tests with CameraX
- iOS: XCTest with AVFoundation
- Memory leak detection (LeakCanary/Instruments)
- Thread safety tests (concurrent encode/decode)

### Integration Tests
- Round-trip encode→decode for all color modes
- Damaged code recovery (5%, 10%, 20% error rates)
- Performance benchmarks on target devices
- Cross-platform parity (Android output == iOS output)

---

## File Locations

### Source Code
- **Native C:** `@/src/jabcode/` (desktop implementation)
- **Mobile Root:** `@/swift-java-wrapper/` (to be created)
- **Specification:** `@/memory-bank/documentation/specification/ISO-IEC-23634.txt`

### Documentation
- **This folder:** `@/memory-bank/documentation/specification/mobile-spec/`
- **Research (exploratory):** `@/memory-bank/research/swift-java-poc/`
- **Diagnostics:** `@/memory-bank/diagnostics/`

### Test Fixtures
- **Location:** `@/swift-java-wrapper/test/fixtures/`
- **Format:** Raw RGBA buffers (`.rgba` files) + expected outputs (`.txt`)
- **Examples:**
  - `test_4color_21x21.rgba` (1,764 bytes: 21×21×4)
  - `test_8color_25x25.rgba` (2,500 bytes)
  - `expected_decode_4color.txt`

---

## Next Actions by Role

### Native C Developer
1. Read `01-native-compilation.md`
2. Create `mobile_bridge.c` with API specified above
3. Write C unit tests (no image I/O dependencies)
4. Enable `MOBILE_BUILD` flag, remove `image.c` from build

### Android Developer
1. Read `04-swift-java-interop.md`
2. Set up NDK environment (CMakeLists.txt)
3. Implement JNI wrapper or use swift-java bridge
4. Integrate CameraX for live scanning

### iOS Developer
1. Read `04-swift-java-interop.md`
2. Set up Swift Package or Xcode project
3. Implement Swift C interop layer
4. Integrate AVFoundation camera

### QA/Test Engineer
1. Read `03-tdd-benchmarks.md`
2. Create test fixture library (RGBA buffers)
3. Set up benchmark harness (JMH/XCTest.measure)
4. Define acceptance criteria for each release

### Project Manager
1. Read `05-implementation-roadmap.md`
2. Allocate 5-7 weeks for initial implementation
3. Plan for iterative releases (Phase 1 → Phase 5)
4. Set up CI/CD pipeline (GitHub Actions / Bitrise)

---

## ISO/IEC 23634 Key Sections for Mobile

| Section | Topic | Mobile Relevance |
|---------|-------|------------------|
| **4.1.e** | Module color modes (Nc) | Use modes 0-6, skip mode 7 (256-color) |
| **5.3** | Error correction (LDPC) | Already optimized (matrix caching) |
| **5.8** | Data masking | Optimize with early termination |
| **6.2** | Decoding algorithm | Focus on finder pattern detection speed |
| **7.3** | Symbol dimensions | Cap at 49×49 for mobile screens |

---

## Glossary

- **RGBA buffer:** Raw pixel data in Red-Green-Blue-Alpha format (4 bytes per pixel)
- **LDPC:** Low-Density Parity-Check code (error correction algorithm)
- **NEON:** ARM's SIMD instruction set (4x-8x speedup for vector operations)
- **NDK:** Android Native Development Kit (C/C++ compilation for Android)
- **Swift-Java:** Bidirectional interop between Swift and JVM languages
- **TDD:** Test-Driven Development (write tests before implementation)
- **Finder Pattern:** Corner markers in JABCode symbols for detection
- **ECC Level:** Error Correction Capability level (1-7, higher = more robust)

---

**Remember:** Mobile JABCode is all about **eliminating desktop dependencies** and **optimizing for constraints**. When in doubt, consult the ISO specification and test on real devices early.
