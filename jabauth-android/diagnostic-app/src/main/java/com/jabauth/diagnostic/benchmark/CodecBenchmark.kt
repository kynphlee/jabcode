package com.jabauth.diagnostic.benchmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.jabauth.jabcode.ColorMode
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.EncodeOptions
import com.jabauth.jabcode.JABCodeDecoderImpl
import com.jabauth.jabcode.JABCodeEncoderImpl

/**
 * "Suite B" — in-app, tap-to-run codec benchmark covering the full JABCode
 * colour-mode sweep (Nc=0..7) for both **decode** and **encode**.
 *
 * Decode is the dominant mobile latency concern (the camera→decode path runs on
 * every scanned frame), so it is measured against the bundled per-Nc PNG
 * fixtures in `assets/benchmark/`. Encode is now also measured on-device: with
 * the encoder-JNI wiring (`JABCodeMobile.nativeEncodeToBitmap`), the shipped
 * `libjabcode-mobile.so` exports an encode bridge, so [JABCodeEncoderImpl] runs
 * natively instead of throwing UnsatisfiedLinkError. Each mode encodes a fixed
 * representative payload so encode latencies are comparable across colour modes
 * (more colours ⇒ fewer modules for the same payload).
 *
 * This is the runnable-from-the-installed-app counterpart to the androidTest
 * [com.jabauth.diagnostic.benchmark.JABCodeDecodeBenchmark] (which runs only
 * under instrumentation). Because on-device benchmark harnesses uninstall
 * instrumentation packages between runs, the diagnostic app needs its own
 * in-process benchmark that survives reinstall cycles and needs no camera.
 *
 * **Reuse:** Extends [BenchmarkSuite] for warmup+measure timing statistics,
 * exactly like the androidTest decode benchmark. Warmup 5 / measurement 15 —
 * matching that suite so decode numbers are directly comparable.
 *
 * **Output:** Each op emits one greppable JSON line to logcat at I level under
 * tag [TAG], prefixed `BENCHMARK_JSON` (`"op":"decode"` / `"op":"encode"`).
 * Decoded bytes and encode payloads are never logged, keeping the project-wide
 * habit of not surfacing payloads.
 *
 * **Failure isolation:** Each (Nc, op) is guarded in try/catch so one cell's
 * failure (e.g. a missing or unreadable fixture) doesn't abort the rest of the
 * sweep — that cell is recorded with `ok=false` and an error string instead.
 */
class CodecBenchmark : BenchmarkSuite() {

    companion object {
        const val TAG = "CodecBenchmark"

        /** Asset subdirectory holding the per-Nc PNG fixtures. */
        private const val ASSET_DIR = "benchmark"

        /** Wide enough to capture worst-case Nc decode without self-timeout. */
        private const val DECODE_TIMEOUT_MS = 2000L

        private const val NC_RANGE_START = 0
        private const val NC_RANGE_END = 7

        /**
         * Fixed payload encoded at every colour mode. Holding the payload
         * constant makes encode latencies comparable across Nc (more colours
         * pack more bits per module, so the same bytes need fewer modules).
         */
        private val BENCH_PAYLOAD =
            "JABCode-COA-benchmark-payload-0123456789".toByteArray()
    }

    // Match the androidTest decode benchmark so decode latencies line up.
    override val defaultWarmupIterations: Int = 5
    override val defaultMeasurementIterations: Int = 15

    private val decoder = JABCodeDecoderImpl()
    private val encoder = JABCodeEncoderImpl()

