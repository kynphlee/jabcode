package com.jabauth.core.validation

/**
 * JWT (JSON Web Token) validator
 *
 * Provides validation of JWT format and basic structure.
 * Does not perform signature verification (deferred to cryptographic module).
 *
 * Usage:
 * ```
 * val validator = JWTValidatorImpl()
 * val result = validator.validateFormat(token)
 * if (result.isValid) {
 *     val claims = validator.extractClaims(token)
 *     if (!validator.isExpired(claims)) {
 *         // Proceed with authentication
 *     }
 * }
 * ```
 */
interface JWTValidator {

    /**
     * Validate JWT format
     *
     * Checks if the token follows JWT structure: header.payload.signature
     * All three parts must be base64url-encoded.
     *
     * @param token JWT string
     * @return ValidationResult indicating success or failure with error details
     */
    fun validateFormat(token: String): ValidationResult

    /**
     * Extract claims from JWT payload
     *
     * Decodes the payload section and returns claims as a map.
     * Does not verify signature.
     *
     * @param token JWT string
     * @return Map of claim names to values
     * @throws IllegalArgumentException if token format is invalid
     */
    fun extractClaims(token: String): Map<String, Any>

    /**
     * Check if JWT is expired based on 'exp' claim
     *
     * @param claims Map of JWT claims
     * @return true if token is expired or 'exp' claim is missing
     */
    fun isExpired(claims: Map<String, Any>): Boolean

    /**
     * Extract issuer ('iss' claim) from JWT
     *
     * @param claims Map of JWT claims
     * @return Issuer string, or null if not present
     */
    fun getIssuer(claims: Map<String, Any>): String?

    /**
     * Extract subject ('sub' claim) from JWT
     *
     * @param claims Map of JWT claims
     * @return Subject string, or null if not present
     */
    fun getSubject(claims: Map<String, Any>): String?

    /**
     * Check if JWT has all required claims
     *
     * @param claims Map of JWT claims
     * @param requiredClaims List of required claim names
     * @return ValidationResult indicating if all required claims are present
     */
    fun hasRequiredClaims(claims: Map<String, Any>, requiredClaims: List<String>): ValidationResult
}
