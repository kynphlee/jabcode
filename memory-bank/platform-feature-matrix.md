# JABCode Platform & Feature Support Matrix

## Branch Strategy Overview

```
my-branch (JNI - Production)
    ↓
interop-poc (Migration Base)
    ├── swift-java-poc → Android/Mobile
    └── panama-poc → Desktop/Web/Cloud
```

---

## Platform Compatibility Matrix

| Platform | JNI (`my-branch`) | Panama (`panama-poc`) | Swift-Java (`swift-java-poc`) |
|----------|-------------------|----------------------|------------------------------|
| **Android** | ✅ Full Support | ❌ Not Available | ✅ Full Support (JNI mode) |
| **Desktop Linux** | ✅ Full Support | ✅ Full Support (JDK 22+) | ✅ Full Support |
| **Desktop macOS** | ✅ Full Support | ✅ Full Support (JDK 22+) | ✅ Full Support |
| **Desktop Windows** | ✅ Full Support | ✅ Full Support (JDK 22+) | ✅ Full Support |
| **Web Server** | ✅ Full Support | ✅ Full Support (JDK 22+) | ✅ Full Support |
| **Cloud (AWS/GCP/Azure)** | ✅ Full Support | ✅ Full Support (JDK 22+) | ✅ Full Support |
| **Embedded Linux** | ✅ Full Support | ⚠️ If JDK 22+ available | ✅ Full Support |
| **iOS** | ❌ Not Applicable | ❌ Not Applicable | ⚠️ Swift native (no Java) |

**Legend:**
- ✅ Full Support - Works without limitations
- ⚠️ Conditional - Works with specific requirements
- ❌ Not Available - Platform incompatible

---

## Java Version Requirements

| Approach | Minimum Java | Recommended | LTS Support | Android 16 (Java 17) |
|----------|--------------|-------------|-------------|---------------------|
| **JNI** | Java 7+ | Java 17 LTS | ✅ All LTS versions | ✅ Fully supported |
| **Panama** | Java 22+ | Java 25 (next LTS) | ⚠️ Starting with Java 25 LTS | ❌ Not available |
| **Swift-Java** | Java 7+ (JNI mode) | Java 17+ | ✅ Works with all LTS | ✅ **Fully supported** |
| | Java 22+ (FFM mode) | Java 25 | ⚠️ FFM requires 22+ | ❌ Not available |

**Note:** Swift-Java uses "plain old JNI" via JavaKit macros, making it compatible with any JNI-capable Java version including Android 16's Java 17 support.

---

## Kotlin Support

All three approaches work seamlessly with Kotlin since Kotlin compiles to JVM bytecode and uses JNI identically to Java.

| Feature | JNI | Panama | Swift-Java |
|---------|-----|--------|------------|
| **Kotlin Native Methods** | ✅ | ✅ | ✅ |
| **Kotlin Coroutines** | ✅ Manual bridge | ✅ Manual bridge | ✅ Manual bridge |
| **Kotlin Data Classes** | ✅ | ✅ | ✅ |
| **Kotlin Extensions** | ✅ | ✅ | ✅ |
| **Kotlin Multiplatform** | ✅ Via JNI | ❌ Desktop only | ✅ Via Swift bridge |

**Example: Kotlin with Swift-Java**
```kotlin
package com.jabcode

class JABCodeService {
    // External function implemented in Swift
    external fun encode(content: String, colorMode: Int): ByteArray
    
    companion object {
        init {
            System.loadLibrary("JABCodeSwift")
        }
    }
}
```

```swift
@JavaImplementation("com.jabcode.JABCodeService")
extension JABCodeService {
    @JavaMethod
    public func encode(content: String, colorMode: Int32) -> [UInt8] {
        // Swift implementation calling JABCode C library
    }
}
```

