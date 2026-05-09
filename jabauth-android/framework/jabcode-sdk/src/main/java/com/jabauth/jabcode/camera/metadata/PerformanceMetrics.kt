package com.jabauth.jabcode.camera.metadata

/**
 * Camera performance metrics
 * 
 * @property fps Frames per second
 * @property averageLatencyMs Average frame processing latency in milliseconds
 * @property droppedFrames Number of dropped frames
 * @property totalFrames Total number of frames processed
 */
data class PerformanceMetrics(
    val fps: Float,
    val averageLatencyMs: Float,
    val droppedFrames: Int,
    val totalFrames: Int
) {
    /**
     * Calculate frame drop rate
     * 
     * @return Drop rate as ratio (0-1)
     */
    val dropRate: Float
        get() = if (totalFrames > 0) {
            droppedFrames.toFloat() / totalFrames.toFloat()
        } else {
            0.0f
        }
    
    init {
        require(fps >= 0f) { "FPS must be non-negative" }
        require(averageLatencyMs >= 0f) { "Latency must be non-negative" }
        require(droppedFrames >= 0) { "Dropped frames must be non-negative" }
        require(totalFrames >= 0) { "Total frames must be non-negative" }
        require(droppedFrames <= totalFrames) { "Dropped frames cannot exceed total frames" }
    }
}
