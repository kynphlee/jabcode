package com.jabauth.diagnostic.ui.verify

import com.jabauth.diagnostic.verify.AbeDetail
import com.jabauth.diagnostic.verify.CertChainDetail
import com.jabauth.diagnostic.verify.JwtDetail
import com.jabauth.diagnostic.verify.RevocationStatus
import com.jabauth.diagnostic.verify.StageResult
import com.jabauth.diagnostic.verify.VerificationStage

/**
 * Pure content for the verification-forward HUD summary (Flow A of the design): per-stage tag, name, and
 * right-aligned value, drawn from each [StageResult]'s typed payload. Matches the mock's rows —
 * `● PKI  certificate chain … VALID`, `● JWT  signature & claims … ES256`, `● ABE  access policy … GRANTED`.
 * Kept pure so the content is unit-tested; the composable renders these + the state colours.
 */
object VerificationSummaryContent {

    /** Stage tag (mono caps, in the stage's module colour). */
    fun tag(stage: VerificationStage): String = when (stage) {
        VerificationStage.DECODE -> "JABCODE"
        VerificationStage.PKI -> "PKI"
        VerificationStage.JWT -> "JWT"
        VerificationStage.ABE -> "ABE"
    }

    /** Stage name (the design's wording). */
    fun name(stage: VerificationStage): String = when (stage) {
        VerificationStage.DECODE -> "decode"
        VerificationStage.PKI -> "certificate chain"
        VerificationStage.JWT -> "signature & claims"
        VerificationStage.ABE -> "access policy"
    }

    /** The right-aligned value: the salient fact per stage (revocation / algorithm / grant / latency). */
    fun value(result: StageResult): String = when (val d = result.detail) {
        is CertChainDetail -> d.revocation.status.label()
        is JwtDetail -> d.algorithm
        is AbeDetail -> if (d.granted) "GRANTED" else "DENIED"
        else -> if (result.stage == VerificationStage.DECODE) "${result.latencyMs}ms" else result.state.name
    }

    internal fun RevocationStatus.label(): String = when (this) {
        RevocationStatus.VALID -> "VALID"
        RevocationStatus.REVOKED -> "REVOKED"
        RevocationStatus.UNKNOWN_OFFLINE -> "UNKNOWN·offline"
        RevocationStatus.NOT_CHECKED -> "not-checked"
    }
}