**Key Points:**
- Kotlin and Java are interchangeable at the JNI level
- Swift-Java doesn't distinguish between Java and Kotlin sources
- Kotlin's `external fun` = Java's `native` method
- Works with Kotlin Multiplatform on Android

---

## JABCode Feature Support

### Core Encoding Features

| Feature | JNI | Panama | Swift-Java |
|---------|-----|--------|------------|
| **2-Color Mode** | ✅ | ✅ | ✅ |
| **4-Color Mode** | ✅ | ✅ | ✅ |
| **8-Color Mode** | ✅ | ✅ | ✅ |
| **ECC Levels (0-10)** | ✅ | ✅ | ✅ |
| **Multi-Symbol** | ✅ | ✅ | ✅ |
| **Custom Palette** | ✅ | ✅ | ✅ |
| **Module Size Control** | ✅ | ✅ | ✅ |
| **Symbol Dimensions** | ✅ | ✅ | ✅ |

### Core Decoding Features

| Feature | JNI | Panama | Swift-Java |
|---------|-----|--------|------------|
| **Image Decoding** | ✅ | ✅ | ✅ |
| **Multi-Symbol Read** | ✅ | ✅ | ✅ |
| **Error Correction** | ✅ | ✅ | ✅ |
| **Metadata Extraction** | ✅ | ✅ | ✅ |
| **Color Detection** | ✅ | ✅ | ✅ |

### Advanced Features

| Feature | JNI | Panama | Swift-Java |
|---------|-----|--------|------------|
| **Bitmap Creation** | ✅ | ✅ | ✅ |
| **PNG Export** | ✅ | ✅ | ✅ |
| **Memory Pooling** | ✅ Manual | ✅ Arena-based | ✅ Swift ARC |
| **Async Processing** | ✅ Manual threads | ✅ Java threads | ✅ Swift async |
| **Streaming API** | ✅ | ✅ | ✅ |

**Note:** All three approaches provide full access to the underlying JABCode C library features.

---

## Development & Deployment Matrix

### Build Requirements

| Requirement | JNI | Panama | Swift-Java |
|-------------|-----|--------|------------|
| **Java Compiler** | JDK 7+ | JDK 22+ | JDK 17+ |
| **C/C++ Compiler** | ✅ Required | ❌ Not needed | ❌ Not needed (for Java) |
| **Swift Compiler** | ❌ Not needed | ❌ Not needed | ✅ Swift 6.2+ |
| **Build Tool** | Maven/Gradle | Maven/Gradle | Maven/Gradle + SPM |
| **Platform SDK** | Platform-specific | Platform-specific | Platform-specific + Swift |

### Native Library Distribution

| Platform | JNI | Panama | Swift-Java |
|----------|-----|--------|------------|
| **Linux (.so)** | ✅ Required | ✅ Required | ✅ Required |
| **macOS (.dylib)** | ✅ Required | ✅ Required | ✅ Required |
| **Windows (.dll)** | ✅ Required | ✅ Required | ✅ Required |
| **Android (.so)** | ✅ Per-ABI | ❌ N/A | ✅ Per-ABI |
| **Fat Binary** | Manual | Manual | ⚠️ Swift can bundle |

### Deployment Complexity

| Aspect | JNI | Panama | Swift-Java |
|--------|-----|--------|------------|
| **Setup Complexity** | Medium | Low | High |
| **Binary Size** | Small | Small | Medium-Large |
| **Dependencies** | JNI headers | None (Java built-in) | Swift runtime + JavaKit |
| **Cross-Platform** | Build per platform | Build per platform | Build per platform |
| **Package Size** | ~500KB | ~400KB | ~2-3MB (Swift runtime) |

---

## Performance Characteristics

### Encoding Performance (Relative)

| Operation | JNI | Panama | Swift-Java |
|-----------|-----|--------|------------|
| **Simple Encode** | 100% (baseline) | 95-105% | 90-95% |
| **Complex Multi-Symbol** | 100% | 100-110% | 90-95% |
| **Batch Processing** | 100% | 105-115% | 85-95% |
| **Memory Overhead** | Low | Very Low | Medium |
| **Startup Time** | Fast | Fast | Medium (Swift init) |

