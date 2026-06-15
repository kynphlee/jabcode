package com.jabauth.benchmark.macro

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Ignore
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
 * Metrics: end-to-end `FrameTimingMetric` plus per-stage `TraceSectionMetric`
 * for `Camera2JABCodeAnalyzer.analyze` and `JABCodeDecoder.decode` (the
 * `Trace.beginSection(...)` markers already exist in production) and
 * `MemoryUsageMetric`. The earlier `0 found for frameDurationCpuMs` failure was a
 * symptom of the camera-pipeline stall — the analyzer held the ImageReader buffer
 * across the long decode, exhausting the pool — now fixed in Camera2JABCodeAnalyzer
 * by closing the Image immediately after the bitmap copy.
 */
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

    // Compiles, runs green, and measures memory — but the analyze/decode trace sections
    // read 0 under automation, so it cannot measure real decode latency on a static rig:
    //   1. FrameTimingMetric finds 0 Choreographer frames on the preview Surface (hence
    //      TraceSectionMetric below instead);
    //   2. the analyzer is motion-throttled — ScannerViewModel.analyzeFrame skips analyze()
    //      when !isStable(), and a perfectly-still benchmark device reports not-stable, so
    //      analyzeCount/decodeCount = 0;
    //   3. even if it ran, the camera sees no JABCode, so decode-attempts would be the
    //      failure path, not real decode latency.
    // The metric design here (TraceSectionMetric for analyze/decode + MemoryUsageMetric) is
    // correct; re-enable on a rig that presents a JABCode and induces motion. For mobile
    // DECODE latency at server rigor, use a synthetic-bitmap JNI microbenchmark (Suite B) —
    // controllable input, no camera/motion/automation confounds.
    @Ignore("Live-camera macrobenchmark can't measure decode latency under automation; see comment above. Use Suite B (synthetic-bitmap JNI) for mobile decode latency.")
    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun decodeEndToEnd() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            // Per-stage pipeline timing via the trace sections that already exist in
            // Camera2JABCodeAnalyzer (analyze + JABCodeDecoder.decode), plus peak memory.
            // FrameTimingMetric is intentionally omitted: the scanner is a camera-preview
            // Surface that doesn't drive Choreographer UI frames, so FrameTimingMetric
            // finds 0 frames and aborts (the StartupTimingMetric benchmarks pass on this
            // app; the FrameTiming ones don't). The trace sections measure the actual
            // analyze/decode work — which is the end-to-end signal we want — and they
            // populate because the camera fix keeps the analyze loop running.
            TraceSectionMetric("Camera2JABCodeAnalyzer.analyze"),
            TraceSectionMetric("JABCodeDecoder.decode"),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Max)
        ),
        compilationMode = CompilationMode.None(),
        iterations = 3,
        startupMode = StartupMode.WARM,
        setupBlock = {
            // Pre-grant CAMERA permission — see CameraStartupBenchmark
            // for why this is required for activity-launch detection.
            device.executeShellCommand(
                "pm grant $TARGET_PACKAGE android.permission.CAMERA"
            )
            pressHome()
            startActivityViaShell(TARGET_PACKAGE)
            device.wait(Until.findObject(By.desc(SCANNER_TAB_DESC)), NAV_TIMEOUT_MS)
                ?.click()
        }
    ) {
        Thread.sleep(SCAN_DURATION_MS)
    }
}
