package com.jabauth.jabcode.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jabauth.jabcode.camera.CameraEnumerator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Jetpack Microbenchmark — measures CameraEnumerator throughput.
 *
 * Per BENCHMARK_TESTING_GUIDE.md (Phase 7, § Microbenchmark Tests):
 * - **Test 1: Camera Enumeration Benchmark**
 * - Target: <10ms (infrequent operation, but should be fast)
 * - Target (single lookup by facing): <5ms (called during camera selection)
 *
 * Uses `BenchmarkRule.measureRepeated` which provides:
 * - Automatic warmup phase before measurement starts
 * - GC suppression during measurement
 * - CPU clock locking (when device is in benchmark mode)
 * - Result reporting via `instrumentation` output stream
 *
 * **Run:**
 * ```
 * ./gradlew :framework:jabcode-sdk:connectedAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=\
 *   com.jabauth.jabcode.benchmark.CameraEnumerationBenchmark
 * ```
 */
@RunWith(AndroidJUnit4::class)
class CameraEnumerationBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val enumerator = CameraEnumerator(context)

    @Test
    fun getAllCameras() = benchmarkRule.measureRepeated {
        enumerator.getAllCameras()
    }

    @Test
    fun findCameraByFacing_back() = benchmarkRule.measureRepeated {
        enumerator.findCameraByFacing(android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK)
    }

    @Test
    fun findCameraByFacing_front() = benchmarkRule.measureRepeated {
        enumerator.findCameraByFacing(android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT)
    }
}
