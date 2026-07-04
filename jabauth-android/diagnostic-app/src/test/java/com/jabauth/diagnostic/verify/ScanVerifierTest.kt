package com.jabauth.diagnostic.verify

import com.google.common.truth.Truth.assertThat
import com.jabauth.client.jwt.SdJwtVcRequest
import com.jabauth.client.jwt.SdJwtVcService
import com.jabauth.client.v2.PayloadFormatV2
import com.jabauth.client.v2.PayloadFormatV2.Section
import com.jabauth.client.v2.PayloadFormatV2.SectionType
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Duration

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

    /** A v2 COA: SD-JWT VC in the SDJWT_VC section, the issuer public key (SPKI) in TRUST_CHAIN. */
    private fun v2Coa(issuerKeys: KeyPair, trustKey: PublicKey = issuerKeys.public): ByteArray =
        PayloadFormatV2.encode(
            listOf(
                Section.of(SectionType.SDJWT_VC, service.issue(credential(), issuerKeys.private).toByteArray()),
                Section.of(SectionType.TRUST_CHAIN, trustKey.encoded),
                Section.of(SectionType.META, "{\"vct\":\"coa\"}".toByteArray()),
            ),
        )

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
        val r = ScanVerifier().verify(v2Coa(ecKeyPair(), trustKey = ecKeyPair().public), decodeLatencyMs = 100)
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
}
