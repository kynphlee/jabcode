package com.jabauth.diagnostic.metrics

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for MetricsCollector
 * 
 * Tests metrics recording, retrieval, and export.
 * Coverage Target: 75%+
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetricsCollectorTest {
    
    private lateinit var collector: MetricsCollector
    
    @Before
    fun setup() {
        collector = TestMetricsCollectorImpl()
    }
    
    @Test
    fun `recordMetric stores metric successfully`() {
        val metric = PerformanceMetrics(
            name = "test_operation",
            timestamp = System.currentTimeMillis(),
            executionTimeMs = 100
        )
        
        collector.recordMetric(metric)
        
        assertThat(collector.getMetricsCount()).isEqualTo(1)
        assertThat(collector.getAllMetrics()).contains(metric)
    }
    
    @Test
    fun `getAllMetrics returns all recorded metrics`() {
        val metric1 = PerformanceMetrics("op1", System.currentTimeMillis())
        val metric2 = PerformanceMetrics("op2", System.currentTimeMillis())
        
        collector.recordMetric(metric1)
        collector.recordMetric(metric2)
        
        val allMetrics = collector.getAllMetrics()
        assertThat(allMetrics).hasSize(2)
        assertThat(allMetrics).containsExactly(metric1, metric2)
    }
    
    @Test
    fun `getMetricsByName filters by name correctly`() {
        val metric1 = PerformanceMetrics("encode", System.currentTimeMillis())
        val metric2 = PerformanceMetrics("decode", System.currentTimeMillis())
        val metric3 = PerformanceMetrics("encode", System.currentTimeMillis())
        
        collector.recordMetric(metric1)
        collector.recordMetric(metric2)
        collector.recordMetric(metric3)
        
        val encodeMetrics = collector.getMetricsByName("encode")
        assertThat(encodeMetrics).hasSize(2)
        assertThat(encodeMetrics).containsExactly(metric1, metric3)
    }
    
    @Test
    fun `getMetricsInTimeRange returns metrics in range`() {
        val now = System.currentTimeMillis()
        val metric1 = PerformanceMetrics("op1", now - 1000)
        val metric2 = PerformanceMetrics("op2", now)
        val metric3 = PerformanceMetrics("op3", now + 1000)
        
        collector.recordMetric(metric1)
        collector.recordMetric(metric2)
        collector.recordMetric(metric3)
        
        val rangeMetrics = collector.getMetricsInTimeRange(now - 500, now + 500)
        assertThat(rangeMetrics).hasSize(1)
        assertThat(rangeMetrics).contains(metric2)
    }
    
    @Test
    fun `clearMetrics removes all metrics`() {
        collector.recordMetric(PerformanceMetrics("op1", System.currentTimeMillis()))
        collector.recordMetric(PerformanceMetrics("op2", System.currentTimeMillis()))
        
        assertThat(collector.getMetricsCount()).isEqualTo(2)
        
        collector.clearMetrics()
        
        assertThat(collector.getMetricsCount()).isEqualTo(0)
        assertThat(collector.getAllMetrics()).isEmpty()
    }
    
    @Test
    fun `getMetricsCount returns correct count`() {
        assertThat(collector.getMetricsCount()).isEqualTo(0)
        
        collector.recordMetric(PerformanceMetrics("op1", System.currentTimeMillis()))
        assertThat(collector.getMetricsCount()).isEqualTo(1)
        
        collector.recordMetric(PerformanceMetrics("op2", System.currentTimeMillis()))
        assertThat(collector.getMetricsCount()).isEqualTo(2)
    }
    
    @Test
    fun `exportToJson produces valid JSON`() {
        val metric = PerformanceMetrics(
            name = "test_op",
            timestamp = 1234567890,
            executionTimeMs = 100,
            memoryUsedBytes = 1024,
            isSuccess = true
        )
        
        collector.recordMetric(metric)
        
        val json = collector.exportToJson()
        assertThat(json).contains("test_op")
        assertThat(json).contains("1234567890")
        assertThat(json).contains("100")
        assertThat(json).contains("1024")
    }
    
    @Test
    fun `metrics can store all field types`() {
        val metric = PerformanceMetrics(
            name = "full_metric",
            timestamp = System.currentTimeMillis(),
            executionTimeMs = 150,
            memoryUsedBytes = 2048,
            cpuUsagePercent = 45.5,
            batteryLevelPercent = 80,
            isSuccess = false,
            errorMessage = "Test error",
            metadata = mapOf("key1" to "value1", "key2" to 123)
        )
        
        collector.recordMetric(metric)
        
        val retrieved = collector.getAllMetrics().first()
        assertThat(retrieved.name).isEqualTo("full_metric")
        assertThat(retrieved.executionTimeMs).isEqualTo(150)
        assertThat(retrieved.memoryUsedBytes).isEqualTo(2048)
        assertThat(retrieved.cpuUsagePercent).isEqualTo(45.5)
        assertThat(retrieved.batteryLevelPercent).isEqualTo(80)
        assertThat(retrieved.isSuccess).isFalse()
        assertThat(retrieved.errorMessage).isEqualTo("Test error")
        assertThat(retrieved.metadata).containsEntry("key1", "value1")
    }
}