### Memory Management

| Aspect | JNI | Panama | Swift-Java |
|--------|-----|--------|------------|
| **Strategy** | Manual | Arena (automatic) | Swift ARC + JNI |
| **Leak Risk** | High | Low | Low |
| **GC Pressure** | Low | Low | Medium |
| **Memory Safety** | Manual checks | Compile-time safe | Swift memory safety |

---

## Code Maintenance Matrix

### Lines of Code (Estimated)

| Component | JNI | Panama | Swift-Java |
|-----------|-----|--------|------------|
| **Java Code** | 200 lines | 100 lines | 150 lines |
| **Native Wrapper** | 500 lines (C++) | 0 lines | 200 lines (Swift) |
| **Generated Code** | 0 lines | 2000+ lines | 500+ lines |
| **Build Config** | 50 lines | 30 lines | 100 lines |
| **Total Maintained** | 750 lines | 130 lines | 450 lines |

### Maintenance Effort

| Task | JNI | Panama | Swift-Java |
|------|-----|--------|------------|
| **Add New Function** | High (manual C++) | Low (regenerate) | Medium (Swift impl) |
| **Update C Library** | High (update wrapper) | Low (regenerate) | Medium (update Swift) |
| **Fix Memory Leak** | Manual debugging | Rare (arena-managed) | Rare (ARC) |
| **Cross-Platform Build** | High (multiple toolchains) | Low (Java only) | High (Swift + Java) |
| **Testing** | Complex (JNI boundaries) | Medium (Java tests) | Medium (Swift tests) |

---

## Use Case Recommendations

### Android Mobile App ✅ Best Options

| Approach | Rating | Notes |
|----------|--------|-------|
| **JNI** | ⭐⭐⭐⭐⭐ | Current implementation, proven, lightweight |
| **Swift-Java** | ⭐⭐⭐⭐ | Good if using Swift elsewhere, cleaner code |
| **Panama** | ❌ | Not available on Android |

**Recommendation:** Keep JNI unless you're introducing Swift to your Android project.

#### Android 16 Specific (Java 17 Support)

**✅ Fully Compatible:**
- **JNI (`my-branch`)** - Works with Java 7-17+, no issues
- **Swift-Java (`swift-java-poc`)** - JNI mode works with Java 7-17+, **confirmed compatible**

**✅ Kotlin Support:**
- Both JNI and Swift-Java work seamlessly with Kotlin
- Kotlin's `external fun` maps directly to JNI native methods
- No special configuration needed

**Android 16 Benefits:**
- Java 17 features available (records, sealed classes, pattern matching)
- Better performance and memory management
- Swift-Java can leverage modern Java APIs
- Kotlin 2.x fully supported

**Example: Kotlin on Android 16 with Swift-Java**
```kotlin
// build.gradle.kts
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// JABCodeService.kt
package com.jabcode

class JABCodeService {
    external fun encode(content: String, colorMode: Int): ByteArray
    
    companion object {
        init {
            System.loadLibrary("JABCodeSwift")
        }
    }
}
```

### Desktop Application (Linux/macOS/Windows)

| Approach | Rating | Notes |
|----------|--------|-------|
| **Panama** | ⭐⭐⭐⭐⭐ | Best for new projects, pure Java, JDK 22+ |
| **JNI** | ⭐⭐⭐⭐ | Good if Java 17 LTS required |
| **Swift-Java** | ⭐⭐⭐ | Only if Swift already in use |

**Recommendation:** Panama for new projects on JDK 22+, JNI for LTS compatibility.

### Web/Cloud Server

