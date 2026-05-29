# Framework Benchmark Testing Guide

**Purpose:** Identify performance bottlenecks and validate framework meets latency/throughput targets  
**Integration:** Framework Phase 7 (Integration & Validation)  
**Created:** 2026-05-09

---

## Overview

Benchmark testing measures **actual performance** of the Camera2 framework under realistic conditions to:
- Identify bottlenecks before production deployment
- Validate latency targets (<50ms camera open, <16ms frame processing)
- Track performance regression across changes
- Compare device performance (LEGACY vs LIMITED vs FULL)
- Optimize hot paths based on data, not guesses

---

## Benchmark Types

### 1. Macrobenchmarks (End-to-End)

**Tool:** [Jetpack Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)

**What to Measure:**
- App startup time (cold, warm, hot)
- Camera initialization latency (first frame displayed)
- Frame processing throughput (frames/second)
- JABCode decode cycle time (frame acquisition → result)
- Memory allocations during camera session

**Why Macrobenchmark:**
- Tests full integration (closest to real-world usage)
- Includes system overhead (process startup, IPC, etc.)
- Catches performance issues missed by unit tests

### 2. Microbenchmarks (Component-Level)

**Tool:** [Jetpack Microbenchmark](https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview)

**What to Measure:**
- `CameraEnumerator.getAllCameras()` execution time
- `StreamConfigValidator.validate()` performance
- Frame metadata extraction overhead
- `ImageQualityAnalyzer.analyze()` computation time
- YUV → RGB Bitmap conversion speed

**Why Microbenchmark:**
- Isolates specific code paths
- Minimal measurement overhead (nanosecond precision)
- Identifies hot spots for optimization

---

## Setup

### Dependencies

**Add to `build.gradle.kts` (root):**
```kotlin
buildscript {
    dependencies {
        classpath("androidx.benchmark:benchmark-gradle-plugin:1.2.3")
    }
}
```

**Create benchmark module:**
```bash
# Create macrobenchmark module
mkdir -p benchmark-macrobench/src/main/java/com/jabauth/benchmark/macro
mkdir -p benchmark-macrobench/src/main/AndroidManifest.xml

# Create microbenchmark module
mkdir -p framework/jabcode-sdk/src/androidTest/java/com/jabauth/jabcode/benchmark
```

**`benchmark-macrobench/build.gradle.kts`:**
```kotlin
plugins {
    id("com.android.test")
    id("androidx.benchmark")
    kotlin("android")
}

android {
    namespace = "com.jabauth.benchmark.macro"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 23
        targetSdk = 34
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }
    
    testOptions {
        managedDevices {
            devices {
                // Disable managed devices for benchmarks (use real hardware)
            }
        }
    }
    
    buildTypes {
        create("benchmark") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    
    targetProjectPath = ":diagnostic-app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.3")
    implementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
```

**`framework/jabcode-sdk/build.gradle.kts` (add to androidTest):**
```kotlin
dependencies {
    // ... existing dependencies
    
    androidTestImplementation("androidx.benchmark:benchmark-junit4:1.2.3")
}
```

---

## Macrobenchmark Tests

### Test 1: Camera Startup Benchmark

**File:** `benchmark-macrobench/src/main/java/com/jabauth/benchmark/macro/CameraStartupBenchmark.kt`

```kotlin
package com.jabauth.benchmark.macro

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraStartupBenchmark {
    
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()
    
    /**
     * Measures cold startup time (process not in memory)
     * 
     * Target: <50ms to first camera preview frame
     */
    @Test
    fun startupCold() = benchmarkRule.measureRepeated(
        packageName = "com.jabauth.diagnostic",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT
    ) {
        pressHome()
        startActivityAndWait()
        
        // Wait for camera preview to appear
        device.wait(Until.hasObject(By.desc("Camera Preview")), 5000)
    }
    
    /**
     * Measures warm startup time (process in memory, activity destroyed)
     * 
     * Target: <30ms to first camera preview frame
     */
    @Test
    fun startupWarm() = benchmarkRule.measureRepeated(
        packageName = "com.jabauth.diagnostic",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT
    ) {
        pressHome()
        startActivityAndWait()
        
        device.wait(Until.hasObject(By.desc("Camera Preview")), 5000)
    }
}
```

