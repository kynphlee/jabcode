package com.jabauth.diagnostic.ui.capturetest

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabauth.diagnostic.benchmark.BenchmarkResult
import com.jabauth.diagnostic.benchmark.CodecBenchmark
import com.jabauth.diagnostic.metric.DeviceVerifyAttemptExporter
import com.jabauth.diagnostic.metric.deviceVerifyAttemptExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for Capture Test screen
 *
 * Manages camera stream validation and quality metrics
 */
class CaptureTestViewModel : ViewModel() {

    private val _streamState = MutableStateFlow<StreamState>(StreamState.Stopped)
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    private val _frameMetrics = MutableStateFlow<FrameMetrics?>(null)
    val frameMetrics: StateFlow<FrameMetrics?> = _frameMetrics.asStateFlow()

    private val _captureStats = MutableStateFlow(CaptureStats())
    val captureStats: StateFlow<CaptureStats> = _captureStats.asStateFlow()

    private val _benchmarkState = MutableStateFlow<BenchmarkState>(BenchmarkState.Idle)
    val benchmarkState: StateFlow<BenchmarkState> = _benchmarkState.asStateFlow()

    /**
     * Run the in-app "Suite B" codec benchmark (decode and encode across all
     * 8 colour modes, Nc=0..7) — decode against the bundled PNG fixtures, encode
     * against a fixed payload; no camera.
     *
     * Runs off the main thread on [Dispatchers.Default] (codec work is a
     * CPU-bound JNI call). Per-op JSON is also emitted to logcat by
     * [CodecBenchmark]; the returned results drive the on-screen summary.
     * Ignores re-taps while a run is in flight.
     *
     * @param context any Context (used only to read assets); pass the
     *   application context to avoid leaking an Activity.
     */
    fun runCodecBenchmark(context: Context) {
        if (_benchmarkState.value is BenchmarkState.Running) return
        val appContext = context.applicationContext
        _benchmarkState.value = BenchmarkState.Running
        viewModelScope.launch {
            val results = withContext(Dispatchers.Default) {
                CodecBenchmark().run(appContext)
            }
            // Stage 1b: promote the Suite-B decode cells to durable shared-schema
            // records, alongside the existing BENCHMARK_JSON logcat lines (which
            // CodecBenchmark still emits). Only decode cells map to the shared
            // VerifyAttempt schema (its first stage is a decode); encode cells
            // keep only their logcat line. Best-effort — an export I/O failure
            // must not break the on-screen benchmark summary.
            withContext(Dispatchers.IO) {
                runCatching {
                    val exporter = appContext.deviceVerifyAttemptExporter()
                    val records = results.mapNotNull { r ->
                        decodeColorNumber(r.benchmarkName)?.let { colorNumber ->
                            DeviceVerifyAttemptExporter.fromSuiteBDecode(
                                colorNumber = colorNumber,
                                medianDecodeMs = r.medianTimeMs
                            )
                        }
                    }
                    exporter.appendAll(records)
                    Log.i(
                        TAG,
                        "DEVICE_VERIFY_EXPORT suite-b wrote ${records.size} decode record(s) " +
                            "-> ${exporter.jsonlPath()}"
                    )
                }.onFailure { Log.w(TAG, "DEVICE_VERIFY_EXPORT suite-b export failed: ${it.message}") }
            }
            _benchmarkState.value = BenchmarkState.Done(results)
        }
    }

    /**
     * Maps a Suite-B decode benchmark name (`decode_ncN`, N=0..7) to its carrier colour count
     * (2,4,8,...,256). Returns null for non-decode cells (e.g. `encode_ncN`), which have no shared
     * VerifyAttempt analogue.
     */
    private fun decodeColorNumber(benchmarkName: String): Int? {
        if (!benchmarkName.startsWith("decode_nc")) return null
        val nc = benchmarkName.removePrefix("decode_nc").toIntOrNull() ?: return null
        if (nc < 0 || nc > 7) return null
        return 1 shl (nc + 1)
    }

    private companion object {
        const val TAG = "CaptureTestViewModel"
    }

    fun startStream() {
        _streamState.value = StreamState.Running
        resetStats()
    }
    
    fun stopStream() {
        _streamState.value = StreamState.Stopped
    }
    
    fun updateFrameMetrics(
        focusScore: Double,
        brightness: Double,
        contrast: Double,
        frameRate: Double
    ) {
        _frameMetrics.value = FrameMetrics(
            focusScore = focusScore,
            brightness = brightness,
            contrast = contrast,
            frameRate = frameRate
        )
        
        // Update stats
        val current = _captureStats.value
        _captureStats.value = current.copy(
            framesProcessed = current.framesProcessed + 1,
            avgFocusScore = (current.avgFocusScore * current.framesProcessed + focusScore) / (current.framesProcessed + 1),
            avgBrightness = (current.avgBrightness * current.framesProcessed + brightness) / (current.framesProcessed + 1),
            avgFrameRate = (current.avgFrameRate * current.framesProcessed + frameRate) / (current.framesProcessed + 1)
        )
    }
    
    private fun resetStats() {
        _captureStats.value = CaptureStats()
        _frameMetrics.value = null
    }
}

/**
 * Stream state
 */
sealed class StreamState {
    object Stopped : StreamState()
    object Running : StreamState()
}

/**
 * Codec benchmark ("Suite B") lifecycle state.
 */
sealed class BenchmarkState {
    object Idle : BenchmarkState()
    object Running : BenchmarkState()
    data class Done(val results: List<BenchmarkResult>) : BenchmarkState()
}

/**
 * Real-time frame quality metrics
 */
data class FrameMetrics(
    val focusScore: Double,
    val brightness: Double,
    val contrast: Double,
    val frameRate: Double
)

/**
 * Aggregate capture statistics
 */
data class CaptureStats(
    val framesProcessed: Int = 0,
    val avgFocusScore: Double = 0.0,
    val avgBrightness: Double = 0.0,
    val avgFrameRate: Double = 0.0
)
