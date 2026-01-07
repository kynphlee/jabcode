# JABCode Multi-Platform Build Strategy

## Branch Overview

This project maintains **three distinct approaches** for Java integration, each optimized for specific platforms:

```
my-branch (JNI - Production)
    ↓
interop-poc (Migration Base)
    ├── panama-poc      → Desktop/Server (JDK 23+)
    └── swift-java-poc  → Android/Mobile (Java 17+, Kotlin)
```

## Directory Structure by Branch

### `my-branch` - JNI Wrapper (Current Production)

```
jabcode/
├── src/jabcode/              # C library source
├── lib/                      # Compiled native libraries
└── javacpp-wrapper/          # JNI implementation
    ├── src/main/c/           # C++ JNI wrapper code
    └── src/main/java/        # Java API
```

**Platform:** Universal (Java 7+, Android, Desktop)
**Build:** Requires C++ compiler

### `panama-poc` - Panama FFM Wrapper

```
jabcode/
├── src/jabcode/              # C library source (shared)
├── lib/                      # Compiled native libraries (shared)
└── panama-wrapper/           # NEW: Panama implementation
    ├── jextract.sh           # Binding generator
    ├── pom.xml               # Maven build
    └── src/main/java/        # Pure Java API
```

**Platform:** Desktop/Server only (JDK 23+)
**Build:** No C++ compiler needed, jextract only

### `swift-java-poc` - Swift Interop Wrapper

```
jabcode/
├── src/jabcode/              # C library source (shared)
├── lib/                      # Compiled native libraries (shared)
└── swift-wrapper/            # NEW: Swift implementation
    ├── Package.swift         # Swift package
    ├── Sources/              # Swift wrapper around C
    └── java-bindings/        # Java interface
```

**Platform:** Android, iOS, Desktop (Java 7+)
**Build:** Requires Swift 6.2+ toolchain

## Build Requirements by Branch

### my-branch (JNI)
- JDK 11+
- C++ compiler (gcc/clang/MSVC)
- Maven or Gradle
- Platform-specific toolchain

### panama-poc (Panama)
- **JDK 23+ or JDK 25** (you have both)
- jextract tool
- Maven
- No C++ compiler needed

### swift-java-poc (Swift-Java)
- JDK 17+
- Swift 6.2+ compiler
- swift-java CLI tool
- Maven or Gradle

## Shared Components

All three branches share:
- **JABCode C library** (`/src/jabcode/`)
- **Native libraries** (`/lib/`)
- **Documentation** (`/memory-bank/`)

## When to Use Each Branch

### Use `my-branch` (JNI) for:
✅ Android applications (any version)
✅ Maximum compatibility (Java 7+)
✅ Production deployments
✅ Proven, stable implementation

### Use `panama-poc` (Panama) for:
✅ Desktop applications (JDK 23+)
✅ Cloud/server deployments
✅ New projects on modern JDK
✅ Pure Java preference
❌ **NOT for Android**

### Use `swift-java-poc` (Swift-Java) for:
✅ Android apps with Swift components
✅ iOS + Android cross-platform
✅ Kotlin projects on Android 16
✅ Swift ecosystem integration
❌ **NOT if avoiding Swift**

## Build Instructions

### Building on panama-poc Branch (Current)

```bash
# 1. Ensure you're on the right branch
git checkout panama-poc

# 2. Set up JDK 25
export JAVA_HOME=/home/kynphlee/tools/compilers/java/jdk-25.0.1
export PATH="$JAVA_HOME/bin:$PATH"

# 3. Build JABCode C library (if not already built)
cd src/jabcode
make clean && make
cd ../..

# 4. Generate Panama bindings
cd panama-wrapper
./jextract.sh

# 5. Build Java wrapper
mvn clean package

# 6. Test
mvn test -Djava.library.path=../lib
```

### Building JNI (for comparison)

```bash
git checkout my-branch
cd javacpp-wrapper
mvn clean package
```

### Building Swift-Java (future)

```bash
git checkout swift-java-poc
cd swift-wrapper
swift build
./generate-java-bindings.sh
mvn clean package
```

