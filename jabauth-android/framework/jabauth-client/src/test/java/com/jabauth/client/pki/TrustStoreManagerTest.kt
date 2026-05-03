package com.jabauth.client.pki

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * Unit tests for TrustStoreManager
 * 
 * Tests CA certificate storage and retrieval.
 * Coverage Target: 80%+
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrustStoreManagerTest {
    
    private lateinit var trustStoreManager: TrustStoreManager
    
    @Before
    fun setup() {
        trustStoreManager = TestTrustStoreManagerImpl()
    }
    
    @Test
    fun `addTrustedCA stores CA certificate successfully`() {
        // Given
        val caCert = createTestCACertificate()
        
        // When
        val result = trustStoreManager.addTrustedCA("test-ca", caCert)
        
        // Then
        assertThat(result).isTrue()
        assertThat(trustStoreManager.isTrusted("test-ca")).isTrue()
    }
    
    @Test
    fun `getTrustedCA retrieves stored certificate`() {
        // Given
        val caCert = createTestCACertificate()
        trustStoreManager.addTrustedCA("test-ca", caCert)
        
        // When
        val retrieved = trustStoreManager.getTrustedCA("test-ca")
        
        // Then
        assertThat(retrieved).isNotNull()
        assertThat(retrieved).isEqualTo(caCert)
    }
    
    @Test
    fun `getTrustedCA returns null for non-existent alias`() {
        // When
        val retrieved = trustStoreManager.getTrustedCA("non-existent")
        
        // Then
        assertThat(retrieved).isNull()
    }
    
    @Test
    fun `removeTrustedCA removes certificate successfully`() {
        // Given
        val caCert = createTestCACertificate()
        trustStoreManager.addTrustedCA("test-ca", caCert)
        
        // When
        val result = trustStoreManager.removeTrustedCA("test-ca")
        
        // Then
        assertThat(result).isTrue()
        assertThat(trustStoreManager.isTrusted("test-ca")).isFalse()
    }
    
    @Test
    fun `getAllTrustedCAs returns all stored certificates`() {
        // Given
        val ca1 = createTestCACertificate()
        val ca2 = createTestCACertificate()
        trustStoreManager.addTrustedCA("ca1", ca1)
        trustStoreManager.addTrustedCA("ca2", ca2)
        
        // When
        val allCAs = trustStoreManager.getAllTrustedCAs()
        
        // Then
        assertThat(allCAs).hasSize(2)
        assertThat(allCAs).contains(ca1)
        assertThat(allCAs).contains(ca2)
    }
    
    @Test
    fun `isTrusted returns false for non-existent CA`() {
        // When
        val result = trustStoreManager.isTrusted("non-existent")
        
        // Then
        assertThat(result).isFalse()
    }
    
    @Test
    fun `clear removes all trusted CAs`() {
        // Given
        trustStoreManager.addTrustedCA("ca1", createTestCACertificate())
        trustStoreManager.addTrustedCA("ca2", createTestCACertificate())
        
        // When
        val result = trustStoreManager.clear()
        
        // Then
        assertThat(result).isTrue()
        assertThat(trustStoreManager.getAllTrustedCAs()).isEmpty()
    }
    
    // Helper method to create test CA certificate
    private fun createTestCACertificate(): X509Certificate {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()
        
        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + 365L * 24 * 60 * 60 * 1000) // 1 year
        
        val subject = X500Name("CN=Test CA ${System.nanoTime()}, O=Test Org")
        val serial = BigInteger.valueOf(now)
        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        
        val certBuilder = X509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, publicKeyInfo
        )
        
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        
        return JcaX509CertificateConverter().getCertificate(certHolder)
    }
}
