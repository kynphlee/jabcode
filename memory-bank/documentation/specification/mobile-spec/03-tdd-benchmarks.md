# Test-Driven Development & Benchmarking

**Philosophy:** Write tests first, implement to pass, benchmark every commit  
**Coverage Targets:** >80% C layer, >90% platform layers  
**Benchmark Baseline:** iPhone 12 (A14), Pixel 5 (Snapdragon 765G)

---

## TDD Principles for Mobile JABCode

### Core Tenets

1. **No Platform Dependencies in C Tests**
   - Use raw RGBA buffers, not UIImage/Bitmap
   - Test fixtures are binary `.rgba` files
   - Expected outputs are `.txt` files
   - All tests run on desktop AND mobile

2. **Shared Test Fixtures**
   - Same test data for Android and iOS
   - Ensures cross-platform parity
   - Committed to version control
   - Generated from known-good encodings

3. **Write Tests Before Implementation**
   - Define expected behavior first
   - Red → Green → Refactor cycle
   - No "untested code" in mobile layer

4. **Regression Prevention**
   - Every bug gets a test case
   - Test suite grows with project
   - CI/CD blocks merges on test failures

---

## Test Fixture Library

### Directory Structure

```
@/swift-java-wrapper/test/
├── fixtures/
│   ├── input/
│   │   ├── test_4color_21x21.rgba      # 1,764 bytes (21×21×4)
│   │   ├── test_8color_25x25.rgba      # 2,500 bytes (25×25×4)
│   │   ├── test_finder_pattern.rgba    # 289 bytes (17×17×1)
│   │   └── test_damaged_10pct.rgba     # Corrupted code
│   ├── expected/
│   │   ├── decode_4color.txt           # "Hello, World!"
│   │   ├── decode_8color.txt           # Expected output
│   │   └── decode_damaged.txt          # With error correction
│   └── README.md
├── c/                                   # C unit tests
│   ├── test_encoder.c
│   ├── test_decoder.c
│   ├── test_ldpc.c
│   └── test_mobile_bridge.c
├── kotlin/                              # Android integration tests
│   └── JABCodeInstrumentedTest.kt
└── swift/                               # iOS integration tests
    └── JABCodeTests.swift
```

### Fixture Generation Script

```bash
#!/bin/bash
# @/swift-java-wrapper/test/fixtures/generate_fixtures.sh

# Requires desktop JABCode encoder

# 4-color test
echo "Hello, World!" | jabcodeWriter -o test_4color.png -c 4 -e 3
convert test_4color.png -depth 8 test_4color_21x21.rgba  # ImageMagick
echo "Hello, World!" > expected/decode_4color.txt

# 8-color test
echo "Testing 8-color mode" | jabcodeWriter -o test_8color.png -c 8 -e 3
convert test_8color.png -depth 8 test_8color_25x25.rgba
echo "Testing 8-color mode" > expected/decode_8color.txt

# Damaged code (10% pixel corruption)
cp test_8color_25x25.rgba test_damaged_10pct.rgba
# Flip 10% of bits randomly
python3 corrupt_image.py test_damaged_10pct.rgba 0.10
echo "Testing 8-color mode" > expected/decode_damaged.txt  # Should still decode
```

---

## C Layer Testing (No Platform Dependencies)

### Test Framework: Unity (C)

**Setup:**
```c
// @/swift-java-wrapper/test/c/unity/unity.h
// Lightweight C test framework: https://github.com/ThrowTheSwitch/Unity

#include "unity.h"
#include "mobile_bridge.h"

void setUp(void) {
    // Run before each test
}

void tearDown(void) {
    // Run after each test
}
```

### Unit Test Examples