    /**
     * Run the full Nc=0..7 sweep for both decode and encode.
     *
     * For each mode: benchmarks [JABCodeDecoderImpl.decode] on the bundled
     * fixture, then [JABCodeEncoderImpl.encode] on [BENCH_PAYLOAD]. Emits one
     * JSON line per measured benchmark (`decode_ncN` / `encode_ncN`).
     *
     * @param context any Context (used only for `assets`); safe to pass the
     *   application context.
     * @return one [BenchmarkResult] per *successful* (mode, op) — up to 16.
     *   Cells that fail are logged with `ok=false` and omitted from the list.
     */
    fun run(context: Context): List<BenchmarkResult> {
        val results = mutableListOf<BenchmarkResult>()

        for (nc in NC_RANGE_START..NC_RANGE_END) {
            val colorCount = 1 shl (nc + 1) // 2,4,8,16,32,64,128,256
            val mode = ColorMode.fromValue(colorCount)

            try {
                val fixture = loadFixture(context, nc, colorCount)
                val decodeResult = runBenchmark("decode_nc$nc") {
                    decoder.decode(fixture, DecodeOptions(timeout = DECODE_TIMEOUT_MS))
                }
                logJson(nc, mode, "decode", decodeResult, ok = true, error = null)
                results.add(decodeResult)
            } catch (t: Throwable) {
                logFailure(nc, mode, "decode", t)
            }

            try {
                val encodeResult = runBenchmark("encode_nc$nc") {
                    encoder.encode(BENCH_PAYLOAD, EncodeOptions(colorMode = mode))
                }
                logJson(nc, mode, "encode", encodeResult, ok = true, error = null)
                results.add(encodeResult)
            } catch (t: Throwable) {
                logFailure(nc, mode, "encode", t)
            }
        }

        return results
    }

    private fun loadFixture(context: Context, nc: Int, colorCount: Int): Bitmap {
        val name = "$ASSET_DIR/nc$nc-${colorCount}c.png"
        context.assets.open(name).use { stream ->
            return BitmapFactory.decodeStream(stream)
                ?: error("Fixture $name failed to decode into a Bitmap")
        }
    }

    private fun logFailure(nc: Int, mode: ColorMode, op: String, t: Throwable) {
        Log.w(TAG, "BENCHMARK_JSON ${failureJson(nc, mode, op, t.message ?: t.javaClass.simpleName)}")
    }

    private fun logJson(
        nc: Int,
        mode: ColorMode,
        op: String,
        result: BenchmarkResult,
        ok: Boolean,
        error: String?
    ) {
        Log.i(TAG, "BENCHMARK_JSON ${resultJson(nc, mode, op, result, ok, error)}")
    }

    private fun resultJson(
        nc: Int,
        mode: ColorMode,
        op: String,
        r: BenchmarkResult,
        ok: Boolean,
        error: String?
    ): String = buildString {
        append('{')
        append("\"op\":\"").append(op).append("\",")
        append("\"nc\":").append(nc).append(',')
        append("\"colors\":").append(mode.value).append(',')
        append("\"ok\":").append(ok).append(',')
        append("\"warmup\":").append(r.warmupIterations).append(',')
        append("\"iterations\":").append(r.measurementIterations).append(',')
        append("\"mean_ms\":").append(fmt(r.meanTimeMs)).append(',')
        // median == p50; emitted under both keys for grep convenience.
        append("\"median_ms\":").append(fmt(r.medianTimeMs)).append(',')
        append("\"p50_ms\":").append(fmt(r.medianTimeMs)).append(',')
        append("\"min_ms\":").append(fmt(r.minTimeMs)).append(',')
        append("\"max_ms\":").append(fmt(r.maxTimeMs)).append(',')
        append("\"stddev_ms\":").append(fmt(r.stdDevMs)).append(',')
        append("\"error\":").append(jsonStringOrNull(error))
        append('}')
    }

    private fun failureJson(nc: Int, mode: ColorMode, op: String, error: String): String =
        buildString {
            append('{')
            append("\"op\":\"").append(op).append("\",")
            append("\"nc\":").append(nc).append(',')
            append("\"colors\":").append(mode.value).append(',')
            append("\"ok\":false,")
            append("\"error\":").append(jsonStringOrNull(error))
            append('}')
        }

    private fun fmt(v: Double): String = "%.2f".format(v)

    private fun jsonStringOrNull(s: String?): String =
        if (s == null) "null" else "\"${escape(s)}\""

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
}
