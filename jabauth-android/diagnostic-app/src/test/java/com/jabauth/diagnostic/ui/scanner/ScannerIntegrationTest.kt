package com.jabauth.diagnostic.ui.scanner

import com.jabauth.client.jwt.JWTClaims
import com.jabauth.client.jwt.JWTParser
import com.jabauth.core.validation.ValidationResult
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.DecodeResult
import com.jabauth.jabcode.JABCodeDecoder
import com.jabauth.ui.scanner.ResultStatus
import com.jabauth.ui.scanner.ScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Scanner Integration Tests
 * 
 * Tests the complete scanner flow including:
 * - JABCode decoding
 * - JWT parsing and validation
 * - Authentication result generation
 * - State management
 * 
 * Phase 3 Day 5: Scanner SDK Integration
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerIntegrationTest {
    
    private lateinit var mockDecoder: JABCodeDecoder
    private lateinit var mockJWTParser: JWTParser
    private lateinit var viewModel: ScannerViewModel
    
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockDecoder = mock(JABCodeDecoder::class.java)
        mockJWTParser = mock(JWTParser::class.java)
        
        viewModel = ScannerViewModel(
            decoder = mockDecoder,
            jwtParser = mockJWTParser
        )
    }
    
    // ========================================================================
    // Successful Authentication Tests
    // ========================================================================
    
    @Test
    fun `onCodeDetected with valid JWT produces success result`() = runTest {
        // Arrange
        val validJWT = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
        val claims = JWTClaims(
            subject = "user@example.com",
            issuer = "test-issuer",
            issuedAt = System.currentTimeMillis() / 1000,
            expirationTime = (System.currentTimeMillis() / 1000) + 3600
        )
        
        `when`(mockJWTParser.extractClaims(validJWT)).thenReturn(claims)
        `when`(mockJWTParser.parseToken(validJWT)).thenReturn(ValidationResult.success())
        `when`(mockJWTParser.isNotExpired(validJWT)).thenReturn(true)
        
        // Act
        viewModel.onCodeDetected(validJWT, 50)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Assert
        val result = viewModel.authenticationResult.first()
        assert(result != null) { "Result should not be null" }
        assert(result?.status == ResultStatus.SUCCESS) { "Status should be SUCCESS" }
        assert(result?.subject == "user@example.com") { "Subject should match" }
        
        val scanStatus = viewModel.scanStatus.first()
        assert(scanStatus == ScanStatus.SUCCESS) { "Scan status should be SUCCESS" }
    }
    
    @Test
    fun `onCodeDetected with expired JWT produces failure result`() = runTest {
        // Arrange
        val expiredJWT = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
        val claims = JWTClaims(
            subject = "user@example.com",
            issuer = "test-issuer",
            issuedAt = System.currentTimeMillis() / 1000 - 7200,
            expirationTime = System.currentTimeMillis() / 1000 - 3600 // Expired 1 hour ago
        )
        
        `when`(mockJWTParser.extractClaims(expiredJWT)).thenReturn(claims)
        `when`(mockJWTParser.parseToken(expiredJWT)).thenReturn(ValidationResult.success())
        `when`(mockJWTParser.isNotExpired(expiredJWT)).thenReturn(false)
        
        // Act
        viewModel.onCodeDetected(expiredJWT, 75)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Assert
        val result = viewModel.authenticationResult.first()
        assert(result != null) { "Result should not be null" }
        assert(result?.status == ResultStatus.FAILED) { "Status should be FAILED" }
        
        val scanStatus = viewModel.scanStatus.first()
        assert(scanStatus == ScanStatus.ERROR) { "Scan status should be ERROR" }
    }
    
    @Test
    fun `onCodeDetected with invalid JWT signature produces failure result`() = runTest {
        // Arrange
        val invalidJWT = "invalid.jwt.signature"
        val claims = JWTClaims(
            subject = "user@example.com",
            issuer = "test-issuer",
            issuedAt = System.currentTimeMillis() / 1000,
            expirationTime = (System.currentTimeMillis() / 1000) + 3600
        )
        
        `when`(mockJWTParser.extractClaims(invalidJWT)).thenReturn(claims)
        `when`(mockJWTParser.parseToken(invalidJWT)).thenReturn(
            ValidationResult.failure("Invalid signature", "INVALID_SIG")
        )
        `when`(mockJWTParser.isNotExpired(invalidJWT)).thenReturn(true)
        
        // Act
        viewModel.onCodeDetected(invalidJWT, 60)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Assert
        val result = viewModel.authenticationResult.first()
        assert(result != null) { "Result should not be null" }
        assert(result?.status == ResultStatus.FAILED) { "Status should be FAILED" }
        assert(result?.validations?.any { !it.passed && it.label == "JWT Signature" } == true) {
            "Should have failed JWT Signature validation"
        }
    }
    
    @Test
    fun `onCodeDetected with null claims produces failure result`() = runTest {
        // Arrange
        val malformedJWT = "not.a.jwt"
        
        `when`(mockJWTParser.extractClaims(malformedJWT)).thenReturn(null)
        
        // Act
        viewModel.onCodeDetected(malformedJWT, 80)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Assert
        val result = viewModel.authenticationResult.first()
        assert(result != null) { "Result should not be null" }
        assert(result?.status == ResultStatus.FAILED) { "Status should be FAILED" }
        assert(result?.subject == "not.a.jwt") { "Subject should contain truncated data" }
    }
    
    // ========================================================================
    // Quality Metrics Tests
    // ========================================================================
    
    @Test
    fun `updateQualityMetrics updates state correctly`() = runTest {
        // Act
        viewModel.updateQualityMetrics(0.8f, 0.9f, 0.7f)
        
        // Assert
        val metrics = viewModel.qualityMetrics.first()
        assert(metrics.size == 3) { "Should have 3 metrics" }
        assert(metrics[0].value == 0.8f) { "Brightness should be 0.8" }
        assert(metrics[1].value == 0.9f) { "Focus should be 0.9" }
        assert(metrics[2].value == 0.7f) { "Contrast should be 0.7" }
    }
    
    @Test
    fun `createAnalyzer returns JABCodeAnalyzer with correct decoder`() {
        // Act
        val analyzer = viewModel.createAnalyzer()
        
        // Assert
        assert(analyzer is JABCodeAnalyzer) { "Should be JABCodeAnalyzer instance" }
    }
    
    // ========================================================================
    // Error Handling Tests
    // ========================================================================
    
    @Test
    fun `onCodeDetectionError sets status to ERROR`() = runTest {
        // Act
        viewModel.onCodeDetectionError("Decode failed")
        
        // Assert
        val status = viewModel.scanStatus.first()
        assert(status == ScanStatus.ERROR) { "Status should be ERROR" }
    }
    
    @Test
    fun `resetScanning clears result and sets status to SCANNING`() = runTest {
        // Arrange - set up a completed scan
        val validJWT = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
        val claims = JWTClaims(subject = "test", issuer = "issuer")
        `when`(mockJWTParser.extractClaims(validJWT)).thenReturn(claims)
        `when`(mockJWTParser.parseToken(validJWT)).thenReturn(ValidationResult.success())
        `when`(mockJWTParser.isNotExpired(validJWT)).thenReturn(true)
        
        viewModel.onCodeDetected(validJWT, 50)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Act
        viewModel.resetScanning()
        
        // Assert
        val result = viewModel.authenticationResult.first()
        assert(result == null) { "Result should be null after reset" }
        
        val status = viewModel.scanStatus.first()
        assert(status == ScanStatus.SCANNING) { "Status should be SCANNING" }
    }
    
    // ========================================================================
    // Torch Control Tests
    // ========================================================================
    
    @Test
    fun `toggleTorch switches state`() = runTest {
        // Initial state
        assert(!viewModel.isTorchOn.first()) { "Torch should start off" }
        
        // Toggle on
        viewModel.toggleTorch()
        assert(viewModel.isTorchOn.first()) { "Torch should be on" }
        
        // Toggle off
        viewModel.toggleTorch()
        assert(!viewModel.isTorchOn.first()) { "Torch should be off again" }
    }
    
    // ========================================================================
    // Result Dismissal Tests
    // ========================================================================
    
    @Test
    fun `dismissResult clears result and resets to scanning`() = runTest {
        // Arrange
        val validJWT = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
        val claims = JWTClaims(subject = "test", issuer = "issuer")
        `when`(mockJWTParser.extractClaims(validJWT)).thenReturn(claims)
        `when`(mockJWTParser.parseToken(validJWT)).thenReturn(ValidationResult.success())
        `when`(mockJWTParser.isNotExpired(validJWT)).thenReturn(true)
        
        viewModel.onCodeDetected(validJWT, 50)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Act
        viewModel.dismissResult()
        
        // Assert
        assert(viewModel.authenticationResult.first() == null) { "Result should be cleared" }
        assert(viewModel.scanStatus.first() == ScanStatus.SCANNING) { "Should return to scanning" }
    }
    
    @Test
    fun `acceptResult clears result and resets to scanning`() = runTest {
        // Arrange
        val validJWT = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
        val claims = JWTClaims(subject = "test", issuer = "issuer")
        `when`(mockJWTParser.extractClaims(validJWT)).thenReturn(claims)
        `when`(mockJWTParser.parseToken(validJWT)).thenReturn(ValidationResult.success())
        `when`(mockJWTParser.isNotExpired(validJWT)).thenReturn(true)
        
        viewModel.onCodeDetected(validJWT, 50)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Act
        viewModel.acceptResult()
        
        // Assert
        assert(viewModel.authenticationResult.first() == null) { "Result should be cleared" }
        assert(viewModel.scanStatus.first() == ScanStatus.SCANNING) { "Should return to scanning" }
    }
}
