package com.jabauth.diagnostic.ui.errorlog

import androidx.lifecycle.ViewModel
import com.jabauth.diagnostic.verify.StageErrorTag
import com.jabauth.diagnostic.verify.StageResult
import com.jabauth.diagnostic.verify.StageState
import com.jabauth.diagnostic.verify.VerificationResult
import com.jabauth.diagnostic.verify.VerificationStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for Error Log screen.
 *
 * The entry list is owned by the process-wide [ErrorLogRepository] so entries published from another
 * screen (a verification failure raised on the Scanner) surface here — this ViewModel only mirrors the
 * repository's flow and holds the transient [filter] selection. Sample entries are seeded once (guarded)
 * for demonstration so a fresh install still shows the mock's populated look.
 */
class ErrorLogViewModel(
    private val repository: ErrorLogRepository = ErrorLogRepository
) : ViewModel() {

    val errors: StateFlow<List<ErrorEntry>> = repository.entries

    private val _filter = MutableStateFlow(ErrorFilter.ALL)
    val filter: StateFlow<ErrorFilter> = _filter.asStateFlow()

    init {
        seedSampleErrorsOnce()
    }

    fun setFilter(filter: ErrorFilter) {
        _filter.value = filter
    }

    fun clearErrors() {
        repository.clear()
    }

    fun addError(
        severity: ErrorSeverity,
        source: String,
        message: String,
        details: String? = null,
        stageTag: StageErrorTag? = null
    ) {
        repository.add(
            ErrorEntry(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                severity = severity,
                source = source,
                message = message,
                details = details,
                stageTag = stageTag
            )
        )
    }

    private fun seedSampleErrorsOnce() {
        // The repository is a process singleton; seed the demo entries only while it is still empty so
        // re-entering the screen (a fresh ViewModel) does not duplicate them.
        if (repository.entries.value.isNotEmpty()) return
        SAMPLE_ERRORS.forEach(repository::add)
    }

    private companion object {
        /** Demo entries (newest-first); [seedSampleErrorsOnce] prepends them, so list them oldest-first here. */
        private val SAMPLE_ERRORS: List<ErrorEntry> = listOf(
            ErrorEntry(
                id = "sample-pki-warn",
                timestamp = System.currentTimeMillis() - 3600000,
                severity = ErrorSeverity.WARNING,
                source = "Verification · PKI",
                message = "Revocation unknown",
                details = "OCSP unreachable → warn, not fail",
                stageTag = StageErrorTag.PKI
            ),
            ErrorEntry(
                id = "sample-abe-fail",
                timestamp = System.currentTimeMillis() - 2700000,
                severity = ErrorSeverity.ERROR,
                source = "Verification · ABE",
                message = "Policy not satisfied",
                details = "Access denied: missing region:EU",
                stageTag = StageErrorTag.ABE
            ),
            ErrorEntry(
                id = "sample-jwt-fail",
                timestamp = System.currentTimeMillis() - 1800000,
                severity = ErrorSeverity.ERROR,
                source = "Verification · JWT",
                message = "Algorithm not allowed",
                details = "HS256 rejected · allowlist ES256/384/512",
                stageTag = StageErrorTag.JWT
            ),
            ErrorEntry(
                id = "sample-pki-fail",
                timestamp = System.currentTimeMillis() - 900000,
                severity = ErrorSeverity.ERROR,
                source = "Verification · PKI",
                message = "Certificate revoked",
                details = "leaf serial 4A:F3 on CRL · issued 2026-02",
                stageTag = StageErrorTag.PKI
            )
        )
    }
}

/**
 * Error entry data class
 */
