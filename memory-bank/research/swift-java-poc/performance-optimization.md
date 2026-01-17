# Performance Optimization for Mobile JABCode

**Strategies for efficient JABCode processing on mobile devices**

[← Back to Index](index.md) | [Overview](overview.md)

---

## 📋 Table of Contents

1. [Mobile Constraints](#mobile-constraints)
2. [Memory Optimization](#memory-optimization)
3. [CPU Optimization](#cpu-optimization)
4. [Battery Efficiency](#battery-efficiency)
5. [Camera Processing](#camera-processing)
6. [Network and Storage](#network-and-storage)
7. [Platform-Specific Optimizations](#platform-specific-optimizations)
8. [Profiling and Benchmarking](#profiling-and-benchmarking)
9. [Real-World Performance Tips](#real-world-performance-tips)

---

## 📱 Mobile Constraints

### Understanding the Challenge

Mobile devices face unique constraints compared to desktop/server environments:

| Constraint | Impact | Mitigation Strategy |
|------------|--------|---------------------|
| **Limited RAM** | OOM crashes, app termination | Aggressive memory management |
| **Battery Life** | Thermal throttling, user complaints | Efficient algorithms, background limits |
| **Variable CPU** | Inconsistent performance | Adaptive processing, progressive loading |
| **Thermal Throttling** | Performance degradation | Batch processing, cooling periods |
| **Network Latency** | Slow data transfer | Local processing, compression |

### Performance Targets

**Encoding:**
- 640x480 image: < 20ms
- 1920x1080 image: < 60ms
- Memory overhead: < 5MB peak

**Decoding:**
- Simple code: < 30ms
- Complex multi-symbol: < 100ms
- Camera preview: 30fps minimum

---

## 🧠 Memory Optimization

### 1. Bitmap Memory Management

#### Android Best Practices

```kotlin
// ❌ BAD: Allocate large bitmap without recycling
fun encodeBad(data: String): Bitmap {
    val bitmap = JABCode.encode(data)
    // Bitmap never recycled - memory leak!
    return bitmap
}

// ✅ GOOD: Use try-with-resources pattern
fun encodeGood(data: String): Bitmap? {
    var result: Bitmap? = null
    var tempBitmap: Bitmap? = null
    
    try {
        tempBitmap = JABCode.encode(data)
        // Process or copy if needed
        result = tempBitmap.copy(tempBitmap.config, false)
        return result
    } finally {
        // Always recycle temporary bitmaps
        if (tempBitmap != null && tempBitmap != result) {
            tempBitmap.recycle()
        }
    }
}

// ✅ BETTER: Use BitmapFactory options for downsampling
fun decodeLargeImage(file: File, maxSize: Int): Bitmap {
    val options = BitmapFactory.Options().apply {
        // First pass: get dimensions only
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(file.path, options)
    
    // Calculate sample size
    options.inSampleSize = calculateSampleSize(
        options.outWidth, 
        options.outHeight, 
        maxSize
    )
    
    // Second pass: load downsampled image
    options.inJustDecodeBounds = false
    options.inPreferredConfig = Bitmap.Config.RGB_565 // Use 16-bit if no alpha needed
    
    return BitmapFactory.decodeFile(file.path, options)
}

private fun calculateSampleSize(
    width: Int, 
    height: Int, 
    maxSize: Int
): Int {
    var sampleSize = 1
    while (width / sampleSize > maxSize || height / sampleSize > maxSize) {
        sampleSize *= 2
    }
    return sampleSize
}
```

#### iOS Best Practices

```swift
// ❌ BAD: Load full resolution image
func decodeBad(_ image: UIImage) throws -> DecodeResult {
    // Loads full resolution into memory - wasteful!
    return try JABCode.shared.decode(image)
}

// ✅ GOOD: Use CGImageSource for memory-efficient loading
func decodeGood(url: URL, maxSize: CGFloat) throws -> DecodeResult {
    guard let imageSource = CGImageSourceCreateWithURL(url as CFURL, nil) else {
        throw JABCodeError.invalidImage
    }
    
    // Create thumbnail at max size
    let options: [CFString: Any] = [
        kCGImageSourceThumbnailMaxPixelSize: maxSize,
        kCGImageSourceCreateThumbnailFromImageAlways: true,
        kCGImageSourceShouldCache: false  // Don't cache, we process once
    ]
    
    guard let cgImage = CGImageSourceCreateThumbnailAtIndex(imageSource, 0, options as CFDictionary) else {
        throw JABCodeError.invalidImage
    }
    
    return try JABCode.shared.decode(cgImage)
}

// ✅ BETTER: Use autoreleasepool for batch processing
func processBatch(_ images: [UIImage]) throws -> [DecodeResult] {
    var results: [DecodeResult] = []
    
    for image in images {
        try autoreleasepool {
            let result = try JABCode.shared.decode(image)
            results.append(result)
            // Autorelease pool drains temporary objects here
        }
    }
    
    return results
}
```

### 2. Native Buffer Management

```cpp
// C/C++ optimization: Reuse buffers instead of allocating

class BufferPool {
private:
    std::vector<jab_byte*> available_buffers;
    std::vector<jab_byte*> used_buffers;
    size_t buffer_size;
    
public:
    BufferPool(size_t size, size_t count) : buffer_size(size) {
        for (size_t i = 0; i < count; i++) {
            available_buffers.push_back(new jab_byte[buffer_size]);
        }
    }
    
    ~BufferPool() {
        for (auto* buf : available_buffers) delete[] buf;
        for (auto* buf : used_buffers) delete[] buf;
    }
    
    jab_byte* acquire() {
        if (available_buffers.empty()) {
            return new jab_byte[buffer_size];
        }
        jab_byte* buf = available_buffers.back();
        available_buffers.pop_back();
        used_buffers.push_back(buf);
        return buf;
    }
    
    void release(jab_byte* buf) {
        auto it = std::find(used_buffers.begin(), used_buffers.end(), buf);
        if (it != used_buffers.end()) {
            used_buffers.erase(it);
            available_buffers.push_back(buf);
        }
    }
};

// Global buffer pool for image processing
static BufferPool g_image_buffer_pool(1920 * 1080 * 4, 3); // 3 buffers
```

### 3. Memory Leak Detection

#### Android (LeakCanary)

```kotlin
// build.gradle.kts
dependencies {
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
}

// Application.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // LeakCanary automatically detects leaks in debug builds
        // Watch for JABCode-related leaks
        LeakCanary.config = LeakCanary.config.copy(
            retainedVisibleThreshold = 3
        )
    }
}

// Custom ObjectWatcher for native handles
class NativeHandleWatcher {
    private val watcher = ObjectWatcher(
        clock = { System.currentTimeMillis() },
        checkRetainedExecutor = Executors.newSingleThreadExecutor()
    )
    
    fun watch(handle: Long, name: String) {
        watcher.watch(handle, name)
    }
}
```

#### iOS (Instruments)

```bash
# Profile with Instruments
# Run in Xcode: Product > Profile > Leaks

# Or via command line:
xcrun xctrace record --template 'Leaks' \
    --device <device-id> \
    --launch com.example.jabcode \
    --output profile.trace

# Analyze results
xcrun xctrace export --input profile.trace --output leaks.xml
```

---

## ⚡ CPU Optimization

### 1. Algorithm Optimization

#### Use SIMD Instructions

```c
// Leverage ARM NEON or x86 SSE for vector operations

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>

// Optimized color distance calculation using NEON
void calculateColorDistance_NEON(
    const uint8_t* pixels, 
    const uint8_t* palette,
    int width,
    int height,
    uint8_t* output
) {
    int total_pixels = width * height;
    int i = 0;
    
    // Process 4 pixels at once with NEON
    for (; i <= total_pixels - 4; i += 4) {
        // Load 4 RGB pixels (12 bytes)
        uint8x16_t pixel_data = vld1q_u8(&pixels[i * 3]);
        
        // Calculate distances (simplified example)
        uint8x16_t palette_color = vld1q_u8(palette);
        uint8x16_t diff = vabdq_u8(pixel_data, palette_color);
        
        // Store results
        vst1q_lane_u32((uint32_t*)&output[i], vreinterpretq_u32_u8(diff), 0);
    }
    
    // Process remaining pixels scalar
    for (; i < total_pixels; i++) {
        output[i] = calculate_distance_scalar(&pixels[i * 3], palette);
    }
}
#endif
```

#### Precompute Lookup Tables

```c
// Precompute color conversion tables
static uint8_t rgb_to_gray_table[256][256][256];
static bool table_initialized = false;

void init_color_tables() {
    if (table_initialized) return;
    
    for (int r = 0; r < 256; r++) {
        for (int g = 0; g < 256; g++) {
            for (int b = 0; b < 256; b++) {
                // ITU-R BT.709 formula
                rgb_to_gray_table[r][g][b] = 
                    (uint8_t)(0.2126 * r + 0.7152 * g + 0.0722 * b);
            }
        }
    }
    
    table_initialized = true;
}

uint8_t rgb_to_gray_fast(uint8_t r, uint8_t g, uint8_t b) {
    return rgb_to_gray_table[r][g][b];
}
```

### 2. Multi-Threading

#### Android (Kotlin Coroutines)

```kotlin
class OptimizedJABCodeProcessor {
    private val processingScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob()
    )
    
    // Parallel batch encoding
    suspend fun encodeBatch(
        data: List<String>,
        options: EncodeOptions = EncodeOptions.DEFAULT
    ): List<Result<Bitmap>> = coroutineScope {
        data.chunked(Runtime.getRuntime().availableProcessors())
            .flatMap { chunk ->
                chunk.map { text ->
                    async(Dispatchers.Default) {
                        runCatching {
                            JABCode.encode(text, options)
                        }
                    }
                }.awaitAll()
            }
    }
    
    // Background decoding with progress
    fun decodeAsync(
        images: List<Bitmap>,
        onProgress: (Int, Int) -> Unit
    ): Flow<Result<DecodeResult>> = flow {
        images.forEachIndexed { index, bitmap ->
            val result = withContext(Dispatchers.Default) {
                runCatching { JABCode.decode(bitmap) }
            }
            onProgress(index + 1, images.size)
            emit(result)
        }
    }.flowOn(Dispatchers.Default)
}
```

#### iOS (Swift Concurrency)

```swift
@available(iOS 13.0, *)
actor JABCodeProcessor {
    private let maxConcurrentOperations = ProcessInfo.processInfo.activeProcessorCount
    
    /// Process multiple images concurrently with task groups
    func decodeBatch(_ images: [UIImage]) async throws -> [DecodeResult] {
        try await withThrowingTaskGroup(of: (Int, DecodeResult).self) { group in
            // Add tasks with semaphore for concurrency control
            for (index, image) in images.enumerated() {
                group.addTask {
                    let result = try await JABCode.shared.decode(image)
                    return (index, result)
                }
                
                // Limit concurrent tasks
                if group.index >= maxConcurrentOperations {
                    _ = try await group.next()
                }
            }
            
            // Collect results in order
            var results = [(Int, DecodeResult)]()
            for try await result in group {
                results.append(result)
            }
            
            return results.sorted { $0.0 < $1.0 }.map { $0.1 }
        }
    }
    
    /// Progressive decoding with cancellation support
    func decodeWithProgress(
        _ images: [UIImage],
        progress: @escaping (Int, Int) -> Void
    ) async throws -> [DecodeResult] {
        var results: [DecodeResult] = []
        
        for (index, image) in images.enumerated() {
            try Task.checkCancellation()
            
            let result = try await JABCode.shared.decode(image)
            results.append(result)
            
            progress(index + 1, images.count)
        }
        
        return results
    }
}
```

### 3. Caching Strategies

```kotlin
// LRU cache for decoded results
class JABCodeCache(maxSize: Int = 50) {
    private val cache = object : LruCache<String, DecodeResult>(maxSize) {
        override fun sizeOf(key: String, value: DecodeResult): Int {
            return value.data.size / 1024 // Size in KB
        }
    }
    
    fun get(imageHash: String): DecodeResult? = cache.get(imageHash)
    
    fun put(imageHash: String, result: DecodeResult) {
        cache.put(imageHash, result)
    }
    
    // Generate hash from image
    fun hashImage(bitmap: Bitmap): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        val hash = digest.digest(buffer.array())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
```

---

## 🔋 Battery Efficiency

### 1. Reduce Camera Usage

```kotlin
// Throttle camera frame processing
class ThrottledAnalyzer(
    private val intervalMs: Long = 500,
    private val analyzer: (ImageProxy) -> Unit
) : ImageAnalysis.Analyzer {
    
    private var lastAnalyzedTimestamp = 0L
    
    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastAnalyzedTimestamp >= intervalMs) {
            analyzer(image)
            lastAnalyzedTimestamp = currentTime
        }
        
        image.close()
    }
}

// Use lower resolution for preview
val imageAnalysis = ImageAnalysis.Builder()
    .setTargetResolution(Size(1280, 720)) // Not full sensor resolution
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()
```

### 2. Background Processing Limits

```swift
// iOS: Use background task API responsibly
func processBatchInBackground(_ images: [UIImage]) async {
    var backgroundTask: UIBackgroundTaskIdentifier = .invalid
    
    backgroundTask = UIApplication.shared.beginBackgroundTask {
        // Time expired - clean up
        UIApplication.shared.endBackgroundTask(backgroundTask)
        backgroundTask = .invalid
    }
    
    defer {
        if backgroundTask != .invalid {
            UIApplication.shared.endBackgroundTask(backgroundTask)
        }
    }
    
    // Process with timeout awareness
    let timeoutTask = Task {
        try await Task.sleep(nanoseconds: 25_000_000_000) // 25 seconds
        throw TimeoutError()
    }
    
    do {
        try await withThrowingTaskGroup(of: Void.self) { group in
            group.addTask { try await self.doActualProcessing(images) }
            group.addTask { try await timeoutTask.value }
            
            // Race - first to complete wins
            try await group.next()
            group.cancelAll()
        }
    } catch {
        print("Background processing failed or timed out")
    }
}
```

### 3. Adaptive Quality

```kotlin
class AdaptiveBrightnessScanner {
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    
    fun getOptimalScanOptions(): ScanOptions {
        val batteryLevel = batteryManager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY
        )
        
        return when {
            batteryLevel > 50 -> ScanOptions(
                resolution = Size(1920, 1080),
                frameRate = 30,
                qualityMode = QualityMode.HIGH
            )
            batteryLevel > 20 -> ScanOptions(
                resolution = Size(1280, 720),
                frameRate = 15,
                qualityMode = QualityMode.MEDIUM
            )
            else -> ScanOptions(
                resolution = Size(640, 480),
                frameRate = 10,
                qualityMode = QualityMode.LOW
            )
        }
    }
}
```

---

## 📸 Camera Processing

### 1. Image Format Optimization

```kotlin
// Use YUV format for faster processing
val imageAnalysis = ImageAnalysis.Builder()
    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
    .build()

fun processYUV(image: ImageProxy): Bitmap? {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer
    
    // Direct processing on YUV data (faster than RGB conversion)
    val nv21 = yuv420ToNv21(yBuffer, uBuffer, vBuffer, image)
    
    // Convert to bitmap only when necessary
    return nv21ToBitmap(nv21, image.width, image.height)
}
```

### 2. Region of Interest (ROI)

```swift
// Process only center region for faster scanning
func extractROI(from image: CGImage) -> CGImage? {
    let width = image.width
    let height = image.height
    
    // Center 60% of image
    let roiWidth = Int(Double(width) * 0.6)
    let roiHeight = Int(Double(height) * 0.6)
    let x = (width - roiWidth) / 2
    let y = (height - roiHeight) / 2
    
    let roi = CGRect(x: x, y: y, width: roiWidth, height: roiHeight)
    return image.cropping(to: roi)
}

// Use ROI for faster decoding
func quickScan(_ image: UIImage) throws -> DecodeResult? {
    guard let cgImage = image.cgImage,
          let roi = extractROI(from: cgImage) else {
        throw JABCodeError.invalidImage
    }
    
    do {
        return try JABCode.shared.decode(roi)
    } catch {
        // Fallback to full image if ROI fails
        return try JABCode.shared.decode(image)
    }
}
```

### 3. Motion Detection

```kotlin
// Skip processing when camera is moving
class MotionDetector {
    private var previousFrame: Bitmap? = null
    private val threshold = 0.15 // 15% difference threshold
    
    fun isStable(currentFrame: Bitmap): Boolean {
        val prev = previousFrame ?: run {
            previousFrame = currentFrame.copy(currentFrame.config, false)
            return false
        }
        
        val diff = calculateFrameDifference(prev, currentFrame)
        previousFrame?.recycle()
        previousFrame = currentFrame.copy(currentFrame.config, false)
        
        return diff < threshold
    }
    
    private fun calculateFrameDifference(prev: Bitmap, current: Bitmap): Float {
        // Downsample for speed
        val small1 = Bitmap.createScaledBitmap(prev, 32, 32, false)
        val small2 = Bitmap.createScaledBitmap(current, 32, 32, false)
        
        var totalDiff = 0
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                val color1 = small1.getPixel(x, y)
                val color2 = small2.getPixel(x, y)
                totalDiff += abs(Color.red(color1) - Color.red(color2))
            }
        }
        
        small1.recycle()
        small2.recycle()
        
        return totalDiff / (32f * 32f * 255f)
    }
}
```

---

## 📊 Profiling and Benchmarking

### Android Profiling

```kotlin
// Systrace for performance analysis
class PerformanceTracer {
    inline fun <T> trace(label: String, block: () -> T): T {
        Trace.beginSection(label)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }
}

// Usage
val tracer = PerformanceTracer()

fun encodeWithProfiling(data: String): Bitmap {
    return tracer.trace("JABCode.encode") {
        tracer.trace("Data preparation") {
            // Prepare data
        }
        
        tracer.trace("Native encoding") {
            JABCode.encode(data)
        }
    }
}

// Capture trace:
// adb shell atrace --async_start -b 32768 -a com.example.jabcode gfx view
// (perform operations)
// adb shell atrace --async_stop > trace.html
```

### iOS Profiling

```swift
// Signposts for Instruments
import os.signpost

class PerformanceMonitor {
    private let log = OSLog(subsystem: "com.example.jabcode", category: "Performance")
    
    func measure<T>(_ name: StaticString, _ block: () throws -> T) rethrows -> T {
        let signpostID = OSSignpostID(log: log)
        os_signpost(.begin, log: log, name: name, signpostID: signpostID)
        defer {
            os_signpost(.end, log: log, name: name, signpostID: signpostID)
        }
        return try block()
    }
}

// Usage
let monitor = PerformanceMonitor()

func encodeWithProfiling(_ data: String) throws -> UIImage {
    try monitor.measure("JABCode Encode") {
        try JABCode.shared.encode(data)
    }
}

// View in Instruments: Xcode > Product > Profile > Time Profiler
```

### Benchmark Suite

```kotlin
// JMH-style benchmark
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class JABCodeBenchmark {
    
    private val testData = "Hello, World!".repeat(100)
    
    @Benchmark
    fun benchmarkEncode8Colors() {
        JABCode.encode(testData, EncodeOptions(colorNumber = 8))
    }
    
    @Benchmark
    fun benchmarkEncode256Colors() {
        JABCode.encode(testData, EncodeOptions(colorNumber = 256))
    }
    
    @Benchmark
    fun benchmarkDecodeSimple() {
        val encoded = JABCode.encode("Test", EncodeOptions.DEFAULT)
        JABCode.decode(encoded)
        encoded.recycle()
    }
}
```

---

## 💡 Real-World Performance Tips

### 1. Encode Once, Display Many

```kotlin
// Cache encoded images
class JABCodeGenerator {
    private val cache = LruCache<String, Bitmap>(10)
    
    fun getOrCreate(data: String, options: EncodeOptions): Bitmap {
        val key = "$data:${options.hashCode()}"
        return cache.get(key) ?: run {
            val bitmap = JABCode.encode(data, options)
            cache.put(key, bitmap)
            bitmap
        }
    }
}
```

### 2. Progressive Quality Enhancement

```swift
// Start with low quality, enhance if needed
func progressiveDecode(_ image: UIImage) async throws -> DecodeResult {
    // Try quick scan with downsampled image first
    if let quickResult = try? await quickDecode(image, scale: 0.5) {
        return quickResult
    }
    
    // Fall back to full resolution
    return try await JABCode.shared.decode(image)
}
```

### 3. Thermal Management

```kotlin
class ThermalMonitor(private val context: Context) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    
    fun shouldThrottle(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val thermalStatus = powerManager.currentThermalStatus
            return thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        }
        return false
    }
    
    fun adaptiveDelay(): Long {
        return when {
            shouldThrottle() -> 2000L // 2 second delay
            else -> 100L // Normal 100ms delay
        }
    }
}
```

### 4. Network Optimization

```swift
// Compress before uploading
func uploadJABCode(_ image: UIImage) async throws {
    // Compress aggressively (JABCode is already error-corrected)
    guard let jpegData = image.jpegData(compressionQuality: 0.6) else {
        throw JABCodeError.imageCreationFailed
    }
    
    // Upload compressed data
    try await uploadData(jpegData)
}
```

---

## 📈 Performance Metrics

### Target Benchmarks

| Operation | Device | Target Time | Memory |
|-----------|--------|-------------|---------|
| Encode 100 chars (8 colors) | Mid-range (2020) | < 15ms | < 2MB |
| Encode 1000 chars (256 colors) | Mid-range (2020) | < 50ms | < 8MB |
| Decode simple code | Mid-range (2020) | < 25ms | < 3MB |
| Decode 4-symbol code | Mid-range (2020) | < 80ms | < 10MB |
| Camera preview (30fps) | Mid-range (2020) | 33ms/frame | < 50MB total |

### Measurement Tools

**Android:**
- Android Profiler (CPU, Memory, Energy)
- Systrace / Perfetto
- Battery Historian
- LeakCanary

**iOS:**
- Instruments (Time Profiler, Allocations, Leaks)
- MetricKit for production metrics
- Xcode Gauges
- Network Link Conditioner

---

[← Back to Index](index.md) | [Troubleshooting →](troubleshooting.md) | [Cross-Platform →](cross-platform-considerations.md)