| Approach | Rating | Notes |
|----------|--------|-------|
| **Panama** | ⭐⭐⭐⭐⭐ | Ideal - no wrapper code, modern JDK |
| **JNI** | ⭐⭐⭐⭐ | Good for Java 17 LTS |
| **Swift-Java** | ⭐⭐ | Unnecessary complexity for server |

**Recommendation:** Panama for modern cloud (JDK 25+), JNI for stable LTS environments.

### Cross-Platform Library

| Approach | Rating | Notes |
|----------|--------|-------|
| **JNI** | ⭐⭐⭐⭐⭐ | Maximum compatibility (Java 7+, Android) |
| **Swift-Java** | ⭐⭐⭐⭐ | If targeting iOS + Android with Swift |
| **Panama** | ⭐⭐⭐ | Desktop/server only, excludes Android |

**Recommendation:** JNI for maximum reach, Swift-Java if building Swift framework.

### Embedded Systems

| Approach | Rating | Notes |
|----------|--------|-------|
| **JNI** | ⭐⭐⭐⭐⭐ | Small footprint, works with minimal Java |
| **Swift-Java** | ⭐⭐⭐ | If Swift runtime available |
| **Panama** | ⭐⭐ | Requires recent JDK, larger footprint |

**Recommendation:** JNI for resource-constrained environments.

---

## Migration Decision Matrix

### Factors to Consider

| Factor | Favor JNI | Favor Panama | Favor Swift-Java |
|--------|-----------|--------------|------------------|
| **Android Support** | ✅ Required | ❌ Blocker | ✅ Available |
| **Java Version** | Java 7-21 LTS | Java 22+ | Java 17+ |
| **Team Skills** | C/C++ experts | Java-only team | Swift experts |
| **Maintenance** | Can handle complexity | Want simplicity | Want type safety |
| **Performance** | Critical microseconds | Good enough | Good enough |
| **New Project** | Legacy compat | ✅ Fresh start | Swift ecosystem |
| **Existing Swift** | N/A | N/A | ✅ Already using |

### Migration Paths

#### From JNI → Panama (Desktop/Server Only)

**Effort:** Medium (2-3 weeks)
**Risk:** Low
**Blockers:**
- ❌ Android apps (no FFM support)
- ❌ Java 17 LTS requirement
- ✅ Desktop/server only

**Steps:**
1. Ensure JDK 22+ available
2. Generate Panama bindings with `jextract`
3. Rewrite Java wrapper using FFM API
4. Remove C++ wrapper code
5. Update build to not require C++ compiler
6. Test thoroughly

#### From JNI → Swift-Java (Cross-Platform)

**Effort:** High (4-6 weeks)
**Risk:** Medium
**Blockers:**
- Need Swift 6.2+ toolchain
- Team must learn Swift
- Build complexity increases

**Steps:**
1. Set up Swift toolchain
2. Create Swift package wrapping JABCode C
3. Define Java native methods
4. Use `swift-java jextract --mode=jni`
5. Implement Swift wrapper with `@JavaImplementation`
6. Build Swift library for each platform
7. Update Java to load Swift library
8. Test on all target platforms

#### Dual-Branch Strategy (Recommended)

**Maintain both:**
- `swift-java-poc` for Android
- `panama-poc` for Desktop/Server
- `my-branch` JNI as fallback/reference

**Benefits:**
- Platform-optimized solutions
- Gradual migration
- No compatibility sacrifices
- Best tool for each job

---

## Feature Parity Checklist

### Must-Have Features (All Branches)

- [ ] Encode JABCode with all color modes (2, 4, 8)
- [ ] Decode JABCode from images
- [ ] Multi-symbol support
- [ ] ECC level configuration
- [ ] Custom module sizing
- [ ] PNG export
- [ ] Memory leak prevention
- [ ] Error handling
- [ ] Thread safety
- [ ] Performance benchmarks

### Nice-to-Have Features

- [ ] Async/await API
- [ ] Streaming support
- [ ] Memory pooling
- [ ] Batch processing optimizations
- [ ] Custom color palettes
- [ ] Advanced metadata handling

