package com.jabauth.jabcode.camera.metadata

/**
 * Tracks camera performance metrics over time
 * 
 * Monitors FPS, latency, and dropped frames
 */
class PerformanceTracker {
    
    private var frameTimestamps = mutableListOf<Long>()
    private var latencies = mutableListOf<Float>()
    private var droppedCount = 0
    private var totalCount = 0
    
    /**
     * Record a frame timestamp
     * 
     * @param timestamp Frame timestamp in nanoseconds
     */
    fun recordFrame(timestamp: Long) {
        frameTimestamps.add(timestamp)
        totalCount++
        
        // Keep only recent frames for FPS calculation (last 2 seconds)
        val twoSecondsAgo = timestamp - 2_000_000_000L
        frameTimestamps.removeAll { it < twoSecondsAgo }
    }
    
    /**
     * Record a dropped frame
     */
    fun recordDroppedFrame() {
        droppedCount++
        totalCount++
    }
    
    /**
     * Record frame processing latency
     * 
     * @param latencyMs Processing latency in milliseconds
     */
    fun recordLatency(latencyMs: Float) {
        latencies.add(latencyMs)
        
        // Keep only recent latencies (last 100)
        if (latencies.size > 100) {
            latencies.removeAt(0)
        }
    }
    
    /**
     * Get current performance metrics
     * 
     * @return Current metrics or null if insufficient data
     */
    fun getCurrentMetrics(): PerformanceMetrics? {
        if (totalCount == 0) return null
        
        val fps = calculateFPS()
        val avgLatency = if (latencies.isNotEmpty()) {
            latencies.average().toFloat()
        } else {
            0.0f
        }
        
        return PerformanceMetrics(
            fps = fps,
            averageLatencyMs = avgLatency,
            droppedFrames = droppedCount,
            totalFrames = totalCount
        )
    }
    
    /**
     * Reset tracking state
     */
    fun reset() {
        frameTimestamps.clear()
        latencies.clear()
        droppedCount = 0
        totalCount = 0
    }
    
    private fun calculateFPS(): Float {
        if (frameTimestamps.size < 2) return 0.0f
        
        val first = frameTimestamps.first()
        val last = frameTimestamps.last()
        val durationNs = last - first
        
        if (durationNs <= 0) return 0.0f
        
        val durationSeconds = durationNs / 1_000_000_000.0
        return ((frameTimestamps.size - 1) / durationSeconds).toFloat()
    }
}
