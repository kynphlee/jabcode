package com.jabauth.client.jwt

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Unit tests for JWTParser
 * 
 * Tests JWT token parsing, validation, and claims extraction.
 * Coverage Target: 80%+
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JWTParserTest {
    
    private lateinit var parser: JWTParser
    
    @Before
    fun setup() {
        parser = TestJWTParserImpl()
    }
    
    @Test
    fun `parseToken accepts valid JWT token`() {
        // Given
        val token = createValidJWT()
        
        // When
        val result = parser.parseToken(token)
        
        // Then
        assertThat(result.isValid).isTrue()
        assertThat(result.errorMessage).isNull()
    }
    
    @Test
    fun `parseToken rejects malformed token with wrong part count`() {
        // Given
        val malformedToken = "header.payload" // Missing signature
        
        // When
        val result = parser.parseToken(malformedToken)
        
        // Then
        assertThat(result.isValid).isFalse()
        assertThat(result.errorMessage).contains("3 parts")
    }
    
    @Test
    fun `parseToken rejects token with invalid base64 encoding`() {
        // Given
        val invalidToken = "not-base64.not-base64.not-base64"
        
        // When
        val result = parser.parseToken(invalidToken)
        
        // Then
        assertThat(result.isValid).isFalse()
        assertThat(result.errorMessage).isNotNull()
    }
    
    @Test
    fun `parseToken rejects token with missing algorithm in header`() {
        // Given
        val headerWithoutAlg = encodeBase64Json(JSONObject().apply {
            put("typ", "JWT")
            // Missing "alg" field
        })
        val payload = encodeBase64Json(JSONObject().apply {
            put("sub", "user123")
        })
        val signature = "signature"
        val token = "$headerWithoutAlg.$payload.$signature"
        
        // When
        val result = parser.parseToken(token)
        
        // Then
        assertThat(result.isValid).isFalse()
        assertThat(result.errorMessage).contains("alg")
    }
    
    @Test
    fun `extractClaims returns all standard claims`() {
        // Given
        val now = System.currentTimeMillis() / 1000
        val token = createJWT(
            issuer = "https://auth.example.com",
            subject = "user123",
            audience = listOf("app1", "app2"),
            expirationTime = now + 3600,
            issuedAt = now,
            jwtId = "jwt-id-123"
        )
        
        // When
        val claims = parser.extractClaims(token)
        
        // Then
        assertThat(claims).isNotNull()
        assertThat(claims!!.issuer).isEqualTo("https://auth.example.com")
        assertThat(claims.subject).isEqualTo("user123")
        assertThat(claims.audience).containsExactly("app1", "app2")
        assertThat(claims.expirationTime).isEqualTo(now + 3600)
        assertThat(claims.issuedAt).isEqualTo(now)
        assertThat(claims.jwtId).isEqualTo("jwt-id-123")
    }
    
    @Test
    fun `extractClaims returns custom claims`() {
        // Given
        val token = createJWT(
            subject = "user123",
            customClaims = mapOf(
                "role" to "admin",
                "department" to "engineering"
            )
        )
        
        // When
        val claims = parser.extractClaims(token)
        
        // Then
        assertThat(claims).isNotNull()
        assertThat(claims!!.customClaims["role"]).isEqualTo("admin")
        assertThat(claims.customClaims["department"]).isEqualTo("engineering")
    }
    
    @Test
    fun `extractClaims returns null for malformed token`() {
        // Given
        val malformedToken = "not.a.valid.jwt"
        
        // When
        val claims = parser.extractClaims(malformedToken)
        
        // Then
        assertThat(claims).isNull()
    }
    
    @Test
    fun `verifySignature returns true for valid signature`() {
        // Given
        val token = createValidJWT()
        val publicKey = "mock-public-key".toByteArray()
        
        // When
        val result = parser.verifySignature(token, publicKey)
        
        // Then
        assertThat(result).isTrue()
    }
    
    @Test
    fun `verifySignature returns false for token without signature`() {
        // Given
        val tokenWithoutSignature = "header.payload."
        val publicKey = "mock-public-key".toByteArray()
        
        // When
        val result = parser.verifySignature(tokenWithoutSignature, publicKey)
        
        // Then
        assertThat(result).isFalse()
    }
    
    @Test
    fun `verifySignatureHMAC returns true for valid HMAC signature`() {
        // Given
        val token = createValidJWT()
        val secret = "test-secret"
        
        // When
        val result = parser.verifySignatureHMAC(token, secret)
        
        // Then
        assertThat(result).isTrue()
    }
    
    @Test
    fun `verifySignatureHMAC returns false for empty secret`() {
        // Given
        val token = createValidJWT()
        val emptySecret = ""
        
        // When
        val result = parser.verifySignatureHMAC(token, emptySecret)
        
        // Then
        assertThat(result).isFalse()
    }
    
    @Test
    fun `isNotExpired returns true for valid token`() {
        // Given
        val futureExpiry = (System.currentTimeMillis() / 1000) + 3600 // 1 hour from now
        val token = createJWT(expirationTime = futureExpiry)
        
        // When
        val result = parser.isNotExpired(token)
        
        // Then
        assertThat(result).isTrue()
    }
    
    @Test
    fun `isNotExpired returns false for expired token`() {
        // Given
        val pastExpiry = (System.currentTimeMillis() / 1000) - 3600 // 1 hour ago
        val token = createJWT(expirationTime = pastExpiry)
        
        // When
        val result = parser.isNotExpired(token)
        
        // Then
        assertThat(result).isFalse()
    }
    
    @Test
    fun `getExpirationTime returns correct timestamp`() {
        // Given
        val expirySeconds = (System.currentTimeMillis() / 1000) + 3600
        val token = createJWT(expirationTime = expirySeconds)
        
        // When
        val expirationTime = parser.getExpirationTime(token)
        
        // Then
        assertThat(expirationTime).isEqualTo(expirySeconds * 1000)
    }
    
    @Test
    fun `getExpirationTime returns null for token without expiration`() {
        // Given
        val token = createJWT(expirationTime = null)
        
        // When
        val expirationTime = parser.getExpirationTime(token)
        
        // Then
        assertThat(expirationTime).isNull()
    }
    
    // Helper methods to create test JWT tokens
    
    private fun createValidJWT(): String {
        return createJWT(subject = "test-user")
    }
    
    private fun createJWT(
        issuer: String? = null,
        subject: String? = null,
        audience: List<String>? = null,
        expirationTime: Long? = null,
        notBefore: Long? = null,
        issuedAt: Long? = null,
        jwtId: String? = null,
        customClaims: Map<String, Any> = emptyMap()
    ): String {
        val header = JSONObject().apply {
            put("alg", "RS256")
            put("typ", "JWT")
        }
        
        val payload = JSONObject().apply {
            issuer?.let { put("iss", it) }
            subject?.let { put("sub", it) }
            audience?.let { aud -> 
                put("aud", JSONArray(aud))
            }
            expirationTime?.let { put("exp", it) }
            notBefore?.let { put("nbf", it) }
            issuedAt?.let { put("iat", it) }
            jwtId?.let { put("jti", it) }
            customClaims.forEach { (key, value) -> put(key, value) }
        }
        
        val headerEncoded = encodeBase64Json(header)
        val payloadEncoded = encodeBase64Json(payload)
        val signature = "mock-signature"
        
        return "$headerEncoded.$payloadEncoded.$signature"
    }
    
    private fun encodeBase64Json(json: JSONObject): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.toString().toByteArray())
    }
}
