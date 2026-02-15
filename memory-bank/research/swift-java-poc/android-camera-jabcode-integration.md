# Android Camera Integration for JABCode Reading

## Overview

This document provides best practices for integrating camera-based JABCode scanning into Android applications using the JABCode mobile library.

---

## Table of Contents

1. [Architecture Options](#architecture-options)
2. [CameraX Integration (Recommended)](#camerax-integration-recommended)
3. [Image Processing Pipeline](#image-processing-pipeline)
4. [Performance Optimization](#performance-optimization)
5. [UI/UX Best Practices](#uiux-best-practices)
6. [Error Handling](#error-handling)
7. [ML Integration Options](#ml-integration-options)
8. [CameraX Hardware Control](#camerax-hardware-control)
9. [Implementation Checklist](#implementation-checklist)

---

## Architecture Options

### Option A: CameraX + ImageAnalysis (Recommended)

| Aspect | Details |
|--------|---------|
| **API Level** | 21+ (with backports) |
| **Complexity** | Low |
| **Lifecycle** | Automatic |
| **Performance** | Excellent |

**Pros:**
- Lifecycle-aware, handles configuration changes
- Built-in image analysis use case
- Consistent behavior across devices
- Google-maintained, actively updated

**Cons:**
- Additional dependency (~1.5 MB)

### Option B: Camera2 API

| Aspect | Details |
|--------|---------|
| **API Level** | 21+ |
| **Complexity** | High |
| **Lifecycle** | Manual |
| **Performance** | Excellent |

**Pros:**
- Full control over camera hardware
- No additional dependencies

**Cons:**
- Complex state machine management
- Device-specific quirks
- Manual lifecycle handling

### Option C: Deprecated Camera API

**Not recommended.** Deprecated in API 21, removed in API 30+.

---

## CameraX Integration (Recommended)

### Dependencies

```kotlin
// build.gradle.kts
dependencies {
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:\$cameraxVersion")
    implementation("androidx.camera:camera-camera2:\$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:\$cameraxVersion")
    implementation("androidx.camera:camera-view:\$cameraxVersion")
    
    // JABCode library
    implementation(project(":library"))
}
```

### Permissions

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="true" />
```

### Layout

```xml
<!-- activity_scanner.xml -->
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.camera.view.PreviewView
        android:id="@+id/previewView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- Overlay for scan region indicator -->
    <View
        android:id="@+id/scanOverlay"
        android:layout_width="250dp"
        android:layout_height="250dp"
        android:layout_gravity="center"
        android:background="@drawable/scan_frame" />

    <TextView
        android:id="@+id/resultText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:background="#CC000000"
        android:padding="16dp"
        android:textColor="@android:color/white"
        android:textSize="16sp" />

</FrameLayout>
```

### Scanner Activity

```kotlin
class JABCodeScannerActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var resultText: TextView
    
    private val analyzer = JABCodeAnalyzer { result ->
        runOnUiThread {
            resultText.text = "Decoded: \$result"
            // Optionally pause scanning after success
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)
        
        previewView = findViewById(R.id.previewView)
        resultText = findViewById(R.id.resultText)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Preview use case
            val preview = Preview.Builder()
                .setTargetResolution(Size(1280, 720))
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            // Image analysis use case
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, analyzer)
                }
            
            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("JABCodeScanner", "Camera binding failed", e)
            }
            
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
    
    private fun allPermissionsGranted() = 
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == 
            PackageManager.PERMISSION_GRANTED
    
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS
        )
    }
    
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}
```

### JABCode Analyzer

```kotlin
class JABCodeAnalyzer(
    private val onDecoded: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L
    private val throttleMs = 100L // Analyze at most 10 fps
    
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        
        // Throttle analysis
        if (currentTimestamp - lastAnalyzedTimestamp < throttleMs) {
            imageProxy.close()
            return
        }
        lastAnalyzedTimestamp = currentTimestamp
        
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                decodeJABCode(bitmap)
            }
        } finally {
            imageProxy.close()
        }
    }
    
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        // For RGBA_8888 format (configured in ImageAnalysis.Builder)
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        
        val bitmap = Bitmap.createBitmap(
            imageProxy.width, 
            imageProxy.height, 
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        
        // Handle rotation
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }
    
    private fun decodeJABCode(bitmap: Bitmap) {
        // Extract center region for faster processing
        val centerCrop = extractCenterRegion(bitmap, 0.6f)
        
        // Convert bitmap to RGBA byte array
        val width = centerCrop.width
        val height = centerCrop.height
        val pixels = IntArray(width * height)
        centerCrop.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val rgbaBytes = ByteArray(width * height * 4)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            rgbaBytes[i * 4] = ((pixel shr 16) and 0xFF).toByte()     // R
            rgbaBytes[i * 4 + 1] = ((pixel shr 8) and 0xFF).toByte()  // G
            rgbaBytes[i * 4 + 2] = (pixel and 0xFF).toByte()          // B
            rgbaBytes[i * 4 + 3] = ((pixel shr 24) and 0xFF).toByte() // A
        }
        
        // Call native decoder
        val result = JABCodeMobile.decodeFromCamera(rgbaBytes, width, height)
        
        if (result != null) {
            onDecoded(result)
        }
    }
    
    private fun extractCenterRegion(bitmap: Bitmap, ratio: Float): Bitmap {
        val cropWidth = (bitmap.width * ratio).toInt()
        val cropHeight = (bitmap.height * ratio).toInt()
        val x = (bitmap.width - cropWidth) / 2
        val y = (bitmap.height - cropHeight) / 2
        return Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
    }
}
```

---

## Image Processing Pipeline

### Frame Processing Flow

```
Camera Frame (YUV_420_888 or RGBA_8888)
    │
    ▼
