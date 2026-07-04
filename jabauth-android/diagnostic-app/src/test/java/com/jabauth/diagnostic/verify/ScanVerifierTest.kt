package com.jabauth.diagnostic.verify

import com.google.common.truth.Truth.assertThat
import com.jabauth.client.jwt.SdJwtVcRequest
import com.jabauth.client.jwt.SdJwtVcService
import com.jabauth.client.v2.PayloadFormatV2
import com.jabauth.client.v2.PayloadFormatV2.Section
import com.jabauth.client.v2.PayloadFormatV2.SectionType
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Test
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.util.Date

/**
 * The scan→verdict bridge over real Payload v2: a plain symbol is NOT_A_COA; a v2 COA has its SD-JWT VC
 * verified with the issuer key carried in the TRUST_CHAIN section — a real end-to-end JWT verdict. Pure JVM.
 */
class ScanVerifierTest {

    private val service = SdJwtVcService()

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun credential() = SdJwtVcRequest.builder()
        .issuer("https://issuer.jabauth.example").subject("coa-1").vct("coa").expiration(Duration.ofHours(1))
        .algorithm("ES256").alwaysVisible(mapOf("origin" to "ai-generated"))
        .selectivelyDisclosable(mapOf("licenseTerms" to "CC-BY-4.0")).build()

    @Test fun `a plain (non-v2) symbol is NOT_A_COA and skips the crypto stages`() {
        val r = ScanVerifier().verify("HELLO WORLD 4C".toByteArray(), decodeLatencyMs = 63)
        assertThat(r.verdict).isEqualTo(TrustVerdict.NOT_A_COA)
        assertThat(r.stage(VerificationStage.JWT)!!.state).isEqualTo(StageState.SKIPPED)
        assertThat(r.formatProfile.payloadVersion).isEqualTo("unknown")
    }

    @Test fun `a v2 COA verifies its SD-JWT VC with the issuer key from TRUST_CHAIN`() {
        val keys = ecKeyPair()
        val r = ScanVerifier().verify(v2Coa(keys), decodeLatencyMs = 100)

        assertThat(r.stage(VerificationStage.JWT)!!.state).isEqualTo(StageState.PASS)   // real signature verify
        assertThat(r.stage(VerificationStage.PKI)!!.state).isEqualTo(StageState.WARN)   // interim (no full chain yet)
        assertThat(r.verdict).isEqualTo(TrustVerdict.UNTRUSTED)                         // PKI warn → server confirms VERIFIED
        assertThat(r.formatProfile.payloadVersion).isEqualTo("v2")
    }

    @Test fun `a v2 COA whose TRUST_CHAIN key does not match the signature fails the JWT stage`() {
        val r = ScanVerifier().verify(v2Coa(ecKeyPair(), trustChain = ecKeyPair().public.encoded), decodeLatencyMs = 100)
        assertThat(r.stage(VerificationStage.JWT)!!.state).isEqualTo(StageState.FAIL)
        assertThat(r.stage(VerificationStage.ABE)!!.state).isEqualTo(StageState.SKIPPED) // short-circuit
        assertThat(r.verdict).isEqualTo(TrustVerdict.FAILED)
    }

    @Test fun `parsePublicKey round-trips an EC public key from its SPKI encoding`() {
        val pub = ecKeyPair().public
        assertThat(ScanVerifier.parsePublicKey(pub.encoded)).isEqualTo(pub)
    }

    @Test fun `decode latency is carried into the result`() {
        assertThat(ScanVerifier().verify("HELLO".toByteArray(), decodeLatencyMs = 42).totalLatencyMs).isEqualTo(42)
    }

    // ── spine: leaf-cert TRUST_CHAIN + real ABE policy ──────────────────────────

    @Test fun `a leaf-cert TRUST_CHAIN verifies the JWT and renders a real cert node`() {
        val keys = ecKeyPair()
        val leaf = selfSignedEcCert(keys, "CN=JABAuth Field Issuer,O=JABAuth,C=US")
        val coa = v2Coa(keys, trustChain = leaf.encoded)

        val r = ScanVerifier().verify(coa, decodeLatencyMs = 100)
        assertThat(r.stage(VerificationStage.JWT)!!.state).isEqualTo(StageState.PASS)   // key resolved from the cert
        val pki = r.stage(VerificationStage.PKI)!!
        assertThat(pki.state).isEqualTo(StageState.WARN)                                // self-signed → untrusted anchor
        val d = pki.detail as CertChainDetail
        assertThat(d.nodes).hasSize(1)                                                  // the leaf renders, unlike a bare SPKI
        assertThat(d.nodes.first().subject).contains("JABAuth Field Issuer")
        assertThat(d.nodes.first().sha256Fingerprint).contains(":")
        assertThat(r.verdict).isEqualTo(TrustVerdict.UNTRUSTED)
    }

