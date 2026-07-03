package com.jabauth.client.jwt

import com.jabauth.core.validation.ValidationResult
import org.json.JSONObject
import java.util.Base64

/**
 * Test implementation of JWTParser
 * 
 * Simplified JWT parsing for unit testing.
 * Production implementation uses Auth0 java-jwt library.
 */
class TestJWTParserImpl : JWTParser {
    
    override fun parseToken(token: String): ValidationResult {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Invalid JWT format: expected 3 parts (header.payload.signature)"
                )
            }
            
            // Decode header
            val header = decodeBase64Json(parts[0])
            if (!header.has("alg")) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Invalid JWT header: missing 'alg' field"
                )
            }
            
            // Decode payload for format validation
            decodeBase64Json(parts[1])
            
            ValidationResult(isValid = true)
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                errorMessage = "JWT parsing failed: ${e.message}"
            )
        }
    }
    
    override fun extractClaims(token: String): JWTClaims? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null
            
            val payload = decodeBase64Json(parts[1])
            
            JWTClaims(
                issuer = payload.optString("iss").takeIf { it.isNotEmpty() },
                subject = payload.optString("sub").takeIf { it.isNotEmpty() },
                audience = payload.optJSONArray("aud")?.let { arr ->
                    List(arr.length()) { i -> arr.getString(i) }
                },
                expirationTime = payload.optLong("exp").takeIf { it != 0L },
                notBefore = payload.optLong("nbf").takeIf { it != 0L },
                issuedAt = payload.optLong("iat").takeIf { it != 0L },
                jwtId = payload.optString("jti").takeIf { it.isNotEmpty() },
                customClaims = extractCustomClaims(payload)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun verifySignature(token: String, publicKey: ByteArray): Boolean {
        // Stub implementation - always returns true for testing
        // Production implementation would use RSA public key verification
        return try {
            val parts = token.split(".")
            parts.size == 3 && parts[2].isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    override fun isNotExpired(token: String): Boolean {
        val claims = extractClaims(token) ?: return false
        return !claims.isExpired()
    }
    
    override fun getExpirationTime(token: String): Long? {
        return extractClaims(token)?.expirationTime?.times(1000)
    }
    
    // Helper methods
    
    private fun decodeBase64Json(base64: String): JSONObject {
        val decoded = Base64.getUrlDecoder().decode(base64)
        return JSONObject(String(decoded))
    }
    
    private fun extractCustomClaims(payload: JSONObject): Map<String, Any> {
        val standardClaims = setOf("iss", "sub", "aud", "exp", "nbf", "iat", "jti")
        val customClaims = mutableMapOf<String, Any>()
        
        payload.keys().forEach { key ->
            if (key !in standardClaims) {
                customClaims[key] = payload.get(key)
            }
        }
        
        return customClaims
    }
}
