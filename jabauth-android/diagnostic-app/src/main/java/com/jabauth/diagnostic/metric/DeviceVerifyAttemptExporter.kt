package com.jabauth.diagnostic.metric

import java.io.File

/**
 * Stage 1b structured file exporter — promotes the diagnostic app's existing per-attempt logcat
 * telemetry (the `QUALITY_DECODE` correlation line in `ScannerViewModel.recordAttempt`, and the
 * `BENCHMARK_JSON` Suite-B lines in `CodecBenchmark`) to durable, diffable [DeviceVerifyAttempt]
 * records on disk.
 *
 * <h2>Why files, alongside (not instead of) logcat</h2>
 * The logcat lines stay exactly as they are — they remain the live, greppable signal during a scan.
 * This exporter ADDS a durable mirror so a device run produces the same {@code VerifyAttempt} JSON the
 * host sweep produces, letting the two be diffed column-for-column offline. This is the device leg of
 * the Stage-1 "one metric schema" spine.
 *
 * <h2>Output</h2>
 * Two sibling files in a caller-provided directory (Android wires this to the app-scoped external
 * files dir — see [androidExportDir]):
 *  - [JSONL_FILE] — one [DeviceVerifyAttempt.toJson] object per line. This is the byte-exact host
 *    mirror: each line equals what the host's `ObjectMapper().writeValueAsString(verifyAttempt)`
 *    produces on the shared columns, so `diff`/`jq` line up.
 *  - [CSV_FILE] — the same records flattened to scalar columns ([DeviceVerifyAttempt.CSV_HEADER]),
 *    for spreadsheet inspection. The header is written once on first append.
 *
 * <h2>Pure-Kotlin core — JVM-unit-testable</h2>
 * The class takes a plain [File] directory and uses only `java.io`, so it runs under a plain JVM unit
 * test with a temp dir — no Android `Context`, no instrumentation, no NDK. The Android side only needs
 * to hand it the right directory via [androidExportDir].
 *
 * <h2>Telemetry only</h2>
 * Every record is built from timings / quality / outcomes — the same fields the logcat lines already
 * carry. No decoded payload, key, or secret is ever passed in or written; [DeviceVerifyAttempt] has no
 * field for one.
 *
 * @param dir directory the JSONL + CSV files are written into; created on first write if absent.
 */
class DeviceVerifyAttemptExporter(private val dir: File) {

    private val jsonlFile: File get() = File(dir, JSONL_FILE)
    private val csvFile: File get() = File(dir, CSV_FILE)

    /**
     * Append one record to both the JSONL and CSV sinks. The CSV header is written exactly once, the
     * first time the CSV file is created. Returns the record so callers can chain / inspect.
     */
    @Synchronized
    fun append(attempt: DeviceVerifyAttempt): DeviceVerifyAttempt {
        dir.mkdirs()
        jsonlFile.appendText(attempt.toJson() + "\n")
        val csv = csvFile
        if (!csv.exists()) {
            csv.appendText(DeviceVerifyAttempt.CSV_HEADER + "\n")
        }
        csv.appendText(attempt.toCsvRow() + "\n")
        return attempt
    }

    /** Append a batch of records (e.g. a full Suite-B sweep) in order. */
    @Synchronized
    fun appendAll(attempts: List<DeviceVerifyAttempt>) {
        attempts.forEach { append(it) }
    }

    /** Absolute path of the JSONL sink (for surfacing to the user / logs). */
    fun jsonlPath(): String = jsonlFile.absolutePath

    /** Absolute path of the CSV sink (for surfacing to the user / logs). */
    fun csvPath(): String = csvFile.absolutePath