    @Test fun `a sealed ABE policy is adjudicated GRANTED when the verifier attributes satisfy it`() {
        val keys = ecKeyPair()
        val coa = v2Coa(keys, abe = abeEnvelope("role:inspector AND region:EU"))

        val r = ScanVerifier(verifierAttributes = { setOf("role:inspector", "region:EU") })
            .verify(coa, decodeLatencyMs = 100)
        val abe = r.stage(VerificationStage.ABE)!!
        assertThat(abe.state).isEqualTo(StageState.PASS)
        val d = abe.detail as AbeDetail
        assertThat(d.granted).isTrue()
        assertThat(d.policyExpression).isEqualTo("(role:inspector AND region:EU)")
        assertThat(d.verifierAttributes.map { it.name }).containsExactly("role:inspector", "region:EU")
    }

    @Test fun `a sealed ABE policy is DENIED and names the failing clause when attributes fall short`() {
        val keys = ecKeyPair()
        val coa = v2Coa(keys, abe = abeEnvelope("role:inspector AND region:EU"))

        val r = ScanVerifier(verifierAttributes = { setOf("role:inspector") }) // missing region:EU
            .verify(coa, decodeLatencyMs = 100)
        val abe = r.stage(VerificationStage.ABE)!!
        assertThat(abe.state).isEqualTo(StageState.FAIL)
        assertThat((abe.detail as AbeDetail).failingClause).isEqualTo("region:EU")
    }

    @Test fun `extractPolicy pulls the AST straight from an ABE_SEALED section`() {
        val coa = v2Coa(ecKeyPair(), abe = abeEnvelope("role:inspector AND region:EU"))
        assertThat(ScanVerifier.extractPolicy(coa).toString()).isEqualTo("(role:inspector AND region:EU)")
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    /** A v2 COA whose TRUST_CHAIN is [trustChain] (leaf cert DER by default is the SPKI) and optional ABE_SEALED. */
    private fun v2Coa(issuerKeys: KeyPair, trustChain: ByteArray = issuerKeys.public.encoded, abe: ByteArray? = null): ByteArray {
        val sections = buildList {
            add(Section.of(SectionType.SDJWT_VC, service.issue(credential(), issuerKeys.private).toByteArray()))
            add(Section.of(SectionType.TRUST_CHAIN, trustChain))
            abe?.let { add(Section.of(SectionType.ABE_SEALED, it)) }
            add(Section.of(SectionType.META, "{\"vct\":\"coa\"}".toByteArray()))
        }
        return PayloadFormatV2.encode(sections)
    }

    /** A self-signed EC (secp256r1) X.509 leaf whose key is [keys] — the same key that signs the SD-JWT. */
    private fun selfSignedEcCert(keys: KeyPair, dn: String): X509Certificate {
        val name = X500Name(dn)
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            name, BigInteger.valueOf(now), Date(now - 86_400_000L), Date(now + 31_536_000_000L), name, keys.public,
        ).addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        val signer = JcaContentSignerBuilder("SHA256withECDSA").setProvider(BouncyCastleProvider.PROVIDER_NAME).build(keys.private)
        return JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(builder.build(signer))
    }

    /** An ABE1 envelope carrying [policy] cleartext (mirrors the server encoder; ct/policyData are demo bytes). */
    private fun abeEnvelope(policy: String): ByteArray {
        val p = policy.toByteArray(StandardCharsets.UTF_8)
        val ct = byteArrayOf(1, 2, 3, 4); val pd = byteArrayOf(5, 6)
        return ByteBuffer.allocate(14 + p.size + ct.size + pd.size).apply {
            put('A'.code.toByte()); put('B'.code.toByte()); put('E'.code.toByte()); put('1'.code.toByte())
            put(0x01); put(0x01); putShort(p.size.toShort()); putInt(ct.size); putShort(pd.size.toShort())
            put(p); put(ct); put(pd)
        }.array()
    }

    companion object {
        init {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
        }
    }
}
