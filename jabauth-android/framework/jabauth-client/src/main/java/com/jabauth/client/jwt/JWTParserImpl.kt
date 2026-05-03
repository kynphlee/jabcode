package com.jabauth.client.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.jabauth.core.validation.ValidationResult
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Date

class JWTParserImpl : JWTParser {
    
    override fun parseToken(token: String): ValidationResult {
        return try {
            val decoded = JWT.decode(token)
            
            when {
                decoded.issuer.isNullOrBlank() -> 
                    ValidationResult.failure("Missing issuer claim", "INVALID_TOKEN")
                decoded.subject.isNullOrBlank() -> 
                    ValidationResult.failure("Missing subject claim", "INVALID_TOKEN")
                else -> 
                    ValidationResult.success()
            }
        } catch (e: JWTDecodeException) {
            ValidationResult.failure("Invalid JWT format: ${e.message}", "INVALID_FORMAT")
        } catch (e: Exception) {
            ValidationResult.failure("Failed to parse JWT: ${e.message}", "PARSE_ERROR")
        }
    }
    
    override fun extractClaims(token: String): JWTClaims? {
        return try {
            val decoded = JWT.decode(token)
            
            val customClaims = mutableMapOf<String, Any>()
            decoded.claims.forEach { (key, claim) ->
                if (key !in listOf("iss", "sub", "aud", "exp", "nbf", "iat", "jti")) {
                    claim.asString()?.let { customClaims[key] = it }
                        ?: claim.asInt()?.let { customClaims[key] = it }
                        ?: claim.asBoolean()?.let { customClaims[key] = it }
                        ?: claim.asDouble()?.let { customClaims[key] = it }
                }
            }
            
            JWTClaims(
                issuer = decoded.issuer,
                subject = decoded.subject,
                audience = decoded.audience,
                expirationTime = decoded.expiresAt?.time?.let { it / 1000 },
                notBefore = decoded.notBefore?.time?.let { it / 1000 },
                issuedAt = decoded.issuedAt?.time?.let { it / 1000 },
                jwtId = decoded.id,
                customClaims = customClaims
            )
        } catch (e: JWTDecodeException) {
            null
        } catch (e: Exception) {
            null
        }
    }
    
    override fun verifySignature(token: String, publicKey: ByteArray): Boolean {
        return try {
            val keySpec = X509EncodedKeySpec(publicKey)
            val keyFactory = KeyFactory.getInstance("RSA")
            val rsaPublicKey = keyFactory.generatePublic(keySpec) as RSAPublicKey
            
            val algorithm = Algorithm.RSA256(rsaPublicKey, null)
            val verifier = JWT.require(algorithm).build()
            verifier.verify(token)
            true
        } catch (e: JWTVerificationException) {
            false
        } catch (e: Exception) {
            false
        }
    }
    
    override fun verifySignatureHMAC(token: String, secret: String): Boolean {
        return try {
            val algorithm = Algorithm.HMAC256(secret)
            val verifier = JWT.require(algorithm).build()
            verifier.verify(token)
            true
        } catch (e: JWTVerificationException) {
            false
        } catch (e: Exception) {
            false
        }
    }
    
    override fun isNotExpired(token: String): Boolean {
        return try {
            val decoded = JWT.decode(token)
            val expiresAt = decoded.expiresAt ?: return true
            Date().before(expiresAt)
        } catch (e: JWTDecodeException) {
            false
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getExpirationTime(token: String): Long? {
        return try {
            val decoded = JWT.decode(token)
            decoded.expiresAt?.time?.let { it / 1000 }
        } catch (e: JWTDecodeException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
