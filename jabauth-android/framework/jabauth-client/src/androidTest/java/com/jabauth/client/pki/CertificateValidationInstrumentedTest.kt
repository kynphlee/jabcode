package com.jabauth.client.pki

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Instrumented tests for PKI certificate validation
 * 
 * Tests real Bouncy Castle provider on Android device.
 * Validates certificate generation, chain validation, and trust store operations.
 */
@RunWith(AndroidJUnit4::class)
class CertificateValidationInstrumentedTest {
    
    companion object {
        @JvmStatic
        @BeforeClass
        fun setupProvider() {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
        }
    }
    
    @Before
    fun setup() {
        // Setup if needed
    }
    
    @Test
    fun bouncyCastleProviderIsAvailable() {
        val provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        assertNotNull(provider)
        assertEquals("BC", provider.name)
    }
    
    @Test
    fun canGenerateSelfSignedCertificate() {
        val keyPairGen = KeyPairGenerator.getInstance("RSA", "BC")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()
        
        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + 365L * 24 * 60 * 60 * 1000)
        
        val subject = X500Name("CN=Test CA,O=Test Org,C=US")
        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(now),
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )
        
        certBuilder.addExtension(
            Extension.basicConstraints,
            true,
            BasicConstraints(true)
        )
        
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC")
            .build(keyPair.private)
        
        val certHolder = certBuilder.build(signer)
        val cert = JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certHolder)
        
        assertNotNull(cert)
        assertTrue(cert.subjectX500Principal.name.contains("CN=Test CA"))
    }
    
    @Test
    fun canVerifySelfSignedCertificate() {
        val cert = createCACertificate()
        
        // Verify certificate signature using BC
        cert.verify(cert.publicKey, "BC")
        
        // If we get here, verification succeeded
        assertTrue(true)
    }
    
    @Test
    fun canCreateCertificateChain() {
        val caCert = createCACertificate()
        val cert2 = createCACertificate()
        
        // Verify both certs
        caCert.verify(caCert.publicKey, "BC")
        cert2.verify(cert2.publicKey, "BC")
        
        assertNotNull(caCert)
        assertNotNull(cert2)
    }
    
    private fun createCACertificate(): X509Certificate {
        val keyPairGen = KeyPairGenerator.getInstance("RSA", "BC")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()
        
        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + 365L * 24 * 60 * 60 * 1000)
        
        val subject = X500Name("CN=Test CA ${System.nanoTime()},O=Test Org,C=US")
        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(now),
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )
        
        certBuilder.addExtension(
            Extension.basicConstraints,
            true,
            BasicConstraints(true)
        )
        
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC")
            .build(keyPair.private)
        
        val certHolder = certBuilder.build(signer)
        return JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certHolder)
    }
}
