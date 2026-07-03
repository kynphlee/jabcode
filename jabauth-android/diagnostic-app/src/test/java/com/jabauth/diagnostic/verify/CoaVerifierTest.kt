package com.jabauth.diagnostic.verify

import com.google.common.truth.Truth.assertThat
import com.jabauth.client.abe.ABEPolicy
import com.jabauth.client.jwt.SdJwtVcRequest
import com.jabauth.client.jwt.SdJwtVcService
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Duration

/**
 * End-to-end assembly test for [CoaVerifier]: the three real adapters composed into the orchestrator,
 * exercised with REAL SD-JWT VC crypto + a real CP-ABE policy. Proves the whole on-device pre-check
 * pipeline works today; only the Payload-v2 extraction seams remain (Phase 6).
 *
 * Because PKI is interim (WARN), a fully crypto-valid COA lands on UNTRUSTED, not VERIFIED — the honest
 * current state. When the PKI impl lands, the first assertion flips to VERIFIED with no wiring change.
 */
class CoaVerifierTest {

    private val service = SdJwtVcService()

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun credential(): SdJwtVcRequest =
        SdJwtVcRequest.builder().issuer("https://issuer.jabauth.example").subject("coa-1")
            .vct("coa").expiration(Duration.ofHours(1)).algorithm("ES256")
            .selectivelyDisclosable(mapOf("ownerName" to "Acme")).build()

    private val policy = ABEPolicy.And(listOf(ABEPolicy.Leaf("role:inspector"), ABEPolicy.Leaf("region:EU")))

    private fun symbol(isCoa: Boolean = true) =
        DecodeInput.Decoded(DecodedSymbol(byteArrayOf(1, 2, 3), isCoa, 10L, FormatProfile("v2", "FIELD")))

    private fun orchestrator(keys: KeyPair, token: String, attrs: Set<String>, key: java.security.PublicKey = keys.public) =
        CoaVerifier.orchestrator(
            extractToken = { token }, resolveIssuerKey = { key },
            extractPolicy = { policy }, verifierAttributes = { attrs }, sdJwt = service,
        )

    @Test fun `crypto-valid COA lands on UNTRUSTED because PKI is interim (WARN), with JWT and ABE PASS`() {
        val keys = ecKeyPair()
        val token = service.issue(credential(), keys.private)
        val r = orchestrator(keys, token, setOf("role:inspector", "region:EU")).verify(symbol())

        assertThat(r.verdict).isEqualTo(TrustVerdict.UNTRUSTED)
        assertThat(r.stage(VerificationStage.PKI)!!.state).isEqualTo(StageState.WARN)
        assertThat(r.stage(VerificationStage.JWT)!!.state).isEqualTo(StageState.PASS)
        assertThat(r.stage(VerificationStage.ABE)!!.state).isEqualTo(StageState.PASS)
    }

    @Test fun `ABE deny drives FAILED and names the missing clause (PKI warn does not short-circuit)`() {
        val keys = ecKeyPair()
        val token = service.issue(credential(), keys.private)
        val r = orchestrator(keys, token, setOf("role:inspector")).verify(symbol())  // missing region:EU

        assertThat(r.verdict).isEqualTo(TrustVerdict.FAILED)
        assertThat(r.stage(VerificationStage.ABE)!!.state).isEqualTo(StageState.FAIL)
        assertThat((r.stage(VerificationStage.ABE)!!.detail as AbeDetail).failingClause).isEqualTo("region:EU")
    }

    @Test fun `a bad JWT signature short-circuits ABE to SKIPPED and yields FAILED`() {
        val keys = ecKeyPair()
        val token = service.issue(credential(), keys.private)
        // Resolve a DIFFERENT key → signature fails.
        val r = orchestrator(keys, token, setOf("role:inspector", "region:EU"), key = ecKeyPair().public).verify(symbol())

        assertThat(r.verdict).isEqualTo(TrustVerdict.FAILED)
        assertThat(r.stage(VerificationStage.JWT)!!.state).isEqualTo(StageState.FAIL)
        assertThat(r.stage(VerificationStage.ABE)!!.state).isEqualTo(StageState.SKIPPED)
    }

    @Test fun `a non-COA symbol is NOT_A_COA and never runs the crypto stages`() {
        val keys = ecKeyPair()
        val token = service.issue(credential(), keys.private)
        val r = orchestrator(keys, token, setOf("role:inspector", "region:EU")).verify(symbol(isCoa = false))

        assertThat(r.verdict).isEqualTo(TrustVerdict.NOT_A_COA)
        assertThat(r.stage(VerificationStage.JWT)!!.state).isEqualTo(StageState.SKIPPED)
    }
}
