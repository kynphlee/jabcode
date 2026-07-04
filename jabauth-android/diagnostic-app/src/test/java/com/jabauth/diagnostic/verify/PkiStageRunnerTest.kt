package com.jabauth.diagnostic.verify

import com.google.common.truth.Truth.assertThat
import com.jabauth.client.pki.CertificateChainValidatorImpl
import com.jabauth.client.pki.TrustStoreManagerImpl
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
 * PKI stage — the honest **interim** (no validator wired) and the **real-validator** path (Phase 6).
 *
 * Offline posture: even a chain that validates to a *trusted* anchor is `WARN` (revocation indeterminate
 * offline — the server confirms); expired / broken chains are `FAIL`. Real BouncyCastle-generated CA
 * chains, pure JVM.
 */
class PkiStageRunnerTest {

    companion object {
        init {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val symbol = DecodedSymbol(byteArrayOf(1), true, 10L, FormatProfile("v2", "FIELD"))

    // ── interim (no validator) ─────────────────────────────────────────────────
    @Test fun `interim PKI is WARN, UNKNOWN_OFFLINE, no anchor`() {
        val r = PkiStageRunner().run(symbol)
        assertThat(r.state).isEqualTo(StageState.WARN)
        val d = r.detail as CertChainDetail
        assertThat(d.revocation.status).isEqualTo(RevocationStatus.UNKNOWN_OFFLINE)
        assertThat(d.nodes).isEmpty()
        assertThat(d.rootTrusted).isFalse()
    }

    // ── real validator path ─────────────────────────────────────────────────────
    @Test fun `a chain to a trusted anchor is WARN (trusted, revocation unknown offline) with a populated chain`() {
        val root = ca("Root"); val inter = ca("Intermediate", root); val leaf = leaf("Leaf", inter)
        val r = runner(listOf(root), listOf(leaf.cert, inter.cert, root.cert)).run(symbol)

        assertThat(r.state).isEqualTo(StageState.WARN)
        assertThat(r.reason).contains("trusted anchor")
        val d = r.detail as CertChainDetail
        assertThat(d.nodes).hasSize(3)
        assertThat(d.rootTrusted).isTrue()                              // the root is a configured anchor
        assertThat(d.revocation.status).isEqualTo(RevocationStatus.UNKNOWN_OFFLINE)
        assertThat(d.nodes.first().sha256Fingerprint).contains(":")    // a real fingerprint is rendered
    }

    @Test fun `a chain to an untrusted root is WARN and flagged not-in-trust-store`() {
        val root = ca("Root"); val leaf = leaf("Leaf", root)
        val otherAnchor = ca("Some Other Root")
        val r = runner(listOf(otherAnchor), listOf(leaf.cert, root.cert)).run(symbol)

        assertThat(r.state).isEqualTo(StageState.WARN)
        assertThat(r.reason).ignoringCase().contains("untrusted")
        assertThat((r.detail as CertChainDetail).rootTrusted).isFalse()
    }

    @Test fun `an expired certificate is a hard FAIL`() {
        val root = ca("Root")
        val expired = leaf("Leaf", root, notBefore = Date(now() - 2 * YEAR), notAfter = Date(now() - YEAR))
        val r = runner(listOf(root), listOf(expired.cert, root.cert)).run(symbol)
        assertThat(r.state).isEqualTo(StageState.FAIL)
        assertThat(r.reason).ignoringCase().contains("expired")
    }

    @Test fun `a broken issuer-signature link is a hard FAIL`() {
        val root = ca("Root"); val otherCa = ca("Other CA")
        val leaf = leaf("Leaf", otherCa) // signed by otherCa but presented under root
        val r = runner(listOf(root), listOf(leaf.cert, root.cert)).run(symbol)
        assertThat(r.state).isEqualTo(StageState.FAIL)
        assertThat(r.reason).ignoringCase().contains("broken")
    }

    // ── helpers (real CA chains — mirrors CertificateChainValidatorImplTest) ─────
    private data class CertKey(val cert: X509Certificate, val keyPair: KeyPair)
    private var serial = 1L
    private val YEAR = 31536000000L
    private fun now() = System.currentTimeMillis()
    private fun kp() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun makeCert(cn: String, issuer: CertKey?, isCa: Boolean, nb: Date, na: Date): CertKey {
        val pair = kp()
        val subject = X500Name("CN=$cn")
        val issuerName = if (issuer != null) X500Name(issuer.cert.subjectX500Principal.name) else subject
        val signingKey = issuer?.keyPair?.private ?: pair.private
        val builder = JcaX509v3CertificateBuilder(issuerName, BigInteger.valueOf(serial++), nb, na, subject, pair.public)
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(isCa))
        builder.addExtension(
            Extension.keyUsage, true,
            if (isCa) KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign) else KeyUsage(KeyUsage.digitalSignature),
        )
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(signingKey)
        return CertKey(JcaX509CertificateConverter().getCertificate(builder.build(signer)), pair)
    }

    private fun ca(cn: String, issuer: CertKey? = null) = makeCert(cn, issuer, true, Date(now()), Date(now() + YEAR))
    private fun leaf(cn: String, issuer: CertKey, notBefore: Date = Date(now()), notAfter: Date = Date(now() + YEAR)) =
        makeCert(cn, issuer, false, notBefore, notAfter)

    private fun runner(anchors: List<CertKey>, chain: List<X509Certificate>?): PkiStageRunner {
        val store = TrustStoreManagerImpl()
        anchors.forEachIndexed { i, a -> store.addTrustedCA("anchor$i", a.cert) }
        return PkiStageRunner(extractChain = { chain }, validator = CertificateChainValidatorImpl(store), trustStore = store)
    }
}
