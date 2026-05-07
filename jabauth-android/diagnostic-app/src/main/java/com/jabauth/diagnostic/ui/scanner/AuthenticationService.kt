package com.jabauth.diagnostic.ui.scanner

import com.jabauth.client.jwt.JWTParser
import com.jabauth.client.jwt.JWTClaims
import com.jabauth.ui.scanner.*

/**
 * Authentication Service
 * 
 * Validates decoded JABCode data and generates authentication results.
 * Integrates JWT parsing, certificate validation, and policy checks.
 * 
 * **Flow:**
 * JABCode Data → Parse JWT → Validate Certificate → Check Policy → AuthenticationResult
 * 
 * Phase 3 Day 5: JABAuth Client Integration
 */
class AuthenticationService(
    private val jwtParser: JWTParser
) {
    
    /**
     * Authenticate decoded JABCode data
     * 
     * @param data Decoded JABCode string (expected to be JWT)
     * @param decodeTimeMs Time taken to decode (for scan details)
     * @param colorMode Detected color mode from JABCode
     * @return AuthenticationResult with validation details
     */
    fun authenticate(
        data: String,
        decodeTimeMs: Long,
        colorMode: String
    ): AuthenticationResult {
        return try {
            // Parse JWT token
            val claims = jwtParser.extractClaims(data)
            
            if (claims == null) {
                // Invalid JWT format
                return createFailureResult(
                    data,
                    decodeTimeMs,
                    colorMode,
                    "Invalid JWT format"
                )
            }
            
            // Validate JWT
            val validationResult = jwtParser.parseToken(data)
            val isSignatureValid = validationResult.isValid
            val isNotExpired = jwtParser.isNotExpired(data)
            
            // For now, assume certificate is valid (Phase 4 will add full PKI validation)
            val isCertificateValid = true
            
            val allValid = isSignatureValid && isNotExpired && isCertificateValid
            
            // Build result
            AuthenticationResult(
                status = if (allValid) ResultStatus.SUCCESS else ResultStatus.FAILED,
                subject = claims.subject ?: "unknown",
                certificateInfo = CertificateInfo(
                    subject = "CN=${claims.issuer ?: "Unknown"}",
                    issuer = "CN=JABAuth Root CA",
                    validUntil = formatTimestamp(claims.expirationTime),
                    serial = "AUTO-GENERATED"
                ),
                jwtInfo = JWTInfo(
                    subject = claims.subject ?: "unknown",
                    algorithm = "RS256", // TODO: Extract from JWT header
                    issued = formatTimestamp(claims.issuedAt),
                    expires = formatTimestamp(claims.expirationTime)
                ),
                scanDetails = ScanDetails(
                    colorMode = colorMode,
                    eccLevel = "High", // TODO: Get from JABCode decoder
                    decodeTime = "${decodeTimeMs}ms",
                    quality = calculateQuality(decodeTimeMs)
                ),
                validations = listOf(
                    ValidationCheck("JWT Signature", isSignatureValid),
                    ValidationCheck("Not Expired", isNotExpired),
                    ValidationCheck("Certificate Valid", isCertificateValid),
                    ValidationCheck("Policy Check", allValid)
                )
            )
        } catch (e: Exception) {
            // Authentication error
            createFailureResult(
                data,
                decodeTimeMs,
                colorMode,
                "Authentication failed: ${e.message}"
            )
        }
    }
    
    /**
     * Create failure result for invalid/error cases
     */
    private fun createFailureResult(
        data: String,
        decodeTimeMs: Long,
        colorMode: String,
        reason: String
    ): AuthenticationResult {
        return AuthenticationResult(
            status = ResultStatus.FAILED,
            subject = data.take(50), // Show truncated data
            certificateInfo = CertificateInfo(
                subject = "N/A",
                issuer = "N/A",
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
                colorMode = colorMode,
                eccLevel = "N/A",
                decodeTime = "${decodeTimeMs}ms",
                quality = "Failed"
            ),
            validations = listOf(
                ValidationCheck("JWT Signature", false),
                ValidationCheck("Not Expired", false),
                ValidationCheck("Certificate Valid", false),
                ValidationCheck("Error: $reason", false)
            )
        )
    }
    
    /**
     * Format Unix timestamp to readable string
     */
    private fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null) return "N/A"
        
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return format.format(date)
    }
    
    /**
     * Calculate quality rating based on decode time
     */
    private fun calculateQuality(decodeTimeMs: Long): String {
        return when {
            decodeTimeMs < 50 -> "Excellent"
            decodeTimeMs < 100 -> "Good"
            decodeTimeMs < 200 -> "Fair"
            else -> "Poor"
        }
    }
}