**Test: Encode 4-Color Mode**
```c
// @/swift-java-wrapper/test/c/test_encoder.c

#include "unity.h"
#include "mobile_bridge.h"
#include <stdio.h>

void test_encode_4color_basic(void) {
    // Load test data
    const char* input = "Hello, World!";
    
    // Set encoding parameters
    jab_mobile_encode_params params = {
        .color_number = 4,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    // Create encoder
    jab_encode* enc = jabMobileEncodeCreate(
        NULL,  // No input image for encoding
        0, 0,
        (jab_char*)input,
        strlen(input),
        &params
    );
    
    TEST_ASSERT_NOT_NULL(enc);
    TEST_ASSERT_EQUAL(4, enc->color_number);
    TEST_ASSERT_EQUAL(1, enc->symbol_number);
    
    jabMobileEncodeFree(enc);
}

void test_encode_rejects_256_color(void) {
    jab_mobile_encode_params params = {
        .color_number = 256,  // Known broken mode
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_encode* enc = jabMobileEncodeCreate(
        NULL, 0, 0, "data", 4, &params
    );
    
    TEST_ASSERT_NULL(enc);  // Should fail
    TEST_ASSERT_NOT_NULL(jabMobileGetLastError());  // Error message set
}
```

**Test: Decode from Raw RGBA Buffer**
```c
// @/swift-java-wrapper/test/c/test_decoder.c

void test_decode_4color_from_fixture(void) {
    // Load fixture
    FILE* fp = fopen("test/fixtures/input/test_4color_21x21.rgba", "rb");
    TEST_ASSERT_NOT_NULL(fp);
    
    jab_byte buffer[21 * 21 * 4];
    size_t read = fread(buffer, 1, sizeof(buffer), fp);
    fclose(fp);
    TEST_ASSERT_EQUAL(sizeof(buffer), read);
    
    // Decode
    jab_data* result = jabMobileDecode(buffer, 21, 21);
    TEST_ASSERT_NOT_NULL(result);
    
    // Load expected output
    fp = fopen("test/fixtures/expected/decode_4color.txt", "r");
    char expected[256];
    fgets(expected, sizeof(expected), fp);
    fclose(fp);
    
    // Verify
    TEST_ASSERT_EQUAL_STRING(expected, result->data);
    
    jabMobileDataFree(result);
}
```

**Test: LDPC Error Correction**
```c
// @/swift-java-wrapper/test/c/test_ldpc.c

void test_ldpc_corrects_10pct_errors(void) {
    // Load damaged fixture
    FILE* fp = fopen("test/fixtures/input/test_damaged_10pct.rgba", "rb");
    jab_byte buffer[25 * 25 * 4];
    fread(buffer, 1, sizeof(buffer), fp);
    fclose(fp);
    
    // Decode with error correction
    jab_data* result = jabMobileDecode(buffer, 25, 25);
    TEST_ASSERT_NOT_NULL(result);  // Should succeed despite damage
    
    // Verify output matches clean version
    fp = fopen("test/fixtures/expected/decode_damaged.txt", "r");
    char expected[256];
    fgets(expected, sizeof(expected), fp);
    fclose(fp);
    
    TEST_ASSERT_EQUAL_STRING(expected, result->data);
    
    jabMobileDataFree(result);
}
```

**Test: Round-Trip (Encode → Decode)**
```c
void test_roundtrip_8color(void) {
    const char* input = "Round-trip test data 123";
    
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    // Encode
    jab_encode* enc = jabMobileEncodeCreate(
        NULL, 0, 0, (jab_char*)input, strlen(input), &params
    );
    TEST_ASSERT_NOT_NULL(enc);
    
    // Extract bitmap from encoder
    jab_bitmap* bitmap = enc->bitmap;
    TEST_ASSERT_NOT_NULL(bitmap);
    
    // Decode
    jab_data* decoded = jabMobileDecode(
        bitmap->pixel, 
        bitmap->width, 
        bitmap->height
    );
    TEST_ASSERT_NOT_NULL(decoded);
    TEST_ASSERT_EQUAL_STRING(input, decoded->data);
    
    jabMobileEncodeFree(enc);
    jabMobileDataFree(decoded);
}
```

### Running C Tests

```bash
# Compile tests
cd swift-java-wrapper/test/c
gcc -o test_suite \
    test_encoder.c test_decoder.c test_ldpc.c \
    unity/unity.c \
    ../../src/mobile_bridge.c \
    -I../../src/jabcode/include \
    -L../../build -ljabcode \
    -lm -DMOBILE_BUILD

# Run
./test_suite

# Expected output:
# test_encode_4color_basic: PASS
# test_encode_rejects_256_color: PASS
# test_decode_4color_from_fixture: PASS
# test_ldpc_corrects_10pct_errors: PASS
# test_roundtrip_8color: PASS
# --------------------
# 5 Tests 0 Failures 0 Ignored
```

