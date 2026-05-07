package com.jabauth.diagnostic.ui.scanner

import androidx.lifecycle.ViewModel
import com.jabauth.ui.scanner.QualityMetric
import com.jabauth.ui.scanner.ScanStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Scanner ViewModel
 * 
 * Manages state for scanner screen:
 * - Quality metrics (brightness, focus, contrast)
 * - Scan status (scanning, detected, error)
 * - Torch state
 * 
 * Future (Phase 4): Will integrate with jabcode-sdk for actual decoding
 * Current: Manages UI state and camera quality metrics
 */
class ScannerViewModel : ViewModel() {
    
    // ========================================
    // State
    // ========================================
    
    private val _scanStatus = MutableStateFlow(ScanStatus.SCANNING)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()
    
    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()
    
    private val _qualityMetrics = MutableStateFlow<List<QualityMetric>>(
        listOf(
            QualityMetric("Brightness", 0.5f),
            QualityMetric("Focus", 0.5f),
            QualityMetric("Contrast", 0.5f)
        )
    )
    val qualityMetrics: StateFlow<List<QualityMetric>> = _qualityMetrics.asStateFlow()
    
    // ========================================
    // Actions
    // ========================================
    
    /**
     * Update quality metrics from camera analyzer
     * 
     * @param brightness Normalized brightness (0.0-1.0)
     * @param focus Normalized focus sharpness (0.0-1.0)
     * @param contrast Normalized contrast (0.0-1.0)
     */
    fun updateQualityMetrics(brightness: Float, focus: Float, contrast: Float) {
        _qualityMetrics.value = listOf(
            QualityMetric("Brightness", brightness),
            QualityMetric("Focus", focus),
            QualityMetric("Contrast", contrast)
        )
    }
    
    /**
     * Toggle torch (flashlight) on/off
     */
    fun toggleTorch() {
        _isTorchOn.value = !_isTorchOn.value
    }
    
    /**
     * Update scan status (scanning, detected, error)
     */
    fun updateScanStatus(status: ScanStatus) {
        _scanStatus.value = status
    }
    
    /**
     * Simulate code detection (placeholder for Phase 4 jabcode-sdk integration)
     */
    fun onCodeDetected() {
        _scanStatus.value = ScanStatus.SUCCESS
    }
    
    /**
     * Reset to scanning state
     */
    fun resetScanning() {
        _scanStatus.value = ScanStatus.SCANNING
    }
}
