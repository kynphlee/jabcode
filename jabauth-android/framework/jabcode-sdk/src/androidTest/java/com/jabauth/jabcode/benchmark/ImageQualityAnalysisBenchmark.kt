package com.jabauth.jabcode.benchmark

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jabauth.jabcode.camera.ImageQualityAnalyzer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Jetpack Microbenchmark — measures ImageQualityAnalyzer.analyze throughput.
 *
 * Per BENCHMARK_TESTING_GUIDE.md (Phase 7, § Microbenchmark Tests):
 * - **Test 3: Image Quality Analysis Benchmark**
 * - Target: <5ms (called per frame, must not block 60 FPS)
 *
 * Quality analysis runs once per frame at the analysis-thread cadence; if it
 * exceeds 5ms, it eats into the frame budget and degrades scan throughput.
 * This benchmark catches accidental regressions in brightness / contrast /
 * focus calculations (e.g., switching from sample-based to full-bitmap
 * pixel reads).
 *
 * Uses the bundled `nc1-4c.png` fixture — a clean 4-color JABCode with
 * known quality characteristics, providing stable input across runs.
 */
@RunWith(AndroidJUnit4::class)
class ImageQualityAnalysisBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context = InstrumentationRegistry.getInstrumentation().context
    private lateinit var analyzer: ImageQualityAnalyzer
    private lateinit var fixture: Bitmap

    @Before
    fun setup() {
        analyzer = ImageQualityAnalyzer()
        fixture = context.assets.open("nc1-4c.png").use { stream ->
            BitmapFactory.decodeStream(stream)
                ?: error("Failed to decode nc1-4c.png fixture")
        }
    }

    @Test
    fun analyze() = benchmarkRule.measureRepeated {
        analyzer.analyze(fixture)
    }
}