┌─────────────────────────────┐
│  1. Format Conversion       │  YUV → RGB if needed
└─────────────────────────────┘
    │
    ▼
┌─────────────────────────────┐
│  2. Rotation Correction     │  Based on device orientation
└─────────────────────────────┘
    │
    ▼
┌─────────────────────────────┐
│  3. Center Crop (Optional)  │  Focus on scan region
└─────────────────────────────┘
    │
    ▼
┌─────────────────────────────┐
│  4. Downscale (Optional)    │  Reduce processing load
└─────────────────────────────┘
    │
    ▼
┌─────────────────────────────┐
│  5. JABCode Detection       │  Native library call
└─────────────────────────────┘
    │
    ▼
┌─────────────────────────────┐
│  6. Decode & Return Result  │  String or null
└─────────────────────────────┘
```

### YUV to RGB Conversion

If using `YUV_420_888` format (default for Camera2/CameraX):

```kotlin
private fun yuvToRgb(imageProxy: ImageProxy): Bitmap {
    val yBuffer = imageProxy.planes[0].buffer
    val uBuffer = imageProxy.planes[1].buffer
    val vBuffer = imageProxy.planes[2].buffer
    
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, 
        imageProxy.width, imageProxy.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(
        Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}
```

**Better approach:** Use RenderScript or native code for YUV→RGB conversion to avoid JPEG compression overhead.

---

## Performance Optimization

### 1. Frame Throttling

```kotlin
private val throttleMs = 100L // 10 fps max

override fun analyze(imageProxy: ImageProxy) {
    val now = System.currentTimeMillis()
    if (now - lastAnalyzedTimestamp < throttleMs) {
        imageProxy.close()
        return
    }
    lastAnalyzedTimestamp = now
    // ... process frame
}
```

### 2. Resolution Selection

| Resolution | Use Case | Processing Time |
|------------|----------|-----------------|
| 640×480 | Fast scanning, small codes | ~15ms |
| 1280×720 | Balanced | ~30ms |
| 1920×1080 | High detail, large codes | ~60ms |

**Recommendation:** Start with 1280×720, adjust based on testing.

### 3. Background Processing

```kotlin
// Use dedicated executor for analysis
private val analysisExecutor = Executors.newSingleThreadExecutor()

// Or use coroutines
private val analysisScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

private fun analyzeAsync(bitmap: Bitmap) {
    analysisScope.launch {
        val result = withContext(Dispatchers.Default) {
            JABCodeMobile.decodeFromCamera(...)
        }
        if (result != null) {
            withContext(Dispatchers.Main) {
                onDecoded(result)
            }
        }
    }
}
```

### 4. Memory Management

```kotlin
// Reuse bitmap buffer
private var reusableBitmap: Bitmap? = null

private fun getOrCreateBitmap(width: Int, height: Int): Bitmap {
    val existing = reusableBitmap
    return if (existing != null && 
               existing.width == width && 
               existing.height == height) {
        existing
    } else {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            reusableBitmap = it
        }
    }
}
```

### 5. Native Buffer Passing

Avoid copying data between Java and native:

```kotlin
// In JABCodeMobile.java - add direct buffer method
public static native String decodeFromDirectBuffer(
    ByteBuffer buffer, int width, int height);
