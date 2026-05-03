package com.jabauth.client.jwt

/**
 * JWT token claims
 * 
 * Standard JWT claims as defined in RFC 7519.
 * Custom claims can be accessed via the customClaims map.
 */
data class JWTClaims(
    /**
     * Issuer (iss) - Entity that issued the token
     */
    val issuer: String? = null,
    
    /**
     * Subject (sub) - Subject of the token (user ID, etc.)
     */
    val subject: String? = null,
    
    /**
     * Audience (aud) - Intended recipients of the token
     */
    val audience: List<String>? = null,
    
    /**
     * Expiration Time (exp) - Token expiration timestamp (seconds since epoch)
     */
    val expirationTime: Long? = null,
    
    /**
     * Not Before (nbf) - Token not valid before this timestamp
     */
    val notBefore: Long? = null,
    
    /**
     * Issued At (iat) - Token issued timestamp
     */
    val issuedAt: Long? = null,
    
    /**
     * JWT ID (jti) - Unique identifier for the token
     */
    val jwtId: String? = null,
    
    /**
     * Custom claims - Additional application-specific claims
     */
    val customClaims: Map<String, Any> = emptyMap()
) {
    /**
     * Get a custom claim by key
     * 
     * @param key Claim key
     * @return Claim value or null if not found
     */
    fun getCustomClaim(key: String): Any? = customClaims[key]
    
    /**
     * Check if token is expired
     * 
     * @param currentTimeMillis Current time in milliseconds (default: now)
     * @return true if expired
     */
    fun isExpired(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return expirationTime?.let { exp ->
            (exp * 1000) < currentTimeMillis
        } ?: false
    }
    
    /**
     * Check if token is not yet valid
     * 
     * @param currentTimeMillis Current time in milliseconds (default: now)
     * @return true if not yet valid
     */
    fun isNotYetValid(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return notBefore?.let { nbf ->
            (nbf * 1000) > currentTimeMillis
        } ?: false
    }
}
