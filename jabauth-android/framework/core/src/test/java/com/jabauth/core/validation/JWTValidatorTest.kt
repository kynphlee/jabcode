package com.jabauth.core.validation

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for JWTValidator interface contract
 *
 * Uses TestJWTValidatorImpl to verify JWT validation behavior.
 * Production JWTValidatorImpl will be tested via instrumented tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JWTValidatorTest {

    private lateinit var context: Context
    private lateinit var validator: JWTValidator

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        validator = TestJWTValidatorImpl()
    }

    @Test
    fun `validateFormat accepts valid JWT`() {
        val validJWT = createTestJWT()
        
        val result = validator.validateFormat(validJWT)
        
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `validateFormat rejects JWT with missing parts`() {
        val invalidJWT = "header.payload" // Missing signature
        
        val result = validator.validateFormat(invalidJWT)
        
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertEquals("INVALID_STRUCTURE", result.errorCode)
    }

    @Test
    fun `validateFormat rejects JWT with empty parts`() {
        val invalidJWT = "header..signature" // Empty payload
        
        val result = validator.validateFormat(invalidJWT)
        
        assertFalse(result.isValid)
        assertEquals("EMPTY_PART", result.errorCode)
    }

    @Test
    fun `validateFormat rejects JWT with invalid encoding`() {
        val invalidJWT = "not-base64!.not-base64!.not-base64!"
        
        val result = validator.validateFormat(invalidJWT)
        
        assertFalse(result.isValid)
        assertEquals("INVALID_ENCODING", result.errorCode)
    }

    @Test
    fun `extractClaims returns claims map from JWT`() {
        val jwt = createTestJWT(mapOf(
            "sub" to "user123",
            "name" to "John Doe",
            "admin" to true
        ))
        
        val claims = validator.extractClaims(jwt)
        
        assertEquals("user123", claims["sub"])
        assertEquals("John Doe", claims["name"])
        assertEquals(true, claims["admin"])
    }

    @Test
    fun `isExpired returns false for non-expired JWT`() {
        val futureExp = (System.currentTimeMillis() / 1000) + 3600 // 1 hour from now
        val claims = mapOf("exp" to futureExp)
        
        val result = validator.isExpired(claims)
        
        assertFalse(result)
    }

    @Test
    fun `isExpired returns true for expired JWT`() {
        val pastExp = (System.currentTimeMillis() / 1000) - 3600 // 1 hour ago
        val claims = mapOf("exp" to pastExp)
        
        val result = validator.isExpired(claims)
        
        assertTrue(result)
    }

    @Test
    fun `isExpired returns true when exp claim is missing`() {
        val claims = mapOf("sub" to "user123")
        
        val result = validator.isExpired(claims)
        
        assertTrue(result)
    }

    @Test
    fun `getIssuer extracts issuer claim`() {
        val claims = mapOf("iss" to "https://auth.example.com")
        
        val issuer = validator.getIssuer(claims)
        
        assertEquals("https://auth.example.com", issuer)
    }

    @Test
    fun `getSubject extracts subject claim`() {
        val claims = mapOf("sub" to "user123")
        
        val subject = validator.getSubject(claims)
        
        assertEquals("user123", subject)
    }

    @Test
    fun `hasRequiredClaims returns success when all claims present`() {
        val claims = mapOf(
            "sub" to "user123",
            "iss" to "auth.example.com",
            "exp" to 1234567890
        )
        val requiredClaims = listOf("sub", "iss", "exp")
        
        val result = validator.hasRequiredClaims(claims, requiredClaims)
        
        assertTrue(result.isValid)
    }

    @Test
    fun `hasRequiredClaims returns failure when claims missing`() {
        val claims = mapOf("sub" to "user123")
        val requiredClaims = listOf("sub", "iss", "exp")
        
        val result = validator.hasRequiredClaims(claims, requiredClaims)
        
        assertFalse(result.isValid)
        assertEquals("MISSING_CLAIMS", result.errorCode)
        assertTrue(result.errorMessage!!.contains("iss"))
        assertTrue(result.errorMessage!!.contains("exp"))
    }

    /**
     * Create a test JWT with specified claims
     */
    private fun createTestJWT(claims: Map<String, Any> = emptyMap()): String {
        val header = JSONObject().apply {
            put("alg", "HS256")
            put("typ", "JWT")
        }

        val payload = JSONObject().apply {
            claims.forEach { (key, value) ->
                put(key, value)
            }
            // Add default exp if not provided
            if (!claims.containsKey("exp")) {
                put("exp", (System.currentTimeMillis() / 1000) + 3600)
            }
        }

        val headerEncoded = base64UrlEncode(header.toString())
        val payloadEncoded = base64UrlEncode(payload.toString())
        val signature = "fake-signature"

        return "$headerEncoded.$payloadEncoded.$signature"
    }

    /**
     * Base64URL encode a string
     */
    private fun base64UrlEncode(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