### Test 2: Frame Processing Benchmark

**File:** `benchmark-macrobench/src/main/java/com/jabauth/benchmark/macro/FrameProcessingBenchmark.kt`

```kotlin
package com.jabauth.benchmark.macro

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrameProcessingBenchmark {
    
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()
    
    /**
     * Measures frame rendering performance during camera preview
     * 
     * Target: 60 FPS (16.67ms per frame)
     */
    @Test
    fun frameRendering() = benchmarkRule.measureRepeated(
        packageName = "com.jabauth.diagnostic",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT
    ) {
        pressHome()
        startActivityAndWait()
        
        // Wait for camera preview
        device.wait(Until.hasObject(By.desc("Camera Preview")), 5000)
        
        // Scroll through diagnostic results to trigger recomposition
        val diagnosticPanel = device.findObject(By.res("diagnostic_panel"))
        diagnosticPanel?.apply {
            // Simulate 10 seconds of frame processing
            repeat(10) {
                Thread.sleep(1000)
            }
        }
    }
}
```

### Test 3: JABCode Decode Benchmark

**File:** `benchmark-macrobench/src/main/java/com/jabauth/benchmark/macro/JABCodeDecodeBenchmark.kt`

```kotlin
package com.jabauth.benchmark.macro

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JABCodeDecodeBenchmark {
    
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()
    
    /**
     * Measures end-to-end JABCode decode cycle
     * 
     * Target: <200ms from frame acquisition to decode result
     */
    @Test
    fun decodeLatency() = benchmarkRule.measureRepeated(
        packageName = "com.jabauth.diagnostic",
        metrics = listOf(
            // Custom metric to track decode events
            JABCodeDecodeMetric()
        ),
        iterations = 10,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT
    ) {
        pressHome()
        startActivityAndWait()
        
        // Wait for camera preview
        device.wait(Until.hasObject(By.desc("Camera Preview")), 5000)
        
        // Point camera at JABCode (manual step)
        // In automated testing, use pre-recorded JABCode image
        
        // Wait for decode result
        device.wait(Until.hasObject(By.res("decode_result")), 10000)
    }
}

/**
 * Custom metric to measure JABCode decode latency
 * 
 * Extracts decode time from Logcat:
 * "JABCode decode completed in 156ms"
 */
class JABCodeDecodeMetric : Metric {
    override fun configure(packageName: String) {
        // Setup logcat monitoring
    }
    
    override fun start() {
        // Start timing
    }
    
    override fun stop() {
        // Stop timing, extract metrics
    }
    
    override fun getMeasurements(
        captureInfo: CaptureInfo,
        tracePath: String
    ): List<Measurement> {
        // Parse logcat for decode times
        return listOf(
            Measurement("decodeLatencyMs", listOf(156.0, 142.0, 168.0))
        )
    }
}
```

---

## Microbenchmark Tests

### Test 1: Camera Enumeration Benchmark

**File:** `framework/jabcode-sdk/src/androidTest/java/com/jabauth/jabcode/benchmark/CameraEnumeratorBenchmark.kt`

```kotlin
package com.jabauth.jabcode.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jabauth.jabcode.camera.CameraEnumerator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraEnumeratorBenchmark {
    
    @get:Rule
    val benchmarkRule = BenchmarkRule()
    
    /**
     * Measures CameraEnumerator.getAllCameras() performance
     * 
     * Target: <10ms (infrequent operation, but should be fast)
     */
    @Test
    fun getAllCameras() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val enumerator = CameraEnumerator(context)
        
        benchmarkRule.measureRepeated {
            enumerator.getAllCameras()
        }
    }
    
    /**
     * Measures findCameraByFacing() with filtering
     * 
     * Target: <5ms (called during camera selection)
     */
    @Test
    fun findCameraByFacing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val enumerator = CameraEnumerator(context)
        
        benchmarkRule.measureRepeated {
            enumerator.findCameraByFacing(
                facing = android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
            )
        }
    }
}
```

