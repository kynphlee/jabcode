package com.jabauth.diagnostic.report

/**
 * Interface for building bug reports
 * 
 * Collects diagnostic information for issue reporting.
 */
interface BugReportBuilder {
    
    /**
     * Add log entry to report
     * 
     * @param log Log message
     */
    fun addLog(log: String): BugReportBuilder
    
    /**
     * Add stack trace to report
     * 
     * @param stackTrace Stack trace string
     */
    fun addStackTrace(stackTrace: String): BugReportBuilder
    
    /**
     * Add performance metric to report
     * 
     * @param key Metric name
     * @param value Metric value
     */
    fun addMetric(key: String, value: Any): BugReportBuilder
    
    /**
     * Add context information
     * 
     * @param key Context key
     * @param value Context value
     */
    fun addContext(key: String, value: String): BugReportBuilder
    
    /**
     * Build the bug report
     * 
     * @return Complete BugReport
     */
    fun build(): BugReport
    
    /**
     * Reset builder state
     */
    fun reset()
}
