package com.jabauth.client.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Date

class JWTParserImplTest {
    
    private lateinit var parser: JWTParserImpl
    private lateinit var rsaPublicKey: RSAPublicKey
    private lateinit var rsaPrivateKey: RSAPrivateKey
    private val hmacSecret = "test-secret-key-for-hmac-256"
    
    @Before
    fun setup() {
        parser = JWTParserImpl()
        
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()
        rsaPublicKey = keyPair.public as RSAPublicKey
        rsaPrivateKey = keyPair.private as RSAPrivateKey
    }
    
    @Test
    fun `parseToken validates format with required claims`() {
        val token = createValidToken()
        val result = parser.parseToken(token)
        
        assertTrue(result.isValid)
        assertNull(result.errorCode)
    }
    
    @Test
    fun `parseToken rejects invalid format`() {
        val result = parser.parseToken("invalid.token")
        
        assertFalse(result.isValid)
        assertEquals("INVALID_FORMAT", result.errorCode)
    }
    
    @Test
    fun `parseToken rejects token without issuer`() {
        val token = JWT.create()
            .withSubject("user123")
            .sign(Algorithm.HMAC256(hmacSecret))
        
        val result = parser.parseToken(token)
        
        assertFalse(result.isValid)
        assertEquals("INVALID_TOKEN", result.errorCode)
        assertTrue(result.errorMessage?.contains("issuer") == true)
    }
    
    @Test
    fun `parseToken rejects token without subject`() {
        val token = JWT.create()
            .withIssuer("test-issuer")
            .sign(Algorithm.HMAC256(hmacSecret))
        
        val result = parser.parseToken(token)
        
        assertFalse(result.isValid)
        assertEquals("INVALID_TOKEN", result.errorCode)
        assertTrue(result.errorMessage?.contains("subject") == true)
    }
    
    @Test
    fun `extractClaims returns all standard claims`() {
        val issuedAt = Date()
        val expiresAt = Date(issuedAt.time + 3600000)
        
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withSubject("user123")
            .withAudience("test-audience")
            .withExpiresAt(expiresAt)
            .withIssuedAt(issuedAt)
            .withJWTId("jwt-id-123")
            .sign(Algorithm.HMAC256(hmacSecret))
        
        val claims = parser.extractClaims(token)
        
        assertNotNull(claims)
        assertEquals("test-issuer", claims!!.issuer)
        assertEquals("user123", claims.subject)
        assertEquals(listOf("test-audience"), claims.audience)
        assertEquals(expiresAt.time / 1000, claims.expirationTime)
        assertEquals(issuedAt.time / 1000, claims.issuedAt)
        assertEquals("jwt-id-123", claims.jwtId)
    }
    
    @Test
    fun `extractClaims includes custom claims`() {
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withSubject("user123")
            .withClaim("role", "admin")
            .withClaim("permissions", 15)
            .withClaim("verified", true)
            .sign(Algorithm.HMAC256(hmacSecret))
        
        val claims = parser.extractClaims(token)
        
        assertNotNull(claims)
        assertEquals("admin", claims!!.customClaims["role"])
        assertEquals(15, claims.customClaims["permissions"])
        assertEquals(true, claims.customClaims["verified"])
    }
    
    @Test
    fun `extractClaims returns null for invalid token`() {
        val claims = parser.extractClaims("invalid.token")
        assertNull(claims)
    }
    
    @Test
    fun `verifySignature validates RS256 signature`() {
        val algorithm = Algorithm.RSA256(rsaPublicKey, rsaPrivateKey)
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withSubject("user123")
            .sign(algorithm)
        
        val isValid = parser.verifySignature(token, rsaPublicKey.encoded)
        assertTrue(isValid)
    }
    
    @Test
    fun `verifySignature rejects invalid RS256 signature`() {
        val algorithm = Algorithm.RSA256(rsaPublicKey, rsaPrivateKey)
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withSubject("user123")
            .sign(algorithm)
        
        val tamperedToken = token.substring(0, token.length - 10) + "TAMPERED12"
        
        val isValid = parser.verifySignature(tamperedToken, rsaPublicKey.encoded)
        assertFalse(isValid)
    }
    
    @Test
    fun `verifySignature rejects RS256 token signed with different key`() {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val wrongKeyPair = keyPairGenerator.generateKeyPair()
        
        val algorithm = Algorithm.RSA256(
            wrongKeyPair.public as RSAPublicKey,
            wrongKeyPair.private as RSAPrivateKey
        )
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withSubject("user123")
            .sign(algorithm)
        
        val isValid = parser.verifySignature(token, rsaPublicKey.encoded)
        assertFalse(isValid)
    }
    
    @Test
    fun `verifySignature rejects HS256 token against a public key (allowlist blocks HS)`() {
        // An attacker crafts an HS256 token; the COA verify path must reject it
        // rather than treat the public key bytes as an HMAC secret.
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withSubject("user123")
            .sign(Algorithm.HMAC256(hmacSecret))

        val isValid = parser.verifySignature(token, rsaPublicKey.encoded)
        assertFalse(isValid)
    }

    @Test
    fun `verifySignature rejects none-algorithm token (allowlist blocks none)`() {
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withSubject("user123")
            .sign(Algorithm.none())

        val isValid = parser.verifySignature(token, rsaPublicKey.encoded)
        assertFalse(isValid)
    }

    @Test
    fun `isNotExpired returns true for non-expired token`() {
        val expiresAt = Date(System.currentTimeMillis() + 3600000)
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withSubject("user123")
            .withExpiresAt(expiresAt)
            .sign(Algorithm.HMAC256(hmacSecret))
        
        assertTrue(parser.isNotExpired(token))
    }
    
    @Test
    fun `isNotExpired returns false for expired token`() {
        val expiresAt = Date(System.currentTimeMillis() - 3600000)
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withSubject("user123")
            .withExpiresAt(expiresAt)
            .sign(Algorithm.HMAC256(hmacSecret))
        
        assertFalse(parser.isNotExpired(token))
    }
    
    @Test
    fun `isNotExpired returns true for token without expiration`() {
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withSubject("user123")
            .sign(Algorithm.HMAC256(hmacSecret))
        
        assertTrue(parser.isNotExpired(token))
    }
    
    @Test
    fun `isNotExpired returns false for invalid token`() {
        assertFalse(parser.isNotExpired("invalid.token"))
    }
    
    @Test
    fun `getExpirationTime returns correct timestamp`() {
        val expiresAt = Date(System.currentTimeMillis() + 3600000)
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withSubject("user123")
            .withExpiresAt(expiresAt)
            .sign(Algorithm.HMAC256(hmacSecret))
        
        val expTime = parser.getExpirationTime(token)
        
        assertNotNull(expTime)
        assertEquals(expiresAt.time / 1000, expTime)
    }
    
    @Test
    fun `getExpirationTime returns null for token without expiration`() {
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withSubject("user123")
            .sign(Algorithm.HMAC256(hmacSecret))
        
        val expTime = parser.getExpirationTime(token)
        assertNull(expTime)
    }
    
    @Test
    fun `getExpirationTime returns null for invalid token`() {
        val expTime = parser.getExpirationTime("invalid.token")
        assertNull(expTime)
    }
    
    private fun createValidToken(): String {
        return JWT.create()
            .withIssuer("test-issuer")
            .withSubject("user123")
            .withAudience("test-audience")
            .sign(Algorithm.HMAC256(hmacSecret))
    }
}
