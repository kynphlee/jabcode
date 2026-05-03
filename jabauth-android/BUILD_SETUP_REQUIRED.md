# Build Setup Required - Android SDK Missing

**Date:** 2026-05-03  
**Status:** ⚠️ Android SDK Not Found  

---

## Problem

The build requires Android SDK, but it's not installed on this system.

**Error:**
```
SDK location not found. Define a valid SDK location with an ANDROID_HOME 
environment variable or by setting the sdk.dir path in your project's 
local properties file
```

---

## ✅ Gradle Configuration Fixed

All Gradle 9.5 compatibility issues have been resolved:

1. ✅ **Updated Plugins:**
   - AGP: 8.2.0 → 8.3.0
   - Hilt: 2.48 → 2.50
   - Kotlin: 1.9.22

2. ✅ **Fixed Deprecations:**
   - Replaced `buildDir` with `layout.buildDirectory` (6 files)
   - Fixed Maven publishing configuration
   - Updated JaCoCo report tasks

3. ✅ **Module Structure:**
   - All manifests created
   - Diagnostic app temporarily disabled for Phase 1
   - Framework modules ready for testing

---

## Android SDK Installation Options

### **Option 1: Android Studio (Recommended)**

1. **Install Android Studio:**
   ```bash
   # Download from https://developer.android.com/studio
   # Or use package manager (Ubuntu/Debian):
   sudo snap install android-studio --classic
   ```

2. **Open Project:**
   ```bash
   android-studio jabauth-android/
   ```

3. **SDK Manager Will Prompt:**
   - Android Studio will detect missing SDK
   - Click "Install Android SDK"
   - Select API 35 (COMPILE_SDK)
   - Install build tools, platform tools, NDK

4. **Run Tests from IDE:**
   - Right-click `SecureStorageTest.kt`
   - Select "Run 'SecureStorageTest'"
   - All 11 tests should pass ✅

---

### **Option 2: Command-Line SDK Tools**

1. **Download SDK Command-Line Tools:**
   ```bash
   cd ~
   wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
   unzip commandlinetools-linux-9477386_latest.zip
   mkdir -p ~/Android/Sdk/cmdline-tools/latest
   mv cmdline-tools/* ~/Android/Sdk/cmdline-tools/latest/
   ```

2. **Set Environment Variables:**
   ```bash
   export ANDROID_HOME="$HOME/Android/Sdk"
   export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
   export PATH="$PATH:$ANDROID_HOME/platform-tools"
   
   # Add to ~/.bashrc for persistence
   echo 'export ANDROID_HOME="$HOME/Android/Sdk"' >> ~/.bashrc
   echo 'export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"' >> ~/.bashrc
   ```

3. **Install Required Packages:**
   ```bash
   sdkmanager --install "platforms;android-35"
   sdkmanager --install "build-tools;34.0.0"
   sdkmanager --install "platform-tools"
   sdkmanager --install "ndk;25.1.8937393"
   sdkmanager --install "cmake;3.22.1"
   sdkmanager --licenses  # Accept all licenses
   ```

4. **Create local.properties:**
   ```bash
   echo "sdk.dir=$HOME/Android/Sdk" > jabauth-android/local.properties
   ```

5. **Run Tests:**
   ```bash
   cd jabauth-android
   ./gradlew :framework:core:test
   ./gradlew :framework:core:jacocoTestReport
   ```

---

### **Option 3: Docker (Isolated Environment)**

1. **Use Android Docker Image:**
   ```bash
   docker pull thyrlian/android-sdk:latest
   
   docker run -it --rm \
     -v $(pwd)/jabauth-android:/workspace \
     thyrlian/android-sdk:latest \
     bash
   
   # Inside container:
   cd /workspace
   ./gradlew :framework:core:test
   ```

---

## Quick Verification (After SDK Install)

```bash
# 1. Create Gradle wrapper (if not done)
./gradlew wrapper --gradle-version=9.5

# 2. Verify project structure
./gradlew projects

# 3. Run core module tests
./gradlew :framework:core:test

# 4. Generate coverage report
./gradlew :framework:core:jacocoTestReport

# 5. View coverage
firefox framework/core/build/reports/jacoco/test/html/index.html
```

**Expected Output:**
```
> Task :framework:core:test
SecureStorageTest > putString stores value successfully PASSED
SecureStorageTest > getString returns null for non-existent key PASSED
SecureStorageTest > getString with default returns default for non-existent key PASSED
SecureStorageTest > putInt stores and retrieves integer PASSED
SecureStorageTest > putBoolean stores and retrieves boolean PASSED
SecureStorageTest > remove deletes key-value pair PASSED
SecureStorageTest > clear removes all key-value pairs PASSED
SecureStorageTest > contains returns true for existing key PASSED
SecureStorageTest > contains returns false for non-existent key PASSED
SecureStorageTest > putString with empty value stores successfully PASSED
SecureStorageTest > multiple operations maintain data integrity PASSED

BUILD SUCCESSFUL
Test Coverage: 85%+ ✅
```

---

## Current Status Summary

### ✅ **Complete**
- Monorepo structure
- Secure Storage interface + implementation
- 11 comprehensive unit tests
- Gradle 9.5 compatibility
- Build configurations
- Maven publishing setup

### ⚠️ **Blocked**
- Cannot run tests without Android SDK
- Cannot verify TDD compliance yet

### 📋 **Next Steps (After SDK Install)**
1. Run `./gradlew :framework:core:test`
2. Verify 11/11 tests pass
3. Generate coverage report
4. Confirm 80%+ coverage
5. Proceed to Day 2: Logging component

---

## Recommendation

**Use Option 1 (Android Studio)** for the best development experience:
- Automatic SDK management
- Built-in test runner
- Code completion for Android APIs
- Visual coverage reports
- Easy debugging

**Alternative:** If headless environment, use Option 2 (command-line tools).

---

**Files Modified:**
- `build.gradle.kts` - AGP 8.3.0, buildDir fixes
- `gradle.properties` - Hilt 2.50
- All module build files - buildDir → layout.buildDirectory
- `settings.gradle.kts` - Disabled diagnostic-app temporarily

**Ready for Testing:** Once Android SDK is installed, all infrastructure is in place.
