package com.jabauth.jabcode

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PerformanceTracker
 */
class PerformanceTrackerTest {

    private lateinit var tracker: PerformanceTracker

    @Before
    fun setup() {
        tracker = PerformanceTracker(null)
    }

    @Test
    fun `initial state has empty metrics`() {
        val encode = tracker.getEncodeMetrics()
        val decode = tracker.getDecodeMetrics()
        
        assertTrue(encode.isEmpty())
        assertTrue(decode.isEmpty())
        assertEquals(0, encode.totalOperations)
        assertEquals(0, decode.totalOperations)
    }

    @Test
    fun `recordEncode updates encode metrics`() {
        tracker.recordEncode(100L, true)
        
        val metrics = tracker.getEncodeMetrics()
        
        assertEquals(1, metrics.totalOperations)
        assertEquals(1, metrics.successfulOperations)
        assertEquals(100.0, metrics.avgTimeMs, 0.1)
    }

    @Test
    fun `recordDecode updates decode metrics`() {
        tracker.recordDecode(200L, true)
        
        val metrics = tracker.getDecodeMetrics()
        
        assertEquals(1, metrics.totalOperations)
        assertEquals(1, metrics.successfulOperations)
        assertEquals(200.0, metrics.avgTimeMs, 0.1)
    }

    @Test
    fun `multiple operations calculate correct average`() {
        tracker.recordEncode(100L, true)
        tracker.recordEncode(200L, true)
        tracker.recordEncode(300L, true)
        
        val metrics = tracker.getEncodeMetrics()
        
        assertEquals(3, metrics.totalOperations)
        assertEquals(200.0, metrics.avgTimeMs, 0.1)
    }

    @Test
    fun `tracks min and max times correctly`() {
        tracker.recordEncode(50L, true)
        tracker.recordEncode(300L, true)
        tracker.recordEncode(150L, true)
        
        val metrics = tracker.getEncodeMetrics()
        
        assertEquals(50L, metrics.minTimeMs)
        assertEquals(300L, metrics.maxTimeMs)
    }

    @Test
    fun `success rate calculated correctly`() {
        tracker.recordDecode(100L, true)
        tracker.recordDecode(100L, true)
        tracker.recordDecode(100L, false)
        
        val metrics = tracker.getDecodeMetrics()
        
        assertEquals(3, metrics.totalOperations)
        assertEquals(2, metrics.successfulOperations)
        assertEquals(0.666, metrics.successRate, 0.01)
    }

    @Test
    fun `reset clears all metrics`() {
        tracker.recordEncode(100L, true)
        tracker.recordDecode(200L, true)
        
        tracker.reset()
        
        assertTrue(tracker.getEncodeMetrics().isEmpty())
        assertTrue(tracker.getDecodeMetrics().isEmpty())
    }

    @Test
    fun `getSummary returns formatted report`() {
        tracker.recordEncode(100L, true)
        tracker.recordEncode(200L, false)
        tracker.recordDecode(150L, true)
        
        val summary = tracker.getSummary()
        
        assertTrue(summary.contains("Encode Operations"))
        assertTrue(summary.contains("Decode Operations"))
        assertTrue(summary.contains("Total: 2"))
        assertTrue(summary.contains("Total: 1"))
    }

    @Test
    fun `operations are independent between encode and decode`() {
        tracker.recordEncode(100L, true)
        tracker.recordDecode(200L, true)
        
        val encode = tracker.getEncodeMetrics()
        val decode = tracker.getDecodeMetrics()
        
        assertEquals(1, encode.totalOperations)
        assertEquals(1, decode.totalOperations)
        assertNotEquals(encode.avgTimeMs, decode.avgTimeMs, 0.1)
    }

    @Test
    fun `metrics track total time correctly`() {
        tracker.recordEncode(100L, true)
        tracker.recordEncode(200L, true)
        tracker.recordEncode(300L, true)
        
        val metrics = tracker.getEncodeMetrics()
        
        assertEquals(600L, metrics.totalTimeMs)
    }
}
