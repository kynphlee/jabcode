# JABAuth Android - Multi-Module Monorepo

**Version:** 1.0.0  
**Architecture:** Multi-Module Monorepo  
**Status:** 🚧 Under Development

---

## Overview

JABAuth Android is a comprehensive authentication framework built around JABCode 2D barcodes, implementing PKI, JWT, and ABE (Attribute-Based Encryption) authentication methods for mobile devices.

**Components:**
- **Framework:** 5 reusable library modules
- **Diagnostic App:** Reference implementation and testing application

---

## Project Structure

```
jabauth-android/
├── framework/                          # Framework modules (5)
│   ├── core/                          # :framework:core
│   │   └── Storage, Logging, Networking, Data Validation
│   ├── jabcode-sdk/                   # :framework:jabcode-sdk
│   │   └── Native JABCode encoding/decoding (JNI)
│   ├── jabauth-client/                # :framework:jabauth-client
│   │   └── PKI, JWT, ABE authentication
│   ├── diagnostic-engine/             # :framework:diagnostic-engine
│   │   └── Benchmarks, color mode comparison, bug reporting
│   └── ui-components/                 # :framework:ui-components
│       └── Reusable Compose UI components
└── apps/                              # Applications
    └── diagnostic-app/                # :apps:diagnostic-app
        └── Diagnostic & demo application
```

---

## Quick Start

### **Prerequisites**

- **JDK:** 17+
- **Android Studio:** Hedgehog (2023.1.1) or newer
- **Android SDK:** API 24+ (compileSdk 35)
- **NDK:** 25.1.8937393 or newer (for JABCode native library)
- **CMake:** 3.22.1+

### **Initial Setup**

```bash
# 1. Clone repository
git clone https://github.com/yourorg/jabauth-android.git
cd jabauth-android

# 2. Sync Gradle
./gradlew --refresh-dependencies

# 3. Build all modules
./gradlew build

# 4. Run tests
./gradlew test
```

---

## Development Workflows

### **Workflow 1: Framework Development**

Develop framework modules independently:

```bash
# Build specific module
./gradlew :framework:core:build
./gradlew :framework:jabcode-sdk:build

# Test specific module
./gradlew :framework:core:test
./gradlew :framework:jabauth-client:test

# Generate coverage report
./gradlew :framework:core:jacocoTestReport
open framework/core/build/reports/jacoco/test/html/index.html

# Build all framework modules
./gradlew framework:core:build \
          framework:jabcode-sdk:build \
          framework:jabauth-client:build \
          framework:diagnostic-engine:build \
          framework:ui-components:build

# Test all framework modules
./gradlew framework:test
```

### **Workflow 2: Publish Framework (Local Maven)**

After completing framework phases 1-5, publish to Maven Local:

```bash
# Publish all framework modules to ~/.m2/repository/
./gradlew publishAllPublicationsToLocalRepository

# Verify published artifacts
ls -R ~/.m2/repository/com/jabauth/framework/

# Artifacts available at:
# ~/.m2/repository/com/jabauth/framework/core/1.0.0/
# ~/.m2/repository/com/jabauth/framework/jabcode-sdk/1.0.0/
# ... etc.
```

### **Workflow 3: Diagnostic App Development**

#### **Option A: Use Project Dependencies (Active Development)**

```bash
# In gradle.properties
USE_PUBLISHED_FRAMEWORK=false

# App automatically uses project dependencies
./gradlew :apps:diagnostic-app:build
./gradlew :apps:diagnostic-app:test

# Run app on device/emulator
./gradlew :apps:diagnostic-app:installDebug
```

#### **Option B: Use Published Framework (Stable Testing)**

```bash
# 1. Publish framework first
./gradlew publishAllPublicationsToLocalRepository

# 2. Switch to published artifacts
# In gradle.properties:
USE_PUBLISHED_FRAMEWORK=true

# 3. Clean and rebuild app
./gradlew clean :apps:diagnostic-app:build
./gradlew :apps:diagnostic-app:test
```

---

## Testing

### **Module-Level Tests**

```bash
# Test individual modules
./gradlew :framework:core:test                    # 25 tests, 80%+ coverage
./gradlew :framework:jabcode-sdk:test             # 35 tests, 85%+ coverage
./gradlew :framework:jabauth-client:test          # 40 tests, 80%+ coverage
./gradlew :framework:diagnostic-engine:test       # 36 tests, 75%+ coverage
./gradlew :framework:ui-components:test           # 40 tests, 70%+ coverage

# Test all framework modules
./gradlew framework:test                          # 176 tests total

# Test diagnostic app
./gradlew :apps:diagnostic-app:test               # 82 tests, 80%+ coverage
```

### **Coverage Reports**

