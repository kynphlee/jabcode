package com.jabauth.diagnostic.ui.capturetest

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for Capture Test screen
 * 
 * Manages camera stream validation and quality metrics
 */
class CaptureTestViewModel : ViewModel() {
    
    private val _streamState = MutableStateFlow<StreamState>(StreamState.Stopped)
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()
    
    private val _frameMetrics = MutableStateFlow<FrameMetrics?>(null)
    val frameMetrics: StateFlow<FrameMetrics?> = _frameMetrics.asStateFlow()
    
    private val _captureStats = MutableStateFlow(CaptureStats())
    val captureStats: StateFlow<CaptureStats> = _captureStats.asStateFlow()
    
    fun startStream() {
        _streamState.value = StreamState.Running
        resetStats()
    }
    
    fun stopStream() {
        _streamState.value = StreamState.Stopped
    }
    
    fun updateFrameMetrics(
        focusScore: Double,
        brightness: Double,
        contrast: Double,
        frameRate: Double
    ) {
        _frameMetrics.value = FrameMetrics(
            focusScore = focusScore,
            brightness = brightness,
            contrast = contrast,
            frameRate = frameRate
        )
        
        // Update stats
        val current = _captureStats.value
        _captureStats.value = current.copy(
            framesProcessed = current.framesProcessed + 1,
            avgFocusScore = (current.avgFocusScore * current.framesProcessed + focusScore) / (current.framesProcessed + 1),
            avgBrightness = (current.avgBrightness * current.framesProcessed + brightness) / (current.framesProcessed + 1),
            avgFrameRate = (current.avgFrameRate * current.framesProcessed + frameRate) / (current.framesProcessed + 1)
        )
    }
    
    private fun resetStats() {
        _captureStats.value = CaptureStats()
        _frameMetrics.value = null
    }
}

/**
 * Stream state
 */
sealed class StreamState {
    object Stopped : StreamState()
    object Running : StreamState()
}

/**
 * Real-time frame quality metrics
 */
data class FrameMetrics(
    val focusScore: Double,
    val brightness: Double,
    val contrast: Double,
    val frameRate: Double
)

/**
 * Aggregate capture statistics
 */
data class CaptureStats(
    val framesProcessed: Int = 0,
    val avgFocusScore: Double = 0.0,
    val avgBrightness: Double = 0.0,
    val avgFrameRate: Double = 0.0
)
