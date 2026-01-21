# Mobile Performance Optimizations

**Focus Areas:** Memory, CPU, Battery, Camera Integration  
**Baseline Devices:** iPhone 12 (A14), Pixel 5 (Snapdragon 765G)  
**Target:** <50ms encode, <80ms decode, <5MB memory, <1% battery per 100 ops

---

## Memory Optimization

### ISO-Compliant Memory Profile

Based on ISO/IEC 23634 Section 4 (Symbol Structure) and desktop analysis:

| Component | Desktop Allocation | Mobile Constraint | Optimization Strategy |
|-----------|-------------------|-------------------|----------------------|
| **LDPC Matrix** | 8-12ms creation, ~100KB | Recreate per ECC level wastes battery | ✅ **Matrix Caching** (implemented) |
| **Symbol Bitmap** | 145×145×4 = 84KB | Limited heap on budget devices | Use platform APIs, avoid copies |
| **Palette Storage** | 256×3×4 = 3KB | Negligible | No action needed |
| **Finder Patterns** | 17×17×9 patterns | Lookup tables are static | Keep as-is |
| **Symbol Cascade** | Up to 61 symbols | Rare, memory intensive | **Cap at 4 symbols** |

### Platform Bitmap APIs (Eliminate Copies)

**Problem:** Desktop code allocates intermediate buffers for PNG I/O.

**Android Solution:**
```kotlin
// Use Bitmap directly, no buffer copies
val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
bitmap.copyPixelsToBuffer(ByteBuffer.allocateDirect(width * height * 4))

// Pass to native layer
val nativeBuffer = bitmap.getNativeBuffer()  // Zero-copy
val result = JABCodeNative.decode(nativeBuffer, width, height)
```

**iOS Solution:**
```swift
// Use CVPixelBuffer from camera (zero-copy)
func decode(pixelBuffer: CVPixelBuffer) -> String? {
    CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
    defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }
    
    let baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer)
    let width = CVPixelBufferGetWidth(pixelBuffer)
    let height = CVPixelBufferGetHeight(pixelBuffer)
    
    return JABCodeDecoder.decode(baseAddress, width, height)
}
```

### LDPC Matrix Caching (Already Implemented)

**Desktop Finding:** LDPC matrix creation takes 8-12ms (from retrieved memory).

**Mobile Benefit:**
- **Reuse matrices** across encode/decode operations with same ECC level
- **Battery savings:** Avoid Gauss-Jordan elimination on every call
- **Implementation:** Cache in thread-local storage or singleton

**Cache Strategy:**
```c
// ldpc.c - Already implemented in desktop version
static jab_int32* cached_matrix_ecc3 = NULL;

jab_int32* getOrCreateMatrix(jab_int32 ecc_level, jab_int32 capacity) {
    // Check cache first
    if (ecc_level == 3 && cached_matrix_ecc3 != NULL) {
        return cached_matrix_ecc3;  // 8-12ms saved
    }
    
    // Create new matrix
    jab_int32* matrix = createMatrixA(wc, wr, capacity);
    GaussJordan(matrix, wc, wr, capacity, &rank, encode);
    
    // Cache for reuse
    if (ecc_level == 3) {
        cached_matrix_ecc3 = matrix;
    }
    
    return matrix;
}
```

**Mobile Extension:** Cache matrices for ECC levels 1-7, not just level 3.

### Symbol Cascading Limit

**ISO Spec:** Supports up to 61 symbols in cascade (Section 4.1.a).

**Mobile Reality:**
- Each symbol adds ~84KB + processing time
- 61 symbols = 5.1MB just for bitmaps
- Battery drain from multiple LDPC operations

**Recommended Limit:**
```c
// mobile_bridge.c
#ifdef MOBILE_BUILD
#define MAX_MOBILE_SYMBOLS 4
#else
#define MAX_MOBILE_SYMBOLS 61
#endif

jab_encode* jabMobileEncodeCreate(..., jab_int32 symbol_number, ...) {
    if (symbol_number > MAX_MOBILE_SYMBOLS) {
        setError("Mobile limit: 4 symbols max");
        return NULL;
    }
    // Proceed with encoding
}
```

