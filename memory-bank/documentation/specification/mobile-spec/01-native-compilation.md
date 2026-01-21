# Native C Compilation for Mobile Platforms

**Target Platforms:** Android NDK, iOS Xcode/Swift Package Manager  
**Toolchain Standards:** C11, ARM NEON (optional), position-independent code  
**Dependencies:** None (all desktop libraries removed)

---

## Current Desktop Build Analysis

### Source: `@/src/jabcode/Makefile`

```makefile
CC = gcc
CFLAGS = -O2 -std=c11 -fPIC
SHARED_LIB = libjabcode.so
STATIC_LIB = libjabcode.a

# Desktop dependencies (REMOVE FOR MOBILE):
LIBS = -lpng16 -lz -ltiff
```

**Desktop Build Process:**
1. Compiles all `.c` files including `image.c`
2. Links against libpng, zlib, libtiff
3. Produces `libjabcode.a` (static) and `libjabcode.so` (shared)

**Mobile Reality:**
- ❌ libpng/libtiff not available on Android/iOS
- ❌ `image.c` must be excluded from build
- ✅ All other C files are platform-agnostic

---

## Mobile Build Requirements

### File Exclusions

**EXCLUDE from mobile builds:**
```
src/jabcode/image.c          # Desktop PNG/TIFF I/O
```

**INCLUDE in mobile builds:**
```
src/jabcode/encoder.c        # Symbol generation
src/jabcode/decoder.c        # Symbol decoding
src/jabcode/ldpc.c           # Error correction
src/jabcode/detector.c       # Pattern detection
src/jabcode/binarizer.c      # Image preprocessing
src/jabcode/mask.c           # Data masking
src/jabcode/sample.c         # Grid sampling
src/jabcode/transform.c      # Perspective correction
src/jabcode/interleave.c     # Data interleaving
src/jabcode/pseudo_random.c  # PRNG for matrix generation
mobile_bridge.c              # NEW: Platform-agnostic API
```

### Header Files

**Required headers:**
```
include/jabcode.h            # Main library header
include/encoder.h            # Encoder constants
include/decoder.h            # Decoder constants
include/ldpc.h               # LDPC structures
include/detector.h           # Detection constants
mobile_bridge.h              # NEW: Mobile API
```

---

## Android NDK Compilation

### CMakeLists.txt

Create `@/swift-java-wrapper/android/jni/CMakeLists.txt`:

```cmake
cmake_minimum_required(VERSION 3.18.1)
project(jabcode)

# Set C11 standard
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Mobile-specific compilation flags
add_compile_definitions(MOBILE_BUILD)
add_compile_options(
    -O3                      # Maximum optimization
    -ffast-math              # Faster floating point
    -fno-exceptions          # No C++ exceptions needed
    -fvisibility=hidden      # Reduce binary size
)

# Enable ARM NEON SIMD on ARM platforms
if(ANDROID_ABI MATCHES "^armeabi-v7a")
    add_compile_options(-mfpu=neon)
elseif(ANDROID_ABI MATCHES "^arm64-v8a")
    # NEON enabled by default on ARM64
endif()

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../src/jabcode/include
)

# Source files (EXCLUDING image.c)
set(JABCODE_SOURCES
    ../../src/jabcode/encoder.c
    ../../src/jabcode/decoder.c
    ../../src/jabcode/ldpc.c
    ../../src/jabcode/detector.c
    ../../src/jabcode/binarizer.c
    ../../src/jabcode/mask.c
    ../../src/jabcode/sample.c
    ../../src/jabcode/transform.c
    ../../src/jabcode/interleave.c
    ../../src/jabcode/pseudo_random.c
    mobile_bridge.c
)

# Create shared library for Android
add_library(jabcode SHARED ${JABCODE_SOURCES})

# Link against standard C math library (no PNG/TIFF)
target_link_libraries(jabcode m log)

# Set output directory
set_target_properties(jabcode PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../libs/${ANDROID_ABI}
)
```

### Build Commands

```bash
# Configure NDK environment
export ANDROID_NDK=/path/to/android-ndk-r25c
export ANDROID_HOME=/path/to/android-sdk

# Build for all ABIs
cd swift-java-wrapper/android/jni
mkdir build && cd build

# ARM64 (primary target)
cmake .. \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-21
make

# ARMv7 (legacy devices)
cmake .. -DANDROID_ABI=armeabi-v7a -DANDROID_PLATFORM=android-21
make

# x86_64 (emulators)
cmake .. -DANDROID_ABI=x86_64 -DANDROID_PLATFORM=android-21
make

# Output: swift-java-wrapper/android/libs/arm64-v8a/libjabcode.so
```

