# JABCode Mobile Development Overview

**Architecture, Technology Stack, and Integration Approaches**

[← Back to Index](index.md)

---

## 🎯 Executive Summary

JABCode is a high-capacity 2D color barcode system specified in ISO/IEC 23634:2022. This guide covers strategies for integrating the native C library into Android and iOS mobile applications, addressing the unique constraints and opportunities of mobile platforms.

### Key Takeaways
- JABCode core is implemented in **portable C** (C99 standard)
- Mobile integration requires **native bridge layers** (JNI for Android, C interop for iOS)
- Real-time scanning demands **careful performance optimization**
- Mobile constraints necessitate **memory-efficient implementations**

---

## 🏗️ System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Mobile Application Layer                  │
│  (Kotlin/Java for Android, Swift/Objective-C for iOS)       │
└────────────────────────┬────────────────────────────────────┘
                         │
        ┌────────────────┴────────────────┐
        │                                  │
┌───────▼──────────┐            ┌─────────▼─────────┐
│   JNI Bridge     │            │  Swift C Interop  │
│   (Android)      │            │     (iOS)         │
└───────┬──────────┘            └─────────┬─────────┘
        │                                  │
        └────────────────┬─────────────────┘
                         │
            ┌────────────▼────────────┐
            │  JABCode Native Library │
            │      (C/C++ Core)       │
            └────────────┬────────────┘
                         │
        ┌────────────────┴────────────────┐
        │                                  │
┌───────▼──────────┐            ┌─────────▼─────────┐
│  Platform APIs   │            │  System Libraries │
│  (Camera, GPU)   │            │  (libpng, etc.)   │
└──────────────────┘            └───────────────────┘
```

### Component Breakdown

#### 1. Application Layer
**Responsibilities:**
- User interface and interaction
- Camera preview management
- Image capture and selection
- Result display and handling
- Business logic integration

**Technologies:**
- **Android:** Kotlin/Java, Jetpack Compose/XML, CameraX
- **iOS:** Swift/SwiftUI, AVFoundation

#### 2. Bridge Layer
**Responsibilities:**
- Type conversion between platform and C
- Memory management across boundaries
- Error handling and exception translation
- Threading coordination
- Resource lifecycle management

**Technologies:**
- **Android:** JNI (Java Native Interface)
- **iOS:** Swift C Interop / Objective-C++
- **Experimental:** Swift-Java bidirectional binding

#### 3. Native Core Layer
**Responsibilities:**
- JABCode encoding/decoding algorithms
- LDPC error correction
- Image processing and analysis
- Color palette management
- Multi-symbol coordination

**Technologies:**
- C99 standard library
- libpng for image I/O
- Math library for geometric transforms

---

## 🔧 Technology Stack

### Android Stack

```
Application Layer:
  - Kotlin 1.9+ / Java 11+
  - Android SDK 21+ (Lollipop+)
  - CameraX for camera operations
  - Coroutines for async operations

Bridge Layer:
  - JNI (Java Native Interface)
  - NDK r21+ for native builds
  - CMake 3.18+ for build configuration
  - JavaCPP (optional wrapper generator)

Native Layer:
  - C99 compiler (Clang via NDK)
  - libpng (bundled or system)
  - ARM/ARM64/x86/x86_64 ABIs
```

### iOS Stack

```
Application Layer:
  - Swift 5.7+ / Objective-C
  - iOS 13.0+ deployment target
  - AVFoundation for camera
  - Combine for reactive patterns

Bridge Layer:
  - Swift C Interop (automatic)
  - Objective-C++ (manual wrapper)
  - Module maps for C headers
  - Swift Package Manager / CocoaPods

Native Layer:
  - Clang/LLVM C compiler
  - libpng (via dependency manager)
  - arm64 / x86_64 (simulator) ABIs
```

---

## 🚀 Integration Approaches

### Approach 1: Direct JNI (Android) / C Interop (iOS)

**Status:** ✅ Production-Ready

#### Android JNI Implementation

**Pros:**
- Excellent performance (direct memory access)
- Full control over threading model
- Well-established tooling (Android Studio, NDK)
- Zero-copy buffer sharing possible
- Standard Android development practice

**Cons:**
- Verbose boilerplate code (~500 lines)
- Manual memory management required
- Type safety only at runtime
- Complex debugging across boundaries
- Requires C/C++ expertise

**Code Structure:**
```
android/
├── app/src/main/
│   ├── java/com/example/jabcode/
│   │   └── JABCode.java          (Java API)
│   └── cpp/
│       ├── jabcode_jni.cpp       (JNI wrapper)
│       └── CMakeLists.txt         (Build config)
└── libs/
    └── libjabcode.so              (Compiled library)
