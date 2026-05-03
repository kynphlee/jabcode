package com.jabauth.core.validation

import android.util.Base64
import org.json.JSONObject

/**
 * Production implementation of JWTValidator
 *
 * Performs JWT format validation and claim extraction.
 * Does not verify signatures (signature verification should be handled by
 * cryptographic modules with proper key management).
 *
 * Thread-safe and suitable for production use.
 */
class JWTValidatorImpl : JWTValidator {

    override fun validateFormat(token: String): ValidationResult {
        val parts = token.split(".")
        
        if (parts.size != 3) {
            return ValidationResult.failure(
                "JWT must have 3 parts (header.payload.signature)",
                "INVALID_STRUCTURE"
            )
        }

        // Validate each part is non-empty
        parts.forEachIndexed { index, part ->
            if (part.isEmpty()) {
                val partName = when(index) {
                    0 -> "header"
                    1 -> "payload"
                    else -> "signature"
                }
                return ValidationResult.failure(
                    "JWT $partName is empty",
                    "EMPTY_PART"
                )
            }
        }

        // Try to decode header and payload as JSON
        return try {
            val header = decodeBase64Url(parts[0])
            val payload = decodeBase64Url(parts[1])
            
            JSONObject(header)  // Validate header is valid JSON
            JSONObject(payload) // Validate payload is valid JSON
            
            ValidationResult.success()
        } catch (e: Exception) {
            ValidationResult.failure(
                "Invalid JWT encoding: ${e.message}",
                "INVALID_ENCODING"
            )
        }
    }

    override fun extractClaims(token: String): Map<String, Any> {
        val parts = token.split(".")
        require(parts.size == 3) { "Invalid JWT format" }
        
        val payload = decodeBase64Url(parts[1])
        val json = JSONObject(payload)
        
        val claims = mutableMapOf<String, Any>()
        json.keys().forEach { key ->
            claims[key] = json.get(key)
        }
        
        return claims
    }

    override fun isExpired(claims: Map<String, Any>): Boolean {
        val exp = claims["exp"] as? Number ?: return true
        val expMillis = exp.toLong() * 1000
        return System.currentTimeMillis() > expMillis
    }

    override fun getIssuer(claims: Map<String, Any>): String? {
        return claims["iss"] as? String
    }

    override fun getSubject(claims: Map<String, Any>): String? {
        return claims["sub"] as? String
    }

    override fun hasRequiredClaims(
        claims: Map<String, Any>,
        requiredClaims: List<String>
    ): ValidationResult {
        val missingClaims = requiredClaims.filter { !claims.containsKey(it) }
        
        return if (missingClaims.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(
                "Missing required claims: ${missingClaims.joinToString(", ")}",
                "MISSING_CLAIMS"
            )
        }
    }

    /**
     * Decode base64url-encoded string
     *
     * Handles padding and converts base64url to standard base64.
     */
    private fun decodeBase64Url(encoded: String): String {
        // Convert base64url to standard base64
        var padded = encoded.replace('-', '+').replace('_', '/')
        
        // Add padding if necessary
        val padding = (4 - padded.length % 4) % 4
        padded += "=".repeat(padding)
        
        val bytes = Base64.decode(padded, Base64.DEFAULT)
        return String(bytes, Charsets.UTF_8)
    }
}