### Alternative: Android.mk (Legacy NDK)

Create `@/swift-java-wrapper/android/jni/Android.mk`:

```makefile
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := jabcode
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../../src/jabcode/include
LOCAL_CFLAGS := -O3 -std=c11 -ffast-math -DMOBILE_BUILD

# Source files (NO image.c)
LOCAL_SRC_FILES := \
    ../../src/jabcode/encoder.c \
    ../../src/jabcode/decoder.c \
    ../../src/jabcode/ldpc.c \
    ../../src/jabcode/detector.c \
    ../../src/jabcode/binarizer.c \
    ../../src/jabcode/mask.c \
    ../../src/jabcode/sample.c \
    ../../src/jabcode/transform.c \
    ../../src/jabcode/interleave.c \
    ../../src/jabcode/pseudo_random.c \
    mobile_bridge.c

# Enable NEON on ARM
LOCAL_ARM_NEON := true

# Link libraries (NO PNG/TIFF)
LOCAL_LDLIBS := -lm -llog

include $(BUILD_SHARED_LIBRARY)
```

Build:
```bash
ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk
```

---

## iOS Compilation

### Swift Package Manager

Create `@/swift-java-wrapper/ios/Package.swift`:

```swift
// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "JABCode",
    platforms: [
        .iOS(.v13),
        .macOS(.v10_15)  // For testing on simulator
    ],
    products: [
        .library(
            name: "JABCode",
            targets: ["JABCode"]
        ),
    ],
    targets: [
        .target(
            name: "JABCodeNative",
            path: "../../src/jabcode",
            exclude: [
                "image.c",           // Desktop-only
                "Makefile",          // Desktop build script
            ],
            sources: [
                "encoder.c",
                "decoder.c",
                "ldpc.c",
                "detector.c",
                "binarizer.c",
                "mask.c",
                "sample.c",
                "transform.c",
                "interleave.c",
                "pseudo_random.c",
            ],
            publicHeadersPath: "include",
            cSettings: [
                .define("MOBILE_BUILD"),
                .unsafeFlags(["-O3", "-ffast-math"]),
            ]
        ),
        .target(
            name: "JABCode",
            dependencies: ["JABCodeNative"],
            path: "Sources/JABCode"
        ),
        .testTarget(
            name: "JABCodeTests",
            dependencies: ["JABCode"],
            path: "Tests/JABCodeTests"
        ),
    ]
)
```

### Xcode Project Configuration

**Project Settings:**
```
Build Settings → Apple Clang - Language:
  C Language Dialect: gnu11 (or c11)
  
Build Settings → Apple Clang - Code Generation:
  Optimization Level (Debug): -O0
  Optimization Level (Release): -O3
  Fast Math: Yes
  
Build Settings → Preprocessor Macros:
  MOBILE_BUILD=1
  
Build Phases → Compile Sources:
  ✅ encoder.c, decoder.c, ldpc.c, detector.c, binarizer.c
  ✅ mask.c, sample.c, transform.c, interleave.c, pseudo_random.c
  ✅ mobile_bridge.c
  ❌ image.c (REMOVE)
```

### CocoaPods (Alternative)

Create `@/swift-java-wrapper/ios/JABCode.podspec`:

```ruby
Pod::Spec.new do |s|
  s.name             = 'JABCode'
  s.version          = '1.0.0'
  s.summary          = 'JABCode polychrome barcode encoder/decoder for iOS'
  s.homepage         = 'https://github.com/yourusername/jabcode'
  s.license          = { :type => 'LGPL', :file => 'LICENSE' }
  s.author           = { 'Your Name' => 'your.email@example.com' }
  s.source           = { :git => 'https://github.com/yourusername/jabcode.git', :tag => s.version.to_s }

  s.ios.deployment_target = '13.0'
  s.swift_version = '5.9'

  s.source_files = [
    'src/jabcode/**/*.{c,h}',
    'swift-java-wrapper/ios/Sources/**/*.{swift,h}'
  ]
  
  s.exclude_files = [
    'src/jabcode/image.c',        # Desktop-only
    'src/jabcode/Makefile*'       # Build scripts
  ]
  
  s.public_header_files = 'src/jabcode/include/**/*.h'
  
  s.compiler_flags = '-O3', '-ffast-math', '-DMOBILE_BUILD'
  
  s.libraries = 'm'  # Math library only, NO png/tiff
end
```

