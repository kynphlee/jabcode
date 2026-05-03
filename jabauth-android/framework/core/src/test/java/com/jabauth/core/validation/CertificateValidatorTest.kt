package com.jabauth.core.validation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.math.BigInteger
import java.security.KeyPair
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * Unit tests for CertificateValidator interface contract
 *
 * Uses TestCertificateValidatorImpl to verify validation behavior.
 * Production CertificateValidatorImpl will be tested via instrumented tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CertificateValidatorTest {

    private lateinit var context: Context
    private lateinit var validator: CertificateValidator

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        validator = TestCertificateValidatorImpl()
    }

    @Test
    fun `validateFormat accepts valid X509 certificate`() {
        val validCert = createTestCertificate()
        
        val result = validator.validateFormat(validCert.encoded)
        
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `validateFormat rejects invalid certificate data`() {
        val invalidData = "not a certificate".toByteArray()
        
        val result = validator.validateFormat(invalidData)
        
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertEquals("INVALID_FORMAT", result.errorCode)
    }

    @Test
    fun `validateFormat rejects empty certificate data`() {
        val emptyData = ByteArray(0)
        
        val result = validator.validateFormat(emptyData)
        
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `parseCertificate returns X509Certificate instance`() {
        val certBytes = createTestCertificate().encoded
        
        val certificate = validator.parseCertificate(certBytes)
        
        assertNotNull(certificate)
        assertTrue(certificate is X509Certificate)
    }

    @Test
    fun `isNotExpired returns true for valid certificate`() {
        val validCert = createTestCertificate()
        
        val result = validator.isNotExpired(validCert)
        
        assertTrue(result)
    }

    @Test
    fun `isNotExpired returns false for expired certificate`() {
        val expiredCert = createExpiredCertificate()
        
        val result = validator.isNotExpired(expiredCert)
        
        assertFalse(result)
    }

    @Test
    fun `getSubjectDN extracts subject from certificate`() {
        val cert = createTestCertificate()
        
        val subjectDN = validator.getSubjectDN(cert)
        
        assertNotNull(subjectDN)
        assertTrue(subjectDN.contains("CN=Test"))
    }

    @Test
    fun `getIssuerDN extracts issuer from certificate`() {
        val cert = createTestCertificate()
        
        val issuerDN = validator.getIssuerDN(cert)
        
        assertNotNull(issuerDN)
        assertTrue(issuerDN.contains("CN=Test"))
    }

    @Test
    fun `isSelfSigned returns true for self-signed certificate`() {
        val selfSignedCert = createTestCertificate()
        
        val result = validator.isSelfSigned(selfSignedCert)
        
        assertTrue(result)
    }

    @Test
    fun `isSelfSigned returns false for non-self-signed certificate`() {
        // Create a certificate with different subject and issuer
        val leafCert = createCertificateWithIssuer("CN=Leaf", "CN=CA")
        
        val result = validator.isSelfSigned(leafCert)
        
        assertFalse(result)
    }

    /**
     * Create a test X.509 certificate for testing
     */
    private fun createTestCertificate(subjectName: String = "CN=Test User, O=Test Org"): X509Certificate {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + 365L * 24 * 60 * 60 * 1000) // 1 year from now

        val subject = X500Name(subjectName)
        val issuer = subject // Self-signed
        val serial = BigInteger.valueOf(now)

        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        
        val certBuilder = X509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            subject,
            publicKeyInfo
        )

        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)

        return JcaX509CertificateConverter().getCertificate(certHolder)
    }

    /**
     * Create an expired test certificate
     */
    private fun createExpiredCertificate(): X509Certificate {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 365L * 24 * 60 * 60 * 1000 * 2) // 2 years ago
        val notAfter = Date(now - 365L * 24 * 60 * 60 * 1000) // 1 year ago (expired)

        val subject = X500Name("CN=Expired Test User")
        val issuer = subject
        val serial = BigInteger.valueOf(now)

        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        
        val certBuilder = X509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            subject,
            publicKeyInfo
        )

        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)

        return JcaX509CertificateConverter().getCertificate(certHolder)
    }

    /**
     * Create a certificate with specified subject and issuer DNs
     */
    private fun createCertificateWithIssuer(subjectName: String, issuerName: String): X509Certificate {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + 365L * 24 * 60 * 60 * 1000) // 1 year from now

        val subject = X500Name(subjectName)
        val issuer = X500Name(issuerName) // Different from subject
        val serial = BigInteger.valueOf(now)

        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        
        val certBuilder = X509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            subject,
            publicKeyInfo
        )

        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)

        return JcaX509CertificateConverter().getCertificate(certHolder)
    }
}