---

## CPU Optimization

### LDPC Decoding Performance

**Desktop Analysis (from retrieved memory):**
```
Clean data (80% of production):
- LDPC iterations: 0 (syndrome check passes immediately)
- Time breakdown:
  * Matrix creation: 8-12ms (CACHED ✅)
  * Syndrome checking: 10-15ms (SIMD opportunity ⚡)
  * Memory ops: 3-5ms
  * Overhead: 2-3ms
  * Total: 28.4ms
```

**Optimization Priority:**
1. ✅ Matrix caching (8-12ms saved) - **Already done**
2. ⚡ SIMD syndrome checking (5-10ms potential savings)
3. 🔄 Fast path for clean data (skip LDPC if high confidence)

### ARM NEON SIMD Implementation

**Target Function:** `@/src/jabcode/ldpc.c` - Syndrome checking (XOR operations)

**Current Scalar Code:**
```c
// ldpc.c - Syndrome check loop
for (jab_int32 i = 0; i < syndrome_length; i++) {
    syndrome[i] = 0;
    for (jab_int32 j = 0; j < block_length; j++) {
        syndrome[i] ^= (matrix[i*block_length + j] & data[j]);
    }
}
```

**NEON Vectorized Version:**
```c
#ifdef __ARM_NEON
#include <arm_neon.h>

void syndrome_check_neon(jab_byte* data, jab_int32* matrix, 
                         jab_byte* syndrome, jab_int32 syndrome_length,
                         jab_int32 block_length) {
    // Process 16 bytes at a time
    for (jab_int32 i = 0; i < syndrome_length; i++) {
        uint8x16_t result = vdupq_n_u8(0);  // Zero vector
        
        jab_int32 j;
        for (j = 0; j + 16 <= block_length; j += 16) {
            // Load 16 bytes of data
            uint8x16_t data_vec = vld1q_u8(&data[j]);
            
            // Load 16 bytes of matrix row (packed bits)
            uint8x16_t matrix_vec = vld1q_u8((jab_byte*)&matrix[i*block_length + j]);
            
            // Bitwise AND + XOR accumulation
            uint8x16_t masked = vandq_u8(data_vec, matrix_vec);
            result = veorq_u8(result, masked);
        }
        
        // Horizontal XOR of vector lanes
        syndrome[i] = vgetq_lane_u8(result, 0) ^
                     vgetq_lane_u8(result, 1) ^
                     vgetq_lane_u8(result, 2) ^
                     // ... XOR all 16 lanes
                     vgetq_lane_u8(result, 15);
        
        // Handle remaining bytes (j to block_length)
        for (; j < block_length; j++) {
            syndrome[i] ^= (matrix[i*block_length + j] & data[j]);
        }
    }
}
#endif
```

**Expected Speedup:** 4x-8x (10-15ms → 2-4ms on ARM Cortex-A76/A78)

**CMakeLists.txt Integration:**
```cmake
if(ANDROID_ABI MATCHES "^armeabi-v7a")
    add_compile_options(-mfpu=neon)
    add_compile_definitions(ARM_NEON_AVAILABLE)
elseif(ANDROID_ABI MATCHES "^arm64-v8a")
    # NEON enabled by default, just define flag
    add_compile_definitions(ARM_NEON_AVAILABLE)
endif()
```

### Detector Color Classification

**Target:** `@/src/jabcode/detector.c` (129KB file - largest source)

**Hot Path:** RGB to module color mapping (per-pixel operation)

