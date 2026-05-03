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
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.security.Security

/**
 * Unit tests for CertificateChainValidator
 * 
 * Tests PKI certificate chain validation logic.
 * Coverage Target: 80%+
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CertificateChainValidatorTest {
    
    private lateinit var validator: CertificateChainValidator
    private lateinit var trustedCAs: List<X509Certificate>
    
    companion object {
        init {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
        }
    }
    
    @Before
    fun setup() {
        validator = TestCertificateChainValidatorImpl()
        trustedCAs = listOf(createRootCACertificate())
    }
    
    @Test
    fun `validateChain accepts valid certificate chain`() {
        // Given
        val rootCA = createRootCACertificate()
        val intermediateCert = createIntermediateCertificate(rootCA)
        val leafCert = createLeafCertificate(intermediateCert)
        val chain = listOf(leafCert, intermediateCert, rootCA)
        
        // When
        val result = validator.validateChain(chain)
        
        // Then
        assertThat(result.isValid).isTrue()
        assertThat(result.errorMessage).isNull()
    }
    
    @Test
    fun `validateChain rejects chain with expired certificate`() {
        // Given
        val expiredCert = createExpiredCertificate()
        val chain = listOf(expiredCert)
        
        // When
        val result = validator.validateChain(chain)
        
        // Then
        assertThat(result.isValid).isFalse()
        assertThat(result.errorMessage).contains("expired")
    }
    
    @Test
    fun `validateChain rejects chain with invalid signature`() {
        // Given
        val rootCA = createRootCACertificate()
        val otherCA = createRootCACertificate() // Different CA
        val leafCert = createLeafCertificate(otherCA) // Signed by different CA
        val chain = listOf(leafCert, rootCA) // But presented with wrong CA
        
        // When
        val result = validator.validateChain(chain)
        
        // Then
        assertThat(result.isValid).isFalse()
        assertThat(result.errorMessage).contains("signature")
    }
    
    @Test
    fun `validateCertificate accepts certificate signed by trusted CA`() {
        // Given
        val leafCert = createLeafCertificate(trustedCAs[0])
        
        // When
        val result = validator.validateCertificate(leafCert, trustedCAs)
        
        // Then
        assertThat(result.isValid).isTrue()
    }
    
    @Test
    fun `validateCertificate rejects certificate signed by untrusted CA`() {
        // Given
        val untrustedCA = createRootCACertificate()
        val leafCert = createLeafCertificate(untrustedCA)
        
        // When
        val result = validator.validateCertificate(leafCert, trustedCAs)
        
        // Then
        assertThat(result.isValid).isFalse()
        assertThat(result.errorMessage).contains("untrusted")
    }
    
    @Test
    fun `isNotExpired returns true for valid certificate`() {
        // Given
        val validCert = createValidCertificate()
        
        // When
        val result = validator.isNotExpired(validCert)
        
        // Then
        assertThat(result).isTrue()
    }
    
    @Test
    fun `isNotExpired returns false for expired certificate`() {
        // Given
        val expiredCert = createExpiredCertificate()
        
        // When
        val result = validator.isNotExpired(expiredCert)
        
        // Then
        assertThat(result).isFalse()
    }
    
    @Test
    fun `verifySignature accepts valid signature`() {
        // Given
        val issuerCert = createRootCACertificate()
        val signedCert = createLeafCertificate(issuerCert)
        
        // When
        val result = validator.verifySignature(signedCert, issuerCert)
        
        // Then
        assertThat(result).isTrue()
    }
    
    @Test
    fun `verifySignature rejects invalid signature`() {
        // Given
        val issuerCert = createRootCACertificate()
        val otherCA = createRootCACertificate()
        val signedCert = createLeafCertificate(otherCA)
        
        // When
        val result = validator.verifySignature(signedCert, issuerCert)
        
        // Then
        assertThat(result).isFalse()
    }
    
    // Helper methods to create test certificates
    
    private fun createRootCACertificate(): X509Certificate {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()
        
        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + 365L * 24 * 60 * 60 * 1000) // 1 year
        
        val subject = X500Name("CN=Test Root CA, O=Test Org")
        val serial = BigInteger.valueOf(now)
        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        
        val certBuilder = X509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, publicKeyInfo
        )
        
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        
        return JcaX509CertificateConverter().getCertificate(certHolder)
    }
    
    private fun createIntermediateCertificate(issuerCert: X509Certificate): X509Certificate {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()
        
        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + 180L * 24 * 60 * 60 * 1000) // 6 months
        
        val issuer = X500Name(issuerCert.subjectX500Principal.name)
        val subject = X500Name("CN=Test Intermediate CA, O=Test Org")
        val serial = BigInteger.valueOf(now)
        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        
        val certBuilder = X509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, subject, publicKeyInfo
        )
        
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        
        return JcaX509CertificateConverter().getCertificate(certHolder)
    }
    
    private fun createLeafCertificate(issuerCert: X509Certificate): X509Certificate {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()
        
        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + 90L * 24 * 60 * 60 * 1000) // 3 months
        
        val issuer = X500Name(issuerCert.subjectX500Principal.name)
        val subject = X500Name("CN=Test Leaf Cert, O=Test Org")
        val serial = BigInteger.valueOf(now)
        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        
        val certBuilder = X509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, subject, publicKeyInfo
        )
        
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        
        return JcaX509CertificateConverter().getCertificate(certHolder)
    }
    
    private fun createValidCertificate(): X509Certificate {
        return createRootCACertificate()
    }
    
    private fun createExpiredCertificate(): X509Certificate {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()
        
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 365L * 24 * 60 * 60 * 1000 * 2) // 2 years ago
        val notAfter = Date(now - 365L * 24 * 60 * 60 * 1000) // 1 year ago (expired)
        
        val subject = X500Name("CN=Expired Test Cert, O=Test Org")
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
