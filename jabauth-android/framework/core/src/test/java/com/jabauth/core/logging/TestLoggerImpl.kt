package com.jabauth.core.logging

/**
 * Test implementation of Logger for unit testing
 *
 * Captures all log messages in memory for verification in tests.
 * Does not perform actual Android logging (no Logcat dependency).
 *
 * Production code uses LoggerImpl with actual Logcat integration.
 */
class TestLoggerImpl(
    private val tag: String? = null,
    private val debugEnabled: Boolean = true
) : Logger {

    private val logs = mutableListOf<LogEntry>()

    override fun debug(message: String, metadata: Map<String, Any?>?) {
        if (debugEnabled) {
            logs.add(LogEntry(LogLevel.DEBUG, message, null, metadata, tag))
        }
    }

    override fun info(message: String, metadata: Map<String, Any?>?) {
        logs.add(LogEntry(LogLevel.INFO, message, null, metadata, tag))
    }

    override fun warn(message: String, metadata: Map<String, Any?>?) {
        logs.add(LogEntry(LogLevel.WARN, message, null, metadata, tag))
    }

    override fun error(message: String, throwable: Throwable?, metadata: Map<String, Any?>?) {
        logs.add(LogEntry(LogLevel.ERROR, message, throwable, metadata, tag))
    }

    override fun withTag(tag: String): Logger {
        return TestLoggerImpl(tag, debugEnabled)
    }

    override fun isDebugEnabled(): Boolean = debugEnabled

    /**
     * Get all logged messages for test verification
     */
    fun getLogs(): List<LogEntry> = logs.toList()

    /**
     * Clear all logged messages
     */
    fun clear() {
        logs.clear()
    }
}

/**
 * Log level enumeration
 */
enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

/**
 * Captured log entry for test verification
 */
data class LogEntry(
    val level: LogLevel,
    val message: String,
    val throwable: Throwable?,
    val metadata: Map<String, Any?>?,
    val tag: String?
)