```

```c
// In jabcode_jni.c
JNIEXPORT jstring JNICALL
Java_com_jabcode_JABCodeMobile_decodeFromDirectBuffer(
    JNIEnv *env, jclass clazz,
    jobject buffer, jint width, jint height) {
    
    // Get direct pointer - no copy!
    jbyte* data = (*env)->GetDirectBufferAddress(env, buffer);
    if (data == NULL) return NULL;
    
    // Decode directly from buffer
    // ...
}
```

---

## UI/UX Best Practices

### 1. Visual Feedback

```kotlin
// Scan region overlay
class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    private val cornerPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val rect = RectF(
            width * 0.15f, height * 0.25f,
            width * 0.85f, height * 0.75f
        )
        
        // Draw frame
        canvas.drawRect(rect, framePaint)
        
        // Draw corners
        val cornerLength = 40f
        // Top-left
        canvas.drawLine(rect.left, rect.top, rect.left + cornerLength, rect.top, cornerPaint)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + cornerLength, cornerPaint)
        // ... other corners
    }
}
```

### 2. Haptic Feedback on Success

```kotlin
private fun onDecodeSuccess(result: String) {
    // Vibrate
    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(100)
    }
    
    // Sound (optional)
    val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
}
```

### 3. Torch/Flash Control

```kotlin
private var camera: Camera? = null

private fun toggleTorch() {
    camera?.cameraControl?.enableTorch(
        camera?.cameraInfo?.torchState?.value != TorchState.ON
    )
}

// In bindToLifecycle:
camera = cameraProvider.bindToLifecycle(...)
```

### 4. Focus on Tap

```kotlin
previewView.setOnTouchListener { _, event ->
    if (event.action == MotionEvent.ACTION_UP) {
        val factory = previewView.meteringPointFactory
        val point = factory.createPoint(event.x, event.y)
        val action = FocusMeteringAction.Builder(point).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }
    true
}
```

---

## Error Handling

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| Camera permission denied | User rejected | Show rationale, request again |
| No camera available | Emulator or device issue | Check `hasSystemFeature()` |
| Low light conditions | Poor image quality | Enable torch, show hint |
| Code too small/far | Resolution mismatch | Prompt user to move closer |
| Blurry image | Motion or focus issue | Add focus-on-tap, stabilization hint |

### Graceful Degradation

```kotlin
class JABCodeAnalyzer(...) : ImageAnalysis.Analyzer {
    
    private var consecutiveFailures = 0
    private val maxFailures = 30 // ~3 seconds at 10fps
    