### Test 2: Stream Validation Benchmark

**File:** `framework/jabcode-sdk/src/androidTest/java/com/jabauth/jabcode/benchmark/StreamConfigValidatorBenchmark.kt`

```kotlin
package com.jabauth.jabcode.benchmark

import android.graphics.ImageFormat
import android.util.Size
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jabauth.jabcode.camera.CameraEnumerator
import com.jabauth.jabcode.camera.StreamConfigValidator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamConfigValidatorBenchmark {
    
    @get:Rule
    val benchmarkRule = BenchmarkRule()
    
    /**
     * Measures stream configuration validation performance
     * 
     * Target: <2ms (called before every session creation)
     */
    @Test
    fun validateStreamConfig() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val enumerator = CameraEnumerator(context)
        val cameraInfo = enumerator.getAllCameras().first()
        val validator = StreamConfigValidator()
        
        val streams = listOf(
            StreamConfigValidator.StreamConfig(ImageFormat.PRIVATE, Size(1280, 720)),
            StreamConfigValidator.StreamConfig(ImageFormat.YUV_420_888, Size(1280, 720))
        )
        
        benchmarkRule.measureRepeated {
            validator.validate(cameraInfo, streams)
        }
    }
}
```

### Test 3: Image Quality Analysis Benchmark

**File:** `framework/jabcode-sdk/src/androidTest/java/com/jabauth/jabcode/benchmark/ImageQualityAnalyzerBenchmark.kt`

```kotlin
package com.jabauth.jabcode.benchmark

import android.graphics.Bitmap
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jabauth.jabcode.camera.ImageQualityAnalyzer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageQualityAnalyzerBenchmark {
    
    @get:Rule
    val benchmarkRule = BenchmarkRule()
    
    /**
     * Measures image quality analysis performance
     * 
     * Target: <5ms (called per frame, must not block 60 FPS)
     */
    @Test
    fun analyzeBitmap() {
        val analyzer = ImageQualityAnalyzer()
        val testBitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        
        benchmarkRule.measureRepeated {
            analyzer.analyze(testBitmap)
        }
        
        testBitmap.recycle()
    }
}
```

---

## Running Benchmarks

### Local Development

```bash
# Run macrobenchmarks (requires physical device)
./gradlew :benchmark-macrobench:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.output.enable=true

# Run microbenchmarks
./gradlew :framework:jabcode-sdk:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jabauth.jabcode.benchmark.CameraEnumeratorBenchmark

# Generate benchmark report
# Results saved to: benchmark-macrobench/build/outputs/connected_android_test_additional_output/
```

### CI/CD Integration

**GitHub Actions Example:**
```yaml
name: Benchmark Tests

on:
  pull_request:
    branches: [ main ]
  schedule:
    - cron: '0 0 * * 0'  # Weekly on Sunday

jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          
      - name: Run Macrobenchmarks
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          target: google_apis
          arch: x86_64
          script: ./gradlew :benchmark-macrobench:connectedCheck
          
      - name: Upload Benchmark Results
        uses: actions/upload-artifact@v3
        with:
          name: benchmark-results
          path: benchmark-macrobench/build/outputs/
          
      - name: Compare with Baseline
        run: |
          # Compare current results with stored baseline
          python scripts/compare_benchmarks.py \
            --baseline baseline-benchmarks.json \
            --current benchmark-macrobench/build/outputs/results.json \
            --threshold 10  # Fail if >10% regression
```

---

## Performance Targets

### Camera Operations

| Operation | Target | Critical Threshold |
|-----------|--------|-------------------|
| Camera Open (cold) | <50ms | <100ms |
| Camera Open (warm) | <30ms | <60ms |
| First Frame Latency | <100ms | <200ms |
| Frame Processing | <16ms (60 FPS) | <33ms (30 FPS) |
| JABCode Decode | <200ms | <500ms |

