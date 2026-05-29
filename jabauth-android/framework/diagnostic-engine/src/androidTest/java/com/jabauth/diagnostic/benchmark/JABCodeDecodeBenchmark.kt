package com.jabauth.diagnostic.benchmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.JABCodeDecoderImpl
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First concrete `BenchmarkSuite` implementation — measures **bare-decoder**
 * JABCode decode latency for each of the 8 color modes (Nc=0..7).
 *
 * This is the synthetic (non-camera-pipeline) counterpart to the
 * `DECODE_FAIL_STATS` runtime telemetry shipped in commit 5b8320a. The
 * camera-pipeline latency numbers from `DECODE_FAIL_STATS` include
 * acquisition + bitmap conversion + decode; this benchmark isolates the
 * decode step alone, using the bundled per-Nc PNG fixtures.
 *
 * **Rationale (Bayesian Council Session bc-2026-05-28-03):** This activates
 * the previously-empty `BenchmarkSuite` abstract class with its first
 * concrete impl. It also provides the contamination-detection baseline for
 * Option (B) C-side PartI instrumentation — before/after deltas show
 * whether any new C-side logging meaningfully degrades decode throughput.
 *
 * **Run locally:**
 * ```
 * ./gradlew :framework:diagnostic-engine:connectedAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=\
 *     com.jabauth.diagnostic.benchmark.JABCodeDecodeBenchmark
 * ```
 *
 * **Results format:** Each test emits a `BENCHMARK_RESULT` log line at I level
 * containing mean / median / min / max / stddev. Greppable from logcat.
 *
 * **Performance targets** (from `BENCHMARK_TESTING_GUIDE.md`):
 * - Decode latency: <200ms target, <500ms hard limit
 * - LDPC iterations: <150ms (mobile-spec/03-tdd-benchmarks.md)
 */
@RunWith(AndroidJUnit4::class)
class JABCodeDecodeBenchmark : BenchmarkSuite() {

    companion object {
        private const val TAG = "JABCodeDecodeBenchmark"

        // Decode timeout for each attempt. Wide enough to capture even
        // worst-case Nc=6 (median 360ms in screen scans) without timing
        // out the benchmark itself.
        private const val DECODE_TIMEOUT_MS = 2000L
    }

    // Slightly more warmup iterations than the BenchmarkSuite default —
    // JIT warmup matters for the JNI call path, and decode is heavy enough
    // that 3 measurement iters would be noisy.
    override val defaultWarmupIterations: Int = 5
    override val defaultMeasurementIterations: Int = 15

    private lateinit var context: Context
    private lateinit var decoder: JABCodeDecoderImpl
    private lateinit var fixtures: Map<Int, Bitmap>

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        decoder = JABCodeDecoderImpl()
        fixtures = (0..7).associateWith { nc ->
            loadFixture(nc)
        }
    }

    private fun loadFixture(nc: Int): Bitmap {
        val colorCount = 1 shl (nc + 1)  // 2,4,8,16,32,64,128,256
        val name = "nc$nc-${colorCount}c.png"
        context.assets.open(name).use { stream ->
            return BitmapFactory.decodeStream(stream)
                ?: error("Fixture $name failed to decode into a Bitmap")
        }
    }

    private fun runDecodeBenchmark(nc: Int): BenchmarkResult {
        val fixture = fixtures.getValue(nc)
        val options = DecodeOptions(timeout = DECODE_TIMEOUT_MS)
        val result = runBenchmark("decode_nc$nc") {
            decoder.decode(fixture, options)
        }
        Log.i(TAG, "BENCHMARK_RESULT $result")
        return result
    }

    @Test fun benchmarkDecodeNc0_2color()    { runDecodeBenchmark(nc = 0) }
    @Test fun benchmarkDecodeNc1_4color()    { runDecodeBenchmark(nc = 1) }
    @Test fun benchmarkDecodeNc2_8color()    { runDecodeBenchmark(nc = 2) }
    @Test fun benchmarkDecodeNc3_16color()   { runDecodeBenchmark(nc = 3) }
    @Test fun benchmarkDecodeNc4_32color()   { runDecodeBenchmark(nc = 4) }
    @Test fun benchmarkDecodeNc5_64color()   { runDecodeBenchmark(nc = 5) }
    @Test fun benchmarkDecodeNc6_128color()  { runDecodeBenchmark(nc = 6) }
    @Test fun benchmarkDecodeNc7_256color()  { runDecodeBenchmark(nc = 7) }
}
