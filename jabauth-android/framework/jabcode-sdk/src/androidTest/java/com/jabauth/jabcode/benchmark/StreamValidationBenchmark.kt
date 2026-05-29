package com.jabauth.jabcode.benchmark

import android.hardware.camera2.CameraCharacteristics
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jabauth.jabcode.camera.CameraEnumerator
import com.jabauth.jabcode.camera.CameraInfo
import com.jabauth.jabcode.camera.StreamConfigValidator
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Jetpack Microbenchmark — measures StreamConfigValidator.validate throughput.
 *
 * Per BENCHMARK_TESTING_GUIDE.md (Phase 7, § Microbenchmark Tests):
 * - **Test 2: Stream Validation Benchmark**
 * - Target: <2ms (called before every session creation)
 *
 * Stream validation is on the camera-open hot path — if it exceeds 2ms,
 * every session creation pays the cost. This benchmark catches regressions
 * in the validation logic (e.g., expensive characteristic queries added
 * by accident).
 */
@RunWith(AndroidJUnit4::class)
class StreamValidationBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var validator: StreamConfigValidator
    private lateinit var backCamera: CameraInfo

    @Before
    fun setup() {
        validator = StreamConfigValidator()
        val enumerator = CameraEnumerator(context)
        backCamera = enumerator.findCameraByFacing(CameraCharacteristics.LENS_FACING_BACK)
            ?: error("No back camera available on test device")
    }

    @Test
    fun validate_previewPlusAnalysisConfig() = benchmarkRule.measureRepeated {
        val config = StreamConfigValidator.previewPlusAnalysisConfig()
        validator.validate(backCamera, config)
    }
}
