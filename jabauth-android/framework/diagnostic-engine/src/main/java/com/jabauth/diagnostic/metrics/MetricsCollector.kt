package com.jabauth.diagnostic.metrics

/**
 * Interface for collecting performance metrics
 * 
 * Tracks app-wide performance data for monitoring and diagnostics.
 */
interface MetricsCollector {
    
    /**
     * Record a performance metric
     * 
     * @param metric Performance metric to record
     */
    fun recordMetric(metric: PerformanceMetrics)
    
    /**
     * Get all recorded metrics
     * 
     * @return List of all metrics
     */
    fun getAllMetrics(): List<PerformanceMetrics>
    
    /**
     * Get metrics by name
     * 
     * @param name Metric name to filter by
     * @return List of matching metrics
     */
    fun getMetricsByName(name: String): List<PerformanceMetrics>
    
    /**
     * Get metrics within time range
     * 
     * @param startTime Start timestamp (inclusive)
     * @param endTime End timestamp (exclusive)
     * @return List of metrics in range
     */
    fun getMetricsInTimeRange(startTime: Long, endTime: Long): List<PerformanceMetrics>
    
    /**
     * Clear all recorded metrics
     */
    fun clearMetrics()
    
    /**
     * Get metrics count
     * 
     * @return Total number of recorded metrics
     */
    fun getMetricsCount(): Int
    
    /**
     * Export metrics to JSON format
     * 
     * @return JSON string containing all metrics
     */
    fun exportToJson(): String
}