    companion object {
        /** Byte-exact host-mirror sink: one [DeviceVerifyAttempt.toJson] per line. */
        const val JSONL_FILE = "device-verify-attempts.jsonl"

        /** Flattened spreadsheet-friendly sink. */
        const val CSV_FILE = "device-verify-attempts.csv"

        /** Subdirectory under the app-scoped external files dir holding the exports. */
        const val EXPORT_SUBDIR = "verify-attempts"

        /**
         * Build a record from a live-scan decode attempt — the join point in
         * `ScannerViewModel.recordAttempt`. The caller passes exactly the data that method already has
         * in hand for its `QUALITY_DECODE` line, so this is a pure promotion of existing telemetry.
         *
         * The device leg is codec-only: decode timing goes to [DeviceVerifyAttempt.StageTimingsMs.decode]
         * with pki/jwt/abe = 0, and [DeviceVerifyAttempt.authOutcome] is all-SKIP.
         *
         * @param isSuccess     whether the decode succeeded ([DeviceVerifyAttempt.Outcome.PASS]/FAIL).
         * @param decodeTimeMs  decode wall-clock in ms (0 on the exception path; passed through).
         * @param colorNumber   carrier colour count (2/4/8/.../256), or 0 if unknown (auto-detect).
         * @param failAttr      decode failure attribution — must be one of NONE/DETECT/CLASSIFICATION.
         *                      On success pass [DeviceVerifyAttempt.FailAttr.NONE].
         * @param focus         per-frame focus score, or null if no live frame / quality disabled.
         * @param brightness    per-frame brightness score, or null (same condition).
         * @param contrast      per-frame contrast score, or null (same condition).
         * @param imagePx       carrier edge length in px if known, else 0.
         */
        fun fromScanAttempt(
            isSuccess: Boolean,
            decodeTimeMs: Long,
            colorNumber: Int,
            failAttr: DeviceVerifyAttempt.FailAttr,
            focus: Float?,
            brightness: Float?,
            contrast: Float?,
            imagePx: Int = 0
        ): DeviceVerifyAttempt {
            // Quality is present only when all three live-frame metrics are available. The scan path
            // either has a full QualityMetrics snapshot or none (e.g. quality disabled, or the
            // picked-image path with no live camera frame), so this is all-or-nothing by construction.
            val quality = if (focus != null && brightness != null && contrast != null) {
                DeviceVerifyAttempt.Quality(
                    focus = focus.toDouble(),
                    brightness = brightness.toDouble(),
                    contrast = contrast.toDouble()
                )
            } else {
                null
            }
            return DeviceVerifyAttempt(
                colorNumber = colorNumber,
                // The device decode path does not surface symbol version or ecc; host schema uses 0
                // for "unknown/not probed", which is exactly this case.
                version = 0,
                ecc = 0,
                imagePx = imagePx,
                quality = quality,
                outcome = if (isSuccess) DeviceVerifyAttempt.Outcome.PASS
                else DeviceVerifyAttempt.Outcome.FAIL,
                failAttr = failAttr,
                stageTimingsMs = DeviceVerifyAttempt.StageTimingsMs(decode = decodeTimeMs.toDouble()),
                authOutcome = DeviceVerifyAttempt.AuthOutcome.ALL_SKIP
            )
        }

        /**
         * Build a record from a Suite-B decode benchmark cell — promotes the `BENCHMARK_JSON`
         * `"op":"decode"` line into the shared schema. The cell decoded a bundled PNG fixture, so the
         * outcome is PASS, there is no live-frame quality (null), and the representative decode timing
         * is the cell's median (p50) ms.
         *
         * Only decode cells map here; encode cells have no [DeviceVerifyAttempt] analogue (the host
         * schema's decode stage is a decode, not an encode) and are left to their existing
         * `BENCHMARK_JSON` logcat line.
         *
         * @param colorNumber   carrier colour count for the cell (2/4/8/.../256).
         * @param medianDecodeMs the cell's median (p50) decode time in ms.
         */
        fun fromSuiteBDecode(
            colorNumber: Int,
            medianDecodeMs: Double
        ): DeviceVerifyAttempt = DeviceVerifyAttempt(
            colorNumber = colorNumber,
            version = 0,
            ecc = 0,
            // Fixture decode from assets — no captured image, so no pixel-edge length.
            imagePx = 0,
            // No live camera frame for a fixture decode → no capture quality.
            quality = null,
            outcome = DeviceVerifyAttempt.Outcome.PASS,
            failAttr = DeviceVerifyAttempt.FailAttr.NONE,
            stageTimingsMs = DeviceVerifyAttempt.StageTimingsMs(decode = medianDecodeMs),
            authOutcome = DeviceVerifyAttempt.AuthOutcome.ALL_SKIP
        )
    }
}