---

## Mobile Bridge API

### mobile_bridge.h

Create `@/swift-java-wrapper/include/mobile_bridge.h`:

```c
#ifndef MOBILE_BRIDGE_H
#define MOBILE_BRIDGE_H

#ifdef __cplusplus
extern "C" {
#endif

#include "jabcode.h"

/**
 * @brief Mobile-specific encode parameters
 */
typedef struct {
    jab_int32 color_number;      // 4, 8, 16, 32, 64, 128 (NOT 256)
    jab_int32 symbol_number;     // Default: 1 (max: 4 for mobile)
    jab_int32 ecc_level;         // 0-7 (default: 3)
    jab_int32 module_size;       // Pixels per module (default: 12)
} jab_mobile_encode_params;

/**
 * @brief Create encoder from raw RGBA buffer
 * @param rgba_buffer Raw pixel data (width × height × 4 bytes)
 * @param width Image width in pixels
 * @param height Image height in pixels
 * @param data Data to encode
 * @param data_length Length of data in bytes
 * @param params Encoding parameters
 * @return Encoder instance or NULL on failure
 */
jab_encode* jabMobileEncodeCreate(
    jab_byte* rgba_buffer,
    jab_int32 width,
    jab_int32 height,
    jab_char* data,
    jab_int32 data_length,
    jab_mobile_encode_params* params
);

/**
 * @brief Encode data into JABCode symbol
 * @param enc Encoder instance
 * @param output_buffer Output RGBA buffer (allocated by caller)
 * @param output_width Output width in pixels (set by function)
 * @param output_height Output height in pixels (set by function)
 * @return JAB_SUCCESS or JAB_FAILURE
 */
jab_boolean jabMobileEncode(
    jab_encode* enc,
    jab_byte* output_buffer,
    jab_int32* output_width,
    jab_int32* output_height
);

/**
 * @brief Decode JABCode from raw RGBA buffer
 * @param rgba_buffer Raw pixel data (width × height × 4 bytes)
 * @param width Image width in pixels
 * @param height Image height in pixels
 * @return Decoded data or NULL on failure
 */
jab_data* jabMobileDecode(
    jab_byte* rgba_buffer,
    jab_int32 width,
    jab_int32 height
);

/**
 * @brief Free encoder instance
 * @param enc Encoder to free
 */
void jabMobileEncodeFree(jab_encode* enc);

/**
 * @brief Free decoded data
 * @param data Data to free
 */
void jabMobileDataFree(jab_data* data);

/**
 * @brief Get last error message
 * @return Error string or NULL
 */
const char* jabMobileGetLastError(void);

#ifdef __cplusplus
}
#endif

#endif // MOBILE_BRIDGE_H
```

### mobile_bridge.c Implementation Skeleton

