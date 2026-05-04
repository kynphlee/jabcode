package com.jabauth.diagnostic.metrics

import org.json.JSONArray
import org.json.JSONObject

/**
 * Test implementation of MetricsCollector
 * 
 * In-memory storage for unit testing.
 */
class TestMetricsCollectorImpl : MetricsCollector {
    
    private val metrics = mutableListOf<PerformanceMetrics>()
    
    override fun recordMetric(metric: PerformanceMetrics) {
        metrics.add(metric)
    }
    
    override fun getAllMetrics(): List<PerformanceMetrics> {
        return metrics.toList()
    }
    
    override fun getMetricsByName(name: String): List<PerformanceMetrics> {
        return metrics.filter { it.name == name }
    }
    
    override fun getMetricsInTimeRange(startTime: Long, endTime: Long): List<PerformanceMetrics> {
        return metrics.filter { it.timestamp >= startTime && it.timestamp < endTime }
    }
    
    override fun clearMetrics() {
        metrics.clear()
    }
    
    override fun getMetricsCount(): Int {
        return metrics.size
    }
    
    override fun exportToJson(): String {
        val jsonArray = JSONArray()
        metrics.forEach { metric ->
            val jsonObject = JSONObject(metric.toMap())
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString(2)
    }
}
