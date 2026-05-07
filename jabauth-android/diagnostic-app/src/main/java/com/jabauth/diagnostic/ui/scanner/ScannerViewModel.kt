package com.jabauth.diagnostic.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabauth.jabcode.JABCodeDecoder
import com.jabauth.jabcode.JABCodeDecoderImpl
import com.jabauth.client.jwt.JWTParser
import com.jabauth.client.jwt.JWTParserImpl
import com.jabauth.ui.scanner.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Scanner ViewModel
 * 
 * Manages state for scanner screen:
 * - Quality metrics (brightness, focus, contrast)
 * - Scan status (scanning, detected, error)
 * - Torch state
 * - JABCode decoding and authentication
 * 
 * Phase 3 Day 5: Integrated with JABCode SDK and JABAuth Client
 */
class ScannerViewModel(
    private val decoder: JABCodeDecoder = JABCodeDecoderImpl(),
    private val jwtParser: JWTParser = JWTParserImpl()
) : ViewModel() {
    
    // Authentication service
    private val authService = AuthenticationService(jwtParser)
    
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
     * Handle successful JABCode decode
     * 
     * Called by JABCodeAnalyzer when barcode is decoded.
     * Triggers authentication pipeline.
     */
    fun onCodeDetected(decodedData: String, decodeTimeMs: Long) {
        viewModelScope.launch {
            try {
                // Authenticate the decoded data
                val result = authService.authenticate(
                    data = decodedData,
                    decodeTimeMs = decodeTimeMs,
                    colorMode = "128-color" // TODO: Get from decoder result
                )
                
                _authenticationResult.value = result
                _scanStatus.value = if (result.status == ResultStatus.SUCCESS) {
                    ScanStatus.SUCCESS
                } else {
                    ScanStatus.ERROR
                }
            } catch (e: Exception) {
                onCodeDetectionError("Authentication failed: ${e.message}")
            }
        }
    }
    
    /**
     * Handle decode failure
     */
    fun onCodeDetectionError(error: String) {
        _scanStatus.value = ScanStatus.ERROR
        // Could show error in UI, but for now just log
        println("Scanner error: $error")
    }
    
    /**
     * Create JABCode analyzer for camera
     */
    fun createAnalyzer(): JABCodeAnalyzer {
        return JABCodeAnalyzer(
            decoder = decoder,
            onDecodeSuccess = { data, time -> onCodeDetected(data, time) },
            onDecodeFailure = { error -> onCodeDetectionError(error) },
            onQualityUpdate = { brightness, focus, contrast ->
                updateQualityMetrics(brightness, focus, contrast)
            }
        )
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
