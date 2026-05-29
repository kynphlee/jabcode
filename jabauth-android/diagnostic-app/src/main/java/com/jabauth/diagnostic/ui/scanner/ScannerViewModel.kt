package com.jabauth.diagnostic.ui.scanner

import android.app.Application
import android.media.ImageReader
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jabauth.diagnostic.data.SettingsRepository
import com.jabauth.diagnostic.util.DiagnosticLogger
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.DecodeResult
import com.jabauth.jabcode.JABCodeDecoderImpl
import com.jabauth.jabcode.PerformanceTracker
import com.jabauth.jabcode.camera.Camera2JABCodeAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val decoder = JABCodeDecoderImpl()
    private val settingsRepository = SettingsRepository(application)
    private val logger = DiagnosticLogger.create("ScannerViewModel", settingsRepository)

    // Production-side PerformanceTracker — records each decode attempt's
    // duration and success across the session lifetime. Complements the
    // rolling-window DECODE_TIME_STATS / DECODE_FAIL_STATS by providing
    // a cumulative aggregate that Macrobenchmark's TraceSectionMetric
    // can correlate against. Previously the tracker existed only in
    // integration tests; this is its first production wire-up.
    private val performanceTracker = PerformanceTracker()
    
    private val _scanResult = MutableStateFlow<DecodeResult?>(null)
    val scanResult: StateFlow<DecodeResult?> = _scanResult.asStateFlow()
    
    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()
    
    private val _scanCount = MutableStateFlow(0)
    val scanCount: StateFlow<Int> = _scanCount.asStateFlow()

    // Tier-1 HUD state — exposed to the UI as Compose-friendly StateFlows.
    // The Camera2Preview callbacks push values in via the setters below.
    private val _currentZoom = MutableStateFlow(1.0f)
    val currentZoom: StateFlow<Float> = _currentZoom.asStateFlow()

    private val _llbSupported = MutableStateFlow(false)
    val llbSupported: StateFlow<Boolean> = _llbSupported.asStateFlow()

    private val _llbState = MutableStateFlow(-1)  // -1 unknown, 0 inactive, 1 active
    val llbState: StateFlow<Int> = _llbState.asStateFlow()

    // Rolling 30-second stats: each attempt records (timestamp, success, time,
    // nc, failureCategory). Nc is the decoder's reported Nc value (0..7)
    // for successes, or -1 for failures. failureCategory classifies the
    // decoder's three error modes for failure attribution stats.
    private data class AttemptRecord(
        val timestampMs: Long,
        val isSuccess: Boolean,
        val decodeTimeMs: Long,
        val nc: Int,
        val failureCategory: FailureCategory = FailureCategory.NONE
    )

    enum class FailureCategory(val displayName: String) {
        NONE("(none — success)"),
        NO_FP_FOUND("status=0 no FP found"),
        SLAVE_DECODE_FAILED("status=1 FP found, slave decode failed"),
        OTHER("status=other unspecified")
    }
    private val attemptLog = mutableListOf<AttemptRecord>()
    private val attemptWindowMs = 30_000L
    private val _recentStats = MutableStateFlow(ScanStats(0, 0))
    val recentStats: StateFlow<ScanStats> = _recentStats.asStateFlow()

    // Decode-time statistics across the rolling window — both overall
    // and broken down per-Nc. Per-Nc breakdown reveals card-mix
    // contamination (e.g., user thought they were scanning Nc=3 but
    // the trace shows Nc=1/4/5 too) and surfaces which modes are
    // contributing to the overall distribution shape.
    private val _decodeTimeStats = MutableStateFlow<DecodeTimeStats?>(null)
    val decodeTimeStats: StateFlow<DecodeTimeStats?> = _decodeTimeStats.asStateFlow()

    private val _perNcStats = MutableStateFlow<Map<Int, DecodeTimeStats>>(emptyMap())
    val perNcStats: StateFlow<Map<Int, DecodeTimeStats>> = _perNcStats.asStateFlow()

    companion object {
        // Annotations for n=0 Nc rows that reference the relevant open
        // hypothesis. When a row stays empty, the annotation tells the
        // reader of the trace why — converting silent absence into
        // actionable signal cross-referenced against the bug register.
        // Cross-references: docs/cassandra-register/*.md
        private val NC_ANNOTATIONS = mapOf(
            0 to "Mode 0 monochrome — H_mode0_partI_decode_failure",
            2 to "8-color — H_nc2_decode_failure",
            6 to "128-color — print gamut-limited, screen works at zoom",
            7 to "256-color — slave-decode + gamut compound bottleneck"
        )
        // ColorMode.value (color count) → Nc index (0..7).
        private val COLOR_COUNT_TO_NC = mapOf(
            2 to 0, 4 to 1, 8 to 2, 16 to 3,
            32 to 4, 64 to 5, 128 to 6, 256 to 7
        )
    }

    // History of last 5 successful decodes, newest first.
    private val _decodeHistory = MutableStateFlow<List<DecodeResult>>(emptyList())
    val decodeHistory: StateFlow<List<DecodeResult>> = _decodeHistory.asStateFlow()
    private val historyMaxSize = 5

    // Expose settings for UI consumption (auto-focus, color mode, etc.)
    val settings = settingsRepository.settingsFlow

    // --- Tier-1 HUD setters (called from ScannerScreen via Camera2Preview callbacks) ---
    fun onZoomChanged(zoomRatio: Float) {
        _currentZoom.value = zoomRatio
    }

    fun onLowLightBoostSupported(supported: Boolean) {
        _llbSupported.value = supported
    }

    fun onLowLightBoostStateChanged(state: Int) {
        _llbState.value = state
    }

    private fun recordAttempt(
        isSuccess: Boolean,
        decodeTimeMs: Long = 0L,
        nc: Int = -1,
        failureCategory: FailureCategory = FailureCategory.NONE
    ) {
        val now = System.currentTimeMillis()
        attemptLog.add(AttemptRecord(now, isSuccess, decodeTimeMs, nc, failureCategory))
        // Prune outside the rolling window
        val cutoff = now - attemptWindowMs
        attemptLog.removeAll { it.timestampMs < cutoff }
        val ok = attemptLog.count { it.isSuccess }
        val fail = attemptLog.size - ok
        _recentStats.value = ScanStats(okCount = ok, failCount = fail)

        // Compute OVERALL decode-time stats over successes in the rolling window.
        val successAttempts = attemptLog.filter { it.isSuccess }
        val overallStats = statsFor(successAttempts.map { it.decodeTimeMs })
        _decodeTimeStats.value = overallStats

        // Compute PER-Nc decode-time stats; only emit a map entry when
        // the Nc has at least one success in the window. The UI / log
        // formatter inserts the "n=0" rows for absent Nc values.
        val perNc = mutableMapOf<Int, DecodeTimeStats>()
        for (n in 0..7) {
            val timesForNc = successAttempts.filter { it.nc == n }.map { it.decodeTimeMs }
            statsFor(timesForNc)?.let { perNc[n] = it }
        }
        _perNcStats.value = perNc

        // Per-category failure breakdown over the rolling window (overall).
        // Mapping mirrors the C-side FAIL_ATTR status codes so screen
        // logs and decoder logs can be cross-referenced on the same axis.
        val failuresInWindow = attemptLog.filter { !it.isSuccess }
        val failByCategory = failuresInWindow.groupingBy { it.failureCategory }.eachCount()
        val noFp = failByCategory[FailureCategory.NO_FP_FOUND] ?: 0
        val slaveFail = failByCategory[FailureCategory.SLAVE_DECODE_FAILED] ?: 0
        val otherFail = failByCategory[FailureCategory.OTHER] ?: 0
        val totalIn30s = _recentStats.value.total

        // Emit the stats to logcat unconditionally — silence on the failure
        // side was the old gap: a "we're scanning but nothing succeeds"
        // block is more diagnostically valuable than no block at all.
        // Four sections: overall success timing, 8 per-Nc success rows,
        // overall failure summary, 8 per-Nc failure rows.
        // Greppable: DECODE_TIME_STATS / DECODE_FAIL_STATS.
        if (overallStats != null) {
            Log.i(
                "ScannerViewModel",
                "DECODE_TIME_STATS overall: min=${overallStats.minMs}ms " +
                "max=${overallStats.maxMs}ms avg=${overallStats.avgMs}ms " +
                "median=${overallStats.medianMs}ms Δ=${overallStats.deltaMs}ms " +
                "n=${overallStats.sampleCount} " +
                "ok=${_recentStats.value.okCount}/${totalIn30s}_in_30s"
            )
        } else {
            Log.i(
                "ScannerViewModel",
                "DECODE_TIME_STATS overall: n=0 no successes in window " +
                "ok=0/${totalIn30s}_in_30s"
            )
        }
        for (n in 0..7) {
            val s = perNc[n]
            if (s != null) {
                Log.i(
                    "ScannerViewModel",
                    "DECODE_TIME_STATS Nc=$n: n=${s.sampleCount} " +
                    "min=${s.minMs}ms max=${s.maxMs}ms " +
                    "avg=${s.avgMs}ms median=${s.medianMs}ms Δ=${s.deltaMs}ms"
                )
            } else {
                val annotation = NC_ANNOTATIONS[n]
                val suffix = if (annotation != null) "  ($annotation)" else ""
                Log.i(
                    "ScannerViewModel",
                    "DECODE_TIME_STATS Nc=$n: n=0   no successes in window$suffix"
                )
            }
        }

        // Overall DECODE_FAIL_STATS — aggregate across all Nc values.
        Log.i(
            "ScannerViewModel",
            "DECODE_FAIL_STATS overall: fail=${_recentStats.value.failCount}/${totalIn30s}_in_30s " +
            "status0=$noFp status1=$slaveFail other=$otherFail"
        )

        // Per-Nc DECODE_FAIL_STATS — landed per Bayesian Council Session
        // bc-2026-05-28-03's discriminator spec (Option A). The per-Nc
        // status0/status1 ratio AND median decode-time fingerprint together
        // discriminate H_partI_unifies from H_independent_bugs / H_clustering_*.
        // Failure attribution uses preferredColorMode (user-set per-fixture)
        // — see onDecodeFailure callback for the derivation. Nc=-1 captures
        // failures from auto-detect sessions where attempted Nc is unknown.
        // Timing aggregates exclude samples with decodeTimeMs == 0 (exception
        // path samples that don't have timing info).
        for (n in 0..7) {
            val nFailures = failuresInWindow.filter { it.nc == n }
            val nFailCount = nFailures.size
            val nNoFp = nFailures.count { it.failureCategory == FailureCategory.NO_FP_FOUND }
            val nSlaveFail = nFailures.count { it.failureCategory == FailureCategory.SLAVE_DECODE_FAILED }
            val nOther = nFailures.count { it.failureCategory == FailureCategory.OTHER }
            val timingStats = statsFor(nFailures.map { it.decodeTimeMs }.filter { it > 0 })
            if (nFailCount == 0) {
                val annotation = NC_ANNOTATIONS[n]
                val suffix = if (annotation != null) "  ($annotation)" else ""
                Log.i(
                    "ScannerViewModel",
                    "DECODE_FAIL_STATS Nc=$n: fail=0$suffix"
                )
            } else if (timingStats != null) {
                Log.i(
                    "ScannerViewModel",
                    "DECODE_FAIL_STATS Nc=$n: fail=$nFailCount " +
                    "status0=$nNoFp status1=$nSlaveFail other=$nOther " +
                    "min=${timingStats.minMs}ms max=${timingStats.maxMs}ms " +
                    "avg=${timingStats.avgMs}ms median=${timingStats.medianMs}ms"
                )
            } else {
                // Failures present but no timing info (all exception-path
                // samples). Emit counts only.
                Log.i(
                    "ScannerViewModel",
                    "DECODE_FAIL_STATS Nc=$n: fail=$nFailCount " +
                    "status0=$nNoFp status1=$nSlaveFail other=$nOther " +
                    "(no timing info available)"
                )
            }
        }

        // Failures from auto-detect sessions where Nc isn't known. If this
        // bucket is non-empty, the user is scanning without setting
        // preferredColorMode — note for the discriminator-scan recipe.
        val unknownNcFailures = failuresInWindow.filter { it.nc == -1 }
        if (unknownNcFailures.isNotEmpty()) {
            val unknownTimingStats = statsFor(unknownNcFailures.map { it.decodeTimeMs }.filter { it > 0 })
            val unknownNoFp = unknownNcFailures.count { it.failureCategory == FailureCategory.NO_FP_FOUND }
            val unknownSlaveFail = unknownNcFailures.count { it.failureCategory == FailureCategory.SLAVE_DECODE_FAILED }
            val unknownOther = unknownNcFailures.count { it.failureCategory == FailureCategory.OTHER }
            if (unknownTimingStats != null) {
                Log.i(
                    "ScannerViewModel",
                    "DECODE_FAIL_STATS Nc=? (auto-detect): fail=${unknownNcFailures.size} " +
                    "status0=$unknownNoFp status1=$unknownSlaveFail other=$unknownOther " +
                    "min=${unknownTimingStats.minMs}ms max=${unknownTimingStats.maxMs}ms " +
                    "avg=${unknownTimingStats.avgMs}ms median=${unknownTimingStats.medianMs}ms"
                )
            } else {
                Log.i(
                    "ScannerViewModel",
                    "DECODE_FAIL_STATS Nc=? (auto-detect): fail=${unknownNcFailures.size} " +
                    "status0=$unknownNoFp status1=$unknownSlaveFail other=$unknownOther " +
                    "(no timing info available)"
                )
            }
        }
    }

    private fun statsFor(times: List<Long>): DecodeTimeStats? {
        if (times.isEmpty()) return null
        val sorted = times.sorted()
        val min = sorted.first()
        val max = sorted.last()
        val avg = sorted.sum() / sorted.size
        // True median: for even-length samples, average the two middle values.
        // Median is the load-bearing failure-timing fingerprint per the
        // Bayesian Council Session bc-2026-05-28-03 — discriminates
        // H_partI_unifies from H_independent_bugs.
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        }
        return DecodeTimeStats(
            minMs = min,
            maxMs = max,
            avgMs = avg,
            medianMs = median,
            deltaMs = max - min,
            sampleCount = times.size
        )
    }

    data class ScanStats(val okCount: Int, val failCount: Int) {
        val total: Int get() = okCount + failCount
        val successRate: Float get() = if (total == 0) 0f else okCount.toFloat() / total
    }

    /**
     * Decode-time spread statistics across the last 30 seconds of
     * successful decodes. Reveals whether decodes are consistent
     * (small Δ) or jittery (large Δ — usually means Nc_FALLBACK is
     * exhausting all 8 iterations on most attempts).
     */
    data class DecodeTimeStats(
        val minMs: Long,
        val maxMs: Long,
        val avgMs: Long,
        val medianMs: Long,
        val deltaMs: Long,
        val sampleCount: Int
    )
    
    // Track debug logging state for synchronous logging
    private var isDebugEnabled = false
    
    // Track preferred color mode for result validation
    private var preferredColorMode: Int? = null
    
    // Mutable analyzer - recreated when settings change
    private var analyzer: Camera2JABCodeAnalyzer
    
    init {
        // Initialize analyzer with default settings
        analyzer = createAnalyzer(
            timeout = SettingsRepository.DEFAULT_DECODE_TIMEOUT.toLong(),
            analyzeInterval = SettingsRepository.DEFAULT_ANALYZE_INTERVAL.toLong()
        )
        
        // Observe settings changes and recreate analyzer
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                isDebugEnabled = settings.debugLogging
                preferredColorMode = settings.preferredColorMode
                
                val colorModeStr = settings.preferredColorMode?.let { "${it}-color" } ?: "auto-detect"
                logger.dSync(
                    "Settings updated: timeout=${settings.decodeTimeout}ms, interval=${settings.analyzeInterval}ms, " +
                    "autoFocus=${settings.autoFocus}, colorMode=$colorModeStr, debug=${settings.debugLogging}", 
                    isDebugEnabled
                )
                
                analyzer = createAnalyzer(
                    timeout = settings.decodeTimeout.toLong(),
                    analyzeInterval = settings.analyzeInterval.toLong()
                )
            }
        }
    }
    
    private fun createAnalyzer(timeout: Long, analyzeInterval: Long): Camera2JABCodeAnalyzer {
        Log.i("ScannerViewModel", "🔧 Creating new Camera2JABCodeAnalyzer")
        Log.i("ScannerViewModel", "   - Timeout: ${timeout}ms")
        Log.i("ScannerViewModel", "   - Analyze interval: ${analyzeInterval}ms")
        Log.i("ScannerViewModel", "   - Preferred color mode: ${preferredColorMode?.let { "$it-color" } ?: "auto-detect"}")
        Log.i("ScannerViewModel", "   - Debug logging: $isDebugEnabled")
        
        logger.iSync("Creating analyzer: timeout=${timeout}ms, interval=${analyzeInterval}ms", isDebugEnabled)
        
        return Camera2JABCodeAnalyzer(
            decoder = decoder,
            options = DecodeOptions(
                timeout = timeout,
                analyzeIntervalMs = analyzeInterval
            ),
            onDecodeSuccess = { result ->
                val decodedColorValue = result.colorMode.value
                Log.i("ScannerViewModel", "✅ DECODE SUCCESS!")
                Log.i("ScannerViewModel", "   - Data: '${result.asString()}'")
                Log.i("ScannerViewModel", "   - Color mode: ${result.colorMode} (${decodedColorValue} colors)")
                Log.i("ScannerViewModel", "   - Decode time: ${result.decodeTimeMs}ms")
                
                logger.dSync("Decode SUCCESS: data='${result.asString()}', colorMode=${result.colorMode}, decodeTime=${result.decodeTimeMs}ms", isDebugEnabled)
                
                // Validate against preferred color mode if set
                preferredColorMode?.let { preferred ->
                    if (decodedColorValue != preferred) {
                        Log.w("ScannerViewModel", "⚠️ Color mode mismatch: expected ${preferred}-color, decoded ${decodedColorValue}-color")
                        logger.dSync(
                            "Color mode mismatch: expected ${preferred}-color, decoded ${decodedColorValue}-color (auto-detect found different mode)",
                            isDebugEnabled
                        )
                    } else {
                        Log.d("ScannerViewModel", "✅ Color mode validated: ${decodedColorValue}-color matches preference")
                        logger.dSync("Color mode validated: ${decodedColorValue}-color matches preference", isDebugEnabled)
                    }
                }
                
                _scanResult.value = result
                _scanError.value = null
                _scanCount.value++
                // Record into the production PerformanceTracker. Provides
                // cumulative session-lifetime aggregates (avg, min, max,
                // success rate) — complements the 30s rolling DECODE_*_STATS.
                performanceTracker.recordDecode(result.decodeTimeMs, success = true)
                // Tier-1 HUD: record success + prepend to history (last 5).
                // Map color count (2/4/8/...) to Nc index (0..7) for the
                // per-Nc stats breakdown. Unknown values default to -1.
                val ncIndex = COLOR_COUNT_TO_NC[result.colorMode.value] ?: -1
                recordAttempt(
                    isSuccess = true,
                    decodeTimeMs = result.decodeTimeMs,
                    nc = ncIndex
                )
                val updated = (listOf(result) + _decodeHistory.value).take(historyMaxSize)
                _decodeHistory.value = updated
            },
            onDecodeFailure = { error, decodeTimeMs ->
                Log.e("ScannerViewModel", "❌ Decode FAILURE: $error (decodeTime=${decodeTimeMs}ms)")
                logger.dSync("Decode FAILURE: $error (decodeTime=${decodeTimeMs}ms)", isDebugEnabled)
                _scanError.value = error
                // Record failure into the production PerformanceTracker. Note
                // that exception-path failures pass decodeTimeMs=0 from the
                // analyzer; the tracker handles them as zero-duration events.
                performanceTracker.recordDecode(decodeTimeMs, success = false)
                // Classify the decoder's failure mode so the rolling stats
                // can attribute attempts to FP-detection vs slave-decode
                // failure. This is the screen-side mirror of the C-side
                // FAIL_ATTR status codes — same axis, different log source.
                val category = when {
                    error.contains("No JABCode found", ignoreCase = true) ->
                        FailureCategory.NO_FP_FOUND
                    error.contains("not decodable", ignoreCase = true) ->
                        FailureCategory.SLAVE_DECODE_FAILED
                    else -> FailureCategory.OTHER
                }
                // Derive attempted Nc from user's preferredColorMode. On
                // auto-detect sessions (preferredColorMode == null), we
                // can't attribute the failure to a specific Nc — it lands
                // in the Nc=-1 bucket and surfaces under the "Nc=? (auto-detect)"
                // row in the per-Nc failure breakdown. For the discriminator
                // scan (council Session bc-2026-05-28-03), the user sets
                // preferredColorMode per fixture so each failure gets
                // attributed cleanly.
                val attemptedNc = preferredColorMode?.let { COLOR_COUNT_TO_NC[it] } ?: -1
                recordAttempt(
                    isSuccess = false,
                    decodeTimeMs = decodeTimeMs,
                    nc = attemptedNc,
                    failureCategory = category
                )
            }
        )
    }
    
    fun analyzeFrame(reader: ImageReader) {
        analyzer.analyze(reader)
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cleanup if needed
    }
}
