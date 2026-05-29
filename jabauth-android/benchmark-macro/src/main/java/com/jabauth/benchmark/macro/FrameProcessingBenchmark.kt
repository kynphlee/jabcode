package com.jabauth.benchmark.macro

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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
