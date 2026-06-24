package com.jabauth.diagnostic.metric

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File

/**
 * The app-scoped export directory both the Stage-1b outcome export and the Stage-3 internals export
 * write into. Centralized here so the two exports are guaranteed siblings — `device-verify-attempts.*`
 * and `decode-internals.jsonl` land in the same folder and correlate row-for-row.
 *
 * Lives under `getExternalFilesDir(null)/[DeviceVerifyAttemptExporter.EXPORT_SUBDIR]` — app-scoped
 * external storage, reachable over `adb pull` / the device file manager without extra permissions,
 * yet auto-removed on uninstall. Falls back to internal `filesDir` if external storage is unavailable.
 */
private fun Context.exportDir(): File {
    val base = getExternalFilesDir(null) ?: filesDir
    return File(base, DeviceVerifyAttemptExporter.EXPORT_SUBDIR)
}

/**
 * Builds a [DeviceVerifyAttemptExporter] (Stage 1b — the cross-leg OUTCOME record) rooted in the
 * shared [exportDir].
 *
 * This is one of the two Android-aware parts of the export path. The exporter itself takes a plain
 * [File] so it stays JVM-unit-testable; this helper just supplies the right on-device directory.
 */
fun Context.deviceVerifyAttemptExporter(): DeviceVerifyAttemptExporter =
    DeviceVerifyAttemptExporter(exportDir())

/**
 * Builds a [DecodeInternalsExporter] (Stage 3 — the codec INTERNALS behind an attempt) rooted in the
 * same [exportDir] as the outcome export, so `decode-internals.jsonl` sits beside
 * `device-verify-attempts.jsonl` and the "what" + "why" of a decode are pulled together.
 *
 * Like its Stage-1b sibling, the exporter itself takes a plain [File] and is JVM-unit-testable; this
 * helper is the only Android-aware bit.
 */
fun Context.androidDecodeInternalsExporter(): DecodeInternalsExporter =
    DecodeInternalsExporter(exportDir())

/**
 * Read this process's own recent JABCode decoder/detector log buffer and return the matching marker
 * lines. This is the on-device adapter that feeds the diag-verbose markers — which the C decoder emits
 * via `__android_log_print` under the `JABCodeDecoder` / `JABCodeDetector` / `JABCode` tags — into the
 * structured [DecodeInternalsExporter]. It is the one part of the Stage-3 path that must touch the
 * platform; the parsing + windowing + export are pure Kotlin and unit-tested.
 *
 * Contained by construction:
 *  - `-d` dumps the current buffer and exits — a single short-lived read, NOT a streaming/background
 *    process or daemon.
 *  - `--pid=<this process>` restricts the read to THIS app's own log lines, so nothing from other apps
 *    is ever read, and no extra permission is needed (an app may always read its own log).
 *  - the tag filter (`<tag>:<level> … *:S`) keeps only the JABCode decoder/detector lines before
 *    parsing. The `JABCodeDecoder` tag is taken at `:D` (Debug) on purpose: the C decoder emits the
 *    `[PartI_DIAG] module_colors=[…]` / `SUCCESS Nc=` lines — the per-module classification evidence
 *    this record exists to surface — via `DEBUG_LOG` at DEBUG level, while the `DETECT SUCCESS`,
 *    `FAIL_ATTR`, `DECODE_OK`, `DIAG_*` and `Nc_FALLBACK` markers are INFO. An `:I` threshold would
 *    silently drop the Part-I colour data.
 *
 * Caller contract: enable markers first (`DiagnosticControl.setDiagVerbose(true)`), run the decode(s),
 * then call this — the markers must already be in the buffer. Best-effort: any failure (no `logcat`
 * binary on the device, permission, I/O) yields an empty list and is logged, never thrown.
 *
 * @return the JABCode marker lines from the current log buffer, oldest first; empty on any failure.
 */
fun readJabCodeMarkerLines(): List<String> = runCatching {
    val cmd = arrayOf(
        "logcat", "-d", "--pid=${Process.myPid()}",
        // brief format keeps the tag + message; the parser is prefix-tolerant either way.
        "-v", "brief",
        // JABCodeDecoder at :D — the [PartI_DIAG] module-colour lines are DEBUG-level; the rest INFO.
        "JABCodeDecoder:D", "JABCodeDetector:I", "JABCode:I", "*:S"
    )
    val proc = Runtime.getRuntime().exec(cmd)
    val lines = proc.inputStream.bufferedReader().use { it.readLines() }
    proc.waitFor()
    lines
}.getOrElse {
    Log.w("DecodeInternals", "DECODE_INTERNALS logcat read failed: ${it.message}")
    emptyList()
}