```

#### iOS C Interop Implementation

**Pros:**
- Swift automatically bridges C functions
- Clean, type-safe Swift API
- Excellent Xcode integration
- ARC handles memory management
- Minimal boilerplate

**Cons:**
- Some manual wrapper for complex structs
- Bridging header maintenance
- Pointer safety requires care
- C callbacks need @convention(c)

**Code Structure:**
```
iOS/
├── Sources/
│   ├── JABCode/
│   │   ├── JABCode.swift          (Swift API)
│   │   └── JABCodeBridge.h        (C bridging header)
│   └── CJABCode/
│       ├── include/jabcode.h      (C headers)
│       └── libjabcode.a           (Static library)
└── Package.swift                  (SPM manifest)
```

---

### Approach 2: Swift-Java Interop (Experimental)

**Status:** ⚠️ Proof-of-Concept

#### Architecture

```
Java Application
    ↓ [swift-java generated bindings]
Swift Wrapper Module
    ↓ [Swift C interop]
JABCode C Library
```

**Pros:**
- Type-safe bidirectional bindings
- Automatic code generation
- Reduced boilerplate (~150 lines)
- Cross-platform Swift code reuse
- Modern Swift features available

**Cons:**
- Requires Swift 6.2+ toolchain
- Complex build pipeline
- Limited tooling support
- Slower compilation
- Experimental stability
- Large binary size overhead

**Use Cases:**
- Cross-platform Swift libraries
- Teams with Swift expertise
- Rapid prototyping
- Research projects

**Not Recommended For:**
- Production Android apps
- Performance-critical applications
- Teams without Swift knowledge
- Apps with tight size constraints

---

### Approach 3: React Native / Flutter Bridges

**Status:** 🔄 Community-Driven

#### React Native Integration

```javascript
// JavaScript API
import JABCode from 'react-native-jabcode';

const result = await JABCode.encode({
  data: "Hello, World!",
  colors: 8,
  eccLevel: 3
});
```

**Architecture:**
- Native modules for Android (JNI) and iOS (Objective-C++)
- Promise-based async API
- Image buffers via Base64 or File URIs
- Event emitters for camera scanning

**Considerations:**
- Requires maintaining both Android and iOS bridges
- Performance overhead from JS bridge
- Memory copies for image data
- Limited to React Native ecosystem

#### Flutter Integration

```dart
// Dart API
import 'package:jabcode_flutter/jabcode_flutter.dart';

final image = await JABCode.encode(
  data: "Hello, World!",
  colors: 8,
  eccLevel: 3,
);
```

**Architecture:**
- Platform channels for method invocation
- MethodChannel for sync/async calls
- EventChannel for streams
- Isolates for background processing

**Considerations:**
- Dart FFI for direct C interop (faster)
- Platform channels easier but slower
- UI thread blocking concerns
- Memory management across Dart VM

---

## 📊 Decision Matrix

### When to Use Each Approach

| Criteria | Direct JNI/Interop | Swift-Java | React Native | Flutter |
|----------|-------------------|------------|--------------|---------|
| **Performance** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Development Speed** | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Maintenance** | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ |
| **Type Safety** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐ |
| **Binary Size** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ |
| **Tooling** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Production Ready** | ✅ Yes | ⚠️ No | ✅ Yes | ✅ Yes |

---

## 🎨 Design Patterns

### Pattern 1: Facade Pattern for Native API

**Purpose:** Provide clean, platform-idiomatic API over C library

**Android Example:**
```java
public class JABCode {
    // High-level, safe API
    public static Bitmap encode(String data, EncodeOptions options) {
        // Validation and error handling
        // JNI call to native
        // Bitmap conversion
    }
    
    // Native declarations hidden as private
    private native long nativeEncode(String data, int colors, int ecc);
    private native void nativeFree(long handle);
}
```

**iOS Example:**
```swift
public class JABCode {
    // Swift-friendly API
    public static func encode(
        _ data: String,
        options: EncodeOptions = .default
    ) throws -> UIImage {
        // Swift error handling
        // C function calls
        // UIImage conversion
    }
    
    // C interop hidden in implementation
}
```

### Pattern 2: Resource Management with RAII

**Android (Kotlin):**
```kotlin
class JABCodeEncoder : AutoCloseable {
    private var nativeHandle: Long = 0
    
    init {
        nativeHandle = nativeCreate()
    }
    
    override fun close() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0
        }
    }
}

// Usage with automatic cleanup
JABCodeEncoder().use { encoder ->
    val result = encoder.encode(data)
}
```

**iOS (Swift):**
```swift
class JABCodeEncoder {
    private var handle: OpaquePointer?
    
    init() {
        handle = jab_encoder_create()
    }
    
