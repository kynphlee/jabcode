package com.jabauth.jabcode.camera.metadata

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PerformanceMetrics data class
 * 
 * Tests camera performance metric properties
 */
class PerformanceMetricsTest {
    
    @Test
    fun performanceMetrics_storesCorrectProperties() {
        val metrics = PerformanceMetrics(
            fps = 30.0f,
            averageLatencyMs = 33.3f,
            droppedFrames = 5,
            totalFrames = 100
        )
        
        assertEquals(30.0f, metrics.fps, 0.001f)
        assertEquals(33.3f, metrics.averageLatencyMs, 0.001f)
        assertEquals(5, metrics.droppedFrames)
        assertEquals(100, metrics.totalFrames)
    }
    
    @Test
    fun performanceMetrics_calculatesDropRate() {
        val metrics = PerformanceMetrics(
            fps = 30.0f,
            averageLatencyMs = 30.0f,
            droppedFrames = 10,
            totalFrames = 100
        )
        
        val dropRate = metrics.dropRate
        assertEquals(0.1f, dropRate, 0.001f) // 10/100 = 0.1
    }
    
    @Test
    fun performanceMetrics_dropRateZeroWhenNoDrops() {
        val metrics = PerformanceMetrics(
            fps = 30.0f,
            averageLatencyMs = 30.0f,
            droppedFrames = 0,
            totalFrames = 100
        )
        
        assertEquals(0.0f, metrics.dropRate, 0.001f)
    }
    
    @Test
    fun performanceMetrics_copyWorks() {
        val original = PerformanceMetrics(
            fps = 30.0f,
            averageLatencyMs = 30.0f,
            droppedFrames = 5,
            totalFrames = 100
        )
        
        val modified = original.copy(
            droppedFrames = 10,
            totalFrames = 150
        )
        
        assertEquals(30.0f, modified.fps, 0.001f)
        assertEquals(10, modified.droppedFrames)
        assertEquals(150, modified.totalFrames)
    }
    
    @Test
    fun performanceMetrics_fpsValidRange() {
        val lowFps = PerformanceMetrics(
            fps = 15.0f,
            averageLatencyMs = 66.6f,
            droppedFrames = 0,
            totalFrames = 100
        )
        
        val highFps = PerformanceMetrics(
            fps = 60.0f,
            averageLatencyMs = 16.6f,
            droppedFrames = 0,
            totalFrames = 100
        )
        
        assertTrue("Low FPS should be < 30", lowFps.fps < 30.0f)
        assertTrue("High FPS should be >= 60", highFps.fps >= 60.0f)
    }
}