---

## Android Integration Testing

### Instrumented Tests (On-Device)

**Setup: build.gradle**
```gradle
android {
    defaultConfig {
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test:runner:1.5.2'
    androidTestImplementation 'androidx.benchmark:benchmark-junit4:1.2.0'
}
```

**Test: Native Library Loading**
```kotlin
// @/swift-java-wrapper/android/src/androidTest/kotlin/JABCodeInstrumentedTest.kt

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class JABCodeInstrumentedTest {
    
    @Test
    fun testNativeLibraryLoads() {
        // Should not throw UnsatisfiedLinkError
        System.loadLibrary("jabcode")
    }
    
    @Test
    fun testEncodeBasic() {
        val encoder = JABCodeEncoder(colorMode = 8)
        val result = encoder.encode("Test data")
        
        assertNotNull(result)
        assert(result.size > 0)
    }
    
    @Test
    fun testDecodeFromAsset() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val inputStream = context.assets.open("test_4color_21x21.rgba")
        val buffer = inputStream.readBytes()
        
        val decoder = JABCodeDecoder()
        val result = decoder.decode(buffer, 21, 21)
        
        assertEquals("Hello, World!", result)
    }
    
    @Test
    fun testRoundTrip() {
        val input = "Round-trip integration test"
        
        val encoded = JABCodeEncoder.encode(input, colorMode = 8)
        val decoded = JABCodeDecoder.decode(encoded.buffer, encoded.width, encoded.height)
        
        assertEquals(input, decoded)
    }
    
    @Test
    fun testCameraIntegration() {
        // Requires physical device with camera
        val scanner = JABCodeScanner(context)
        
        val latch = CountDownLatch(1)
        var capturedResult: String? = null
        
        scanner.startScanning { result ->
            capturedResult = result
            latch.countDown()
        }
        
        // Manually point camera at JABCode for this test
        latch.await(10, TimeUnit.SECONDS)
        
        assertNotNull(capturedResult)
    }
}
```

### Performance Benchmarks (Jetpack Benchmark)

```kotlin
// @/swift-java-wrapper/android/src/androidTest/kotlin/JABCodeBenchmark.kt

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JABCodeBenchmark {
    
    @get:Rule
    val benchmarkRule = BenchmarkRule()
    
    @Test
    fun benchmarkEncode100Chars() {
        val data = "A".repeat(100)
        
        benchmarkRule.measureRepeated {
            JABCodeEncoder.encode(data, colorMode = 8)
        }
        // Target: median < 50ms
    }
    
    @Test
    fun benchmarkDecodeClean() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val buffer = context.assets.open("test_8color_25x25.rgba").readBytes()
        
        benchmarkRule.measureRepeated {
            JABCodeDecoder.decode(buffer, 25, 25)
        }
        // Target: median < 80ms
    }
    
    @Test
    fun benchmarkDecodeDamaged() {
        val buffer = loadFixture("test_damaged_10pct.rgba")
        
        benchmarkRule.measureRepeated {
            JABCodeDecoder.decode(buffer, 25, 25)
        }
        // Target: median < 150ms (LDPC iterations)
    }
}
```

**Run Benchmarks:**
```bash
./gradlew connectedAndroidTest -P androidx.benchmark.output.enable=true

# Results in: build/outputs/connected_android_test_additional_output/
# Example output:
# benchmarkEncode100Chars
#   median: 42.3ms, min: 39.1ms, max: 51.7ms ✅
# benchmarkDecodeClean
#   median: 68.5ms, min: 62.3ms, max: 79.2ms ✅
# benchmarkDecodeDamaged
#   median: 132.1ms, min: 125.6ms, max: 148.3ms ✅
```

---

## iOS Testing (XCTest)

### Unit Tests