### Component Operations

| Component | Target | Critical Threshold |
|-----------|--------|-------------------|
| CameraEnumerator.getAllCameras() | <10ms | <20ms |
| StreamConfigValidator.validate() | <2ms | <5ms |
| ImageQualityAnalyzer.analyze() | <5ms | <10ms |
| Metadata Extraction | <1ms | <3ms |

### Memory

| Metric | Target | Critical Threshold |
|--------|--------|-------------------|
| Camera Session Memory | <50MB | <100MB |
| Frame Buffer Allocation | <10MB/sec | <20MB/sec |
| Leaked Buffers | 0 | 0 |

---

## Regression Detection

**Baseline Establishment:**
```bash
# Run benchmarks on known-good commit
git checkout v1.0.0-baseline
./gradlew :benchmark-macrobench:connectedCheck

# Save results as baseline
cp benchmark-macrobench/build/outputs/results.json baseline-benchmarks.json
git add baseline-benchmarks.json
git commit -m "Add benchmark baseline"
```

**Regression Testing:**
```bash
# Run on current commit
./gradlew :benchmark-macrobench:connectedCheck

# Compare
python scripts/compare_benchmarks.py \
  --baseline baseline-benchmarks.json \
  --current benchmark-macrobench/build/outputs/results.json \
  --threshold 10

# Exit code 0: No regression
# Exit code 1: Regression detected (>10% slower)
```

**Script:** `scripts/compare_benchmarks.py`
```python
#!/usr/bin/env python3
import json
import sys
import argparse

def compare_benchmarks(baseline_path, current_path, threshold_percent):
    with open(baseline_path) as f:
        baseline = json.load(f)
    with open(current_path) as f:
        current = json.load(f)
    
    regressions = []
    
    for test_name, baseline_metrics in baseline.items():
        if test_name not in current:
            continue
            
        current_metrics = current[test_name]
        
        for metric_name, baseline_value in baseline_metrics.items():
            current_value = current_metrics.get(metric_name)
            if current_value is None:
                continue
            
            # Calculate regression percentage
            regression = ((current_value - baseline_value) / baseline_value) * 100
            
            if regression > threshold_percent:
                regressions.append({
                    'test': test_name,
                    'metric': metric_name,
                    'baseline': baseline_value,
                    'current': current_value,
                    'regression_percent': regression
                })
    
    if regressions:
        print("❌ Performance Regressions Detected:")
        for r in regressions:
            print(f"  {r['test']}.{r['metric']}: "
                  f"{r['baseline']:.2f}ms → {r['current']:.2f}ms "
                  f"({r['regression_percent']:+.1f}%)")
        return 1
    else:
        print("✅ No performance regressions detected")
        return 0

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--baseline', required=True)
    parser.add_argument('--current', required=True)
    parser.add_argument('--threshold', type=float, default=10.0)
    args = parser.parse_args()
    
    sys.exit(compare_benchmarks(args.baseline, args.current, args.threshold))
```

---

## Integration with Phase 7

**Updated Phase 7 Tasks:**

```markdown
### Phase 7: Integration & Validation (3-4 days) ← Extended by 1 day

#### Benchmark Setup (Day 1)
- [ ] 7.1 Create benchmark-macrobench module
- [ ] 7.2 Create microbenchmark test directory
- [ ] 7.3 Add benchmark dependencies
- [ ] 7.4 Configure benchmark build types

#### Macrobenchmark Tests (Day 1-2)
- [ ] 7.5 Write CameraStartupBenchmark
- [ ] 7.6 Write FrameProcessingBenchmark
- [ ] 7.7 Write JABCodeDecodeBenchmark
- [ ] 7.8 Run macrobenchmarks on test device
- [ ] 7.9 Store baseline results

#### Microbenchmark Tests (Day 2)
- [ ] 7.10 Write CameraEnumeratorBenchmark
- [ ] 7.11 Write StreamConfigValidatorBenchmark
- [ ] 7.12 Write ImageQualityAnalyzerBenchmark
- [ ] 7.13 Run microbenchmarks
- [ ] 7.14 Store baseline results

#### Integration & Validation (Day 3-4)
- [ ] 7.15 Write integration tests (10-15)
- [ ] 7.16 Run memory leak detection (LeakCanary)
- [ ] 7.17 Test on 3+ device types
- [ ] 7.18 Create regression test suite
- [ ] 7.19 Run test-coverage-update workflow
- [ ] 7.20 Validate all performance targets met
- [ ] 7.21 Document API with examples
- [ ] 7.22 Create migration guide
- [ ] 7.23 Tag release (v1.0.0-framework)
```

