package com.jabauth.client.jwt

import com.jabauth.core.validation.ValidationResult

/**
 * JWT (JSON Web Token) parser and validator
 * 
 * Provides parsing and validation of JWT tokens including:
 * - Token structure validation (header.payload.signature)
 * - Signature verification against an asymmetric (RSA/EC) public key, routed
 *   through the [JwtAlgorithms] allowlist (RS/ES families only; none/HS blocked)
 * - Expiration checking
 * - Claims extraction
 *
 * Symmetric (HMAC/HS*) verification is intentionally NOT offered on the COA
 * verify path: admitting an HS* token against a public key is an RS/HS
 * algorithm-confusion vector. See [JwtAlgorithms].
 *
 * @see JWTClaims for token claims data structure
 */
interface JWTParser {
    
    /**
     * Parse and validate a JWT token
     * 
     * @param token JWT token string
     * @return ValidationResult with success/failure and errors
     */
    fun parseToken(token: String): ValidationResult
    
    /**
     * Extract claims from a JWT token
     * 
     * @param token JWT token string
     * @return JWTClaims if valid, null otherwise
     */
    fun extractClaims(token: String): JWTClaims?
    
    /**
     * Verify a JWT signature against an asymmetric (RSA or EC) public key.
     *
     * The token's declared `alg` header is checked against the [JwtAlgorithms]
     * allowlist (RS/ES families only; `none` and HS* rejected) and the key type
     * must match the algorithm family, so an algorithm-confusion attempt fails
     * closed rather than being coerced.
     *
     * @param token JWT token string
     * @param publicKey X.509-encoded RSA or EC public key
     * @return true if the algorithm is allowed, the key matches, and the
     *   signature is valid; false otherwise
     */
    fun verifySignature(token: String, publicKey: ByteArray): Boolean

    /**
     * Check if JWT token is expired
     * 
     * @param token JWT token string
     * @return true if token is valid (not expired)
     */
    fun isNotExpired(token: String): Boolean
    
    /**
     * Get token expiration time
     * 
     * @param token JWT token string
     * @return Expiration timestamp in milliseconds, or null if not set
     */
    fun getExpirationTime(token: String): Long?
}