    override fun analyze(imageProxy: ImageProxy) {
        // ... decode attempt
        
        if (result != null) {
            consecutiveFailures = 0
            onDecoded(result)
        } else {
            consecutiveFailures++
            if (consecutiveFailures >= maxFailures) {
                onHint("Try moving closer or improving lighting")
                consecutiveFailures = 0
            }
        }
    }
}
```

---

## ML Integration Options

Machine learning can enhance JABCode scanning in challenging conditions. CameraX integrates seamlessly with ML frameworks.

### When ML Adds Value

| Scenario | ML Value | Native-Only Sufficient? |
|----------|----------|-------------------------|
| Controlled environment | Low | ✅ Yes |
| Variable lighting | Medium | ⚠️ Maybe |
| Small/distant codes | High | ❌ No |
| Mixed barcode types | High | ❌ No |
| Cluttered scenes | High | ❌ No |
| High-volume scanning | Medium | ⚠️ Maybe |

### ML Enhancement Options

| Use Case | ML Approach | Benefit | Complexity |
|----------|-------------|---------|------------|
| **Code Detection** | Object detection (YOLO/SSD) | Find JABCode region before decoding | Medium |
| **Image Enhancement** | Super-resolution | Improve low-res captures | High |
| **Blur Detection** | CNN classifier | Skip blurry frames, save processing | Low |
| **Lighting Correction** | Image-to-image model | Normalize uneven lighting | High |
| **Orientation Detection** | Pose estimation | Pre-rotate before decode | Medium |
| **Confidence Scoring** | Binary classifier | Predict decode success probability | Low |

### Architecture Patterns

#### Option A: Pre-filter Pipeline (Recommended for Detection)

```
Camera Frame → ML Detection → Crop Region → JABCode Decode
```

- ML finds the barcode region first
- Only decode cropped area (faster)
- Skip frames with no detection

```kotlin
class MLPrefilterAnalyzer(
    private val detector: ObjectDetector,
    private val onDecoded: (String) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        
        // Step 1: ML detection
        val detections = detector.detect(bitmap)
        
        if (detections.isNotEmpty()) {
            // Step 2: Crop to detected region
            val region = detections[0].boundingBox
            val cropped = Bitmap.createBitmap(bitmap, 
                region.left.toInt(), region.top.toInt(), 
                region.width().toInt(), region.height().toInt())
            
            // Step 3: JABCode decode on cropped region
            val result = JABCodeMobile.decodeFromCamera(cropped)
            if (result != null) onDecoded(result)
        }
        
        imageProxy.close()
    }
}
```

#### Option B: Parallel Pipeline (Fallback Enhancement)

```
Camera Frame ─┬─→ JABCode Decode (primary)
              └─→ ML Enhancement (if decode fails)
```

- Try native decode first (fast path)
- Fall back to ML-enhanced decode on failure

```kotlin
class ParallelAnalyzer(
    private val enhancer: ImageEnhancer,
    private val onDecoded: (String) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        
        // Fast path: try native decode
        var result = JABCodeMobile.decodeFromCamera(bitmap)
        
        if (result == null) {
            // Slow path: enhance then decode
            val enhanced = enhancer.enhance(bitmap)
            result = JABCodeMobile.decodeFromCamera(enhanced)
        }
        
        if (result != null) onDecoded(result)
        imageProxy.close()
    }
}
```

#### Option C: Confidence-Routed Pipeline

```
Camera Frame → ML Confidence → High? → Fast Decode
                             → Low?  → Enhanced Decode
```

- ML predicts decode difficulty
- Route to appropriate pipeline

### ML Kit Integration

Google ML Kit provides ready-to-use barcode scanning (for fallback/comparison):

```kotlin
// Dependencies
implementation("com.google.mlkit:barcode-scanning:17.2.0")

// Usage
val barcodeScanner = BarcodeScanning.getClient()

imageAnalysis.setAnalyzer(executor) { imageProxy ->
    val mediaImage = imageProxy.image ?: return@setAnalyzer
    val inputImage = InputImage.fromMediaImage(
        mediaImage, 
        imageProxy.imageInfo.rotationDegrees
    )
    
    barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            // ML Kit doesn't support JABCode, but can detect other formats
            // Use for hybrid scanning apps
        }
        .addOnCompleteListener { imageProxy.close() }
}
```

### TensorFlow Lite Integration

For custom JABCode detection model:

```kotlin
// Dependencies
implementation("org.tensorflow:tensorflow-lite:2.14.0")
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