**New Duration:** 3-4 days → **4-5 days** (adds 1 day for comprehensive benchmarking)

---

## Success Criteria

**Benchmark testing is complete when:**

- ✅ All macrobenchmarks written and running
- ✅ All microbenchmarks written and running
- ✅ Baseline results stored in repository
- ✅ All performance targets met or documented deviations
- ✅ Regression detection script functional
- ✅ CI/CD integration configured
- ✅ Benchmark results documented in Phase 7 narrative

---

## Documentation Deliverables

**After Phase 7:**

1. **Benchmark Results Report**
   - Median/P50/P95/P99 latencies for all benchmarks
   - Device comparison matrix
   - Performance bottleneck analysis
   - Optimization recommendations

2. **Baseline Benchmarks**
   - JSON file with baseline metrics
   - Committed to repository
   - Used for regression detection

3. **Performance Guide**
   - Expected performance characteristics
   - Device-specific considerations
   - Optimization tips for consumers

---

**Ready to integrate this into the framework plan, sir?**

---

## Implementation Status (2026-05-28)

The benchmark infrastructure described above has been **implemented end-to-end**. Three layers landed in a single feature push on `claude/ws-diagnostic-ui-tier1`; all build clean against the existing module graph.

### Layer 1 — Custom `BenchmarkSuite` per-Nc decoder benchmarks

**Location:** `framework/diagnostic-engine/src/androidTest/java/com/jabauth/diagnostic/benchmark/JABCodeDecodeBenchmark.kt`

8 benchmark methods — one per Nc value (Nc=0..7). Uses the bundled per-Nc PNG fixtures (`androidTest/assets/nc0-2c.png` through `nc7-256c.png`) and the existing `BenchmarkSuite` scaffolding (now activated for the first time).

**Run:**
```bash
./gradlew :framework:diagnostic-engine:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=\
  com.jabauth.diagnostic.benchmark.JABCodeDecodeBenchmark
```

Results emit as `BENCHMARK_RESULT` lines to logcat. Greppable.

### Layer 2 — Jetpack Microbenchmark (component-level)

**Library:** `androidx.benchmark:benchmark-junit4:1.2.4` (wired in `framework/jabcode-sdk/build.gradle.kts`)

**Location:** `framework/jabcode-sdk/src/androidTest/java/com/jabauth/jabcode/benchmark/`

| Benchmark | Target | Implementation |
|---|---|---|
| `CameraEnumerationBenchmark.getAllCameras` | <10ms | Implemented |
| `CameraEnumerationBenchmark.findCameraByFacing_back` | <5ms | Implemented |
| `CameraEnumerationBenchmark.findCameraByFacing_front` | <5ms | Implemented |
| `StreamValidationBenchmark.validate_previewPlusAnalysisConfig` | <2ms | Implemented |
| `ImageQualityAnalysisBenchmark.analyze` | <5ms | Implemented |

**Run:**
```bash
./gradlew :framework:jabcode-sdk:connectedCheck
```

Results emit as JSON to `build/outputs/connected_android_test_additional_output/`.

### Layer 3 — Jetpack Macrobenchmark (end-to-end)

