package com.jabauth.benchmark.macro

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark — measures frame processing throughput during active scanning.
 *
 * Per BENCHMARK_TESTING_GUIDE.md (Phase 7, § Macrobenchmark Tests):
 * - **Test 2: Frame Processing Benchmark**
 * - Target: 60 FPS (16.67ms per frame)
 *
 * Uses Android's `FrameTimingMetric` which reports janks and frame
 * durations across the measurement window. Detected via the Choreographer
 * frame callback machinery — captures real frame-level latency from the
 * end-user's perspective.
 */
@RunWith(AndroidJUnit4::class)
class FrameProcessingBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    companion object {
        private const val TARGET_PACKAGE = "com.jabauth.diagnostic"
        private const val SCANNER_TAB_DESC = "Scanner"
        private const val NAV_TIMEOUT_MS = 5_000L
        private const val SCAN_DURATION_MS = 10_000L
    }

    @Ignore(
        "FrameTimingMetric finds 0 Choreographer frames on the camera-preview scanner " +
            "screen — the preview renders via a dedicated Surface that doesn't drive UI " +
            "frames, so this metric aborts with `0 found for frameDurationCpuMs`. " +
            "(StartupTimingMetric benchmarks pass on this app; FrameTiming ones don't, and " +
            "the camera-buffer fix in Camera2JABCodeAnalyzer didn't change that — it's a " +
            "metric/surface mismatch, not the stall.) End-to-end per-stage latency is " +
            "covered by JABCodeDecodeBenchmark via TraceSectionMetric; a true frame-rate " +
            "benchmark here needs a different signal (e.g. analyze-loop throughput)."
    )
    @Test
    fun frameProcessingDuringScan() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
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
            // Drive into Scanner so frame processing is active.
            device.wait(Until.findObject(By.desc(SCANNER_TAB_DESC)), NAV_TIMEOUT_MS)
                ?.click()
        }
    ) {
        // Allow the scanner to run for SCAN_DURATION_MS; the rule
        // captures frame-timing metrics across that window.
        Thread.sleep(SCAN_DURATION_MS)
    }
}