// Custom detector
class JABCodeDetector(context: Context) {
    
    private val detector = ObjectDetector.createFromFileAndOptions(
        context,
        "jabcode_detector.tflite",
        ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(5)
            .setScoreThreshold(0.5f)
            .build()
    )
    
    fun detect(bitmap: Bitmap): List<Detection> {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        return detector.detect(tensorImage)
    }
    
    fun close() = detector.close()
}
```

### Training a JABCode Detector

To train a custom detection model:

1. **Dataset Creation**
   - Generate 1000+ JABCode images with various:
     - Color modes (4, 8, 16, 32, 64, 128)
     - Sizes and orientations
     - Backgrounds and lighting
   - Annotate bounding boxes (COCO or Pascal VOC format)

2. **Model Selection**
   - **MobileNet SSD** - Fast, good for mobile (5-10ms inference)
   - **EfficientDet-Lite** - Better accuracy, slightly slower (15-25ms)
   - **YOLOv8n** - Best accuracy/speed tradeoff (10-15ms)

3. **Training**
   ```bash
   # Using TensorFlow Object Detection API
   python model_main_tf2.py \
       --model_dir=training/ \
       --pipeline_config_path=ssd_mobilenet_v2.config
   ```

4. **Conversion**
   ```bash
   # Convert to TFLite
   tflite_convert \
       --saved_model_dir=saved_model/ \
       --output_file=jabcode_detector.tflite
   ```

### Recommendation

**Start simple, add ML if needed:**

1. **Phase 1:** Native-only implementation
2. **Phase 2:** Add blur detection (skip bad frames)
3. **Phase 3:** Add detection model (if cluttered scenes are common)
4. **Phase 4:** Add enhancement (if low-quality captures are common)

Most controlled scanning scenarios (retail, logistics) won't need ML. Consumer-facing apps with variable conditions benefit most.

---

## CameraX Hardware Control

CameraX provides good but not full hardware control. Here's what's available:

### Full Control (Direct API)

| Feature | API | Example |
|---------|-----|---------|
| **Auto Focus** | `CameraControl` | `cameraControl.startFocusAndMetering(action)` |
| **Torch/Flash** | `CameraControl` | `cameraControl.enableTorch(true)` |
| **Zoom** | `CameraControl` | `cameraControl.setZoomRatio(2.0f)` |
| **Exposure Compensation** | `CameraControl` | `cameraControl.setExposureCompensationIndex(2)` |
| **Focus/Metering Regions** | `FocusMeteringAction` | Custom AF/AE regions |

### Limited Control (Via Camera2Interop)

| Feature | Access Method | Notes |
|---------|---------------|-------|
| **Manual Focus Distance** | Camera2Interop | Requires interop escape hatch |
| **Manual Exposure (ISO/Shutter)** | Camera2Interop | Device-dependent support |
| **Manual White Balance** | Camera2Interop | Requires interop |
| **Frame Duration** | Camera2Interop | For precise timing control |

### Not Supported

| Feature | Alternative |
|---------|-------------|
| **RAW Capture** | Use Camera2 directly |
| **Burst Mode** | Limited support, use Camera2 for full control |
| **Custom Capture Pipelines** | Use Camera2 directly |

### Camera2Interop Example

When you need Camera2 features within CameraX:

```kotlin
val imageAnalysisBuilder = ImageAnalysis.Builder()

// Access Camera2 capture request options
Camera2Interop.Extender(imageAnalysisBuilder)
    .setCaptureRequestOption(
        CaptureRequest.CONTROL_AF_MODE,
        CaptureRequest.CONTROL_AF_MODE_OFF
    )
    .setCaptureRequestOption(
        CaptureRequest.LENS_FOCUS_DISTANCE,
        0.5f  // Manual focus distance in diopters
    )
    .setCaptureRequestOption(
        CaptureRequest.CONTROL_AE_MODE,
        CaptureRequest.CONTROL_AE_MODE_OFF
    )
    .setCaptureRequestOption(
        CaptureRequest.SENSOR_EXPOSURE_TIME,
        10000000L  // 10ms exposure
    )
    .setCaptureRequestOption(
        CaptureRequest.SENSOR_SENSITIVITY,
        400  // ISO 400
    )