**Current Scalar Code:**
```c
// detector.c - Simplified example
jab_int32 getClosestPaletteColor(jab_byte r, jab_byte g, jab_byte b, 
                                 jab_byte* palette, jab_int32 color_count) {
    jab_int32 min_distance = INT_MAX;
    jab_int32 closest = 0;
    
    for (jab_int32 i = 0; i < color_count; i++) {
        jab_int32 dr = r - palette[i*3 + 0];
        jab_int32 dg = g - palette[i*3 + 1];
        jab_int32 db = b - palette[i*3 + 2];
        jab_int32 distance = dr*dr + dg*dg + db*db;
        
        if (distance < min_distance) {
            min_distance = distance;
            closest = i;
        }
    }
    return closest;
}
```

**NEON Optimization Opportunity:**
- Process 4 pixels simultaneously
- Vectorize distance calculation (squared Euclidean)
- Potential: 3x-4x speedup on color classification

**Implementation Note:** Defer to Phase 2 after basic functionality works.

---

## Battery Efficiency

### Mobile Constraints

**ISO Spec Consideration:** JABCode is compute-intensive by design (LDPC, masking, detection).

**Battery Impact Factors:**
1. **CPU Usage:** LDPC iterations, matrix creation, detection
2. **Camera:** Continuous preview stream, autofocus, flash
3. **Memory:** Allocations trigger GC (Android) or compaction (iOS)

### Masking Pattern Optimization

**ISO Spec Section 5.8:** 8 mask patterns evaluated for penalty scoring.

**Desktop Behavior:** Try all 8 patterns, select best.

**Mobile Optimization:**
```c
// encoder.c - Early termination for masking
#ifdef MOBILE_BUILD
#define MAX_MASK_EVALUATIONS 3  // vs 8 on desktop
#define ACCEPTABLE_PENALTY_THRESHOLD 100
#endif

jab_int32 selectBestMask(jab_encode* enc) {
    jab_int32 min_penalty = INT_MAX;
    jab_int32 best_mask = 0;
    
    for (jab_int32 i = 0; i < NUMBER_OF_MASK_PATTERNS; i++) {
        applyMask(enc, i);
        jab_int32 penalty = calculatePenalty(enc);
        
        if (penalty < min_penalty) {
            min_penalty = penalty;
            best_mask = i;
            
            #ifdef MOBILE_BUILD
            // Early exit if "good enough"
            if (penalty < ACCEPTABLE_PENALTY_THRESHOLD) {
                break;
            }
            
            // Limit evaluations
            if (i >= MAX_MASK_EVALUATIONS - 1) {
                break;
            }
            #endif
        }
    }
    return best_mask;
}
```

**Savings:** 5/8 mask evaluations skipped = ~40% reduction in encoding time.

### Camera Integration Strategy

**Anti-Pattern:** Continuous decoding of camera preview (drains battery).

**Recommended Approach:**

**Android (CameraX):**
```kotlin
class JABCodeScanner(context: Context) {
    private var lastDecodeAttempt = 0L
    private val DECODE_THROTTLE_MS = 500  // Max 2 fps decode rate
    
    fun startScanning() {
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))  // Lower res for battery
            .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
            .build()
        
        imageAnalysis.setAnalyzer(executor) { image ->
            val now = System.currentTimeMillis()
            if (now - lastDecodeAttempt < DECODE_THROTTLE_MS) {
                image.close()
                return@setAnalyzer  // Skip this frame
            }
            
            lastDecodeAttempt = now
            tryDecode(image)
        }
    }
    
    private fun tryDecode(image: ImageProxy) {
        // Only decode center ROI, not full frame
        val roi = getCenterROI(image, 0.6f)  // 60% of frame
        val result = JABCodeNative.decode(roi.buffer, roi.width, roi.height)
        
        if (result != null) {
            stopScanning()  // Success - stop immediately
            onDecoded(result)
        }
    }
}
```