data class ErrorEntry(
    val id: String,
    val timestamp: Long,
    val severity: ErrorSeverity,
    val source: String,
    val message: String,
    val details: String? = null,
    /** The verification pipeline stage this entry is attributed to (Phase 5), or null for non-pipeline entries. */
    val stageTag: StageErrorTag? = null
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

/**
 * Error severity levels
 */
enum class ErrorSeverity {
    INFO,
    WARNING,
    ERROR
}

/**
 * Error filter options
 */
enum class ErrorFilter {
    ALL,
    ERRORS_ONLY,
    WARNINGS_ONLY,
    INFO_ONLY,
    DECODE,
    PKI,
    JWT,
    ABE
}

/**
 * Does [entry] pass this filter? Severity facets match on severity; the stage facets (DECODE/PKI/JWT/ABE)
 * match the verification-stage tag — so the log is filterable by pipeline stage (Phase 5).
 */
fun ErrorFilter.accepts(entry: ErrorEntry): Boolean = when (this) {
    ErrorFilter.ALL -> true
    ErrorFilter.ERRORS_ONLY -> entry.severity == ErrorSeverity.ERROR
    ErrorFilter.WARNINGS_ONLY -> entry.severity == ErrorSeverity.WARNING
    ErrorFilter.INFO_ONLY -> entry.severity == ErrorSeverity.INFO
    ErrorFilter.DECODE -> entry.stageTag == StageErrorTag.DECODE
    ErrorFilter.PKI -> entry.stageTag == StageErrorTag.PKI
    ErrorFilter.JWT -> entry.stageTag == StageErrorTag.JWT
    ErrorFilter.ABE -> entry.stageTag == StageErrorTag.ABE
}

// ── Verification pipeline → Error Log plumbing (pure, unit-tested) ─────────────────────────────────────

/** The stage a failure is attributed to → the log-entry stage tag. Total (every stage has a tag). */
fun VerificationStage.toErrorTag(): StageErrorTag = when (this) {
    VerificationStage.DECODE -> StageErrorTag.DECODE
    VerificationStage.PKI -> StageErrorTag.PKI
    VerificationStage.JWT -> StageErrorTag.JWT
    VerificationStage.ABE -> StageErrorTag.ABE
}

/** A hard FAIL is an ERROR; a degraded/indeterminate WARN is a WARNING; PASS/SKIPPED are not log-worthy. */
private fun StageState.toSeverityOrNull(): ErrorSeverity? = when (this) {
    StageState.FAIL -> ErrorSeverity.ERROR
    StageState.WARN -> ErrorSeverity.WARNING
    StageState.PASS, StageState.SKIPPED -> null
}

/** A short, stage-derived title shown as the card headline (the full [StageResult.reason] is the detail). */
private fun titleFor(stage: VerificationStage, severity: ErrorSeverity): String {
    val stageLabel = stage.name
    val outcome = if (severity == ErrorSeverity.WARNING) "warning" else "failed"
    return "$stageLabel $outcome"
}

/**
 * Map a single [StageResult] to the Error Log entry it warrants, or null when the stage does not warrant
 * one (PASS/SKIPPED). Pure and total over the input — the caller supplies [timestamp] (and an optional
 * [id]) so the function stays deterministic and unit-testable. FAIL→ERROR, WARN→WARNING; the stage becomes
 * its [StageErrorTag]; the stage's real `reason` is carried through as the entry detail.
 */
fun StageResult.toErrorEntry(
    timestamp: Long,
    id: String = UUID.randomUUID().toString()
): ErrorEntry? {
    val severity = state.toSeverityOrNull() ?: return null
    return ErrorEntry(
        id = id,
        timestamp = timestamp,
        severity = severity,
        source = "Verification · ${stage.name}",
        message = titleFor(stage, severity),
        details = reason,
        stageTag = stage.toErrorTag()
    )
}

/**
 * Every log-worthy stage of a completed [VerificationResult] as Error Log entries, in pipeline order
 * (DECODE→PKI→JWT→ABE). PASS/SKIPPED stages contribute nothing; a clean all-pass result yields an empty
 * list. [timestamp] stamps every derived entry with the moment the scan was verified.
 */
fun VerificationResult.toErrorEntries(timestamp: Long = System.currentTimeMillis()): List<ErrorEntry> =
    stages.mapNotNull { it.toErrorEntry(timestamp) }
