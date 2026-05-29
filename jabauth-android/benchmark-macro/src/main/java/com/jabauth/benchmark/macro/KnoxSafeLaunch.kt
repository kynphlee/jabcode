package com.jabauth.benchmark.macro

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/**
 * Knox-Matrix-safe replacement for [MacrobenchmarkScope.startActivityAndWait].
 *
 * On Samsung consumer-stock firmware where `ro.build.type=user` AND
 * `ro.security.knoxmatrix=true` (Galaxy S25 with OneUI 7 / Android 16
 * being our reference case), Macrobenchmark's internal `Shell` helper
 * fails to capture stdout/stderr from `am start -W`. The activity
 * launches successfully on-device, but the test process receives empty
 * buffers, causing `IllegalStateException: Unable to confirm activity
 * launch completion []` from `MacrobenchmarkScope.amStartAndWait`.
 *
 * Root cause: the internal helper writes a temporary script to
 * `/data/local/tmp`, executes it via shell, and reads back the output
 * files. Knox's app-isolation enforcement blocks the test process from
 * reading files written by the shell user. `UiDevice.executeShellCommand`
 * uses a different UiAutomation RPC path that returns stdout directly
 * without touching the filesystem — Knox does not intercept this path.
 *
 * Trade-off accepted: we lose Macrobenchmark's launch-confirmation
 * parser, which would have given us `TotalTime` extracted from `am start`
 * stdout. We still get the `StartupTimingMetric` numbers because that
 * metric is derived from perfetto trace markers captured during the
 * measureBlock window — the launch only needs to *happen* inside the
 * window, not be parsed by Macrobench's internal Shell.
 */
internal fun MacrobenchmarkScope.startActivityViaShell(
    packageName: String,
    activityClass: String = ".MainActivity",
    timeoutMs: Long = 5_000L,
) {
    device.executeShellCommand(
        "am start -W -n $packageName/$activityClass"
    )
    device.wait(
        Until.hasObject(By.pkg(packageName).depth(0)),
        timeoutMs,
    )
}
