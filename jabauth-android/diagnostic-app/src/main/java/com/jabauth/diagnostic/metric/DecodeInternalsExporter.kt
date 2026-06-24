package com.jabauth.diagnostic.metric

import java.io.File

/**
 * Stage 3 structured file exporter — promotes the native decoder's diag-verbose forensic markers from
 * free-text logcat into durable, typed [DecodeInternals] records on disk, written alongside the
 * Stage-1b [DeviceVerifyAttempt] outcome export.
 *
 * <h2>The capture flow</h2>
 * 1. The caller turns markers on for a capture window (`DiagnosticControl.setDiagVerbose(true)`).
 * 2. It collects the marker lines the decoder/detector emit for one decode attempt (the lines between
 *    a `DETECT SUCCESS` and its terminal `DECODE_OK`/`FAIL_ATTR`).
 * 3. It calls [capture] with those lines; the exporter parses them via [DecodeInternals.parse] and
 *    appends one JSONL record. The original logcat lines are untouched — this is a durable, queryable
 *    mirror, not a replacement.
 *
 * <h2>Pairs with the outcome export</h2>
 * The Stage-1b [DeviceVerifyAttemptExporter] writes one OUTCOME row per attempt
 * (`device-verify-attempts.jsonl`). This exporter writes one INTERNALS record per attempt
 * ([INTERNALS_FILE]) in a sibling file under the same export dir, so the pair answers both "what was
 * the outcome" and "why" for the same decode. They share the `failAttr` axis: a
 * [DeviceVerifyAttempt] row whose `failAttr=CLASSIFICATION` is explained by the matching
 * [DecodeInternals] (learned palette, Part II result, DETECT status).
 *
 * <h2>Pure-Kotlin core — JVM-unit-testable</h2>
 * Takes a plain [File] directory and uses only `java.io`, so it runs under a plain JVM unit test with
 * a temp dir — no Android `Context`, no instrumentation, no NDK. The Android side hands it the right
 * directory (the same app-scoped export dir Stage-1b resolves — see [androidDecodeInternalsExporter]).
 *
 * <h2>Telemetry only</h2>
 * Every captured field is a coordinate, module size, palette RGB, index, status code, or outcome
 * token — exactly what the diag markers already print. No decoded payload, key, or secret is ever
 * passed in or written; [DecodeInternals] has no field for one, and unrecognized lines are dropped.
 *
 * @param dir directory the JSONL file is written into; created on first write if absent.
 */
class DecodeInternalsExporter(private val dir: File) {

    private val jsonlFile: File get() = File(dir, INTERNALS_FILE)

    /**
     * Parse one decode window's marker lines and append the resulting [DecodeInternals] as one JSONL
     * record. Returns the parsed record so callers can inspect / log it (e.g. surface the DETECT
     * status or learned palette on screen). The terminal verdict is [DecodeInternals.TerminalOutcome]
     * — an [DecodeInternals.TerminalOutcome.UNKNOWN] record (no terminal marker in the window) is
     * still written, since "the window closed without a verdict" is itself diagnostic.
     */
    @Synchronized
    fun capture(markerLines: List<String>): DecodeInternals {
        val internals = DecodeInternals.parse(markerLines)
        append(internals)
        return internals
    }

    /** Append an already-parsed [DecodeInternals] record as one JSONL line. */
    @Synchronized
    fun append(internals: DecodeInternals): DecodeInternals {
        dir.mkdirs()
        jsonlFile.appendText(internals.toJson() + "\n")
        return internals
    }

    /**
     * Split a flat run of marker lines (e.g. one `logcat -d` dump of the JABCode tags) into per-decode
     * windows and [capture] each, returning one [DecodeInternals] per window in order. A window opens
     * on each `DETECT SUCCESS` line and runs up to (but not including) the next one — every attempt the
     * decoder logged in the dump becomes one record. Any lines before the first `DETECT SUCCESS` (a
     * detection that never locked, hence no internals to speak of) are dropped.
     */
    @Synchronized
    fun captureAll(dumpLines: List<String>): List<DecodeInternals> =
        splitWindows(dumpLines).map { capture(it) }

    /** Absolute path of the JSONL sink (for surfacing to the user / logs). */
    fun jsonlPath(): String = jsonlFile.absolutePath

    companion object {
        /** Decode-internals sink: one [DecodeInternals.toJson] per line, sibling to the outcome JSONL. */
        const val INTERNALS_FILE = "decode-internals.jsonl"

        /**
         * Slice a flat marker dump into per-decode windows, each beginning at a `DETECT SUCCESS` line.
         * Pure (operates on a `List<String>`) so the windowing is unit-testable without a device. A
         * line is treated as a window boundary iff it contains the `DETECT SUCCESS` token, matching the
         * detector's per-attempt success marker.
         */
        internal fun splitWindows(lines: List<String>): List<List<String>> {
            val windows = mutableListOf<List<String>>()
            var current: MutableList<String>? = null
            for (line in lines) {
                if (line.contains("DETECT SUCCESS")) {
                    current?.let { windows.add(it) }
                    current = mutableListOf(line)
                } else {
                    current?.add(line)
                }
            }
            current?.let { windows.add(it) }
            return windows
        }
    }
}