```bash
# Generate per-module coverage
./gradlew :framework:core:jacocoTestReport
./gradlew :apps:diagnostic-app:jacocoTestReport

# Generate composite coverage report (all modules)
./gradlew jacocoRootReport
open build/reports/jacoco/jacocoRootReport/html/index.html
```

### **Test-Coverage-Update Workflow**

Run after **every phase completion**:

```bash
# 1. Clean build
./gradlew clean

# 2. Run all tests (unit + instrumented)
./gradlew test connectedAndroidTest

# 3. Generate JaCoCo coverage report
./gradlew jacocoTestReport

# 4. Review coverage report
open build/reports/jacoco/test/html/index.html

# 5. If failures or coverage < target:
#    a. Collect error statistics from stack traces
#    b. Triage errors by severity
#    c. Fix issues
#    d. Repeat steps 2-4
#
# 6. Phase complete when:
#    ✅ All tests pass
#    ✅ Coverage ≥ target percentage
#    ✅ No critical issues
```

---

## Versioning & Release

### **Framework Versioning**

Edit `gradle.properties`:

```properties
# Development (unstable)
FRAMEWORK_VERSION=1.0.0-SNAPSHOT

# Release (stable)
FRAMEWORK_VERSION=1.0.0
```

### **Release Workflow**

```bash
# 1. Update version in gradle.properties
# FRAMEWORK_VERSION=1.0.0

# 2. Build all modules
./gradlew build

# 3. Run all tests
./gradlew test
# Expected: 176 tests pass, 80%+ coverage

# 4. Publish to Maven Local (for testing)
./gradlew publishAllPublicationsToLocalRepository

# 5. Test diagnostic app with published version
# (Set USE_PUBLISHED_FRAMEWORK=true)
./gradlew :apps:diagnostic-app:test

# 6. Tag release
git tag -a v1.0.0 -m "JABAuth Framework v1.0.0"
git push origin v1.0.0

# 7. (Optional) Publish to remote Maven
# ./gradlew publishAllPublicationsToMavenRepository
```

---

## Module Dependencies

```
apps:diagnostic-app
├── framework:ui-components
│   ├── framework:core
│   └── framework:jabcode-sdk
│       └── framework:core
├── framework:diagnostic-engine
│   ├── framework:core
│   ├── framework:jabcode-sdk
│   └── framework:jabauth-client
│       ├── framework:core
│       └── framework:jabcode-sdk
└── Direct dependencies on all framework modules
```

---

## Implementation Phases

### **Framework (Weeks 1-7)**

1. **Phase 1: :core** (1 week) - Storage, logging, validation
2. **Phase 2: :jabcode-sdk** (1 week) - Native wrapper, calibration
3. **Phase 3: :jabauth-client** (1.5 weeks) - PKI, JWT, ABE
4. **Phase 4: :diagnostic-engine** (1.5 weeks) - Benchmarks
5. **Phase 5: :ui-components** (2 weeks) - Compose components

### **Diagnostic App (Weeks 8-11)**

1. **Phase 1: Setup** (3 days) - Navigation, theme
2. **Phase 2: Dashboard** (5 days) - Metrics, graphs
3. **Phase 3: Scanner** (5 days) - CameraX, QR scanning
4. **Phase 4: Integration** (4 days) - E2E tests, DI
5. **Phase 5: Performance** (3 days) - Profiling, polish

**Total:** 11-12 weeks

---

## Success Criteria

### **Framework Complete (Phase 1-5)**
- ✅ 176 tests passing
- ✅ 80%+ overall coverage
- ✅ 6 color modes functional (4-128)
- ✅ PKI + JWT validation working
- ✅ Encode ≤100ms, Decode ≤150ms

### **Diagnostic App Complete (Phase 1-5)**
- ✅ 82 tests passing
- ✅ 80%+ coverage
- ✅ UI matches prototypes
- ✅ Cold start ≤2s, FPS ≥30
- ✅ Accessibility ≥90%

---

## Useful Commands

```bash
# List all modules
./gradlew projects

# Build specific module
./gradlew :framework:core:build

# Clean all
./gradlew clean

# Generate dependency tree
./gradlew :apps:diagnostic-app:dependencies

# Check for dependency updates
./gradlew dependencyUpdates

# Build release APK
./gradlew :apps:diagnostic-app:assembleRelease
```

---

## Documentation

- **Implementation Plans:** `/memory-bank/documentation/specification/mobile-spec/`
- **Framework Plan:** `FRAMEWORK_IMPLEMENTATION_PLAN.md`
- **App Plan:** `DIAGNOSTIC_APP_PLAN.md`
- **UI Prototypes:** `/swift-java-wrapper/android/ui-prototypes/`

---

## License

MIT License - See LICENSE file for details

---

**Status:** 📋 Ready to Start Implementation  
**Next Milestone:** Framework Phase 1 (:core module)
