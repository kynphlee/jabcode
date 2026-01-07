# JABCode Swift-Java Proof of Concept

This directory contains an experimental Swift-Java interop layer for JABCode.

## Architecture

```
Java (Android/JVM)
  ↓ [JNI via swift-java macros]
Swift (JABCodeSwift wrapper)
  ↓ [Swift C interop]
C (JABCode native library)
```

## Prerequisites

- Swift 6.2+ toolchain
- JDK 17+ (JDK 22+ for FFM mode)
- JABCode C library built

## Setup

1. Install swift-java CLI:
```bash
git clone https://github.com/swiftlang/swift-java.git
cd swift-java
./gradlew publishToMavenLocal
```

2. Build the Swift wrapper:
```bash
cd swift-java-poc
swift build
```

3. Generate Java bindings:
```bash
swift-java jextract \
  --mode=jni \
  --swift-module JABCodeSwift \
  --input-swift Sources/JABCodeSwift \
  --output-java generated/java \
  --output-swift generated/swift
```

## Comparison with Current JNI

### Current Approach (Direct JNI)
- **Lines of code:** ~500 lines C++ JNI wrapper
- **Dependencies:** JNI headers, C++ compiler
- **Build time:** Fast (single compile step)
- **Maintenance:** Manual JNI boilerplate

### Swift-Java Approach
- **Lines of code:** ~150 lines Swift (estimated)
- **Dependencies:** Swift 6.2+, swift-java, JDK 17+
- **Build time:** Slower (Swift + Java + C)
- **Maintenance:** Automatic binding generation

## Decision Criteria

**Use Swift-Java if:**
- You're already using Swift in your project
- You want type-safe native bindings
- You're building cross-platform Swift libraries

**Stick with current JNI if:**
- You don't have Swift infrastructure
- Performance is critical
- Current solution works well
- Team doesn't know Swift

## Status

⚠️ **EXPERIMENTAL** - This is a proof-of-concept only. The production implementation remains the JNI wrapper in `../javacpp-wrapper/`.