```swift
// @/swift-java-wrapper/ios/Tests/JABCodeTests/EncoderTests.swift

import XCTest
@testable import JABCode

class EncoderTests: XCTestCase {
    
    func testEncodeBasic() throws {
        let encoder = JABCodeEncoder(colorMode: .quaternary)
        let result = try encoder.encode("Hello, World!")
        
        XCTAssertNotNil(result)
        XCTAssertGreaterThan(result.count, 0)
    }
    
    func testEncode256ColorThrows() {
        let encoder = JABCodeEncoder(colorMode: .custom(256))
        
        XCTAssertThrowsError(try encoder.encode("data")) { error in
            XCTAssertTrue(error.localizedDescription.contains("256-color"))
        }
    }
    
    func testDecodeFromFixture() throws {
        let bundle = Bundle(for: type(of: self))
        let url = bundle.url(forResource: "test_4color_21x21", withExtension: "rgba")!
        let data = try Data(contentsOf: url)
        
        let decoder = JABCodeDecoder()
        let result = try decoder.decode(data: data, width: 21, height: 21)
        
        XCTAssertEqual(result, "Hello, World!")
    }
    
    func testRoundTrip() throws {
        let input = "Round-trip test"
        
        let encoder = JABCodeEncoder(colorMode: .octonary)
        let encoded = try encoder.encode(input)
        
        let decoder = JABCodeDecoder()
        let decoded = try decoder.decode(imageData: encoded)
        
        XCTAssertEqual(input, decoded)
    }
}
```

### Performance Tests (XCTest Metrics)

```swift
// @/swift-java-wrapper/ios/Tests/JABCodeTests/PerformanceTests.swift

import XCTest
@testable import JABCode

class PerformanceTests: XCTestCase {
    
    func testEncodePerformance() throws {
        let data = String(repeating: "A", count: 100)
        
        measure(metrics: [XCTCPUMetric(), XCTClockMetric()]) {
            _ = try? JABCodeEncoder().encode(data)
        }
        // Xcode reports: avg 45ms, std dev 3ms
    }
    
    func testDecodePerformance() throws {
        let fixture = loadFixture("test_8color_25x25.rgba")
        
        measure(metrics: [XCTCPUMetric(), XCTMemoryMetric()]) {
            _ = try? JABCodeDecoder().decode(data: fixture, width: 25, height: 25)
        }
        // Xcode reports: avg 70ms, peak memory 3.2MB
    }
    
    func testMemoryFootprint() {
        measure(metrics: [XCTMemoryMetric()]) {
            for _ in 0..<100 {
                _ = try? JABCodeEncoder().encode("test")
            }
        }
        // Xcode reports: peak memory < 5MB ✅
    }
    
    func testBatteryImpact() {
        // Requires physical device
        measure(metrics: [XCTOSSignpostMetric.energy]) {
            for _ in 0..<100 {
                _ = try? JABCodeEncoder().encode("data")
                _ = try? JABCodeDecoder().decode(someTestImage)
            }
        }
        // Target: < 1% battery per 100 ops
    }
}
```

**Run Tests:**
```bash
# Command line
swift test

# Xcode UI
Cmd+U (runs all tests)

# Command line with code coverage
swift test --enable-code-coverage
```

---

## Cross-Platform Parity Testing

### Shared Test Data

**Principle:** Same fixtures, same expected outputs on Android and iOS.

**Directory Structure:**
```
@/swift-java-wrapper/test/fixtures/
├── input/          # Shared RGBA files
├── expected/       # Shared expected outputs
└── sync.sh         # Script to copy fixtures to platform test dirs
```

**Sync Script:**
```bash
#!/bin/bash
# @/swift-java-wrapper/test/fixtures/sync.sh

# Copy to Android assets
cp input/*.rgba ../android/src/androidTest/assets/
cp expected/*.txt ../android/src/androidTest/assets/

# Copy to iOS test bundle
cp input/*.rgba ../ios/Tests/JABCodeTests/Resources/
cp expected/*.txt ../ios/Tests/JABCodeTests/Resources/

echo "Test fixtures synced to Android and iOS"
```

### Parity Validation Test

**Concept:** Ensure Android and iOS produce identical outputs for same inputs.

