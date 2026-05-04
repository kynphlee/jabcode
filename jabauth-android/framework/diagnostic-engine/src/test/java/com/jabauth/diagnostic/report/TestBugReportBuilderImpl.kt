package com.jabauth.diagnostic.report

/**
 * Test implementation of BugReportBuilder
 * 
 * For unit testing.
 */
class TestBugReportBuilderImpl(
    private val deviceManufacturer: String = "TestManufacturer",
    private val deviceModel: String = "TestModel",
    private val osVersion: String = "Android 14",
    private val appVersion: String = "1.0.0-test"
) : BugReportBuilder {
    
    private val logs = mutableListOf<String>()
    private val stackTraces = mutableListOf<String>()
    private val metrics = mutableMapOf<String, Any>()
    private val context = mutableMapOf<String, String>()
    
    override fun addLog(log: String): BugReportBuilder {
        logs.add(log)
        return this
    }
    
    override fun addStackTrace(stackTrace: String): BugReportBuilder {
        stackTraces.add(stackTrace)
        return this
    }
    
    override fun addMetric(key: String, value: Any): BugReportBuilder {
        metrics[key] = value
        return this
    }
    
    override fun addContext(key: String, value: String): BugReportBuilder {
        context[key] = value
        return this
    }
    
    override fun build(): BugReport {
        return BugReport(
            timestamp = System.currentTimeMillis(),
            deviceManufacturer = deviceManufacturer,
            deviceModel = deviceModel,
            osVersion = osVersion,
            appVersion = appVersion,
            logs = logs.toList(),
            stackTraces = stackTraces.toList(),
            metrics = metrics.toMap(),
            context = context.toMap()
        )
    }
    
    override fun reset() {
        logs.clear()
        stackTraces.clear()
        metrics.clear()
        context.clear()
    }
}
