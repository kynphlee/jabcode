package com.jabauth.diagnostic.ui.errorlog

import androidx.lifecycle.ViewModel
import com.jabauth.diagnostic.verify.StageErrorTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for Error Log screen
 * 
 * Manages error history with filtering and clearing capabilities
 */
class ErrorLogViewModel : ViewModel() {
    
    private val _errors = MutableStateFlow<List<ErrorEntry>>(emptyList())
    val errors: StateFlow<List<ErrorEntry>> = _errors.asStateFlow()
    
    private val _filter = MutableStateFlow(ErrorFilter.ALL)
    val filter: StateFlow<ErrorFilter> = _filter.asStateFlow()
    
    init {
        // Load sample error data for demonstration
        loadSampleErrors()
    }
    
    fun setFilter(filter: ErrorFilter) {
        _filter.value = filter
    }
    
    fun clearErrors() {
        _errors.value = emptyList()
    }
    
    fun addError(
        severity: ErrorSeverity,
        source: String,
        message: String,
        details: String? = null
    ) {
        val newError = ErrorEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            severity = severity,
            source = source,
            message = message,
            details = details
        )
        _errors.value = listOf(newError) + _errors.value
    }
    
    private fun loadSampleErrors() {
        // Sample errors for UI demonstration
        _errors.value = listOf(
            ErrorEntry(
                id = "1",
                timestamp = System.currentTimeMillis() - 3600000,
                severity = ErrorSeverity.ERROR,
                source = "JABCode Decoder",
                message = "LDPC decoding failed",
                details = "Too many errors in message. Code may be damaged or low quality.",
                stageTag = StageErrorTag.DECODE
            ),
            ErrorEntry(
                id = "2",
                timestamp = System.currentTimeMillis() - 7200000,
                severity = ErrorSeverity.WARNING,
                source = "Camera Focus",
                message = "Focus quality below threshold",
                details = "Laplacian variance: 42.3 (threshold: 100.0)"
            ),
            ErrorEntry(
                id = "3",
                timestamp = System.currentTimeMillis() - 10800000,
                severity = ErrorSeverity.INFO,
                source = "Camera Enumeration",
                message = "Back camera hardware level: LIMITED",
                details = "Full manual control not available on this device"
            ),
            ErrorEntry(
                id = "4",
                timestamp = System.currentTimeMillis() - 1800000,
                severity = ErrorSeverity.ERROR,
                source = "Verification · PKI",
                message = "Certificate revoked",
                details = "OCSP/CRL reports the signer certificate as revoked.",
                stageTag = StageErrorTag.PKI
            ),
            ErrorEntry(
                id = "5",
                timestamp = System.currentTimeMillis() - 900000,
                severity = ErrorSeverity.ERROR,
                source = "Verification · JWT",
                message = "Algorithm not allowed (HS256 rejected)",
                details = "Algorithm-confusion guard: only RS*/ES* signatures are permitted.",
                stageTag = StageErrorTag.JWT
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