```c
#include "mobile_bridge.h"
#include <string.h>
#include <stdio.h>

// Thread-local error storage
static __thread char last_error[256] = {0};

const char* jabMobileGetLastError(void) {
    return last_error[0] ? last_error : NULL;
}

static void setError(const char* msg) {
    strncpy(last_error, msg, sizeof(last_error) - 1);
    last_error[sizeof(last_error) - 1] = '\0';
}

jab_encode* jabMobileEncodeCreate(
    jab_byte* rgba_buffer,
    jab_int32 width,
    jab_int32 height,
    jab_char* data,
    jab_int32 data_length,
    jab_mobile_encode_params* params
) {
    // Validate parameters
    if (!rgba_buffer || !data || !params) {
        setError("Invalid parameters");
        return NULL;
    }
    
    if (params->color_number == 256) {
        setError("256-color mode not supported (known issue)");
        return NULL;
    }
    
    if (params->symbol_number > 4) {
        setError("Symbol cascading limited to 4 on mobile");
        return NULL;
    }
    
    // Create encoder using existing desktop functions
    jab_encode* enc = createEncode(
        params->color_number,
        params->symbol_number,
        params->ecc_level,
        0  // module_size ignored for now
    );
    
    if (!enc) {
        setError("Failed to create encoder");
        return NULL;
    }
    
    // TODO: Implement encoding logic
    // This is where you'd call existing encoder functions
    // but without PNG I/O dependencies
    
    return enc;
}

jab_data* jabMobileDecode(
    jab_byte* rgba_buffer,
    jab_int32 width,
    jab_int32 height
) {
    if (!rgba_buffer) {
        setError("Invalid buffer");
        return NULL;
    }
    
    // Create bitmap structure from RGBA buffer
    jab_bitmap* bitmap = (jab_bitmap*)malloc(
        sizeof(jab_bitmap) + width * height * 4
    );
    if (!bitmap) {
        setError("Memory allocation failed");
        return NULL;
    }
    
    bitmap->width = width;
    bitmap->height = height;
    bitmap->bits_per_pixel = 32;
    bitmap->bits_per_channel = 8;
    bitmap->channel_count = 4;
    memcpy(bitmap->pixel, rgba_buffer, width * height * 4);
    
    // Decode using existing functions
    jab_decoded_symbol* decoded_symbols = decodeJABCode(
        bitmap, 
        NORMAL_DECODE, 
        NULL  // status callback
    );
    
    if (!decoded_symbols) {
        free(bitmap);
        setError("Decoding failed");
        return NULL;
    }
    
    // Extract data from first symbol
    jab_data* result = decoded_symbols[0].data;
    
    // Cleanup
    free(bitmap);
    // Note: Don't free result, caller owns it
    
    return result;
}

void jabMobileEncodeFree(jab_encode* enc) {
    if (enc) {
        destroyEncode(enc);
    }
}

void jabMobileDataFree(jab_data* data) {
    if (data) {
        free(data);
    }
}
```

---

## Compilation Flags Reference

### Required Flags

```
-std=c11                 # C11 standard compliance
-O3                      # Maximum optimization
-fPIC                    # Position-independent code (shared libs)
-DMOBILE_BUILD           # Enable mobile-specific code paths
```

### Optional Performance Flags

```
-ffast-math              # Faster floating point (ISO relaxed)
-funroll-loops           # Loop unrolling
-fomit-frame-pointer     # Smaller stack frames
-march=armv8-a           # Target ARM architecture (iOS/Android)
```

### Debug Flags

```
-O0                      # No optimization
-g                       # Debug symbols
-fsanitize=address       # Memory error detection
-Wall -Wextra            # All warnings
```

---

## Verification Steps

### 1. Build Smoke Test

```bash
# Android
cd swift-java-wrapper/android/jni/build
cmake .. -DANDROID_ABI=arm64-v8a
make
ls -lh ../libs/arm64-v8a/libjabcode.so  # Should exist

# iOS
cd swift-java-wrapper/ios
swift build
# Should complete without errors
```

### 2. Symbol Check

```bash
# Android: Verify exported symbols
nm -D libjabcode.so | grep jabMobile
# Should show: jabMobileEncodeCreate, jabMobileDecode, etc.

# iOS: Verify symbols
nm -g .build/debug/libJABCodeNative.a | grep jabMobile
```

### 3. Size Check

```bash
# Binary size should be reasonable
ls -lh libjabcode.so
# Expected: 200-400 KB (without image.c, optimized)
```

### 4. Dependency Check

```bash
# Android: Should only depend on libc, libm, liblog
readelf -d libjabcode.so | grep NEEDED
# Should NOT show libpng, libtiff

# iOS: Check framework dependencies
otool -L JABCode.framework/JABCode
# Should only show system frameworks
```

---

## Troubleshooting

### "Undefined reference to png_*"
**Cause:** `image.c` still in build  
**Fix:** Remove from CMakeLists.txt or Android.mk

### "Symbol not found: _jabMobileEncode"
**Cause:** Missing `mobile_bridge.c` in build  
**Fix:** Add to source list in build script

### "NEON instruction error"
**Cause:** Compiling for wrong architecture  
**Fix:** Check `-march=armv8-a` flag, verify target ABI

### "Segmentation fault in encoder"
**Cause:** Likely 256-color mode or buffer overflow  
**Fix:** Restrict `color_number <= 128`, enable ASAN

---

**Next Steps:** Once native compilation succeeds, proceed to `02-mobile-optimizations.md` for performance tuning.
