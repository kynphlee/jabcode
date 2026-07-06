package com.jabauth.diagnostic.data

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date

/** Test-only: mint a self-signed EC (secp256r1) leaf certificate for the trust-anchor persistence tests. */
object TestCerts {
    init { if (Security.getProvider("BC") == null) Security.addProvider(BouncyCastleProvider()) }

    /** A fresh self-signed EC cert (new keypair each call, so distinct calls yield distinct fingerprints). */
    fun selfSignedEc(cn: String, serial: Long = 1L): X509Certificate {
        val kp = KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val name = X500Name("CN=$cn")
        val epoch = 1_700_000_000_000L // fixed instant → deterministic certs
        val builder = JcaX509v3CertificateBuilder(
            name, BigInteger.valueOf(serial), Date(epoch - 86_400_000L), Date(epoch + 31_536_000_000L), name, kp.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(kp.private)
        return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
    }
}