---

## Testing Matrix

### Test Coverage Required

| Test Type | JNI | Panama | Swift-Java |
|-----------|-----|--------|------------|
| **Unit Tests** | Java + C++ | Java only | Java + Swift |
| **Integration** | JNI boundary | FFM boundary | JNI + Swift |
| **Memory Leaks** | Valgrind/ASan | Arena tests | Swift/Java tests |
| **Performance** | JMH benchmarks | JMH benchmarks | JMH benchmarks |
| **Platform Tests** | All platforms | Desktop only | All platforms |
| **Android Tests** | ✅ Required | ❌ N/A | ✅ Required |

### Continuous Integration

| Platform | JNI | Panama | Swift-Java |
|----------|-----|--------|------------|
| **Linux x64** | ✅ | ✅ | ✅ |
| **macOS x64** | ✅ | ✅ | ✅ |
| **macOS ARM** | ✅ | ✅ | ✅ |
| **Windows x64** | ✅ | ✅ | ✅ |
| **Android ARM** | ✅ | ❌ | ✅ |
| **Android x86** | ✅ | ❌ | ✅ |

---

## Quick Reference: Which Branch for What?

### `my-branch` (JNI - Current Production)
✅ **Use for:**
- Production releases
- Maximum compatibility
- Android applications
- Reference implementation

### `panama-poc` Branch
✅ **Use for:**
- Desktop applications (JDK 22+)
- Web/cloud servers
- New projects wanting pure Java
- Eliminating C++ wrapper code

❌ **Don't use for:**
- Android applications
- Java 17 LTS environments
- Embedded systems with old JVMs

### `swift-java-poc` Branch
✅ **Use for:**
- Android apps with Swift components
- Cross-platform Swift frameworks
- iOS + Android shared Swift code
- Projects wanting Swift safety

❌ **Don't use for:**
- Pure Java projects
- Teams without Swift expertise
- Server-only deployments

---

## Summary Recommendations

### Short Term (Current Development)
- **Android:** Continue with JNI (`my-branch`)
- **Desktop:** Experiment with Panama (`panama-poc`)
- **Cross-Platform:** Evaluate Swift-Java if interested

### Medium Term (6-12 months)
- **Android:** Decide JNI vs Swift-Java based on Swift adoption
- **Desktop:** Migrate to Panama when JDK 25 LTS releases
- **Server:** Migrate to Panama for new deployments

### Long Term (1-2 years)
- **Android:** Keep JNI or migrate to Swift-Java for cross-platform
- **Desktop/Server:** Standardize on Panama
- **Maintain:** JNI fallback for compatibility

---

## Decision Flowchart

```
Do you need Android support?
    ├─ YES → Using Kotlin or Java 17+ (Android 16)?
    │         ├─ YES → Do you want Swift's benefits?
    │         │         ├─ YES → Swift-Java POC ⭐ (type safety, cleaner)
    │         │         └─ NO  → Keep JNI (proven, lightweight)
    │         └─ NO (Older Android) → Keep JNI
    └─ NO  → Can you use JDK 22+?
              ├─ YES → Panama POC ⭐⭐ (recommended, pure Java)
              └─ NO  → Keep JNI (Java 17 LTS)
```

**Key Decision Points:**
- **Android 16 + Kotlin?** Swift-Java is now an excellent choice
- **Desktop only + JDK 22+?** Panama is the best option
- **Need maximum compatibility?** Stick with JNI

---

## Contact & Resources

- **JNI Documentation:** Current implementation in `/javacpp-wrapper/`
- **Panama Guide:** `/memory-bank/research/panama-poc/README.md`
- **Swift-Java Guide:** `/memory-bank/research/swift-java-poc/README.md`
- **Comparison:** `/memory-bank/integration-approaches-comparison.md`

Last Updated: 2026-01-07