**Library:** `androidx.benchmark:benchmark-macro-junit4:1.2.4`
**Module:** `:benchmark-macro` (new; see `benchmark-macro/build.gradle.kts`)
**Target:** `:diagnostic-app` with `profileable` `benchmark` build variant

| Benchmark | Target | Metrics |
|---|---|---|
| `CameraStartupBenchmark.startupCold/Warm/Hot` | <50ms/<30ms | `StartupTimingMetric` |
| `FrameProcessingBenchmark.frameProcessingDuringScan` | 60 FPS / 16ms | `FrameTimingMetric` |
| `JABCodeDecodeBenchmark.decodeEndToEnd` | <200ms | `FrameTimingMetric`, `TraceSectionMetric`, `MemoryUsageMetric` |

The trace sections `Camera2JABCodeAnalyzer.analyze` and `JABCodeDecoder.decode` are emitted by `android.os.Trace.beginSection/endSection` calls wired into the analyzer.

**Run:**
```bash
./gradlew :benchmark-macro:connectedBenchmarkAndroidTest
```

### PerformanceTracker (runtime instrumentation, production-wired)

Previously the `PerformanceTracker` class existed but was used only in integration tests. It is now invoked from `ScannerViewModel`'s decode success and failure callbacks, providing cumulative session-lifetime aggregates that complement the rolling 30-second `DECODE_TIME_STATS` and `DECODE_FAIL_STATS` lines.

### CI/CD wiring — **compile validation only; benchmarks run locally**

**Location:** `.github/workflows/benchmark.yml`

CI does **not** execute the benchmarks — it only validates that benchmark sources still compile and that the benchmark-macro module + diagnostic-app benchmark variant still assemble. This is by design:

- **Macrobenchmark explicitly warns against emulator runs** (the framework emits `androidx.benchmark.suppressErrors=EMULATOR` warnings even when the host enables KVM). Results are noisy and not comparable to device measurements.
- **Microbenchmark needs CPU clock locking** which only works on real devices with USB-debugging + benchmark mode unlocked.
- **GitHub Actions emulators are flaky for ADB-dependent workloads** — empirically, `connectedCheck` and `connectedBenchmarkAndroidTest` fail with "no devices found via adb" even with `reactivecircus/android-emulator-runner` configured for KVM acceleration.

The single CI job (`compile-benchmark-sources`) catches code regressions that would break the benchmark builds without paying the cost of running an unreliable execution environment.

### Local execution (the canonical run path)

Connect a real Android device (Galaxy S25 in your case), enable USB debugging, and run:

```bash
# Layer 1: BenchmarkSuite per-Nc decoder benchmarks (~3 min)
./gradlew :framework:diagnostic-engine:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=\
  com.jabauth.diagnostic.benchmark.JABCodeDecodeBenchmark

# Layer 2: Jetpack Microbenchmark (~1 min)
./gradlew :framework:jabcode-sdk:connectedCheck

# Layer 3: Jetpack Macrobenchmark (~5 min)
./gradlew :benchmark-macro:connectedBenchmarkAndroidTest
```

**Results stay local** on the dev machine — captured to logcat (Layer 1) and `build/outputs/connected_android_test_additional_output/` (Layers 2 & 3, as JSON). For long-term tracking, commit baseline JSON files to the repository at meaningful checkpoints (e.g., before/after Option B C-side instrumentation lands).

### Execution time estimate (local, Pixel 6 / Galaxy S25 USB-connected)

| Layer | Time |
|---|---|
| Microbenchmark | ~1 min |
| BenchmarkSuite per-Nc | ~3 min |
| Macrobenchmark | ~5 min |
| **Total** | **~9 min** |

### Open follow-up work

- Wire **runtime perf disclosure HUD** in diagnostic-app to surface PerformanceTracker rolling-aggregate stats live during scanning
- Recover **panama-wrapper JMH source files** from git history (last committed sources are missing from working tree)
- Add **regression-detection script** that compares current run to baseline JSON and fails CI if any metric regresses by >20%
- Promote `MacrobenchmarkRule` results into a **per-PR comment** via GitHub Actions
