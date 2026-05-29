package com.jabauth.benchmark.macro

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark — end-to-end JABCode decode latency, camera-pipeline included.
 *
 * Per BENCHMARK_TESTING_GUIDE.md (Phase 7, § Macrobenchmark Tests):
 * - **Test 3: JABCode Decode Benchmark**
 * - Target: <200ms from frame acquisition to decode result
 *
 * Complements the synthetic per-Nc benchmarks in
 * `diagnostic-engine/.../JABCodeDecodeBenchmark.kt` (BenchmarkSuite-based).
 * That one isolates the decoder; this one measures the full pipeline:
 * ImageReader → bitmap conversion → decode → result callback.
 *
 * Uses `TraceSectionMetric` looking for the analyzer's
 * `Camera2JABCodeAnalyzer.analyze` and `JABCodeDecoder.decode` trace
 * sections. To get those sections, the production code must emit
 * `Trace.beginSection("...")` / `Trace.endSection()` calls — that's
 * follow-on work tracked in `P4 PerformanceTracker production wiring`.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class JABCodeDecodeBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    companion object {
        private const val TARGET_PACKAGE = "com.jabauth.diagnostic"
        private const val SCANNER_TAB_DESC = "Scanner"
        private const val NAV_TIMEOUT_MS = 5_000L
        private const val SCAN_DURATION_MS = 15_000L
    }

    @Test
    fun decodeEndToEnd() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            // Trace sections are emitted by Camera2JABCodeAnalyzer (wired
            // up in P4 — `android.os.Trace.beginSection(...)` calls).
            TraceSectionMetric("Camera2JABCodeAnalyzer.analyze"),
            TraceSectionMetric("JABCodeDecoder.decode"),
            // Memory profiling per phase5-performance.md § Memory Profiling.
            MemoryUsageMetric(mode = MemoryUsageMetric.Mode.Max)
        ),
        compilationMode = CompilationMode.None(),
        iterations = 3,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.findObject(By.desc(SCANNER_TAB_DESC)), NAV_TIMEOUT_MS)
                ?.click()
        }
    ) {
        Thread.sleep(SCAN_DURATION_MS)
    }
}
