package com.jabauth.diagnostic.ui.verify

import com.google.common.truth.Truth.assertThat
import com.jabauth.diagnostic.verify.AbeDetail
import com.jabauth.diagnostic.verify.CertChainDetail
import com.jabauth.diagnostic.verify.JwtDetail
import com.jabauth.diagnostic.verify.RevocationInfo
import com.jabauth.diagnostic.verify.RevocationStatus
import com.jabauth.diagnostic.verify.StageDetail
import com.jabauth.diagnostic.verify.StageResult
import com.jabauth.diagnostic.verify.StageState
import com.jabauth.diagnostic.verify.VerificationStage
import org.junit.Test

/** The verification-forward HUD row content (tag / name / value) per stage. Pure JVM. */
class VerificationSummaryContentTest {

    private fun stage(st: VerificationStage, state: StageState, latencyMs: Long = 0L, detail: StageDetail? = null) =
        StageResult(st, state, latencyMs = latencyMs, detail = detail)

    @Test fun `tags and names match the design wording`() {
        assertThat(VerificationSummaryContent.tag(VerificationStage.PKI)).isEqualTo("PKI")
        assertThat(VerificationSummaryContent.name(VerificationStage.PKI)).isEqualTo("certificate chain")
        assertThat(VerificationSummaryContent.name(VerificationStage.JWT)).isEqualTo("signature & claims")
        assertThat(VerificationSummaryContent.name(VerificationStage.ABE)).isEqualTo("access policy")
        assertThat(VerificationSummaryContent.name(VerificationStage.DECODE)).isEqualTo("decode")
    }

    @Test fun `PKI value is the revocation status`() {
        val d = CertChainDetail(emptyList(), RevocationInfo("OCSP + CRL", RevocationStatus.UNKNOWN_OFFLINE, null))
        assertThat(VerificationSummaryContent.value(stage(VerificationStage.PKI, StageState.WARN, detail = d)))
            .isEqualTo("UNKNOWN·offline")
        val valid = CertChainDetail(emptyList(), RevocationInfo("OCSP + CRL", RevocationStatus.VALID, "14s ago"))
        assertThat(VerificationSummaryContent.value(stage(VerificationStage.PKI, StageState.PASS, detail = valid)))
            .isEqualTo("VALID")
    }

    @Test fun `JWT value is the algorithm`() {
        val d = JwtDetail("ES256", true, null, null, null, emptyMap(), emptyList())
        assertThat(VerificationSummaryContent.value(stage(VerificationStage.JWT, StageState.PASS, detail = d)))
            .isEqualTo("ES256")
    }

    @Test fun `ABE value is GRANTED or DENIED`() {
        val granted = AbeDetail("(role:inspector)", emptyList(), true, null)
        assertThat(VerificationSummaryContent.value(stage(VerificationStage.ABE, StageState.PASS, detail = granted)))
            .isEqualTo("GRANTED")
        val denied = AbeDetail("(role:inspector AND region:EU)", emptyList(), false, "region:EU")
        assertThat(VerificationSummaryContent.value(stage(VerificationStage.ABE, StageState.FAIL, detail = denied)))
            .isEqualTo("DENIED")
    }

    @Test fun `DECODE value is the latency, a detail-less stage shows its state`() {
        assertThat(VerificationSummaryContent.value(stage(VerificationStage.DECODE, StageState.PASS, latencyMs = 63)))
            .isEqualTo("63ms")
        assertThat(VerificationSummaryContent.value(stage(VerificationStage.ABE, StageState.SKIPPED)))
            .isEqualTo("SKIPPED")
    }
}
