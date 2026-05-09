package com.jabauth.jabcode.camera.metadata

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PerformanceTracker
 * 
 * Tests performance metric tracking and aggregation
 */
class PerformanceTrackerTest {
    
    private lateinit var tracker: PerformanceTracker
    
    @Before
    fun setup() {
        tracker = PerformanceTracker()
    }
    
    @Test
    fun tracker_initiallyEmpty() {
        assertNull(tracker.getCurrentMetrics())
    }
    
    @Test
    fun tracker_recordsFrameTimestamp() {
        tracker.recordFrame(timestamp = 1000000000L) // 1 second
        tracker.recordFrame(timestamp = 1033333333L) // +33.33ms for 30 FPS
        tracker.recordFrame(timestamp = 1066666666L) // +33.33ms
        
        val metrics = tracker.getCurrentMetrics()
        assertNotNull(metrics)
        assertTrue("FPS should be around 30", metrics!!.fps in 25f..35f)
    }
    
    @Test
    fun tracker_tracksDroppedFrames() {
        tracker.recordFrame(timestamp = 1000000000L)
        tracker.recordFrame(timestamp = 1033333333L)
        tracker.recordDroppedFrame()
        tracker.recordFrame(timestamp = 1066666666L)
        
        val metrics = tracker.getCurrentMetrics()
        assertNotNull(metrics)
        assertEquals(1, metrics!!.droppedFrames)
        assertEquals(4, metrics.totalFrames) // 3 recorded + 1 dropped
    }
    
    @Test
    fun tracker_calculatesAverageLatency() {
        tracker.recordFrame(timestamp = 1000000000L)
        tracker.recordLatency(latencyMs = 30.0f)
        
        tracker.recordFrame(timestamp = 1033333333L)
        tracker.recordLatency(latencyMs = 40.0f)
        
        val metrics = tracker.getCurrentMetrics()
        assertNotNull(metrics)
        assertEquals(35.0f, metrics!!.averageLatencyMs, 0.1f) // (30 + 40) / 2
    }
    
    @Test
    fun tracker_reset_clearsState() {
        tracker.recordFrame(timestamp = 1000000000L)
        tracker.recordFrame(timestamp = 1033333333L)
        tracker.recordDroppedFrame()
        
        tracker.reset()
        
        assertNull(tracker.getCurrentMetrics())
    }
    
    @Test
    fun tracker_handlesSingleFrame() {
        tracker.recordFrame(timestamp = 1000000000L)
        
        val metrics = tracker.getCurrentMetrics()
        assertNotNull(metrics)
        assertEquals(1, metrics!!.totalFrames)
        assertEquals(0, metrics.droppedFrames)
    }
    
    @Test
    fun tracker_calculatesHighFPS() {
        // Simulate 60 FPS (16.67ms per frame)
        tracker.recordFrame(timestamp = 1000000000L) // 1 second
        tracker.recordFrame(timestamp = 1016670000L) // +16.67ms
        tracker.recordFrame(timestamp = 1033340000L) // +16.67ms
        tracker.recordFrame(timestamp = 1050010000L) // +16.67ms
        
        val metrics = tracker.getCurrentMetrics()
        assertNotNull(metrics)
        assertTrue("FPS should be around 60", metrics!!.fps in 55f..65f)
    }
}
