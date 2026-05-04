package com.jabauth.diagnostic.report

/**
 * Bug report data
 * 
 * Contains device info, logs, stack traces, and metrics for debugging.
 */
data class BugReport(
    /**
     * Report timestamp
     */
    val timestamp: Long,
    
    /**
     * Device manufacturer
     */
    val deviceManufacturer: String,
    
    /**
     * Device model
     */
    val deviceModel: String,
    
    /**
     * Android OS version
     */
    val osVersion: String,
    
    /**
     * App version
     */
    val appVersion: String,
    
    /**
     * Log entries
     */
    val logs: List<String> = emptyList(),
    
    /**
     * Stack traces
     */
    val stackTraces: List<String> = emptyList(),
    
    /**
     * Performance metrics
     */
    val metrics: Map<String, Any> = emptyMap(),
    
    /**
     * Additional context
     */
    val context: Map<String, String> = emptyMap()
) {
    /**
     * Format report as markdown
     */
    fun toMarkdown(): String {
        return buildString {
            appendLine("# Bug Report")
            appendLine()
            appendLine("**Timestamp:** ${java.util.Date(timestamp)}")
            appendLine()
            appendLine("## Device Information")
            appendLine("- **Manufacturer:** $deviceManufacturer")
            appendLine("- **Model:** $deviceModel")
            appendLine("- **OS Version:** $osVersion")
            appendLine("- **App Version:** $appVersion")
            appendLine()
            
            if (context.isNotEmpty()) {
                appendLine("## Context")
                context.forEach { (key, value) ->
                    appendLine("- **$key:** $value")
                }
                appendLine()
            }
            
            if (logs.isNotEmpty()) {
                appendLine("## Logs")
                appendLine("```")
                logs.forEach { appendLine(it) }
                appendLine("```")
                appendLine()
            }
            
            if (stackTraces.isNotEmpty()) {
                appendLine("## Stack Traces")
                stackTraces.forEach { trace ->
                    appendLine("```")
                    appendLine(trace)
                    appendLine("```")
                    appendLine()
                }
            }
            
            if (metrics.isNotEmpty()) {
                appendLine("## Performance Metrics")
                metrics.forEach { (key, value) ->
                    appendLine("- **$key:** $value")
                }
            }
        }
    }
}