    deinit {
        if let handle = handle {
            jab_encoder_destroy(handle)
        }
    }
}
// ARC automatically calls deinit
```

### Pattern 3: Asynchronous Processing

**Android (Coroutines):**
```kotlin
suspend fun decodeFromCamera(): JABCodeResult = withContext(Dispatchers.Default) {
    // Heavy computation on background thread
    val bitmap = captureFrame()
    val bytes = convertToBytes(bitmap)
    nativeDecode(bytes) // Native call
}
```

**iOS (async/await):**
```swift
func decodeFromCamera() async throws -> JABCodeResult {
    // Structured concurrency
    return try await Task.detached {
        let image = self.captureFrame()
        let bytes = image.convertToBytes()
        return try JABCode.decode(bytes)
    }.value
}
```

---

## 🔒 Security Considerations

### Input Validation
- **Sanitize data length** before encoding (prevent buffer overflow)
- **Validate image dimensions** before decoding
- **Check color parameters** against allowed ranges
- **Limit recursion depth** in multi-symbol codes

### Memory Safety
- **Bounds checking** on all array accesses
- **Null pointer checks** before dereferencing
- **Resource cleanup** in error paths
- **Reference counting** for shared resources

### Platform Security
- **Android:** ProGuard/R8 obfuscation for native calls
- **iOS:** App Transport Security for network operations
- **Both:** Secure enclave for sensitive data (if applicable)

---

## 📈 Performance Characteristics

### Encoding Performance

| Platform | Resolution | Colors | Time (avg) | Memory |
|----------|-----------|--------|------------|--------|
| Android (Pixel 7) | 640x480 | 8 | ~15ms | ~2MB |
| Android (Pixel 7) | 1920x1080 | 8 | ~45ms | ~8MB |
| iOS (iPhone 14) | 640x480 | 8 | ~12ms | ~2MB |
| iOS (iPhone 14) | 1920x1080 | 8 | ~38ms | ~8MB |

### Decoding Performance

| Platform | Complexity | Time (avg) | Memory |
|----------|-----------|------------|--------|
| Android | Simple (1 symbol) | ~25ms | ~3MB |
| Android | Complex (4 symbols) | ~80ms | ~10MB |
| iOS | Simple (1 symbol) | ~20ms | ~3MB |
| iOS | Complex (4 symbols) | ~65ms | ~10MB |

**Factors Affecting Performance:**
- Image resolution and quality
- Number of symbols in code
- Color depth (4-256 colors)
- Error correction level (0-7)
- Device CPU/GPU capabilities

---

## 🌐 Cross-Platform Strategy

### Shared Native Code
```
jabcode-core/          (C library - 100% shared)
├── encoder.c
├── decoder.c
├── ldpc.c
└── image.c

platform-bridges/      (Platform-specific - 0% shared)
├── android/           (JNI wrapper)
│   └── jabcode_jni.cpp
└── ios/               (Swift interop)
    └── JABCodeBridge.swift

platform-apps/         (Partial shared logic possible)
├── android/           (Kotlin app)
└── ios/               (Swift app)
```

### Code Reuse Opportunities
- **Core algorithms:** 100% shared via native C library
- **Bridge logic:** Platform-specific, minimal reuse
- **Business logic:** Can share via Kotlin Multiplatform or C++
- **UI components:** Platform-specific (native look and feel)

### Recommended Approach
1. **Keep core C library pure and portable**
2. **Write thin, platform-idiomatic wrappers**
3. **Share test cases and validation logic**
4. **Document API contracts for consistency**
5. **Use CI/CD to ensure parallel feature parity**

---

## 🧪 Testing Strategy

### Unit Tests
- **Native layer:** C unit tests with CTest or Google Test
- **Bridge layer:** Platform-specific tests (JUnit, XCTest)
- **Mock camera input** for deterministic testing

### Integration Tests
- **End-to-end encoding/decoding**
- **Real device camera testing**
- **Performance benchmarking**
- **Memory leak detection** (LeakCanary, Instruments)

### Validation Tests
- **Compliance with ISO/IEC 23634**
- **Interoperability with reference implementation**
- **Edge cases** (low light, motion blur, partial codes)

---

## 📚 Next Steps

1. **Choose your platform:** [Android](android-integration.md) or [iOS](ios-integration.md)
2. **Review performance guide:** [Performance Optimization](performance-optimization.md)
3. **Check common issues:** [Troubleshooting](troubleshooting.md)
4. **Explore cross-platform patterns:** [Cross-Platform Considerations](cross-platform-considerations.md)

---

[← Back to Index](index.md) | [Android Integration →](android-integration.md) | [iOS Integration →](ios-integration.md)
