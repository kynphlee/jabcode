package com.jabauth.diagnostic.metrics

/**
 * Performance metrics data
 * 
 * Contains timing, memory, and resource usage information.
 */
data class PerformanceMetrics(
    /**
     * Metric name
     */
    val name: String,
    
    /**
     * Timestamp when metric was collected (milliseconds since epoch)
     */
    val timestamp: Long,
    
    /**
     * Execution time in milliseconds
     */
    val executionTimeMs: Long? = null,
    
    /**
     * Memory used in bytes
     */
    val memoryUsedBytes: Long? = null,
    
    /**
     * CPU usage percentage (0-100)
     */
    val cpuUsagePercent: Double? = null,
    
    /**
     * Battery level percentage (0-100)
     */
    val batteryLevelPercent: Int? = null,
    
    /**
     * Success indicator
     */
    val isSuccess: Boolean = true,
    
    /**
     * Error message if failed
     */
    val errorMessage: String? = null,
    
    /**
     * Additional metadata
     */
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Convert to map for JSON serialization
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "name" to name,
            "timestamp" to timestamp,
            "executionTimeMs" to executionTimeMs,
            "memoryUsedBytes" to memoryUsedBytes,
            "cpuUsagePercent" to cpuUsagePercent,
            "batteryLevelPercent" to batteryLevelPercent,
            "isSuccess" to isSuccess,
            "errorMessage" to errorMessage,
            "metadata" to metadata
        )
    }
}
