package com.jabauth.jabcode

/**
 * Performance metrics for JABCode operations
 *
 * Tracks timing and success rate statistics.
 *
 * @property operationType Type of operation ("encode" or "decode")
 * @property totalOperations Total number of operations performed
 * @property successfulOperations Number of successful operations
 * @property totalTimeMs Total time spent (milliseconds)
 * @property minTimeMs Fastest operation time
 * @property maxTimeMs Slowest operation time
 * @property avgTimeMs Average operation time
 */
data class PerformanceMetrics(
    val operationType: String,
    val totalOperations: Int = 0,
    val successfulOperations: Int = 0,
    val totalTimeMs: Long = 0,
    val minTimeMs: Long = Long.MAX_VALUE,
    val maxTimeMs: Long = 0,
    val avgTimeMs: Double = 0.0
) {
    /**
     * Success rate (0.0 to 1.0)
     */
    val successRate: Double
        get() = if (totalOperations > 0) {
            successfulOperations.toDouble() / totalOperations
        } else 0.0
    
    /**
     * Check if metrics are empty (no operations recorded)
     */
    fun isEmpty(): Boolean = totalOperations == 0
    
    /**
     * Create metrics with new operation recorded
     */
    fun withOperation(durationMs: Long, success: Boolean): PerformanceMetrics {
        val newTotal = totalOperations + 1
        val newSuccessful = if (success) successfulOperations + 1 else successfulOperations
        val newTotalTime = totalTimeMs + durationMs
        val newMin = minOf(minTimeMs, durationMs)
        val newMax = maxOf(maxTimeMs, durationMs)
        val newAvg = newTotalTime.toDouble() / newTotal
        
        return copy(
            totalOperations = newTotal,
            successfulOperations = newSuccessful,
            totalTimeMs = newTotalTime,
            minTimeMs = newMin,
            maxTimeMs = newMax,
            avgTimeMs = newAvg
        )
    }
}