## Development Workflow

### Scenario 1: Developing Desktop App

**Recommended:** `panama-poc` branch

```bash
git checkout panama-poc
# Work in panama-wrapper/
# No C++ compilation needed
# Fast iteration with jextract
```

### Scenario 2: Developing Android App

**Option A (Current):** `my-branch` branch
```bash
git checkout my-branch
# Use javacpp-wrapper/
# Proven JNI implementation
```

**Option B (Experimental):** `swift-java-poc` branch
```bash
git checkout swift-java-poc
# Use swift-wrapper/
# Modern Swift + Kotlin
```

### Scenario 3: Server Deployment (JDK 25)

**Recommended:** `panama-poc` branch

```bash
git checkout panama-poc
cd panama-wrapper
mvn clean package
# Deploy JAR to server
```

## Migration Strategy

### Phase 1 (Current): Parallel Development
- ✅ Keep `my-branch` (JNI) as stable production
- 🚧 Develop `panama-poc` for desktop
- 📋 Plan `swift-java-poc` for Android modernization

### Phase 2: Platform-Specific Deployment
- Android apps → `my-branch` or `swift-java-poc`
- Desktop apps → `panama-poc`
- Server apps → `panama-poc`

### Phase 3: Consolidation
- Maintain best tool for each platform
- Share core C library across all
- Unified documentation

## Dependency Management

### JNI (my-branch)
```xml
<dependency>
    <groupId>org.bytedeco</groupId>
    <artifactId>javacpp</artifactId>
    <version>1.5.9</version>
</dependency>
```

### Panama (panama-poc)
```xml
<!-- No external dependencies! -->
<!-- Panama FFM is built into JDK 23+ -->
```

### Swift-Java (swift-java-poc)
```swift
dependencies: [
    .package(url: "https://github.com/swiftlang/swift-java", branch: "main")
]
```

## Testing Strategy

Each branch has its own test suite:

```
my-branch:      javacpp-wrapper/src/test/
panama-poc:     panama-wrapper/src/test/
swift-java-poc: swift-wrapper/Tests/
```

Run platform-specific tests on appropriate branch.

## Continuous Integration Considerations

Ideally, CI would test:
- `my-branch`: Linux, macOS, Windows, Android
- `panama-poc`: Linux, macOS, Windows (JDK 25)
- `swift-java-poc`: Android, iOS (Swift)

Currently, test manually per platform.

## Documentation Structure

```
memory-bank/
├── integration-approaches-comparison.md  # High-level comparison
├── platform-feature-matrix.md           # Platform compatibility
└── research/
    ├── panama-poc/
    │   ├── README.md
    │   └── example-usage.java
    └── swift-java-poc/
        └── README.md
```

## Quick Reference

| Need | Branch | Directory |
|------|--------|-----------|
| Android app | `my-branch` or `swift-java-poc` | `javacpp-wrapper/` or `swift-wrapper/` |
| Desktop app (modern) | `panama-poc` | `panama-wrapper/` |
| Desktop app (LTS) | `my-branch` | `javacpp-wrapper/` |
| Server (JDK 25) | `panama-poc` | `panama-wrapper/` |
| Server (JDK 17 LTS) | `my-branch` | `javacpp-wrapper/` |
| Maximum compat | `my-branch` | `javacpp-wrapper/` |

## Current Status

**As of 2026-01-07:**

✅ **my-branch (JNI)**
- Status: Production ready
- Location: `javacpp-wrapper/`
- Platform: Universal

🚧 **panama-poc (Panama)**
- Status: Structure created, needs implementation
- Location: `panama-wrapper/`
- Next: Run jextract, complete encoder/decoder
- Platform: Desktop/Server (JDK 23+)

📋 **swift-java-poc (Swift-Java)**
- Status: Planned, not yet implemented
- Location: TBD `swift-wrapper/`
- Platform: Android, iOS

## Getting Started on panama-poc

You are here → See `panama-wrapper/QUICKSTART.md` for next steps.
