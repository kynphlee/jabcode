package com.jabauth.jabcode

import com.jabauth.core.logging.Logger

/**
 * Tracks performance metrics for JABCode operations
 *
 * Collects timing and success rate statistics for encode/decode operations.
 * Thread-safe for concurrent operations.
 */
class PerformanceTracker(
    private val logger: Logger? = null
) {
    private val lock = Any()
    private var encodeMetrics = PerformanceMetrics("encode")
    private var decodeMetrics = PerformanceMetrics("decode")
    
    /**
     * Record encode operation
     *
     * @param durationMs Time taken for encode operation
     * @param success Whether operation succeeded
     */
    fun recordEncode(durationMs: Long, success: Boolean) {
        synchronized(lock) {
            encodeMetrics = encodeMetrics.withOperation(durationMs, success)
        }
        
        logger?.debug("Encode operation recorded", mapOf(
            "durationMs" to durationMs,
            "success" to success,
            "avgMs" to encodeMetrics.avgTimeMs
        ))
    }
    
    /**
     * Record decode operation
     *
     * @param durationMs Time taken for decode operation
     * @param success Whether operation succeeded
     */
    fun recordDecode(durationMs: Long, success: Boolean) {
        synchronized(lock) {
            decodeMetrics = decodeMetrics.withOperation(durationMs, success)
        }
        
        logger?.debug("Decode operation recorded", mapOf(
            "durationMs" to durationMs,
            "success" to success,
            "avgMs" to decodeMetrics.avgTimeMs
        ))
    }
    
    /**
     * Get encode metrics
     */
    fun getEncodeMetrics(): PerformanceMetrics {
        synchronized(lock) {
            return encodeMetrics
        }
    }
    
    /**
     * Get decode metrics
     */
    fun getDecodeMetrics(): PerformanceMetrics {
        synchronized(lock) {
            return decodeMetrics
        }
    }
    
    /**
     * Reset all metrics
     */
    fun reset() {
        synchronized(lock) {
            encodeMetrics = PerformanceMetrics("encode")
            decodeMetrics = PerformanceMetrics("decode")
        }
        
        logger?.info("Performance metrics reset")
    }
    
    /**
     * Get summary report
     */
    fun getSummary(): String {
        val encode = getEncodeMetrics()
        val decode = getDecodeMetrics()
        
        return buildString {
            appendLine("=== JABCode Performance Summary ===")
            appendLine()
            appendLine("Encode Operations:")
            appendLine("  Total: ${encode.totalOperations}")
            appendLine("  Success Rate: ${String.format("%.1f%%", encode.successRate * 100)}")
            if (!encode.isEmpty()) {
                appendLine("  Avg Time: ${String.format("%.1fms", encode.avgTimeMs)}")
                appendLine("  Min Time: ${encode.minTimeMs}ms")
                appendLine("  Max Time: ${encode.maxTimeMs}ms")
            }
            appendLine()
            appendLine("Decode Operations:")
            appendLine("  Total: ${decode.totalOperations}")
            appendLine("  Success Rate: ${String.format("%.1f%%", decode.successRate * 100)}")
            if (!decode.isEmpty()) {
                appendLine("  Avg Time: ${String.format("%.1fms", decode.avgTimeMs)}")
                appendLine("  Min Time: ${decode.minTimeMs}ms")
                appendLine("  Max Time: ${decode.maxTimeMs}ms")
            }
        }
    }
}
