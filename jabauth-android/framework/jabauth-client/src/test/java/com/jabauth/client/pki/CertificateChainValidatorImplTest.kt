package com.jabauth.client.pki

import com.google.common.truth.Truth.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Production PKIX chain validation. Builds REAL certificate chains (proper CA basicConstraints/keyUsage so
 * `CertPathValidator` accepts them), signing each child with its issuer's key, and checks the validator
 * trusts a chain only when its root is a configured anchor — and rejects unknown roots, expiry, and breaks.
 * Pure JVM (JCA + BouncyCastle), no device.
 */
class CertificateChainValidatorImplTest {

    companion object {
        init {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private data class CertKey(val cert: X509Certificate, val keyPair: KeyPair)

    private var serial = 1L
    private val yearMs = 31536000000L

    private fun keyPair() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    /** Make a cert signed by [issuer] (self-signed if null), CA or end-entity, with the given validity window. */
    private fun makeCert(cn: String, issuer: CertKey?, isCa: Boolean, notBefore: Date, notAfter: Date): CertKey {
        val kp = keyPair()
        val subject = X500Name("CN=$cn")
        val issuerName = if (issuer != null) X500Name(issuer.cert.subjectX500Principal.name) else subject
        val signingKey = issuer?.keyPair?.private ?: kp.private
        val builder = JcaX509v3CertificateBuilder(
            issuerName, BigInteger.valueOf(serial++), notBefore, notAfter, subject, kp.public,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(isCa))
        val usage = if (isCa) KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign) else KeyUsage(KeyUsage.digitalSignature)
        builder.addExtension(Extension.keyUsage, true, usage)
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(signingKey)
        return CertKey(JcaX509CertificateConverter().getCertificate(builder.build(signer)), kp)
    }

    private fun ca(cn: String, issuer: CertKey? = null) =
        makeCert(cn, issuer, isCa = true, Date(now()), Date(now() + yearMs))

    private fun leaf(cn: String, issuer: CertKey, notBefore: Date = Date(now()), notAfter: Date = Date(now() + yearMs)) =
        makeCert(cn, issuer, isCa = false, notBefore, notAfter)

    private fun now() = System.currentTimeMillis()

    private fun validator(vararg anchors: CertKey): CertificateChainValidatorImpl {
        val store = TrustStoreManagerImpl()
        anchors.forEachIndexed { i, a -> store.addTrustedCA("anchor$i", a.cert) }
        return CertificateChainValidatorImpl(store)
    }

    @Test fun `a chain to a trusted root validates`() {
        val root = ca("Root")
        val inter = ca("Intermediate", root)
        val leaf = leaf("Leaf", inter)
        val result = validator(root).validateChain(listOf(leaf.cert, inter.cert, root.cert))
        assertThat(result.errorMessage).isNull()
        assertThat(result.isValid).isTrue()
    }

    @Test fun `a chain whose root is not an anchor is rejected`() {
        val root = ca("Root")
        val inter = ca("Intermediate", root)
        val leaf = leaf("Leaf", inter)
        val otherAnchor = ca("Some Other Root")
        val result = validator(otherAnchor).validateChain(listOf(leaf.cert, inter.cert, root.cert))
        assertThat(result.isValid).isFalse()
    }

    @Test fun `an empty chain is rejected`() {
        assertThat(validator(ca("Root")).validateChain(emptyList()).isValid).isFalse()
    }

    @Test fun `with no anchors configured the chain is rejected`() {
        val root = ca("Root"); val leaf = leaf("Leaf", root)
        assertThat(CertificateChainValidatorImpl(TrustStoreManagerImpl())
            .validateChain(listOf(leaf.cert, root.cert)).isValid).isFalse()
    }

    @Test fun `an expired leaf fails validation`() {
        val root = ca("Root")
        val expiredLeaf = leaf("Leaf", root, notBefore = Date(now() - 2 * yearMs), notAfter = Date(now() - yearMs))
        assertThat(validator(root).validateChain(listOf(expiredLeaf.cert, root.cert)).isValid).isFalse()
    }

    @Test fun `a broken issuer link is rejected`() {
        val root = ca("Root")
        val otherCa = ca("Other CA")
        val leaf = leaf("Leaf", otherCa) // signed by otherCa but presented under root
        assertThat(validator(root).validateChain(listOf(leaf.cert, root.cert)).isValid).isFalse()
    }

    @Test fun `validateCertificate accepts a cert signed by a trusted CA and rejects an untrusted one`() {
        val trusted = ca("Trusted Root")
        val leaf = leaf("Leaf", trusted)
        val v = validator(trusted)
        assertThat(v.validateCertificate(leaf.cert, listOf(trusted.cert)).isValid).isTrue()

        val rogue = ca("Rogue Root")
        val rogueLeaf = leaf("Rogue Leaf", rogue)
        val denied = v.validateCertificate(rogueLeaf.cert, listOf(trusted.cert))
        assertThat(denied.isValid).isFalse()
        assertThat(denied.errorMessage).ignoringCase().contains("untrusted")
    }

    @Test fun `isNotExpired and verifySignature basics`() {
        val root = ca("Root"); val leaf = leaf("Leaf", root)
        val v = validator(root)
        assertThat(v.isNotExpired(leaf.cert)).isTrue()
        assertThat(v.verifySignature(leaf.cert, root.cert)).isTrue()
        assertThat(v.verifySignature(leaf.cert, ca("Unrelated").cert)).isFalse()
    }
}