val imageAnalysis = imageAnalysisBuilder.build()
```

### For JABCode Scanning

**What we need:**
- ✅ Auto focus (tap-to-focus)
- ✅ Torch control (low light)
- ✅ Zoom (distant codes)
- ✅ Frame analysis

**What we don't need:**
- ❌ Manual exposure
- ❌ RAW capture
- ❌ Custom pipelines

**Verdict:** CameraX provides everything needed for barcode scanning without requiring Camera2Interop.

---

## Implementation Checklist

### Phase 1: Basic Camera Integration
- [ ] Add CameraX dependencies
- [ ] Request camera permission
- [ ] Set up PreviewView
- [ ] Bind camera lifecycle

### Phase 2: JABCode Analysis
- [ ] Create ImageAnalysis.Analyzer
- [ ] Convert ImageProxy to bitmap/byte array
- [ ] Handle image rotation
- [ ] Call JABCodeMobile.decode()
- [ ] Throttle frame processing

### Phase 3: UI Polish
- [ ] Add scan region overlay
- [ ] Implement haptic feedback
- [ ] Add torch toggle button
- [ ] Implement tap-to-focus
- [ ] Show decode result

### Phase 4: Optimization
- [ ] Profile frame processing time
- [ ] Optimize resolution selection
- [ ] Implement center crop
- [ ] Consider direct buffer passing
- [ ] Test on low-end devices

### Phase 5: Error Handling
- [ ] Handle permission denial gracefully
- [ ] Add low-light detection
- [ ] Implement user hints
- [ ] Add retry logic
- [ ] Test edge cases

### Phase 6: ML Enhancement (Optional)
- [ ] Evaluate if ML is needed based on field testing
- [ ] Add blur detection to skip bad frames
- [ ] Consider detection model for cluttered scenes
- [ ] Implement confidence-based routing
- [ ] Add image enhancement for marginal captures

---

## API Extension Required

The current `JABCodeMobile.java` needs a camera-specific decode method:

```java
// Add to JABCodeMobile.java
@Nullable
public static String decodeFromCamera(
    @NonNull byte[] rgbaData, 
    int width, 
    int height) {
    return nativeDecodeFromCamera(rgbaData, width, height);
}

private static native String nativeDecodeFromCamera(
    byte[] rgbaData, int width, int height);
```

```c
// Add to jabcode_jni.c
JNIEXPORT jstring JNICALL
Java_com_jabcode_JABCodeMobile_nativeDecodeFromCamera(
    JNIEnv *env, jclass clazz,
    jbyteArray rgbaData, jint width, jint height) {
    
    jbyte* data = (*env)->GetByteArrayElements(env, rgbaData, NULL);
    
    // Call camera decoder (uses full detection pipeline)
    char* result = jabMobileDecodeFromCamera(
        (unsigned char*)data, width, height);
    
    (*env)->ReleaseByteArrayElements(env, rgbaData, data, JNI_ABORT);
    
    if (result == NULL) return NULL;
    
    jstring jresult = (*env)->NewStringUTF(env, result);
    free(result);
    return jresult;
}
```

---

## References

- [CameraX Documentation](https://developer.android.com/training/camerax)
- [Image Analysis Use Case](https://developer.android.com/training/camerax/analyze)
- [Camera2 API Guide](https://developer.android.com/reference/android/hardware/camera2/package-summary)
- [JABCode Specification](https://jabcode.org/JABCode)

---

*Document created: 2026-01-24*
*Last updated: 2026-01-24 (added ML Integration and Hardware Control sections)*
