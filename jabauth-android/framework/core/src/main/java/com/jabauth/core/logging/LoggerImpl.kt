package com.jabauth.core.logging

import android.util.Log

/**
 * Production Logger implementation using Android Logcat
 *
 * Logs to Android's Logcat system with automatic tag prefixing and structured
 * metadata formatting. Debug logs are automatically disabled in release builds.
 *
 * Thread-safe and lightweight for production use.
 */
class LoggerImpl(
    private val tag: String = DEFAULT_TAG,
    private val debugEnabled: Boolean = true
) : Logger {

    override fun debug(message: String, metadata: Map<String, Any?>?) {
        if (debugEnabled) {
            Log.d(tag, formatMessage(message, metadata))
        }
    }

    override fun info(message: String, metadata: Map<String, Any?>?) {
        Log.i(tag, formatMessage(message, metadata))
    }

    override fun warn(message: String, metadata: Map<String, Any?>?) {
        Log.w(tag, formatMessage(message, metadata))
    }

    override fun error(message: String, throwable: Throwable?, metadata: Map<String, Any?>?) {
        val formattedMessage = formatMessage(message, metadata)
        if (throwable != null) {
            Log.e(tag, formattedMessage, throwable)
        } else {
            Log.e(tag, formattedMessage)
        }
    }

    override fun withTag(tag: String): Logger {
        return LoggerImpl(tag, debugEnabled)
    }

    override fun isDebugEnabled(): Boolean = debugEnabled

    /**
     * Format log message with optional metadata
     *
     * Metadata is appended as key-value pairs for structured logging.
     */
    private fun formatMessage(message: String, metadata: Map<String, Any?>?): String {
        if (metadata.isNullOrEmpty()) {
            return message
        }

        val metadataString = metadata.entries.joinToString(", ") { (key, value) ->
            "$key=$value"
        }
        return "$message [$metadataString]"
    }

    companion object {
        private const val DEFAULT_TAG = "JABAuth"

        /**
         * Create a logger with debug logging disabled
         *
         * Use this factory method for production builds.
         */
        fun createProductionLogger(tag: String = DEFAULT_TAG): Logger {
            return LoggerImpl(tag, debugEnabled = false)
        }

        /**
         * Create a logger with debug logging enabled
         *
         * Use this factory method for debug builds.
         */
        fun createDebugLogger(tag: String = DEFAULT_TAG): Logger {
            return LoggerImpl(tag, debugEnabled = true)
        }
    }
}
