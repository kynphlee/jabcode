package com.jabauth.core.logging

/**
 * Logger interface for framework-wide logging
 *
 * Provides structured logging with multiple severity levels and tagging support.
 * Implementations should be lightweight and suitable for production use.
 *
 * Usage:
 * ```
 * logger.info("User authenticated successfully")
 * logger.error("Failed to validate certificate", throwable)
 * logger.debug("Decoding JABCode", mapOf("colorMode" to 4, "eccLevel" to 3))
 * ```
 */
interface Logger {

    /**
     * Log a debug message
     *
     * Use for detailed diagnostic information useful during development.
     * These logs are typically disabled in production builds.
     *
     * @param message The message to log
     * @param metadata Optional key-value pairs for structured logging
     */
    fun debug(message: String, metadata: Map<String, Any?>? = null)

    /**
     * Log an informational message
     *
     * Use for general informational messages that highlight application progress.
     *
     * @param message The message to log
     * @param metadata Optional key-value pairs for structured logging
     */
    fun info(message: String, metadata: Map<String, Any?>? = null)

    /**
     * Log a warning message
     *
     * Use for potentially harmful situations that should be noted but don't
     * prevent operation.
     *
     * @param message The message to log
     * @param metadata Optional key-value pairs for structured logging
     */
    fun warn(message: String, metadata: Map<String, Any?>? = null)

    /**
     * Log an error message
     *
     * Use for error events that might still allow the application to continue.
     *
     * @param message The message to log
     * @param throwable Optional exception associated with the error
     * @param metadata Optional key-value pairs for structured logging
     */
    fun error(message: String, throwable: Throwable? = null, metadata: Map<String, Any?>? = null)

    /**
     * Create a tagged logger instance
     *
     * Returns a new logger that automatically prefixes all messages with the given tag.
     * Useful for identifying the source of log messages.
     *
     * @param tag The tag to prefix to all messages
     * @return A new Logger instance with the tag applied
     */
    fun withTag(tag: String): Logger

    /**
     * Check if debug logging is enabled
     *
     * Use this to avoid expensive string construction for debug messages
     * that won't be logged in production.
     *
     * @return true if debug messages will be logged
     */
    fun isDebugEnabled(): Boolean
}
