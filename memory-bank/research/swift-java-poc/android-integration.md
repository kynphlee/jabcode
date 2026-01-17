# Android Integration Guide for JABCode

**Deep-dive implementation guide for Android applications**

[← Back to Index](index.md) | [Overview](overview.md)

---

## 📋 Table of Contents

1. [Environment Setup](#environment-setup)
2. [Project Structure](#project-structure)
3. [Building Native Library](#building-native-library)
4. [JNI Bridge Implementation](#jni-bridge-implementation)
5. [Java/Kotlin API Design](#javakotlin-api-design)
6. [Camera Integration](#camera-integration)
7. [Memory Management](#memory-management)
8. [Threading Model](#threading-model)
9. [Error Handling](#error-handling)
10. [Testing Strategy](#testing-strategy)
11. [ProGuard/R8 Configuration](#proguardr8-configuration)

---

## 🔧 Environment Setup

### Prerequisites

```bash
# Required tools
- Android Studio Hedgehog (2023.1.1) or later
- NDK 25.0+ (preferably 26.1.10909125)
- CMake 3.22.1+ 
- JDK 17+
- Git for version control

# Recommended
- ccache for faster native builds
- ninja for parallel builds
- Android Emulator with Google Play (for testing)
```

### SDK Configuration

**build.gradle.kts (Module level):**
```kotlin
android {
    namespace = "com.example.jabcode"
    compileSdk = 34
    ndkVersion = "26.1.10909125"
    
    defaultConfig {
        applicationId = "com.example.jabcode"
        minSdk = 21  // Android 5.0+ for Camera2 API
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        
        // Enable native build
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_PLATFORM=android-21"
                )
            }
        }
        
        // Specify ABIs to build
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    buildFeatures {
        viewBinding = true
        compose = true  // If using Jetpack Compose
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // Camera
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // Coroutines for async
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Image processing
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

---

## 📁 Project Structure

```
android-app/
├── src/
│   ├── main/
│   │   ├── cpp/                          # Native C/C++ code
│   │   │   ├── CMakeLists.txt            # CMake build configuration
│   │   │   ├── jabcode_jni.cpp           # JNI wrapper implementation
│   │   │   ├── jabcode_jni.h             # JNI wrapper header
│   │   │   ├── jabcode/                  # JABCode C library (submodule or copy)
│   │   │   │   ├── encoder.c
│   │   │   │   ├── decoder.c
│   │   │   │   ├── ldpc.c
│   │   │   │   └── ...
│   │   │   └── third_party/              # Dependencies
│   │   │       └── libpng/
│   │   │
│   │   ├── java/com/example/jabcode/
│   │   │   ├── JABCode.kt                # Main Kotlin API
│   │   │   ├── JABCodeEncoder.kt         # Encoder wrapper
│   │   │   ├── JABCodeDecoder.kt         # Decoder wrapper
│   │   │   ├── model/                    # Data models
│   │   │   │   ├── EncodeOptions.kt
│   │   │   │   ├── DecodeResult.kt
│   │   │   │   └── JABCodeException.kt
│   │   │   ├── camera/                   # Camera integration
│   │   │   │   ├── CameraManager.kt
│   │   │   │   └── ImageAnalyzer.kt
│   │   │   └── util/                     # Utilities
│   │   │       ├── BitmapUtils.kt
│   │   │       └── ColorUtils.kt
│   │   │
│   │   └── res/                          # Android resources
│   │
│   ├── test/                             # Unit tests
│   │   └── java/com/example/jabcode/
│   │       └── JABCodeTest.kt
│   │
│   └── androidTest/                      # Instrumented tests
│       └── java/com/example/jabcode/
│           └── JABCodeIntegrationTest.kt
│
└── build.gradle.kts
```

---

## 🔨 Building Native Library

### CMakeLists.txt Configuration

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(jabcode-android)

# Set C standard
set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Set C++ standard for JNI wrapper
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Build type optimizations
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG -fvisibility=hidden")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG -fvisibility=hidden")

# Enable Link-Time Optimization for release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    set(CMAKE_INTERPROCEDURAL_OPTIMIZATION TRUE)
endif()

# JABCode C library sources
set(JABCODE_SOURCES
    jabcode/encoder.c
    jabcode/decoder.c
    jabcode/ldpc.c
    jabcode/detector.c
    jabcode/binarizer.c
    jabcode/image.c
    jabcode/mask.c
    jabcode/pseudo_random.c
    jabcode/sample.c
    jabcode/transform.c
)

# Create static library for JABCode core
add_library(jabcode-core STATIC ${JABCODE_SOURCES})

target_include_directories(jabcode-core PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/jabcode
)

# Link against Android log library
find_library(log-lib log)
target_link_libraries(jabcode-core ${log-lib})

# PNG library (use prebuilt or build from source)
# Option 1: Use Android's built-in PNG support
find_library(png-lib png)
if(png-lib)
    target_link_libraries(jabcode-core ${png-lib})
else()
    # Option 2: Build libpng from source
    add_subdirectory(third_party/libpng)
    target_link_libraries(jabcode-core png_static)
endif()

# Math library
target_link_libraries(jabcode-core m)

# JNI wrapper library (shared library for Android)
add_library(jabcode-jni SHARED
    jabcode_jni.cpp
)

target_include_directories(jabcode-jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/jabcode
)

target_link_libraries(jabcode-jni
    jabcode-core
    android       # Android native API
    jnigraphics   # Bitmap API
    ${log-lib}
)

# Strip symbols in release builds to reduce size
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_custom_command(TARGET jabcode-jni POST_BUILD
        COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:jabcode-jni>
        COMMENT "Stripping jabcode-jni"
    )
endif()
```

### Build Commands

```bash
# Clean build
./gradlew clean

# Build debug version
./gradlew assembleDebug

# Build release version
./gradlew assembleRelease

# Build specific ABI only (faster for development)
./gradlew assembleDebug -Pandroid.injected.abi=arm64-v8a

# Run native unit tests (if configured)
./gradlew externalNativeBuildDebug
cd app/.cxx/Debug/<hash>/arm64-v8a
ctest
```

---

## 🌉 JNI Bridge Implementation

### JNI Wrapper Header (jabcode_jni.h)

```cpp
#ifndef JABCODE_JNI_H
#define JABCODE_JNI_H

#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>

// Logging macros
#define TAG "JABCode-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Exception helpers
void throwJABCodeException(JNIEnv* env, const char* message);
void throwOutOfMemoryError(JNIEnv* env, const char* message);

// Type conversion helpers
jbyteArray bitmapToByteArray(JNIEnv* env, jobject bitmap);
jobject byteArrayToBitmap(JNIEnv* env, jbyteArray data, jint width, jint height);

// Resource management
class ScopedJavaGlobalRef {
public:
    ScopedJavaGlobalRef(JNIEnv* env, jobject obj);
    ~ScopedJavaGlobalRef();
    jobject get() const { return obj_; }
private:
    JavaVM* vm_;
    jobject obj_;
};

#endif // JABCODE_JNI_H
```

### JNI Wrapper Implementation (jabcode_jni.cpp)

```cpp
#include "jabcode_jni.h"
#include "jabcode.h"
#include <cstring>
#include <memory>

extern "C" {

// Exception handling
void throwJABCodeException(JNIEnv* env, const char* message) {
    jclass exClass = env->FindClass("com/example/jabcode/model/JABCodeException");
    if (exClass != nullptr) {
        env->ThrowNew(exClass, message);
        env->DeleteLocalRef(exClass);
    }
}

void throwOutOfMemoryError(JNIEnv* env, const char* message) {
    jclass exClass = env->FindClass("java/lang/OutOfMemoryError");
    if (exClass != nullptr) {
        env->ThrowNew(exClass, message);
        env->DeleteLocalRef(exClass);
    }
}

// ============================================================================
// Encoding Functions
// ============================================================================

JNIEXPORT jbyteArray JNICALL
Java_com_example_jabcode_JABCodeEncoder_nativeEncode(
    JNIEnv* env,
    jobject /* this */,
    jstring jData,
    jint colorNumber,
    jint symbolNumber,
    jint eccLevel,
    jint moduleSize
) {
    // Convert Java string to C string
    const char* data = env->GetStringUTFChars(jData, nullptr);
    if (data == nullptr) {
        throwJABCodeException(env, "Failed to get input data");
        return nullptr;
    }
    
    jsize dataLen = env->GetStringUTFLength(jData);
    LOGD("Encoding data: length=%d, colors=%d, ecc=%d", dataLen, colorNumber, eccLevel);
    
    // Create encode parameters
    jab_encode* enc = createEncode(colorNumber, symbolNumber);
    if (enc == nullptr) {
        env->ReleaseStringUTFChars(jData, data);
        throwOutOfMemoryError(env, "Failed to create encoder");
        return nullptr;
    }
    
    // Set parameters
    enc->symbol_ecc_levels[0] = eccLevel;
    enc->module_size = moduleSize;
    
    // Prepare input data
    jab_data inputData;
    inputData.data = (jab_char*)data;
    inputData.length = dataLen;
    
    // Generate code
    jint result = generateJABCode(enc, &inputData);
    
    // Release Java string
    env->ReleaseStringUTFChars(jData, data);
    
    if (result != 0) {
        destroyEncode(enc);
        char errorMsg[256];
        snprintf(errorMsg, sizeof(errorMsg), "Encoding failed with code: %d", result);
        throwJABCodeException(env, errorMsg);
        return nullptr;
    }
    
    // Convert bitmap to byte array (ARGB_8888 format)
    jab_bitmap* bitmap = enc->bitmap;
    jint width = bitmap->width;
    jint height = bitmap->height;
    jint pixelCount = width * height;
    
    // Create Java byte array
    jbyteArray jBitmapData = env->NewByteArray(pixelCount * 4 + 8); // +8 for width/height
    if (jBitmapData == nullptr) {
        destroyEncode(enc);
        throwOutOfMemoryError(env, "Failed to allocate output array");
        return nullptr;
    }
    
    // Pack width and height at start
    jbyte* bytes = env->GetByteArrayElements(jBitmapData, nullptr);
    if (bytes == nullptr) {
        destroyEncode(enc);
        throwOutOfMemoryError(env, "Failed to access output array");
        return nullptr;
    }
    
    // Store width and height (big-endian)
    bytes[0] = (width >> 24) & 0xFF;
    bytes[1] = (width >> 16) & 0xFF;
    bytes[2] = (width >> 8) & 0xFF;
    bytes[3] = width & 0xFF;
    bytes[4] = (height >> 24) & 0xFF;
    bytes[5] = (height >> 16) & 0xFF;
    bytes[6] = (height >> 8) & 0xFF;
    bytes[7] = height & 0xFF;
    
    // Copy pixel data
    memcpy(bytes + 8, bitmap->pixel, pixelCount * 4);
    
    env->ReleaseByteArrayElements(jBitmapData, bytes, 0);
    
    destroyEncode(enc);
    
    LOGD("Encoding successful: %dx%d", width, height);
    return jBitmapData;
}

// ============================================================================
// Decoding Functions
// ============================================================================

JNIEXPORT jstring JNICALL
Java_com_example_jabcode_JABCodeDecoder_nativeDecode(
    JNIEnv* env,
    jobject /* this */,
    jobject jBitmap
) {
    // Lock bitmap pixels
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, jBitmap, &info) < 0) {
        throwJABCodeException(env, "Failed to get bitmap info");
        return nullptr;
    }
    
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        throwJABCodeException(env, "Bitmap must be ARGB_8888 format");
        return nullptr;
    }
    
    void* pixels;
    if (AndroidBitmap_lockPixels(env, jBitmap, &pixels) < 0) {
        throwJABCodeException(env, "Failed to lock bitmap pixels");
        return nullptr;
    }
    
    LOGD("Decoding bitmap: %dx%d", info.width, info.height);
    
    // Create JABCode bitmap
    jab_bitmap bitmap;
    bitmap.width = info.width;
    bitmap.height = info.height;
    bitmap.bits_per_pixel = 32;
    bitmap.bits_per_channel = 8;
    bitmap.channel_count = 4;
    bitmap.pixel = (jab_byte*)pixels;
    
    // Decode
    jab_int32 detectionResult;
    jab_decoded_symbol* decodedSymbols = decodeJABCode(&bitmap, NORMAL_DECODE, &detectionResult);
    
    // Unlock bitmap
    AndroidBitmap_unlockPixels(env, jBitmap);
    
    if (decodedSymbols == nullptr) {
        char errorMsg[256];
        snprintf(errorMsg, sizeof(errorMsg), "Decoding failed: code %d", detectionResult);
        throwJABCodeException(env, errorMsg);
        return nullptr;
    }
    
    // Extract decoded data
    jab_data* data = decodedSymbols[0].data;
    if (data == nullptr || data->length == 0) {
        free(decodedSymbols);
        throwJABCodeException(env, "No data decoded");
        return nullptr;
    }
    
    // Convert to Java string
    jstring result = env->NewStringUTF((const char*)data->data);
    
    // Cleanup
    free(decodedSymbols);
    
    LOGD("Decoding successful: length=%d", data->length);
    return result;
}

// ============================================================================
// Utility Functions
// ============================================================================

JNIEXPORT jstring JNICALL
Java_com_example_jabcode_JABCode_nativeGetVersion(
    JNIEnv* env,
    jclass /* clazz */
) {
    return env->NewStringUTF(JABCODE_VERSION);
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("JABCode JNI library loaded");
    return JNI_VERSION_1_6;
}

} // extern "C"
```

---

## 🎨 Java/Kotlin API Design

### Kotlin API (JABCode.kt)

```kotlin
package com.example.jabcode

import android.graphics.Bitmap
import com.example.jabcode.model.*

/**
 * Main JABCode API for Android
 * 
 * Thread-safe singleton for encoding and decoding JABCode symbols.
 */
object JABCode {
    
    init {
        System.loadLibrary("jabcode-jni")
    }
    
    /**
     * Get JABCode library version
     */
    @JvmStatic
    external fun nativeGetVersion(): String
    
    val version: String
        get() = nativeGetVersion()
    
    /**
     * Encode text data into a JABCode bitmap
     * 
     * @param data The text data to encode (UTF-8)
     * @param options Encoding options (colors, ECC level, etc.)
     * @return Bitmap containing the JABCode symbol
     * @throws JABCodeException if encoding fails
     * @throws IllegalArgumentException if parameters are invalid
     */
    @JvmStatic
    @JvmOverloads
    fun encode(
        data: String,
        options: EncodeOptions = EncodeOptions.DEFAULT
    ): Bitmap {
        require(data.isNotEmpty()) { "Data cannot be empty" }
        require(data.length <= MAX_DATA_LENGTH) { 
            "Data length exceeds maximum ($MAX_DATA_LENGTH)" 
        }
        
        return JABCodeEncoder.encode(data, options)
    }
    
    /**
     * Decode a JABCode symbol from a bitmap
     * 
     * @param bitmap The bitmap containing a JABCode symbol
     * @return Decoded result with data and metadata
     * @throws JABCodeException if decoding fails
     * @throws IllegalArgumentException if bitmap is invalid
     */
    @JvmStatic
    fun decode(bitmap: Bitmap): DecodeResult {
        require(bitmap.config == Bitmap.Config.ARGB_8888) {
            "Bitmap must be ARGB_8888 format"
        }
        require(bitmap.width >= MIN_IMAGE_SIZE && bitmap.height >= MIN_IMAGE_SIZE) {
            "Bitmap too small (minimum ${MIN_IMAGE_SIZE}x${MIN_IMAGE_SIZE})"
        }
        
        return JABCodeDecoder.decode(bitmap)
    }
    
    companion object {
        private const val MAX_DATA_LENGTH = 65536 // Reasonable limit
        private const val MIN_IMAGE_SIZE = 50     // Minimum detectable size
    }
}
```

### Encoder Wrapper (JABCodeEncoder.kt)

```kotlin
package com.example.jabcode

import android.graphics.Bitmap
import android.graphics.Color
import com.example.jabcode.model.*
import kotlinx.coroutines.*
import java.nio.ByteBuffer

/**
 * JABCode encoder with advanced features
 */
object JABCodeEncoder {
    
    @JvmStatic
    external fun nativeEncode(
        data: String,
        colorNumber: Int,
        symbolNumber: Int,
        eccLevel: Int,
        moduleSize: Int
    ): ByteArray
    
    /**
     * Synchronous encoding
     */
    fun encode(data: String, options: EncodeOptions): Bitmap {
        val byteArray = nativeEncode(
            data = data,
            colorNumber = options.colorNumber,
            symbolNumber = options.symbolNumber,
            eccLevel = options.eccLevel,
            moduleSize = options.moduleSize
        )
        
        return byteArrayToBitmap(byteArray)
    }
    
    /**
     * Asynchronous encoding with coroutines
     */
    suspend fun encodeAsync(
        data: String,
        options: EncodeOptions = EncodeOptions.DEFAULT
    ): Bitmap = withContext(Dispatchers.Default) {
        encode(data, options)
    }
    
    /**
     * Batch encoding for multiple codes
     */
    suspend fun encodeBatch(
        dataList: List<String>,
        options: EncodeOptions = EncodeOptions.DEFAULT
    ): List<Bitmap> = coroutineScope {
        dataList.map { data ->
            async(Dispatchers.Default) {
                encode(data, options)
            }
        }.awaitAll()
    }
    
    /**
     * Convert native byte array to Android Bitmap
     */
    private fun byteArrayToBitmap(data: ByteArray): Bitmap {
        val buffer = ByteBuffer.wrap(data)
        
        // Extract dimensions
        val width = buffer.int
        val height = buffer.int
        
        // Create bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Copy pixel data
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val r = buffer.get().toInt() and 0xFF
            val g = buffer.get().toInt() and 0xFF
            val b = buffer.get().toInt() and 0xFF
            val a = buffer.get().toInt() and 0xFF
            pixels[i] = Color.argb(a, r, g, b)
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
```

### Data Models (EncodeOptions.kt)

```kotlin
package com.example.jabcode.model

import androidx.annotation.IntRange

/**
 * Options for JABCode encoding
 */
data class EncodeOptions(
    @IntRange(from = 4, to = 256)
    val colorNumber: Int = 8,
    
    @IntRange(from = 1, to = 61)
    val symbolNumber: Int = 1,
    
    @IntRange(from = 0, to = 7)
    val eccLevel: Int = 3,
    
    @IntRange(from = 1, to = 100)
    val moduleSize: Int = 12
) {
    init {
        require(colorNumber in VALID_COLORS) {
            "Color number must be one of: ${VALID_COLORS.joinToString()}"
        }
        require(eccLevel in 0..7) {
            "ECC level must be between 0 and 7"
        }
        require(symbolNumber in 1..61) {
            "Symbol number must be between 1 and 61"
        }
        require(moduleSize in 1..100) {
            "Module size must be between 1 and 100"
        }
    }
    
    companion object {
        val VALID_COLORS = setOf(4, 8, 16, 32, 64, 128, 256)
        
        val DEFAULT = EncodeOptions(
            colorNumber = 8,
            symbolNumber = 1,
            eccLevel = 3,
            moduleSize = 12
        )
        
        val HIGH_DENSITY = EncodeOptions(
            colorNumber = 256,
            symbolNumber = 1,
            eccLevel = 5,
            moduleSize = 8
        )
        
        val HIGH_RELIABILITY = EncodeOptions(
            colorNumber = 8,
            symbolNumber = 1,
            eccLevel = 7,
            moduleSize = 16
        )
    }
}
```

### Exception Handling (JABCodeException.kt)

```kotlin
package com.example.jabcode.model

/**
 * Exception thrown by JABCode operations
 */
class JABCodeException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    enum class ErrorCode {
        ENCODE_FAILED,
        DECODE_FAILED,
        INVALID_PARAMETER,
        OUT_OF_MEMORY,
        DETECTION_FAILED,
        UNSUPPORTED_FORMAT
    }
    
    var errorCode: ErrorCode? = null
        private set
    
    companion object {
        fun encodeFailed(message: String) = JABCodeException(message).apply {
            errorCode = ErrorCode.ENCODE_FAILED
        }
        
        fun decodeFailed(message: String) = JABCodeException(message).apply {
            errorCode = ErrorCode.DECODE_FAILED
        }
        
        fun detectionFailed(message: String) = JABCodeException(message).apply {
            errorCode = ErrorCode.DETECTION_FAILED
        }
    }
}
```

---

## 📷 Camera Integration

### CameraX Integration (CameraManager.kt)

```kotlin
package com.example.jabcode.camera

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.jabcode.JABCode
import com.example.jabcode.model.DecodeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Camera manager for real-time JABCode scanning
 */
class JABCodeCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private val _scanResult = MutableStateFlow<DecodeResult?>(null)
    val scanResult: StateFlow<DecodeResult?> = _scanResult
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    
    /**
     * Start camera preview and scanning
     */
    suspend fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProvider = cameraProviderFuture.get()
        
        bindCamera(previewView)
    }
    
    private fun bindCamera(previewView: PreviewView) {
        val provider = cameraProvider ?: return
        
        // Unbind previous use cases
        provider.unbindAll()
        
        // Preview use case
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        
        // Image analysis use case
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(android.util.Size(1280, 720))
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, JABCodeImageAnalyzer { result ->
                    _scanResult.value = result
                    if (result != null) {
                        _isScanning.value = false
                    }
                })
            }
        
        // Select back camera
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            _isScanning.value = true
        } catch (e: Exception) {
            _isScanning.value = false
            throw e
        }
    }
    
    /**
     * Stop camera and release resources
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
        _isScanning.value = false
    }
    
    /**
     * Enable/disable torch
     */
    fun setTorchEnabled(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }
    
    /**
     * Release resources
     */
    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
```

### Image Analyzer (ImageAnalyzer.kt)

```kotlin
package com.example.jabcode.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.jabcode.JABCode
import com.example.jabcode.model.DecodeResult
import java.io.ByteArrayOutputStream

/**
 * Analyzer for real-time JABCode detection
 */
class JABCodeImageAnalyzer(
    private val onResult: (DecodeResult?) -> Unit
) : ImageAnalysis.Analyzer {
    
    private var lastAnalysisTime = 0L
    private val analysisIntervalMs = 500L // Throttle to 2 fps
    
    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime < analysisIntervalMs) {
            image.close()
            return
        }
        lastAnalysisTime = currentTime
        
        try {
            val bitmap = imageToBitmap(image)
            if (bitmap != null) {
                try {
                    val result = JABCode.decode(bitmap)
                    onResult(result)
                } catch (e: Exception) {
                    // Decoding failed, continue scanning
                    onResult(null)
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            image.close()
        }
    }
    
    private fun imageToBitmap(image: ImageProxy): Bitmap? {
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
        val imageBytes = out.toByteArray()
        
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}
```

---

## 🧠 Memory Management

### Best Practices

```kotlin
// 1. Use try-with-resources for bitmaps
fun processBitmap(data: String) {
    var bitmap: Bitmap? = null
    try {
        bitmap = JABCode.encode(data)
        // Use bitmap...
    } finally {
        bitmap?.recycle()
    }
}

// 2. Limit bitmap size
fun createScaledBitmap(original: Bitmap, maxSize: Int): Bitmap {
    val ratio = Math.min(
        maxSize.toFloat() / original.width,
        maxSize.toFloat() / original.height
    )
    
    if (ratio >= 1.0f) return original
    
    val width = (original.width * ratio).toInt()
    val height = (original.height * ratio).toInt()
    
    return Bitmap.createScaledBitmap(original, width, height, true).also {
        if (it != original) original.recycle()
    }
}

// 3. Use BitmapFactory options for large images
fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap {
    return BitmapFactory.Options().run {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, this)
        
        inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)
        inJustDecodeBounds = false
        
        BitmapFactory.decodeFile(path, this)
    }
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1
    
    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        
        while (halfHeight / inSampleSize >= reqHeight &&
               halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    
    return inSampleSize
}
```

---

## 🧵 Threading Model

### Coroutine-Based Async API

```kotlin
class JABCodeViewModel : ViewModel() {
    
    private val _encodeState = MutableStateFlow<EncodeState>(EncodeState.Idle)
    val encodeState: StateFlow<EncodeState> = _encodeState
    
    sealed class EncodeState {
        object Idle : EncodeState()
        object Loading : EncodeState()
        data class Success(val bitmap: Bitmap) : EncodeState()
        data class Error(val message: String) : EncodeState()
    }
    
    fun encodeData(data: String, options: EncodeOptions = EncodeOptions.DEFAULT) {
        viewModelScope.launch {
            _encodeState.value = EncodeState.Loading
            
            try {
                val bitmap = JABCodeEncoder.encodeAsync(data, options)
                _encodeState.value = EncodeState.Success(bitmap)
            } catch (e: Exception) {
                _encodeState.value = EncodeState.Error(
                    e.message ?: "Encoding failed"
                )
            }
        }
    }
}
```

---

## 🛡️ ProGuard/R8 Configuration

### proguard-rules.pro

```proguard
# JABCode native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JABCode API classes
-keep class com.example.jabcode.JABCode { *; }
-keep class com.example.jabcode.JABCodeEncoder { *; }
-keep class com.example.jabcode.JABCodeDecoder { *; }

# Keep model classes
-keep class com.example.jabcode.model.** { *; }

# Keep exception for proper stack traces
-keep class com.example.jabcode.model.JABCodeException { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
```

---

[← Back to Index](index.md) | [iOS Integration →](ios-integration.md) | [Performance Optimization →](performance-optimization.md)
