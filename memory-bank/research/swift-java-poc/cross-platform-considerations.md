# Cross-Platform Considerations for JABCode

**Strategies for maintaining consistency between Android and iOS implementations**

[← Back to Index](index.md) | [Overview](overview.md)

---

## 📋 Table of Contents

1. [Code Sharing Strategy](#code-sharing-strategy)
2. [API Consistency](#api-consistency)
3. [Testing Parity](#testing-parity)
4. [CI/CD Pipeline](#cicd-pipeline)
5. [Feature Parity Management](#feature-parity-management)
6. [Documentation Synchronization](#documentation-synchronization)
7. [Version Management](#version-management)
8. [Cross-Platform Frameworks](#cross-platform-frameworks)

---

## 🔄 Code Sharing Strategy

### Layered Architecture for Maximum Reuse

```
┌─────────────────────────────────────────────────┐
│        Platform-Specific UI Layer               │
│   (Android: Kotlin/Compose, iOS: Swift/SwiftUI) │
├─────────────────────────────────────────────────┤
│        Platform-Specific Bridge Layer           │
│        (Android: JNI, iOS: C Interop)           │
├─────────────────────────────────────────────────┤
│           Shared Native C Library               │
│        (100% shared - encoder, decoder)         │
└─────────────────────────────────────────────────┘
```

### What to Share

**✅ Maximum Sharing (100%)**
- Core encoding/decoding algorithms (C library)
- LDPC error correction
- Image processing routines
- Lookup tables and constants
- Unit test specifications

**⚠️ Partial Sharing (20-40%)**
- Business logic (via KMM or C++)
- Validation rules
- Error messages
- Configuration management

**❌ Platform-Specific (0% sharing)**
- Native UI components
- Camera integration
- Platform APIs
- Build configurations

---

## 🎨 API Consistency

### Design Principles

1. **Mirror Method Names**
   ```kotlin
   // Android
   fun encode(data: String, options: EncodeOptions): Bitmap
   ```
   ```swift
   // iOS
   func encode(_ data: String, options: EncodeOptions) throws -> UIImage
   ```

2. **Consistent Error Handling**
   ```kotlin
   // Android - Exceptions
   sealed class JABCodeError : Exception() {
       class EncodingFailed(code: Int) : JABCodeError()
       class DecodingFailed(code: Int) : JABCodeError()
   }
   ```
   ```swift
   // iOS - Swift Errors
   enum JABCodeError: Error {
       case encodingFailed(code: Int)
       case decodingFailed(code: Int)
   }
   ```

3. **Equivalent Data Models**
   ```kotlin
   // Android
   data class EncodeOptions(
       val colorNumber: Int = 8,
       val symbolNumber: Int = 1,
       val eccLevel: Int = 3,
       val moduleSize: Int = 12
   )
   ```
   ```swift
   // iOS
   struct EncodeOptions {
       let colorNumber: Int = 8
       let symbolNumber: Int = 1
       let eccLevel: Int = 3
       let moduleSize: Int = 12
   }
   ```

### Shared API Contract

**Create a specification document:**

```markdown
# JABCode Mobile API Specification v2.0

## Core Methods

### encode(data, options)
**Purpose:** Encode text/data into JABCode image

**Parameters:**
- `data: String` - UTF-8 text to encode (max 65536 chars)
- `options: EncodeOptions` - Encoding configuration

**Returns:** 
- Android: `Bitmap` (ARGB_8888)
- iOS: `UIImage` (RGBA)

**Throws/Errors:**
- `InvalidInput` - Empty or invalid data
- `DataTooLarge` - Data exceeds capacity
- `EncodingFailed` - Native encoding error

**Performance:** < 50ms for typical input (< 1KB)

### decode(image)
**Purpose:** Decode JABCode from image

**Parameters:**
- Android: `image: Bitmap` - Image containing JABCode
- iOS: `image: UIImage` - Image containing JABCode

**Returns:** `DecodeResult` with decoded data and metadata

**Throws/Errors:**
- `InvalidImage` - Image format not supported
- `DetectionFailed` - No JABCode found
- `DecodingFailed` - LDPC correction failed

**Performance:** < 100ms for typical image
```

### Validation Testing

```kotlin
// Android test
@Test
fun testAPICompliance() {
    val data = "Test data"
    val options = EncodeOptions.DEFAULT
    
    // Must complete within 100ms
    val time = measureTimeMillis {
        val bitmap = JABCode.encode(data, options)
        assertNotNull(bitmap)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
    }
    assertTrue(time < 100)
}
```

```swift
// iOS test
func testAPICompliance() async throws {
    let data = "Test data"
    let options = EncodeOptions.default
    
    // Must complete within 100ms
    let start = Date()
    let image = try await JABCode.shared.encode(data, options: options)
    let elapsed = Date().timeIntervalSince(start)
    
    XCTAssertNotNil(image)
    XCTAssertLessThan(elapsed, 0.1)
}
```

---

## 🧪 Testing Parity

### Shared Test Vectors

Create platform-independent test data:

```json
// test-vectors.json
{
  "version": "2.0",
  "test_cases": [
    {
      "id": "basic_encode_001",
      "input": "Hello, World!",
      "options": {
        "colorNumber": 8,
        "symbolNumber": 1,
        "eccLevel": 3,
        "moduleSize": 12
      },
      "expected": {
        "width": 348,
        "height": 348,
        "checksum": "a3f5e8d9c2b1"
      }
    },
    {
      "id": "unicode_encode_002",
      "input": "Hello 世界 🌍",
      "options": {
        "colorNumber": 256,
        "symbolNumber": 1,
        "eccLevel": 5
      },
      "expected": {
        "width": 432,
        "height": 432,
        "checksum": "f7e3a9d1c5b8"
      }
    }
  ]
}
```

### Cross-Platform Test Framework

```kotlin
// Android - Load shared test vectors
class SharedTestRunner {
    private val testVectors = loadTestVectors()
    
    @Test
    fun runSharedTests() {
        testVectors.forEach { testCase ->
            val result = JABCode.encode(testCase.input, testCase.options)
            
            assertEquals(testCase.expected.width, result.width)
            assertEquals(testCase.expected.height, result.height)
            assertEquals(testCase.expected.checksum, calculateChecksum(result))
        }
    }
    
    private fun loadTestVectors(): List<TestCase> {
        val json = context.assets.open("test-vectors.json").bufferedReader().use { it.readText() }
        return Json.decodeFromString(json)
    }
}
```

```swift
// iOS - Same test vectors
class SharedTestRunner: XCTestCase {
    func testSharedVectors() async throws {
        let testVectors = try loadTestVectors()
        
        for testCase in testVectors {
            let result = try await JABCode.shared.encode(testCase.input, options: testCase.options)
            
            XCTAssertEqual(testCase.expected.width, Int(result.size.width))
            XCTAssertEqual(testCase.expected.height, Int(result.size.height))
            XCTAssertEqual(testCase.expected.checksum, calculateChecksum(result))
        }
    }
    
    private func loadTestVectors() throws -> [TestCase] {
        let url = Bundle(for: type(of: self)).url(forResource: "test-vectors", withExtension: "json")!
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode([TestCase].self, from: data)
    }
}
```

### Interoperability Testing

```kotlin
// Android generates, iOS decodes
@Test
fun testCrossPlatformCompatibility() {
    val testData = "Cross-platform test"
    
    // Encode on Android
    val bitmap = JABCode.encode(testData)
    
    // Save to shared location
    saveToSharedStorage(bitmap, "android_encoded.png")
    
    // iOS test loads and decodes this image
}
```

```swift
// iOS decodes Android-generated image
func testDecodeAndroidImage() async throws {
    // Load image generated by Android
    let image = UIImage(named: "android_encoded")!
    
    let result = try await JABCode.shared.decode(image)
    
    XCTAssertEqual("Cross-platform test", result.text)
}
```

---

## 🔧 CI/CD Pipeline

### Multi-Platform Build Pipeline

```yaml
# .github/workflows/mobile-ci.yml
name: Mobile CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  build-android:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'
      
      - name: Build Native Library
        run: |
          cd android
          ./gradlew :jabcode:assembleRelease
      
      - name: Run Tests
        run: |
          cd android
          ./gradlew :jabcode:test
      
      - name: Upload APK
        uses: actions/upload-artifact@v3
        with:
          name: android-jabcode
          path: android/jabcode/build/outputs/aar/*.aar

  build-ios:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Select Xcode
        run: sudo xcode-select -s /Applications/Xcode_15.0.app
      
      - name: Build Framework
        run: |
          cd ios
          swift build -c release
      
      - name: Run Tests
        run: |
          cd ios
          swift test
      
      - name: Build XCFramework
        run: |
          cd ios
          ./build-xcframework.sh
      
      - name: Upload Framework
        uses: actions/upload-artifact@v3
        with:
          name: ios-jabcode
          path: ios/build/JABCode.xcframework

  validate-compatibility:
    needs: [build-android, build-ios]
    runs-on: ubuntu-latest
    steps:
      - name: Download Artifacts
        uses: actions/download-artifact@v3
      
      - name: Run Cross-Platform Tests
        run: |
          python3 scripts/validate_compatibility.py \
            --android android-jabcode/ \
            --ios ios-jabcode/
      
      - name: Check Version Parity
        run: |
          python3 scripts/check_version_parity.py
```

### Version Consistency Check

```python
# scripts/check_version_parity.py
import json
import re
import sys

def get_android_version():
    with open('android/build.gradle.kts', 'r') as f:
        content = f.read()
        match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
        return match.group(1) if match else None

def get_ios_version():
    with open('ios/Package.swift', 'r') as f:
        content = f.read()
        match = re.search(r'version:\s*"([^"]+)"', content)
        return match.group(1) if match else None

def get_native_version():
    with open('native/jabcode.h', 'r') as f:
        content = f.read()
        match = re.search(r'#define\s+JABCODE_VERSION\s+"([^"]+)"', content)
        return match.group(1) if match else None

def main():
    android_ver = get_android_version()
    ios_ver = get_ios_version()
    native_ver = get_native_version()
    
    print(f"Android version: {android_ver}")
    print(f"iOS version: {ios_ver}")
    print(f"Native version: {native_ver}")
    
    if android_ver != ios_ver or android_ver != native_ver:
        print("❌ Version mismatch detected!")
        sys.exit(1)
    
    print("✅ All versions match")
    sys.exit(0)

if __name__ == '__main__':
    main()
```

---

## 📊 Feature Parity Management

### Feature Matrix

Maintain a feature tracking spreadsheet:

| Feature | Android | iOS | Notes |
|---------|---------|-----|-------|
| Basic Encoding | ✅ v1.0 | ✅ v1.0 | Full parity |
| Multi-symbol | ✅ v1.2 | ✅ v1.2 | Full parity |
| Camera Scanning | ✅ v1.5 | ✅ v1.5 | Different APIs, same UX |
| Batch Processing | ✅ v2.0 | ✅ v2.0 | Full parity |
| Background Decode | ✅ v2.1 | ⚠️ v2.2 | iOS limited by OS |
| Torch Control | ✅ v2.0 | ✅ v2.0 | Full parity |
| Image Filters | 🚧 v2.3 | 🚧 v2.3 | In progress |

**Legend:**
- ✅ Implemented and tested
- ⚠️ Implemented with limitations
- 🚧 In development
- ❌ Not yet implemented

### Feature Flag System

```kotlin
// Android
object FeatureFlags {
    const val ENABLE_MULTI_SYMBOL = true
    const val ENABLE_CAMERA_SCANNING = true
    const val ENABLE_BACKGROUND_PROCESSING = true
    const val ENABLE_EXPERIMENTAL_FILTERS = false
    
    fun isFeatureEnabled(feature: String): Boolean {
        return when (feature) {
            "multi_symbol" -> ENABLE_MULTI_SYMBOL
            "camera" -> ENABLE_CAMERA_SCANNING
            "background" -> ENABLE_BACKGROUND_PROCESSING
            "filters" -> ENABLE_EXPERIMENTAL_FILTERS
            else -> false
        }
    }
}
```

```swift
// iOS
enum FeatureFlags {
    static let multiSymbol = true
    static let cameraScanning = true
    static let backgroundProcessing = true
    static let experimentalFilters = false
    
    static func isEnabled(_ feature: String) -> Bool {
        switch feature {
        case "multi_symbol": return multiSymbol
        case "camera": return cameraScanning
        case "background": return backgroundProcessing
        case "filters": return experimentalFilters
        default: return false
        }
    }
}
```

---

## 📚 Documentation Synchronization

### Unified Documentation Source

Use a single source for API documentation:

```markdown
<!-- docs/api/encode.md -->
# encode Method

## Purpose
Encode text data into a JABCode image.

## Signature

### Android (Kotlin)
```kotlin
fun encode(
    data: String,
    options: EncodeOptions = EncodeOptions.DEFAULT
): Bitmap
```

### iOS (Swift)
```swift
func encode(
    _ data: String,
    options: EncodeOptions = .default
) throws -> UIImage
```

## Parameters

### data
- **Type:** String
- **Description:** UTF-8 encoded text to convert into JABCode
- **Constraints:** 
  - Must not be empty
  - Maximum length: 65,536 characters
- **Example:** `"Hello, World!"`

### options
- **Type:** EncodeOptions
- **Description:** Configuration for encoding process
- **Default:** `EncodeOptions.DEFAULT` / `.default`
- **Optional:** Yes

## Return Value

### Android
- **Type:** `android.graphics.Bitmap`
- **Format:** ARGB_8888
- **Description:** Generated JABCode image

### iOS
- **Type:** `UIKit.UIImage`
- **Format:** RGBA
- **Description:** Generated JABCode image

## Exceptions / Errors

| Error | Android | iOS | Description |
|-------|---------|-----|-------------|
| Invalid Input | `IllegalArgumentException` | `JABCodeError.invalidInput` | Empty or null data |
| Data Too Large | `JABCodeException` | `JABCodeError.dataTooLarge` | Exceeds capacity |
| Encoding Failed | `JABCodeException` | `JABCodeError.encodingFailed` | Native error |

## Performance

- **Typical:** 10-30ms for <1KB data
- **Maximum:** 100ms for 10KB data
- **Memory:** ~2-8MB peak depending on size

## Example Usage

### Android
```kotlin
val bitmap = JABCode.encode("Hello, World!")

// With options
val options = EncodeOptions(
    colorNumber = 256,
    eccLevel = 5
)
val bitmap = JABCode.encode("Important data", options)
```

### iOS
```swift
let image = try JABCode.shared.encode("Hello, World!")

// With options
let options = EncodeOptions(
    colorNumber: 256,
    eccLevel: 5
)
let image = try JABCode.shared.encode("Important data", options: options)
```
```

### Auto-Generate Platform Docs

```python
# scripts/generate_docs.py
def generate_platform_docs(source_md: str, platform: str):
    """Extract platform-specific sections from unified docs"""
    
    with open(source_md, 'r') as f:
        content = f.read()
    
    # Parse markdown and extract relevant sections
    if platform == 'android':
        # Extract Android-specific code blocks and explanations
        output = extract_android_sections(content)
        write_file(f'android/docs/{source_md}', output)
    elif platform == 'ios':
        # Extract iOS-specific code blocks and explanations
        output = extract_ios_sections(content)
        write_file(f'ios/docs/{source_md}', output)
```

---

## 🔢 Version Management

### Semantic Versioning Strategy

**Format:** `MAJOR.MINOR.PATCH`

- **MAJOR:** Breaking API changes (both platforms must increment together)
- **MINOR:** New features (can increment independently with feature flags)
- **PATCH:** Bug fixes (can increment independently)

**Example:**
```
Native Library: 2.1.3
Android Wrapper: 2.1.5  (includes Android-specific fixes)
iOS Wrapper: 2.1.4      (includes iOS-specific fixes)
```

### Version Compatibility Matrix

```json
// compatibility-matrix.json
{
  "native_library": {
    "2.1.x": {
      "android_min": "2.1.0",
      "android_max": "2.1.99",
      "ios_min": "2.1.0",
      "ios_max": "2.1.99"
    },
    "2.0.x": {
      "android_min": "2.0.0",
      "android_max": "2.0.99",
      "ios_min": "2.0.0",
      "ios_max": "2.0.99"
    }
  }
}
```

### Release Checklist

```markdown
# Release Checklist for v2.2.0

## Pre-Release
- [ ] Update version in all platform files
- [ ] Update CHANGELOG.md for both platforms
- [ ] Run full test suite on both platforms
- [ ] Run cross-platform compatibility tests
- [ ] Update API documentation
- [ ] Review feature parity matrix

## Native Library
- [ ] Build release binaries for all architectures
- [ ] Run native unit tests
- [ ] Validate memory leaks with Valgrind
- [ ] Tag release: `native-v2.2.0`

## Android
- [ ] Update version in build.gradle.kts
- [ ] Generate release AAR
- [ ] Test on min/max SDK versions
- [ ] Upload to Maven Central
- [ ] Tag release: `android-v2.2.0`

## iOS
- [ ] Update version in Package.swift
- [ ] Build XCFramework
- [ ] Test on min/max iOS versions
- [ ] Update CocoaPods spec
- [ ] Tag release: `ios-v2.2.0`

## Post-Release
- [ ] Publish release notes
- [ ] Update documentation site
- [ ] Notify users of breaking changes
- [ ] Monitor crash reports
```

---

## 🌐 Cross-Platform Frameworks

### Kotlin Multiplatform Mobile (KMM)

**When to use:** Share business logic between Android/iOS

```kotlin
// shared/src/commonMain/kotlin
expect class Platform() {
    val name: String
}

expect fun encodeNative(data: String, options: EncodeOptions): ByteArray

// shared/src/androidMain/kotlin
actual class Platform {
    actual val name: String = "Android"
}

actual fun encodeNative(data: String, options: EncodeOptions): ByteArray {
    // Call JNI
    return JABCodeJNI.encode(data, options)
}

// shared/src/iosMain/kotlin
actual class Platform {
    actual val name: String = "iOS"
}

actual fun encodeNative(data: String, options: EncodeOptions): ByteArray {
    // Call Swift interop
    return JABCodeSwift.encode(data, options)
}
```

### C++ Shared Logic

**When to use:** Complex business logic that needs maximum performance

```cpp
// shared/business_logic.hpp
class JABCodeValidator {
public:
    static bool validateInput(const std::string& data) {
        return !data.empty() && data.size() <= 65536;
    }
    
    static int calculateOptimalColors(int dataLength) {
        if (dataLength < 100) return 8;
        if (dataLength < 1000) return 64;
        return 256;
    }
};

// Android JNI wrapper
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_jabcode_Validator_validateInput(
    JNIEnv* env, jclass, jstring jData
) {
    const char* data = env->GetStringUTFChars(jData, nullptr);
    bool result = JABCodeValidator::validateInput(data);
    env->ReleaseStringUTFChars(jData, data);
    return result;
}

// iOS wrapper
@_cdecl("validateInput")
public func validateInput(_ data: UnsafePointer<CChar>) -> Bool {
    return JABCodeValidator.validateInput(String(cString: data))
}
```

### React Native / Flutter

**When to use:** Building cross-platform apps with these frameworks

```javascript
// React Native module
import { NativeModules } from 'react-native';

const { JABCodeModule } = NativeModules;

export const JABCode = {
  encode: async (data, options = {}) => {
    return await JABCodeModule.encode(data, options);
  },
  
  decode: async (imageUri) => {
    return await JABCodeModule.decode(imageUri);
  }
};
```

```dart
// Flutter plugin
class JABCode {
  static const MethodChannel _channel = MethodChannel('jabcode');
  
  static Future<Uint8List> encode(String data, EncodeOptions options) async {
    return await _channel.invokeMethod('encode', {
      'data': data,
      'options': options.toMap(),
    });
  }
  
  static Future<DecodeResult> decode(Uint8List imageData) async {
    final result = await _channel.invokeMethod('decode', {
      'imageData': imageData,
    });
    return DecodeResult.fromMap(result);
  }
}
```

---

## 🎯 Best Practices Summary

### DO

✅ **Keep native C library pure and portable**
- No platform-specific code in core algorithms
- Use standard C99 only
- Minimize external dependencies

✅ **Maintain API parity between platforms**
- Mirror method names and signatures
- Use equivalent data types
- Provide same error handling semantics

✅ **Share test vectors and specifications**
- Use JSON for test data
- Version test suites
- Run identical tests on both platforms

✅ **Automate compatibility checks**
- CI/CD for both platforms
- Cross-platform validation
- Version consistency checks

✅ **Document everything once**
- Single source of truth
- Generate platform-specific docs
- Keep examples synchronized

### DON'T

❌ **Don't duplicate business logic**
- Extract to shared native code or KMM
- Use C++ for complex algorithms
- Share validation and calculations

❌ **Don't create platform-specific features**
- Feature flags for experimental work
- Maintain feature parity matrix
- Plan simultaneous releases

❌ **Don't diverge APIs unnecessarily**
- Only differ where platform conventions require
- Document differences clearly
- Provide migration guides

❌ **Don't skip cross-platform testing**
- Test on real devices, not just simulators
- Validate interoperability
- Profile on both platforms

---

## 📈 Measuring Success

### Key Metrics

1. **API Consistency:** 95%+ method name/signature parity
2. **Test Coverage:** 80%+ shared test cases passing on both platforms
3. **Feature Parity:** <5% feature gap between platforms
4. **Performance Parity:** <20% performance difference for equivalent operations
5. **Bug Report Ratio:** <2:1 ratio between platforms

### Monitoring Dashboard

```yaml
# metrics.yml
api_consistency:
  target: 0.95
  current: 0.97
  
test_coverage:
  android: 0.85
  ios: 0.83
  shared_passing: 0.84
  
feature_parity:
  total_features: 45
  android_only: 2
  ios_only: 1
  parity_score: 0.97
  
performance:
  encode_time_ratio: 1.08  # iOS/Android
  decode_time_ratio: 0.95
  memory_ratio: 1.12
```

---

[← Back to Index](index.md) | [Android Integration](android-integration.md) | [iOS Integration](ios-integration.md) | [Performance Optimization](performance-optimization.md)
