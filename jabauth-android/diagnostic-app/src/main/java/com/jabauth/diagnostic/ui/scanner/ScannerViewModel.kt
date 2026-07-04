package com.jabauth.diagnostic.ui.scanner

import android.app.Application
import android.media.ImageReader
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jabauth.diagnostic.data.SettingsRepository
import com.jabauth.diagnostic.metric.DeviceVerifyAttempt
import com.jabauth.diagnostic.metric.DeviceVerifyAttemptExporter
import com.jabauth.diagnostic.metric.deviceVerifyAttemptExporter
import com.jabauth.diagnostic.util.DiagnosticLogger
import com.jabauth.diagnostic.util.HapticFeedbackController
import com.jabauth.diagnostic.util.motion.MotionTelemetryController
import com.jabauth.diagnostic.ui.errorlog.ErrorLogRepository
import com.jabauth.diagnostic.ui.errorlog.toErrorEntries
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.diagnostic.verify.ScanVerifier
import com.jabauth.diagnostic.verify.VerificationResult
import com.jabauth.jabcode.DecodeResult
import com.jabauth.jabcode.JABCodeDecoderImpl
import com.jabauth.jabcode.PerformanceTracker
import com.jabauth.jabcode.camera.Camera2JABCodeAnalyzer
import com.jabauth.jabcode.camera.ImageQualityAnalyzer
import com.jabauth.jabcode.camera.RoiSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val decoder = JABCodeDecoderImpl()
    private val settingsRepository = SettingsRepository(application)
    private val logger = DiagnosticLogger.create("ScannerViewModel", settingsRepository)
    private val hapticController = HapticFeedbackController(application)
    private val motionController = MotionTelemetryController(application)

    // Stage 1b: durable structured export of each decode attempt as a shared-schema
    // DeviceVerifyAttempt record (the device leg of the host VerifyAttempt wire
    // format). Built lazily from the Application context so the file dir is resolved
    // only on first attempt. Promotes the QUALITY_DECODE logcat telemetry to files;
    // the logcat line itself is unchanged. Best-effort: an export I/O error never
    // affects decode/scan behaviour.
    private val verifyAttemptExporter: DeviceVerifyAttemptExporter by lazy {
        application.deviceVerifyAttemptExporter()
    }

    // Production-side PerformanceTracker — records each decode attempt's
    // duration and success across the session lifetime. Complements the
    // rolling-window DECODE_TIME_STATS / DECODE_FAIL_STATS by providing
    // a cumulative aggregate that Macrobenchmark's TraceSectionMetric
    // can correlate against. Previously the tracker existed only in
    // integration tests; this is its first production wire-up.
    private val performanceTracker = PerformanceTracker()
    
    private val _scanResult = MutableStateFlow<DecodeResult?>(null)
    val scanResult: StateFlow<DecodeResult?> = _scanResult.asStateFlow()

    // On-device verification pre-check. The ABE stage adjudicates the sealed policy against the operator's
    // own attributes (the Settings "Verifier attributes" group), so the verifier's identity — not a hardcoded
    // set — decides GRANTED/DENIED. Read live via a lambda so a Settings change takes effect on the next scan.
    @Volatile
    private var verifierAttributes: Set<String> = SettingsRepository.DEFAULT_VERIFIER_ATTRIBUTES
    @Suppress("MemberVisibilityCanBePrivate")
    internal var scanVerifier: ScanVerifier = ScanVerifier(verifierAttributes = { verifierAttributes })
    private val _verificationResult = MutableStateFlow<VerificationResult?>(null)
    val verificationResult: StateFlow<VerificationResult?> = _verificationResult.asStateFlow()

    // The signature of the failing/degraded stages last fanned to the Error Log. The live analyzer
    // re-decodes the SAME symbol every frame, so without this guard a persistent PKI WARN would append an
    // identical entry dozens of times per second. We log only when the set of loggable stages *changes*.
    private var lastLoggedVerificationSignature: String? = null

    /**
     * Publish a fresh pre-check [result]: expose it to the Scanner UI AND fan any degraded/failed stage out
     * to the shared Error Log ([ErrorLogRepository]) as stage-tagged entries (WARN→WARNING, FAIL→ERROR;
     * PASS/SKIPPED contribute nothing). The Error Log is a separate navigation destination with its own
     * ViewModel, so the process-wide repository is how a failure surfaced while scanning reaches it. Every
     * site that sets a verification result routes through here so the log stays in lock-step with the HUD.
     *
     * De-duplicated against the previous emission (by content, not the entry's random id/timestamp): a
     * continuously re-decoded symbol logs its failures once, not once per frame.
     */
    private fun setVerificationResult(result: VerificationResult) {
        _verificationResult.value = result
        val entries = result.toErrorEntries()
        val signature = entries.joinToString("|") { "${it.stageTag}/${it.severity}/${it.message}/${it.details}" }
        if (signature != lastLoggedVerificationSignature) {
            entries.forEach(ErrorLogRepository::add)
            lastLoggedVerificationSignature = signature
        }
    }
    
    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    // AE lock policy. The camera runs continuous AE until the FIRST successful
    // decode, then locks the known-good exposure; it re-arms (unlocks) on a
    // sustained failure streak or a large brightness shift vs the locked frame.
    // Fixes the start-pointed-away wash-out: the camera used to latch the lock on
    // first 3A convergence, freezing the startup scene's (wrong) exposure.
    // Collected by ScannerScreen and forwarded to Camera2Preview.setAeLocked().
    private val _aeLocked = MutableStateFlow(false)
    val aeLocked: StateFlow<Boolean> = _aeLocked.asStateFlow()
    private var consecutiveAeFailures = 0
    private var aeLockBrightness: Float? = null
    /** Consecutive failed decodes while locked before re-arming AE. */
    private val aeRearmFailureThreshold = 10
    /** Brightness deviation factor (vs the locked frame) that re-arms AE fast. */
    private val aeRearmBrightnessRatio = 1.6f
    
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

    // Decode region-of-interest (the on-screen reticle). The UI pushes a
    // normalized rect + view aspect via setRoi(); the analyzer's roiProvider
    // reads it (combined with sensorOrientation) each frame and crops the
    // analysis frame to it. workloadPct mirrors the reference app: the % is the
    // ROI's area as a fraction of the frame — bigger reticle, more to process.
    private data class RoiInput(val l: Float, val t: Float, val r: Float, val b: Float, val viewAspect: Float)
    @Volatile private var roiInput: RoiInput? = null
    @Volatile private var sensorOrientation: Int = 90  // back-camera default; set on camera open

    // R4 measure-first acquisition: the analyzer computes per-frame image-quality
    // metrics and invokes onQualityUpdate IMMEDIATELY BEFORE it decodes that same
    // frame (see Camera2JABCodeAnalyzer.analyze: metrics → callback → decode), so
    // this snapshot corresponds to the frame whose decode outcome recordAttempt()
    // then logs. Purely ADDITIVE observation — no decode gate. We measure quality
    // alongside outcome now so thresholds can be calibrated from real field data
    // later, rather than gating on the un-calibrated provisional defaults in
    // ImageQualityAnalyzer.QualityMetrics. @Volatile: the analyzer callback and
    // the decode callbacks run on the camera/analysis threads.
    @Volatile private var latestQuality: ImageQualityAnalyzer.QualityMetrics? = null

    private val _workloadPct = MutableStateFlow(0)
    val workloadPct: StateFlow<Int> = _workloadPct.asStateFlow()

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
    /**
     * Gated verification trigger: run the four-stage on-device pre-check over the current decoded symbol.
     * No-op if nothing has decoded yet; the verdict is exposed via [verificationResult].
     */
    fun verifyCurrentScan() {
        val result = _scanResult.value ?: return
        setVerificationResult(scanVerifier.verify(result.data, result.decodeTimeMs))
    }

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

        // R4 measure-first correlation marker: ONE greppable line per decode
        // attempt joining the just-measured frame quality with the decode
        // outcome (rates/stages only — NEVER the decoded payload). The analyzer
        // computes quality immediately before decoding the SAME frame, so the
        // latestQuality snapshot corresponds to this attempt's frame. Lets us
        // later correlate quality vs decode success and calibrate the (currently
        // provisional) ImageQualityAnalyzer thresholds from real field data.
        // This is purely observational — there is deliberately NO decode gate.
        // Sentinels (-1 / false) cover the null case (e.g. quality disabled, or
        // the Scan-image path which has no live camera frame).
        // Greppable: QUALITY_DECODE.
        val q = latestQuality
        Log.i(
            "ScannerViewModel",
            "QUALITY_DECODE outcome=%s nc=%d decodeMs=%d focus=%.3f brightness=%.3f contrast=%.3f score=%.3f inFocus=%b exposed=%b stage=%s".format(
                if (isSuccess) "ok" else "fail",
                nc,
                decodeTimeMs,
                q?.focus ?: -1f,
                q?.brightness ?: -1f,
                q?.contrast ?: -1f,
                q?.qualityScore ?: -1f,
                q?.isInFocus ?: false,
                q?.isWellExposed ?: false,
                failureCategory.name
            )
        )

        // Stage 1b: promote the SAME per-attempt telemetry to a durable, host-diffable
        // DeviceVerifyAttempt record (JSONL + CSV). Codec-only leg: decode timing only,
        // authOutcome all-SKIP. Mirrors the QUALITY_DECODE line above — never the payload.
        // Wrapped so a storage failure cannot perturb the scan path.
        runCatching {
            verifyAttemptExporter.append(
                DeviceVerifyAttemptExporter.fromScanAttempt(
                    isSuccess = isSuccess,
                    decodeTimeMs = decodeTimeMs,
                    colorNumber = ncToColorNumber(nc),
                    failAttr = failureCategory.toFailAttr(),
                    focus = q?.focus,
                    brightness = q?.brightness,
                    contrast = q?.contrast
                )
            )
        }.onFailure { Log.w("ScannerViewModel", "DEVICE_VERIFY_EXPORT append failed: ${it.message}") }

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

        // Per-Nc end-to-end success counter. Mirrors DECODE_FAIL_STATS
        // structure on the success side, giving the rolling-window pass
        // rate per Nc. Filed per Bayesian Council session bc-2026-06-01-04
        // (Prometheus + Robin Hood reframing): "build per-Nc end-to-end
        // success-rate telemetry now, BEFORE specific investigations" so
        // future anomalies surface in traces rather than via accidental
        // user observation (as the nc=7 COLOR_8 mystery did). nc=0 will
        // stay at successes=0 until the Mode 0 pair_bits/LDPC workstream
        // ships — making nc=0 progress measurable from the moment any
        // downstream fix lands.
        // Greppable: END_TO_END_STATS.
        for (n in 0..7) {
            val nSuccessCount = successAttempts.count { it.nc == n }
            val nAttempts = attemptLog.count { it.nc == n }
            if (nAttempts > 0) {
                val pct = if (nAttempts > 0) (nSuccessCount * 100) / nAttempts else 0
                Log.i(
                    "ScannerViewModel",
                    "END_TO_END_STATS Nc=$n successes=$nSuccessCount/$nAttempts (${pct}%)"
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

    /**
     * Map the rolling-stats Nc index (0..7, or -1 for auto-detect / unknown) to the carrier
     * colour count the shared schema's `colorNumber` column expects (2,4,8,...,256). An unknown
     * Nc becomes 0, matching the host schema's "unknown/not probed" sentinel.
     */
    private fun ncToColorNumber(nc: Int): Int =
        if (nc in 0..7) 1 shl (nc + 1) else 0

    /**
     * Classify a decoder error string into the failure-attribution category. Mirrors the C-side
     * FAIL_ATTR status codes: "No JABCode found" ⇒ detector miss; "not decodable" ⇒ slave/payload
     * recovery failure; anything else ⇒ unspecified. Shared by the live-camera failure callback and
     * the picked-image decode path so both attribute failures on the same axis.
     */
    private fun classifyFailure(error: String): FailureCategory = when {
        error.contains("No JABCode found", ignoreCase = true) -> FailureCategory.NO_FP_FOUND
        error.contains("not decodable", ignoreCase = true) -> FailureCategory.SLAVE_DECODE_FAILED
        else -> FailureCategory.OTHER
    }

    /**
     * Translate this view-model's private decode [FailureCategory] into the shared cross-leg
     * [DeviceVerifyAttempt.FailAttr] vocabulary (decode subset). `NO_FP_FOUND` is a detector miss
     * (`DETECT`); `SLAVE_DECODE_FAILED` is a found-but-unrecoverable symbol (`CLASSIFICATION`); the
     * residual `OTHER` is an unspecified decode failure that, by elimination, was not a clean
     * detector miss, so it maps to `CLASSIFICATION` rather than `DETECT`.
     */
    private fun FailureCategory.toFailAttr(): DeviceVerifyAttempt.FailAttr = when (this) {
        FailureCategory.NONE -> DeviceVerifyAttempt.FailAttr.NONE
        FailureCategory.NO_FP_FOUND -> DeviceVerifyAttempt.FailAttr.DETECT
        FailureCategory.SLAVE_DECODE_FAILED -> DeviceVerifyAttempt.FailAttr.CLASSIFICATION
        FailureCategory.OTHER -> DeviceVerifyAttempt.FailAttr.CLASSIFICATION
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

    // Track haptic-feedback setting — checked in onDecodeSuccess to gate
    // the vibration pulse without recreating the analyzer on every change.
    private var hapticFeedbackEnabled = SettingsRepository.DEFAULT_HAPTIC_FEEDBACK

    // Track motion telemetry + throttling settings. SENSOR_SNAPSHOT log
    // markers fire per decode attempt when telemetry is enabled. Throttling
    // skips analyzer.analyze() when StabilityGate reports motion above
    // threshold (see MotionTelemetryController).
    private var motionTelemetryEnabled = SettingsRepository.DEFAULT_MOTION_TELEMETRY
    private var motionThrottlingEnabled = SettingsRepository.DEFAULT_MOTION_THROTTLING
    
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
                hapticFeedbackEnabled = settings.hapticFeedback
                motionTelemetryEnabled = settings.motionTelemetryEnabled
                motionThrottlingEnabled = settings.motionThrottlingEnabled
                verifierAttributes = settings.verifierAttributes
                
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
                // includeQualityMetrics defaults true — left ON so the analyzer
                // computes per-frame quality and drives onQualityUpdate below.
            ),
            // R4 measure-first: capture the latest frame's quality metrics. The
            // analyzer invokes this on the SAME frame it is about to decode, so
            // latestQuality is current when the decode outcome reaches
            // recordAttempt(), which logs the QUALITY_DECODE correlation line.
            // Store-only — no gate, no decode-behaviour change.
            onQualityUpdate = { metrics ->
                latestQuality = metrics
            },
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
                        // Greppable INFO marker for regression telemetry — surfaces
                        // mismatches automatically rather than via accidental user
                        // observation. Filed per Bayesian Council session
                        // bc-2026-06-01-04 after the user-reported nc=7→COLOR_8
                        // mystery (caused by the missing COLOR_256 enum, fixed
                        // separately in EncodeOptions.kt).
                        Log.i("ScannerViewModel", "RESULT_NC_MISMATCH preferred=$preferred decoded=$decodedColorValue")
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
                setVerificationResult(scanVerifier.verify(result.data, result.decodeTimeMs))
                _scanCount.value++
                // AE lock policy: freeze the now-known-good exposure on the first
                // successful decode (continuous AE ran until now, so a session
                // started pointed away from the screen kept adapting). Reset the
                // failure streak so a later streak/shift can re-arm.
                consecutiveAeFailures = 0
                if (!_aeLocked.value) {
                    aeLockBrightness = latestQuality?.brightness
                    _aeLocked.value = true
                    Log.i("JABCodeAE", "AE_LOCK engaged on first decode (lockBrightness=${aeLockBrightness?.let { "%.3f".format(it) } ?: "?"})")
                }
                // Fire haptic pulse on successful decode when the user
                // has enabled the Settings "Haptic Feedback" toggle.
                // Cheap to gate here — no analyzer recreation needed.
                if (hapticFeedbackEnabled) hapticController.pulseSuccess()
                if (motionTelemetryEnabled) {
                    val s = motionController.currentState()
                    Log.i(
                        "ScannerViewModel",
                        "SENSOR_SNAPSHOT accel_mag=${"%.3f".format(s.accelMagnitude)} " +
                        "gyro_mag=${"%.3f".format(s.gyroMagnitude)} " +
                        "stable=${motionController.isStable()} (decode=success colors=${result.colorMode.value})"
                    )
                }
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
                // AE re-arm policy: if exposure is locked but decodes are failing
                // — a sustained streak, or a large brightness shift vs the locked
                // frame (the camera moved off the screen and the frozen exposure
                // now washes out) — unlock so AE re-converges to the new scene.
                if (_aeLocked.value) {
                    consecutiveAeFailures++
                    val b = latestQuality?.brightness
                    val lb = aeLockBrightness
                    val exposureShift = b != null && lb != null && lb > 0f &&
                        (b > lb * aeRearmBrightnessRatio || b < lb / aeRearmBrightnessRatio)
                    if (consecutiveAeFailures >= aeRearmFailureThreshold || exposureShift) {
                        _aeLocked.value = false
                        consecutiveAeFailures = 0
                        aeLockBrightness = null
                        Log.i("JABCodeAE", "AE_LOCK re-armed (continuous AE) reason=${if (exposureShift) "brightness-shift" else "failure-streak"}")
                    }
                }
                // Record failure into the production PerformanceTracker. Note
                // that exception-path failures pass decodeTimeMs=0 from the
                // analyzer; the tracker handles them as zero-duration events.
                performanceTracker.recordDecode(decodeTimeMs, success = false)
                // Classify the decoder's failure mode so the rolling stats
                // can attribute attempts to FP-detection vs slave-decode
                // failure. This is the screen-side mirror of the C-side
                // FAIL_ATTR status codes — same axis, different log source.
                val category = classifyFailure(error)
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
            },
            // Decode region-of-interest: built fresh each frame from the reticle
            // (roiInput) + the live sensor orientation. null ⇒ full-frame decode.
            roiProvider = {
                roiInput?.let { RoiSpec(it.l, it.t, it.r, it.b, it.viewAspect, sensorOrientation) }
            },
            // nc2 anti-fabrication consensus (diagnostic-app opt-in): an 8-colour
            // decode is trusted only once 2 frames agree byte-identically.
            nc2ConsensusMinAgreement = 2
        )
    }
    
    /**
     * Decode a JABCode from a picked image file (the "Scan image" toolbar
     * action). Routes the outcome through the same result/error/history state
     * as camera frames, so it shows in the result chip, history strip and
     * per-Nc table. This is the camera-free decoder test: a clean PNG fixture
     * isolates decoder logic from the capture pipeline — if nc2 decodes from
     * the file but not the camera, the problem is 100% capture.
     */
    fun decodeImage(uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
            }
            if (bitmap == null) {
                _scanError.value = "Could not load image"
                return@launch
            }
            val start = System.currentTimeMillis()
            val result = withContext(Dispatchers.Default) {
                decoder.decode(bitmap, DecodeOptions(timeout = 2000L))
            }
            val dt = System.currentTimeMillis() - start
            if (result != null) {
                _scanResult.value = result
                _scanError.value = null
                setVerificationResult(scanVerifier.verify(result.data, result.decodeTimeMs))
                _scanCount.value++
                performanceTracker.recordDecode(result.decodeTimeMs, success = true)
                val ncIndex = COLOR_COUNT_TO_NC[result.colorMode.value] ?: -1
                recordAttempt(isSuccess = true, decodeTimeMs = result.decodeTimeMs, nc = ncIndex)
                _decodeHistory.value = (listOf(result) + _decodeHistory.value).take(historyMaxSize)
                Log.i("ScannerViewModel", "SCAN_IMAGE decode OK colors=${result.colorMode.value} bytes=${result.data.size}")
            } else {
                val err = decoder.getLastError() ?: "No JABCode found in image"
                _scanError.value = err
                performanceTracker.recordDecode(dt, success = false)
                recordAttempt(
                    isSuccess = false,
                    decodeTimeMs = dt,
                    nc = preferredColorMode?.let { COLOR_COUNT_TO_NC[it] } ?: -1,
                    // Attribute the picked-image failure on the same axis as the
                    // camera path so a FAIL record never carries failAttr=NONE.
                    failureCategory = classifyFailure(err)
                )
                Log.i("ScannerViewModel", "SCAN_IMAGE decode FAIL: $err")
            }
        }
    }

    fun analyzeFrame(reader: ImageReader) {
        // Motion throttling: skip analyzer.analyze() entirely when the
        // device is moving above the stability threshold. The ImageReader
        // retains the latest frame, so the next stable frame is picked
        // up immediately when motion settles. Filed per Bayesian Council
        // session bc-2026-06-01-05 (Historian + Prometheus).
        if (motionThrottlingEnabled && !motionController.isStable()) {
            // Light log gated on motionTelemetryEnabled to avoid log spam
            // when throttling is hot.
            if (motionTelemetryEnabled) {
                Log.v("ScannerViewModel", "FRAME_THROTTLED motion above threshold")
            }
            return
        }
        analyzer.analyze(reader)
    }

    /** Camera reports its sensor orientation once it opens; needed to map the
     *  portrait reticle onto the landscape analysis frame. */
    fun onSensorOrientation(deg: Int) {
        sensorOrientation = deg
        Log.i("ScannerViewModel", "ROI sensorOrientation=$deg")
    }

    /**
     * Update the decode region-of-interest from the reticle. [left]/[top]/
     * [right]/[bottom] are normalized (0..1) in the preview view; [viewAspect]
     * is the view's width/height. Also refreshes the workload % shown by the
     * coach (ROI area as a fraction of the frame).
     */
    fun setRoi(left: Float, top: Float, right: Float, bottom: Float, viewAspect: Float) {
        roiInput = RoiInput(left, top, right, bottom, viewAspect)
        val areaFrac = (right - left).coerceIn(0f, 1f) * (bottom - top).coerceIn(0f, 1f)
        _workloadPct.value = (areaFrac * 100f).toInt().coerceIn(1, 100)
    }

    init {
        // Start motion telemetry listeners. The controller no-ops on
        // devices without sensors. Stopped in onCleared() below.
        motionController.start()
    }

    override fun onCleared() {
        super.onCleared()
        motionController.stop()
    }
}
