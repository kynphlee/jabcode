package com.jabauth.diagnostic.ui.verify

import com.google.common.truth.Truth.assertThat
import com.jabauth.diagnostic.verify.AbeDetail
import com.jabauth.diagnostic.verify.CertChainDetail
import com.jabauth.diagnostic.verify.FormatProfile
import com.jabauth.diagnostic.verify.JwtDetail
import com.jabauth.diagnostic.verify.PolicyAttribute
import com.jabauth.diagnostic.verify.RevocationInfo
import com.jabauth.diagnostic.verify.RevocationStatus
import com.jabauth.diagnostic.verify.StageDetail
import com.jabauth.diagnostic.verify.StageResult
import com.jabauth.diagnostic.verify.StageState
import com.jabauth.diagnostic.verify.VerificationStage
import com.jabauth.diagnostic.verify.WithheldClaim
import org.junit.Test

/** Per-stage HUD content: header + label/value rows drawn from each stage's typed payload. Pure JVM. */
class StageSummariesTest {

    private fun stage(
        st: VerificationStage, state: StageState, reason: String? = null, detail: StageDetail? = null,
    ) = StageResult(st, state, reason, 0L, detail)

    @Test fun `header combines stage and state`() {
        assertThat(StageSummaries.header(stage(VerificationStage.PKI, StageState.WARN))).isEqualTo("PKI · WARN")
    }

    @Test fun `PKI rows show the chain summary and revocation status`() {
        val d = CertChainDetail(
            nodes = emptyList(),
            revocation = RevocationInfo("OCSP + CRL", RevocationStatus.UNKNOWN_OFFLINE, null),
        )
        val rows = StageSummaries.rows(stage(VerificationStage.PKI, StageState.WARN, "offline", d))
        assertThat(rows.map { it.first }).contains("chain")
        assertThat(rows).contains("revocation" to "UNKNOWN_OFFLINE (OCSP + CRL)")
    }

    @Test fun `JWT rows show algorithm allowlist, token_class, and the disclosure count`() {
        val d = JwtDetail(
            algorithm = "ES256", algorithmAllowed = true, tokenClass = "ARTIFACT", issuer = "iss", expiry = "exp",
            revealedClaims = mapOf("a" to "1", "b" to "2"), withheldDigests = listOf(WithheldClaim("", "dg")),
        )
        val rows = StageSummaries.rows(stage(VerificationStage.JWT, StageState.PASS, null, d))
        assertThat(rows).contains("algorithm" to "ES256 (allowed)")
        assertThat(rows).contains("token_class" to "ARTIFACT")
        assertThat(rows).contains("disclosed" to "2 of 3 claims")
    }

    @Test fun `a rejected algorithm reads as rejected`() {
        val d = JwtDetail("HS256", false, null, null, null, emptyMap(), emptyList())
        assertThat(StageSummaries.rows(stage(VerificationStage.JWT, StageState.FAIL, null, d)))
            .contains("algorithm" to "HS256 (rejected)")
    }

    @Test fun `ABE access reads granted or names the missing clause on deny`() {
        val granted = AbeDetail("(role:inspector)", listOf(PolicyAttribute("role:inspector", true)), true, null)
        assertThat(StageSummaries.rows(stage(VerificationStage.ABE, StageState.PASS, null, granted)))
            .contains("access" to "granted")

        val denied = AbeDetail("(role:inspector AND region:EU)", emptyList(), false, "region:EU")
        assertThat(StageSummaries.rows(stage(VerificationStage.ABE, StageState.FAIL, null, denied)))
            .contains("access" to "denied: missing region:EU")
    }

    @Test fun `the stage reason is surfaced when present`() {
        val rows = StageSummaries.rows(stage(VerificationStage.JWT, StageState.FAIL, "Invalid issuer signature"))
        assertThat(rows).contains("reason" to "Invalid issuer signature")
    }

    @Test fun `a skipped stage has no detail rows`() {
        assertThat(StageSummaries.rows(stage(VerificationStage.ABE, StageState.SKIPPED))).isEmpty()
    }

    @Test fun `the format line shows payload version and profile`() {
        assertThat(StageSummaries.formatLine(FormatProfile("v2", "FIELD"))).isEqualTo("Payload v2 · FIELD")
        assertThat(StageSummaries.formatLine(FormatProfile("unknown", null))).isEqualTo("Payload unknown")
    }
}
