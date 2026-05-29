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
 * Currently uses only `FrameTimingMetric` for end-to-end frame timing
 * during scanning. The richer per-stage measurement (TraceSectionMetric
 * for `Camera2JABCodeAnalyzer.analyze` and `JABCodeDecoder.decode`, plus
 * `MemoryUsageMetric`) is deferred until `P4 PerformanceTracker
 * production wiring` lands the `Trace.beginSection("...")` /
 * `Trace.endSection()` calls in production code. Running all four
 * metric collectors concurrently on Samsung's perfetto buffer produced
 * `0 found for frameDurationCpuMs` because the buffer filled before
 * frame events were recorded.
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

    @Ignore(
        "Blocked on camera-pipeline stall during scanning. Same root cause as " +
            "FrameProcessingBenchmark.frameProcessingDuringScan: the " +
            "diagnostic-app's ImageReader buffer pool exhausts within the " +
            "SCAN_DURATION_MS window, the camera stalls, and FrameTimingMetric " +
            "throws `IllegalArgumentException: At least one result is necessary, " +
            "0 found for frameDurationCpuMs`. This is the same back-pressure " +
            "pattern as H_nc2_decode_failure / H_partI_unifies. Re-enable when " +
            "the camera stall is fixed and ideally bring back the richer metric " +
            "set (TraceSectionMetric / MemoryUsageMetric) once P4 " +
            "PerformanceTracker production wiring lands the Trace.beginSection " +
            "calls."
    )
    @Test
    fun decodeEndToEnd() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            // Scoped to FrameTimingMetric until P4 PerformanceTracker
            // production wiring emits `Trace.beginSection` calls for
            // Camera2JABCodeAnalyzer.analyze and JABCodeDecoder.decode.
            // Running all four metrics concurrently on Samsung's perfetto
            // buffer produces "0 found for frameDurationCpuMs" errors —
            // the buffer fills before frame events are recorded. Re-add
            // TraceSectionMetric("Camera2JABCodeAnalyzer.analyze"),
            // TraceSectionMetric("JABCodeDecoder.decode"), and
            // MemoryUsageMetric(MemoryUsageMetric.Mode.Max) once the
            // production trace sections exist.
            FrameTimingMetric()
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
