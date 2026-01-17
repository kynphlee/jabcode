# Troubleshooting Guide for Mobile JABCode

**Common issues and solutions for Android and iOS development**

[← Back to Index](index.md)

---

## 📋 Table of Contents

1. [Build Issues](#build-issues)
2. [Encoding Problems](#encoding-problems)
3. [Decoding Failures](#decoding-failures)
4. [Camera Integration Issues](#camera-integration-issues)
5. [Memory Problems](#memory-problems)
6. [Performance Issues](#performance-issues)
7. [Platform-Specific Issues](#platform-specific-issues)
8. [Debugging Tools](#debugging-tools)

---

## 🔨 Build Issues

### Android: Native Library Not Found

**Symptom:**
```
java.lang.UnsatisfiedLinkError: dlopen failed: library "libjabcode-jni.so" not found
```

**Causes & Solutions:**

1. **Missing ABI filters**
   ```kotlin
   // build.gradle.kts
   android {
       defaultConfig {
           ndk {
               // Add all required ABIs
               abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
           }
       }
   }
   ```

2. **Library not in correct location**
   ```bash
   # Verify library exists
   unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libjabcode
   
   # Should show:
   # lib/arm64-v8a/libjabcode-jni.so
   # lib/armeabi-v7a/libjabcode-jni.so
   # etc.
   ```

3. **Incorrect library name in System.loadLibrary()**
   ```kotlin
   // ❌ Wrong
   System.loadLibrary("libjabcode-jni")
   
   // ✅ Correct (omit 'lib' prefix and '.so' suffix)
   System.loadLibrary("jabcode-jni")
   ```

4. **CMake not finding sources**
   ```cmake
   # CMakeLists.txt - use absolute paths
   set(JABCODE_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/jabcode)
   
   file(GLOB JABCODE_SOURCES
       "${JABCODE_SRC_DIR}/*.c"
   )
   ```

### Android: CMake Build Errors

**Symptom:**
```
Build command failed: cmake --build ... returned non-zero exit code
```

**Solutions:**

1. **Clean and rebuild**
   ```bash
   ./gradlew clean
   rm -rf .cxx/
   ./gradlew assembleDebug
   ```

2. **Check NDK version compatibility**
   ```kotlin
   // build.gradle.kts - specify exact NDK version
   android {
       ndkVersion = "26.1.10909125"
   }
   ```

3. **Verify C standard**
   ```cmake
   # CMakeLists.txt
   set(CMAKE_C_STANDARD 99)
   set(CMAKE_C_STANDARD_REQUIRED ON)
   ```

### iOS: Module Not Found

**Symptom:**
```
No such module 'CJABCode'
```

**Solutions:**

1. **Check module.modulemap**
   ```modulemap
   // Sources/CJABCode/include/module.modulemap
   module CJABCode [system] {
       header "jabcode.h"
       export *
   }
   ```

2. **Verify Swift settings**
   ```swift
   // Package.swift
   .target(
       name: "JABCode",
       dependencies: ["CJABCode"],
       swiftSettings: [
           .define("DEBUG", .when(configuration: .debug))
       ]
   )
   ```

3. **Check header search paths**
   ```bash
   # In Xcode Build Settings
   HEADER_SEARCH_PATHS = $(SRCROOT)/Sources/CJABCode/include
   SWIFT_INCLUDE_PATHS = $(SRCROOT)/Sources/CJABCode/include
   ```

### iOS: Linker Errors

**Symptom:**
```
Undefined symbols for architecture arm64:
  "_createEncode", referenced from:
```

**Solutions:**

1. **Add C sources to target**
   ```swift
   // Package.swift
   .target(
       name: "CJABCode",
       sources: [
           "encoder.c",
           "decoder.c",
           "ldpc.c"
           // ... all source files
       ]
   )
   ```

2. **Check library dependencies**
   ```swift
   // Package.swift
   .target(
       name: "CJABCode",
       linkerSettings: [
           .linkedLibrary("png"),
           .linkedFramework("Accelerate")
       ]
   )
   ```

3. **Verify function declarations**
   ```c
   // In header file - must use extern "C" for C++
   #ifdef __cplusplus
   extern "C" {
   #endif
   
   jab_encode* createEncode(jab_int32 color_number, jab_int32 symbol_number);
   
   #ifdef __cplusplus
   }
   #endif
   ```

---

## 🎨 Encoding Problems

### Error: "Encoding failed with code: 1"

**Meaning:** Out of memory

**Solutions:**

```kotlin
// Android - Reduce memory usage
val options = EncodeOptions(
    moduleSize = 8,  // Smaller module size
    colorNumber = 8  // Fewer colors
)

// Or increase heap size in AndroidManifest.xml
<application
    android:largeHeap="true"
    ...>
```

```swift
// iOS - Use autoreleasepool
func encodeLarge(_ data: String) throws -> UIImage {
    try autoreleasepool {
        try JABCode.shared.encode(data)
    }
}
```

### Error: "Data exceeds maximum size"

**Symptom:**
```
JABCodeException: Message does not fit into one symbol
```

**Solutions:**

1. **Use multi-symbol encoding**
   ```kotlin
   val options = EncodeOptions(
       symbolNumber = 4,  // Split into 4 symbols
       colorNumber = 256  // Use more colors for higher capacity
   )
   ```

2. **Compress data before encoding**
   ```kotlin
   fun compressAndEncode(data: String): Bitmap {
       val compressed = ByteArrayOutputStream().use { baos ->
           GZIPOutputStream(baos).use { gzip ->
               gzip.write(data.toByteArray())
           }
           baos.toByteArray()
       }
       
       // Encode compressed data
       return JABCode.encode(Base64.encodeToString(compressed, Base64.NO_WRAP))
   }
   ```

3. **Split data manually**
   ```swift
   func encodeChunks(_ data: String, chunkSize: Int) throws -> [UIImage] {
       let chunks = data.chunked(into: chunkSize)
       return try chunks.map { chunk in
           try JABCode.shared.encode(String(chunk))
       }
   }
   
   extension String {
       func chunked(into size: Int) -> [Substring] {
           stride(from: 0, to: count, by: size).map {
               let start = index(startIndex, offsetBy: $0)
               let end = index(start, offsetBy: size, limitedBy: endIndex) ?? endIndex
               return self[start..<end]
           }
       }
   }
   ```

### Invalid Color Number

**Symptom:**
```
IllegalArgumentException: Color number must be one of: [4, 8, 16, 32, 64, 128, 256]
```

**Solution:**
```kotlin
// Use only valid color numbers
val validColors = setOf(4, 8, 16, 32, 64, 128, 256)

fun validateOptions(colors: Int): Int {
    return when {
        colors in validColors -> colors
        colors < 4 -> 4
        colors in 5..8 -> 8
        colors in 9..16 -> 16
        colors in 17..32 -> 32
        colors in 33..64 -> 64
        colors in 65..128 -> 128
        else -> 256
    }
}
```

---

## 🔍 Decoding Failures

### No JABCode Detected

**Symptom:**
```
JABCodeException: Decoding failed: code -1
```

**Common Causes:**

1. **Poor image quality**
   ```kotlin
   // Pre-process image before decoding
   fun enhanceImage(bitmap: Bitmap): Bitmap {
       // Increase contrast
       val matrix = ColorMatrix().apply {
           set(floatArrayOf(
               1.5f, 0f, 0f, 0f, -64f,
               0f, 1.5f, 0f, 0f, -64f,
               0f, 0f, 1.5f, 0f, -64f,
               0f, 0f, 0f, 1f, 0f
           ))
       }
       
       val paint = Paint().apply {
           colorFilter = ColorMatrixColorFilter(matrix)
       }
       
       val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
       Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
       return result
   }
   ```

2. **Image too small/large**
   ```swift
   func normalizeImageSize(_ image: UIImage) -> UIImage {
       let maxDimension: CGFloat = 2048
       let minDimension: CGFloat = 200
       
       let size = image.size
       let maxCurrent = max(size.width, size.height)
       let minCurrent = min(size.width, size.height)
       
       var scale: CGFloat = 1.0
       
       if maxCurrent > maxDimension {
           scale = maxDimension / maxCurrent
       } else if minCurrent < minDimension {
           scale = minDimension / minCurrent
       }
       
       if scale != 1.0 {
           let newSize = CGSize(
               width: size.width * scale,
               height: size.height * scale
           )
           
           UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
           image.draw(in: CGRect(origin: .zero, size: newSize))
           let scaledImage = UIGraphicsGetImageFromCurrentImageContext()
           UIGraphicsEndImageContext()
           
           return scaledImage ?? image
       }
       
       return image
   }
   ```

3. **Incorrect rotation**
   ```kotlin
   fun autoRotateImage(bitmap: Bitmap): Bitmap {
       // Try all 4 orientations
       for (degrees in arrayOf(0, 90, 180, 270)) {
           val rotated = if (degrees == 0) bitmap else {
               val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
               Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
           }
           
           try {
               val result = JABCode.decode(rotated)
               if (rotated != bitmap) rotated.recycle()
               return result
           } catch (e: JABCodeException) {
               if (rotated != bitmap) rotated.recycle()
               continue
           }
       }
       
       throw JABCodeException("Could not decode in any orientation")
   }
   ```

### Partial Decoding

**Symptom:** Some symbols decoded, others failed in multi-symbol code

**Solutions:**

1. **Improve lighting conditions**
   ```kotlin
   // Enable torch for better detection
   camera.cameraControl.enableTorch(true)
   ```

2. **Increase exposure time**
   ```swift
   // iOS - Adjust camera exposure
   let device = AVCaptureDevice.default(for: .video)
   try? device?.lockForConfiguration()
   
   if device?.isExposureModeSupported(.custom) == true {
       let duration = CMTime(seconds: 0.1, preferredTimescale: 1000)
       device?.setExposureModeCustom(duration: duration, iso: AVCaptureDevice.currentISO) { _ in }
   }
   
   device?.unlockForConfiguration()
   ```

### LDPC Decoding Errors

**Symptom:**
```
LDPC decoding failed: Too many errors
```

**Solutions:**

1. **Use higher error correction**
   ```kotlin
   // When encoding, use higher ECC level
   val options = EncodeOptions(
       eccLevel = 7  // Maximum error correction
   )
   ```

2. **Multiple decode attempts**
   ```swift
   func robustDecode(_ image: UIImage, maxAttempts: Int = 3) async throws -> DecodeResult {
       var lastError: Error?
       
       for attempt in 0..<maxAttempts {
           do {
               // Add slight preprocessing variation each attempt
               let processed = preprocessImage(image, variation: attempt)
               return try await JABCode.shared.decode(processed)
           } catch {
               lastError = error
               try await Task.sleep(nanoseconds: 100_000_000) // 100ms delay
           }
       }
       
       throw lastError ?? JABCodeError.decodingFailed(code: -1)
   }
   ```

---

## 📷 Camera Integration Issues

### Camera Permission Denied

**Android:**
```kotlin
// Check and request permission
when {
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
        == PackageManager.PERMISSION_GRANTED -> {
        // Permission granted
        startCamera()
    }
    shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
        // Show rationale dialog
        AlertDialog.Builder(context)
            .setMessage("Camera permission is required for scanning")
            .setPositiveButton("OK") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .show()
    }
    else -> {
        // Request permission
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
}
```

**iOS:**
```swift
// Info.plist must include:
// NSCameraUsageDescription: "We need camera access to scan JABCodes"

func requestCameraPermission() async -> Bool {
    switch AVCaptureDevice.authorizationStatus(for: .video) {
    case .authorized:
        return true
    case .notDetermined:
        return await AVCaptureDevice.requestAccess(for: .video)
    case .denied, .restricted:
        // Show settings alert
        await showSettingsAlert()
        return false
    @unknown default:
        return false
    }
}

@MainActor
func showSettingsAlert() async {
    let alert = UIAlertController(
        title: "Camera Access Required",
        message: "Please enable camera access in Settings",
        preferredStyle: .alert
    )
    alert.addAction(UIAlertAction(title: "Settings", style: .default) { _ in
        UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!)
    })
    alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
    
    // Present alert
}
```

### Camera Preview Not Showing

**Android:**
```kotlin
// Ensure preview is bound correctly
val preview = Preview.Builder().build()
    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

val cameraProvider = ProcessCameraProvider.getInstance(context).get()
try {
    cameraProvider.unbindAll()
    cameraProvider.bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_BACK_CAMERA,
        preview,
        imageAnalysis
    )
} catch (e: Exception) {
    Log.e("Camera", "Binding failed", e)
}
```

**iOS:**
```swift
// Ensure preview layer is properly sized
override func viewDidLayoutSubviews() {
    super.viewDidLayoutSubviews()
    previewLayer.frame = view.bounds
}

// Set correct video gravity
previewLayer.videoGravity = .resizeAspectFill
```

### Low Frame Rate

**Solutions:**

1. **Reduce analysis resolution**
   ```kotlin
   val imageAnalysis = ImageAnalysis.Builder()
       .setTargetResolution(Size(640, 480))  // Lower resolution
       .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
       .build()
   ```

2. **Throttle processing**
   ```swift
   actor FrameThrottler {
       private var lastProcessTime = Date.distantPast
       private let minimumInterval: TimeInterval = 0.5
       
       func shouldProcess() -> Bool {
           let now = Date()
           if now.timeIntervalSince(lastProcessTime) >= minimumInterval {
               lastProcessTime = now
               return true
           }
           return false
       }
   }
   ```

---

## 💾 Memory Problems

### OutOfMemoryError (Android)

**Solutions:**

1. **Enable large heap**
   ```xml
   <!-- AndroidManifest.xml -->
   <application
       android:largeHeap="true"
       ...>
   ```

2. **Recycle bitmaps aggressively**
   ```kotlin
   class BitmapPool(private val maxSize: Int = 10) {
       private val pool = LinkedList<Bitmap>()
       
       fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap {
           synchronized(pool) {
               val iter = pool.iterator()
               while (iter.hasNext()) {
                   val bitmap = iter.next()
                   if (!bitmap.isRecycled && 
                       bitmap.width == width && 
                       bitmap.height == height &&
                       bitmap.config == config) {
                       iter.remove()
                       return bitmap
                   }
               }
           }
           return Bitmap.createBitmap(width, height, config)
       }
       
       fun recycle(bitmap: Bitmap) {
           synchronized(pool) {
               if (pool.size < maxSize) {
                   pool.add(bitmap)
               } else {
                   bitmap.recycle()
               }
           }
       }
   }
   ```

3. **Use RGB_565 when possible**
   ```kotlin
   // Uses half the memory of ARGB_8888
   val options = BitmapFactory.Options().apply {
       inPreferredConfig = Bitmap.Config.RGB_565
   }
   ```

### Memory Leaks (iOS)

**Detection:**
```swift
// Use weak references in closures
class Scanner {
    func startScanning(onResult: @escaping (DecodeResult) -> Void) {
        cameraManager.onFrame = { [weak self] image in
            guard let self = self else { return }
            
            if let result = try? self.decode(image) {
                onResult(result)
            }
        }
    }
}

// Use deinit to verify cleanup
deinit {
    print("Scanner deallocated")
}
```

**Instruments Leaks Tool:**
```bash
# Run in Xcode: Product > Profile > Leaks
# Or command line:
xcrun xctrace record --template 'Leaks' \
    --device <device-id> \
    --launch com.example.jabcode
```

---

## ⚡ Performance Issues

### Slow Encoding

**Diagnosis:**
```kotlin
// Android - Profile with Trace API
Trace.beginSection("JABCode.encode")
try {
    val bitmap = JABCode.encode(data, options)
    return bitmap
} finally {
    Trace.endSection()
}

// View trace: adb shell atrace
```

**Solutions:**

1. **Reduce complexity**
   ```kotlin
   // Use fewer colors for faster encoding
   val fastOptions = EncodeOptions(
       colorNumber = 8,     // vs 256
       moduleSize = 12      // vs 8
   )
   ```

2. **Pre-allocate buffers** (native code)
   ```c
   // Reuse buffers across encode calls
   static jab_byte* g_work_buffer = NULL;
   static size_t g_buffer_size = 0;
   
   jab_byte* getWorkBuffer(size_t required_size) {
       if (g_buffer_size < required_size) {
           free(g_work_buffer);
           g_work_buffer = malloc(required_size);
           g_buffer_size = required_size;
       }
       return g_work_buffer;
   }
   ```

### Battery Drain

**Solutions:**

1. **Limit camera usage**
   ```swift
   // Stop camera when app enters background
   func sceneDidEnterBackground(_ scene: UIScene) {
       cameraManager.stopSession()
   }
   
   func sceneWillEnterForeground(_ scene: UIScene) {
       cameraManager.startSession()
   }
   ```

2. **Use motion detection**
   ```kotlin
   // Only process when device is stable
   if (motionDetector.isStable()) {
       processFrame(image)
   } else {
       // Skip processing during motion
   }
   ```

---

## 🐛 Debugging Tools

### Enable Native Debugging

**Android:**
```kotlin
// build.gradle.kts
android {
    buildTypes {
        debug {
            isJniDebuggable = true
            isDebuggable = true
        }
    }
}

// Set breakpoint in native code and attach debugger
// Run > Debug 'app' > Select 'Native' debugger type
```

**iOS:**
```bash
# Enable address sanitizer
# Edit Scheme > Run > Diagnostics > Address Sanitizer

# Enable malloc stack logging
# Edit Scheme > Run > Diagnostics > Malloc Stack

# View console logs
log stream --predicate 'subsystem == "com.example.jabcode"' --level debug
```

### Logging Best Practices

```kotlin
// Android - Structured logging
object JABCodeLogger {
    private const val TAG = "JABCode"
    
    fun logEncode(dataLength: Int, options: EncodeOptions, timeMs: Long) {
        Log.d(TAG, "Encode: length=$dataLength, colors=${options.colorNumber}, time=${timeMs}ms")
    }
    
    fun logDecode(width: Int, height: Int, success: Boolean, timeMs: Long) {
        Log.d(TAG, "Decode: ${width}x${height}, success=$success, time=${timeMs}ms")
    }
}
```

```swift
// iOS - OSLog for production
import os.log

extension JABCode {
    private static let log = OSLog(subsystem: "com.example.jabcode", category: "Core")
    
    static func logEncode(length: Int, options: EncodeOptions, duration: TimeInterval) {
        os_log(.debug, log: log, "Encode: length=%d, colors=%d, duration=%.2fms", 
               length, options.colorNumber, duration * 1000)
    }
}
```

---

## 📞 Getting Help

### Before Asking for Help

1. **Check logs** for error messages
2. **Test with sample data** to isolate the issue
3. **Verify library version** is up-to-date
4. **Review this troubleshooting guide** thoroughly
5. **Search existing issues** on GitHub

### Providing Debug Information

When reporting issues, include:

```
Platform: Android 13 / iOS 16.0
Device: Pixel 7 / iPhone 14
Library Version: 2.0.0
Error Message: [paste exact error]
Sample Code: [minimal reproduction]
Expected: [what should happen]
Actual: [what actually happened]
```

---

[← Back to Index](index.md) | [Android Integration](android-integration.md) | [iOS Integration](ios-integration.md) | [Performance Optimization](performance-optimization.md)