**Android:**
```kotlin
@Test
fun testParityWithiOS() {
    val input = "Parity test data"
    val androidResult = JABCodeEncoder.encode(input, colorMode = 8)
    
    // Hash the output
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(androidResult.buffer)
        .joinToString("") { "%02x".format(it) }
    
    // Compare with known iOS output hash (generated separately)
    val expectedHash = "a1b2c3d4..."  // From iOS test run
    assertEquals(expectedHash, hash)
}
```

**iOS:**
```swift
func testParityWithAndroid() throws {
    let input = "Parity test data"
    let iosResult = try JABCodeEncoder().encode(input)
    
    // Hash the output
    let hash = SHA256.hash(data: iosResult)
        .map { String(format: "%02x", $0) }
        .joined()
    
    // Compare with known Android output hash
    let expectedHash = "a1b2c3d4..."  // From Android test run
    XCTAssertEqual(expectedHash, hash)
}
```

---

## CI/CD Integration

### GitHub Actions Workflow

```yaml
# @/.github/workflows/mobile-tests.yml

name: Mobile Tests

on: [push, pull_request]

jobs:
  android-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      - name: Build Native Library
        run: |
          cd swift-java-wrapper/android/jni
          mkdir build && cd build
          cmake .. -DANDROID_ABI=arm64-v8a
          make
      
      - name: Run Unit Tests
        run: ./gradlew test
      
      - name: Run Instrumented Tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 30
          arch: x86_64
          script: ./gradlew connectedAndroidTest
      
      - name: Upload Test Results
        uses: actions/upload-artifact@v3
        with:
          name: android-test-results
          path: build/reports/androidTests/

  ios-tests:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Build Native Library
        run: |
          cd swift-java-wrapper/ios
          swift build
      
      - name: Run Tests
        run: swift test --enable-code-coverage
      
      - name: Upload Coverage
        uses: codecov/codecov-action@v3
        with:
          files: .build/debug/codecov/*.json
```

---

## Test Coverage Requirements

### C Layer (>80%)

- [ ] All public functions in `mobile_bridge.c`
- [ ] Encoder: color modes 1-6
- [ ] Decoder: finder pattern detection
- [ ] LDPC: clean data + error correction
- [ ] Edge cases: NULL inputs, invalid parameters

### Platform Layer (>90%)

**Android:**
- [ ] JNI wrapper functions
- [ ] Kotlin API
- [ ] CameraX integration
- [ ] Memory management (buffer pooling)

**iOS:**
- [ ] Swift C interop
- [ ] Swift API wrapper
- [ ] AVFoundation camera
- [ ] Memory management (ARC compliance)

### Integration (100% Critical Paths)

- [ ] Round-trip encode/decode
- [ ] Camera capture → decode pipeline
- [ ] Multi-threading safety
- [ ] Memory leak detection

---

## Regression Testing

### Bug Test Template

**When a bug is found:**

1. **Write failing test first**
2. Fix the bug
3. Verify test now passes
4. Commit both test and fix

**Example:**

```c
// Bug: 128-color mode causes crash
// Discovered: 2026-01-10
// Fix: Restrict placeMasterMetadataPartII to <= 128

void test_bug_128color_crash_fixed(void) {
    jab_mobile_encode_params params = {
        .color_number = 128,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_encode* enc = jabMobileEncodeCreate(
        NULL, 0, 0, "test", 4, &params
    );
    
    TEST_ASSERT_NOT_NULL(enc);  // Should NOT crash
    
    jabMobileEncodeFree(enc);
    // Should NOT crash during cleanup
}
```

---

## Summary: TDD Workflow

```
1. Write test (fails initially)
2. Implement minimal code to pass test
3. Refactor while keeping tests green
4. Run benchmarks to verify performance
5. Commit test + implementation together
6. CI/CD verifies on all platforms
7. Repeat
```

**Next Steps:**
1. Set up test fixtures (`test/fixtures/`)
2. Write C unit tests for `mobile_bridge.c`
3. Implement enough to pass tests
4. Add platform integration tests
5. Run benchmarks, optimize if needed

**Reference:** See `04-swift-java-interop.md` for bridge architecture details.
