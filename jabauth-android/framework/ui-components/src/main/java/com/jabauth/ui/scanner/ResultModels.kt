package com.jabauth.ui.scanner

/**
 * Authentication Result Status
 */
enum class ResultStatus {
    SUCCESS,
    FAILED
}

/**
 * Validation Check
 * 
 * Represents a single validation criterion (signature, expiry, etc.)
 */
data class ValidationCheck(
    val label: String,
    val passed: Boolean
)

/**
 * Certificate Information
 */
data class CertificateInfo(
    val subject: String,
    val issuer: String,
    val validUntil: String,
    val serial: String
)

/**
 * JWT Token Information
 */
data class JWTInfo(
    val subject: String,
    val algorithm: String,
    val issued: String,
    val expires: String
)

/**
 * Scan Details
 */
data class ScanDetails(
    val colorMode: String,
    val eccLevel: String,
    val decodeTime: String,
    val quality: String
)

/**
 * Authentication Result
 * 
 * Complete result data from JABCode scan and validation.
 * Displayed in ResultPanel bottom sheet.
 */
data class AuthenticationResult(
    val status: ResultStatus,
    val subject: String,
    val certificateInfo: CertificateInfo,
    val jwtInfo: JWTInfo,
    val scanDetails: ScanDetails,
    val validations: List<ValidationCheck>
)
