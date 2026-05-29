package com.jabauth.benchmark.macro

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark — measures end-to-end app startup latency including the
 * first camera preview frame becoming available.
 *
 * Per BENCHMARK_TESTING_GUIDE.md (Phase 7, § Macrobenchmark Tests):
 * - **Test 1: Camera Startup Benchmark**
 * - Target (cold): <50ms to first camera preview frame
 * - Target (warm): <30ms to first camera preview frame
 *
 * Macrobenchmark measures the full process startup → activity creation →
 * camera initialization → first frame chain. The numbers include OS
 * overhead and IPC costs that microbenchmarks miss.
 *
 * **Note:** This benchmark measures `timeToInitialDisplay` which is the
 * standard Macrobenchmark startup metric. Reaching the camera preview
 * frame specifically requires the diagnostic-app to navigate to the
 * Scanner screen automatically on startup, or for this benchmark to
 * drive the navigation via UiAutomator (which adds variance).
 */
@RunWith(AndroidJUnit4::class)
class CameraStartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    companion object {
        private const val TARGET_PACKAGE = "com.jabauth.diagnostic"
        private const val SCANNER_TAB_DESC = "Scanner"
        private const val STARTUP_TIMEOUT_MS = 10_000L

        /**
         * Pre-grant CAMERA permission so the activity launch isn't
         * blocked by the system permission dialog. Without this,
         * Macrobenchmark's `startActivityAndWait()` times out with
         * `Unable to confirm activity launch completion` because the
         * activity never produces a measurable frame — it's stuck
         * behind the permission UI.
         *
         * Safe to call regardless of current permission state:
         * `pm grant` is idempotent.
         */
        private fun grantCameraPermission(
            scope: androidx.benchmark.macro.MacrobenchmarkScope
        ) {
            scope.device.executeShellCommand(
                "pm grant $TARGET_PACKAGE android.permission.CAMERA"
            )
        }
    }

    @Test
    fun startupCold() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = {
            grantCameraPermission(this)
            pressHome()
        }
    ) {
        startActivityAndWait()
        // Optionally drive into the Scanner tab to measure camera ready
        device.wait(Until.findObject(By.desc(SCANNER_TAB_DESC)), STARTUP_TIMEOUT_MS)
            ?.click()
    }

    @Test
    fun startupWarm() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            grantCameraPermission(this)
            pressHome()
        }
    ) {
        startActivityAndWait()
    }

    @Test
    fun startupHot() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 5,
        startupMode = StartupMode.HOT,
        setupBlock = {
            grantCameraPermission(this)
            pressHome()
        }
    ) {
        startActivityAndWait()
    }
}
