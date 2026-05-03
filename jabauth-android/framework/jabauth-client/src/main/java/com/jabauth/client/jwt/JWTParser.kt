package com.jabauth.client.jwt

import com.jabauth.core.validation.ValidationResult

/**
 * JWT (JSON Web Token) parser and validator
 * 
 * Provides parsing and validation of JWT tokens including:
 * - Token structure validation (header.payload.signature)
 * - Signature verification (RS256, HS256)
 * - Expiration checking
 * - Claims extraction
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
     * Verify JWT signature
     * 
     * @param token JWT token string
     * @param publicKey Public key for signature verification (RS256)
     * @return true if signature is valid
     */
    fun verifySignature(token: String, publicKey: ByteArray): Boolean
    
    /**
     * Verify JWT signature with secret key
     * 
     * @param token JWT token string
     * @param secret Secret key for HMAC verification (HS256)
     * @return true if signature is valid
     */
    fun verifySignatureHMAC(token: String, secret: String): Boolean
    
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