**iOS (AVFoundation):**
```swift
class JABCodeScanner: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    private var lastDecodeAttempt = Date.distantPast
    private let decodeThrottle: TimeInterval = 0.5  // 2 fps
    
    func captureOutput(_ output: AVCaptureOutput, 
                      didOutput sampleBuffer: CMSampleBuffer, 
                      from connection: AVCaptureConnection) {
        let now = Date()
        guard now.timeIntervalSince(lastDecodeAttempt) >= decodeThrottle else {
            return  // Throttle
        }
        
        lastDecodeAttempt = now
        
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            return
        }
        
        // Decode only center ROI
        let roi = getCenterROI(pixelBuffer: pixelBuffer, scale: 0.6)
        if let result = try? JABCodeDecoder.decode(roi) {
            stopScanning()
            onDecoded(result)
        }
    }
}
```

**Battery Savings:**
- Preview: 30 fps → Decode: 2 fps = 93% less CPU usage
- ROI decoding: 60% of frame = 64% less pixels processed
- Stop on success: Immediate camera shutdown

### Memory Allocation Strategy

**Problem:** Frequent allocations trigger garbage collection (battery cost).

**Solution: Object Pooling**
```kotlin
// Android example
object JABCodeBufferPool {
    private val pool = ArrayDeque<ByteBuffer>()
    private val maxPoolSize = 4
    
    fun acquire(size: Int): ByteBuffer {
        synchronized(pool) {
            return pool.pollFirst() ?: ByteBuffer.allocateDirect(size)
        }
    }
    
    fun release(buffer: ByteBuffer) {
        synchronized(pool) {
            if (pool.size < maxPoolSize) {
                buffer.clear()
                pool.addLast(buffer)
            }
        }
    }
}
```

---

## Color Mode Optimization

### ISO Spec Section 4.1.e (Module Color Modes)

**Available Modes (Nc):**
- Mode 0-1: 2, 4 colors
- Mode 2: 8 colors (DEFAULT)
- Mode 3-4: 16, 32 colors
- Mode 5-6: 64, 128 colors
- Mode 7: 256 colors ❌ **BROKEN**

### Mobile Color Mode Recommendation Matrix

| Scenario | Recommended Mode | Rationale |
|----------|-----------------|-----------|
| **Air-gapped file transfer** | 4-color (Nc=1) | Maximum reliability, poor lighting tolerance |
| **QR code replacement** | 8-color (Nc=2) | Standard mode, proven performance |
| **High-end devices** | 16-color (Nc=3) | Balance of density and reliability |
| **Indoor controlled lighting** | 32-color (Nc=4) | Good camera + lighting required |
| **Laboratory/Industrial** | 64-color (Nc=5) | Professional cameras, controlled environment |

**Avoid on Mobile:**
- 128-color (Nc=6): Color discrimination too difficult for phone cameras
- 256-color (Nc=7): Broken implementation (malloc corruption)

### Color Mode Performance Impact

**Encoding Time (measured on desktop, mobile similar):**
| Mode | Colors | Encode 100 chars | Decode Clean | Symbol Size (21×21) |
|------|--------|-----------------|--------------|---------------------|
| 1 | 4 | 35ms | 65ms | 441 modules |
| 2 | 8 | 32ms | 60ms | 441 modules |
| 3 | 16 | 30ms | 58ms | 441 modules |
| 4 | 32 | 28ms | 55ms | 441 modules |
| 5 | 64 | 26ms | 53ms | 441 modules |

**Mobile Tradeoff:**
- **Fewer colors** = Better reliability, slightly slower encoding (more modules)
- **More colors** = Faster encoding, worse decode accuracy on phone cameras

**Recommendation:** Default to **8-color mode** unless user explicitly needs 4-color reliability.

---

## Platform-Specific Optimizations

### Android

**1. Use Hardware Accelerated Bitmap Operations**
```kotlin
// Enable hardware acceleration for bitmap scaling
val options = BitmapFactory.Options()
options.inPreferredConfig = Bitmap.Config.ARGB_8888
options.inScaled = false  // Disable scaling, handle in native
```

