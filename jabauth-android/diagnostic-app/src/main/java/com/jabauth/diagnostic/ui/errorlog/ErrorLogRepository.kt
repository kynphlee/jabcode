package com.jabauth.diagnostic.ui.errorlog

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide sink for [ErrorEntry]s, shared across the app's independently-scoped screens.
 *
 * The Scanner and the Error Log live on separate navigation destinations, so each gets its own
 * `viewModel()`-scoped ViewModel with no common owner. A verification failure surfaced while scanning must
 * nonetheless appear on the Error Log screen — so both sides talk to this single object: the Scanner
 * publishes ([add]); the Error Log observes ([entries]) and clears ([clear]).
 *
 * Newest-first ordering (prepend) matches the screen's reverse-chronological list. In-memory only — the log
 * is session-scoped diagnostic state, not persisted (consistent with the pre-existing decode telemetry).
 */
object ErrorLogRepository {

    private val _entries = MutableStateFlow<List<ErrorEntry>>(emptyList())
    val entries: StateFlow<List<ErrorEntry>> = _entries.asStateFlow()

    /** Prepend [entry] (newest-first). */
    fun add(entry: ErrorEntry) {
        _entries.value = listOf(entry) + _entries.value
    }

    /** Drop every entry (the Error Log "clear" action). */
    fun clear() {
        _entries.value = emptyList()
    }
}
