package com.jabauth.diagnostic.ui.scanner

import androidx.lifecycle.ViewModel
import com.jabauth.ui.scanner.*
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
    
    private val _authenticationResult = MutableStateFlow<AuthenticationResult?>(null)
    val authenticationResult: StateFlow<AuthenticationResult?> = _authenticationResult.asStateFlow()
    
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
        _authenticationResult.value = null
    }
    
    /**
     * Show mock successful result (for testing UI)
     */
    fun showMockSuccessResult() {
        _authenticationResult.value = AuthenticationResult(
            status = ResultStatus.SUCCESS,
            subject = "prescription#RX-8472",
            certificateInfo = CertificateInfo(
                subject = "CN=MedicalCenter, O=HealthCorp",
                issuer = "CN=HealthCorp Root CA",
                validUntil = "2027-05-15 23:59:59 UTC",
                serial = "4F:A3:2E:1B:9C:7D"
            ),
            jwtInfo = JWTInfo(
                subject = "prescription#RX-8472",
                algorithm = "RS256",
                issued = "2026-05-02 10:30:00 UTC",
                expires = "2026-05-02 22:30:00 UTC"
            ),
            scanDetails = ScanDetails(
                colorMode = "8 colors",
                eccLevel = "3 (High)",
                decodeTime = "67ms",
                quality = "Excellent"
            ),
            validations = listOf(
                ValidationCheck("Signature", true),
                ValidationCheck("Expiry", true),
                ValidationCheck("Certificate", true),
                ValidationCheck("JWT Valid", true)
            )
        )
        _scanStatus.value = ScanStatus.SUCCESS
    }
    
    /**
     * Show mock failed result (for testing UI)
     */
    fun showMockFailureResult() {
        _authenticationResult.value = AuthenticationResult(
            status = ResultStatus.FAILED,
            subject = "unknown#INVALID",
            certificateInfo = CertificateInfo(
                subject = "CN=Unknown",
                issuer = "CN=Unknown CA",
                validUntil = "N/A",
                serial = "N/A"
            ),
            jwtInfo = JWTInfo(
                subject = "N/A",
                algorithm = "N/A",
                issued = "N/A",
                expires = "N/A"
            ),
            scanDetails = ScanDetails(
                colorMode = "8 colors",
                eccLevel = "3 (High)",
                decodeTime = "52ms",
                quality = "Good"
            ),
            validations = listOf(
                ValidationCheck("Signature", false),
                ValidationCheck("Expiry", false),
                ValidationCheck("Certificate", true),
                ValidationCheck("JWT Valid", false)
            )
        )
        _scanStatus.value = ScanStatus.ERROR
    }
    
    /**
     * Dismiss result panel
     */
    fun dismissResult() {
        _authenticationResult.value = null
        resetScanning()
    }
    
    /**
     * Accept result and navigate (placeholder)
     */
    fun acceptResult() {
        // TODO: Phase 4 - Navigate to result confirmation screen
        dismissResult()
    }
}