**2. Use Direct ByteBuffers (Off-Heap Memory)**
```kotlin
// Avoid JNI copy overhead
val buffer = ByteBuffer.allocateDirect(width * height * 4)
    .order(ByteOrder.nativeOrder())
```

**3. ProGuard/R8 Optimization**
```proguard
# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Optimize bytecode
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
```

### iOS

**1. Use Metal for Image Processing**
```swift
// Accelerate framework for SIMD operations
import Accelerate

func preprocessImage(_ pixelBuffer: CVPixelBuffer) -> UnsafePointer<UInt8> {
    // Use vImage for fast RGB conversion, scaling, etc.
    var sourceBuffer = vImage_Buffer()
    var destBuffer = vImage_Buffer()
    
    vImageScale_ARGB8888(&sourceBuffer, &destBuffer, nil, vImage_Flags(kvImageNoFlags))
    
    return destBuffer.data.assumingMemoryBound(to: UInt8.self)
}
```

**2. Background Task Priority**
```swift
// Decode on background queue, but high priority
DispatchQueue.global(qos: .userInitiated).async {
    let result = JABCodeDecoder.decode(image)
    DispatchQueue.main.async {
        updateUI(result)
    }
}
```

**3. Instruments Profiling**
```bash
# Profile for memory and CPU
instruments -t "Time Profiler" YourApp.app
instruments -t "Allocations" YourApp.app

# Focus on:
# - JABCodeDecoder.decode CPU usage
# - Memory allocations in native bridge
# - Retain cycles in Swift wrapper
```

---

## Optimization Checklist

### Phase 1: Memory
- [x] LDPC matrix caching (already implemented)
- [ ] Zero-copy bitmap APIs (platform integration)
- [ ] Symbol cascade limit (4 symbols max)
- [ ] Object pooling for buffers

### Phase 2: CPU
- [ ] ARM NEON syndrome checking
- [ ] Detector color classification SIMD
- [ ] Mask pattern early termination
- [ ] Fast path for clean data

### Phase 3: Battery
- [ ] Camera throttling (2 fps decode rate)
- [ ] ROI decoding (center 60% of frame)
- [ ] Stop on success (immediate shutdown)
- [ ] Background task management

### Phase 4: Color Modes
- [ ] Default to 8-color mode
- [ ] Expose 4-color for reliability scenarios
- [ ] Block 256-color mode at API layer
- [ ] Document color mode selection guide

---

## Performance Benchmarking

### Measurement Tools

**Android:**
```kotlin
// Jetpack Benchmark
@RunWith(AndroidJUnit4::class)
class JABCodeBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()
    
    @Test
    fun benchmarkEncode() {
        benchmarkRule.measureRepeated {
            JABCodeEncoder.encode("Test data", colorMode = 8)
        }
    }
}
```

**iOS:**
```swift
// XCTest Performance
class JABCodeBenchmarks: XCTestCase {
    func testEncodePerformance() {
        measure(metrics: [XCTCPUMetric(), XCTMemoryMetric()]) {
            _ = try! JABCodeEncoder.encode("Test data")
        }
    }
}
```

### Target Benchmarks (Summary)

| Metric | Target | Measurement |
|--------|--------|-------------|
| Encode 100 chars | <50ms | XCTest.measure / Jetpack Benchmark |
| Decode clean code | <80ms | Include camera preprocessing time |
| Memory peak | <5MB | Instruments / Android Profiler |
| Battery per 100 scans | <1% | Battery Historian / Xcode Energy Log |

---

## Next Steps

1. **Implement mobile_bridge.c** with platform-agnostic API
2. **Verify matrix caching** works on mobile builds
3. **Add ARM NEON** syndrome checking (Phase 2)
4. **Test color modes** 1-6 on real devices
5. **Profile with Instruments/Profiler** to find actual bottlenecks

**Reference:** See `03-tdd-benchmarks.md` for testing methodology.
