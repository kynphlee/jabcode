package com.jabauth.diagnostic.ui.errorlog

import com.google.common.truth.Truth.assertThat
import com.jabauth.diagnostic.verify.StageErrorTag
import com.jabauth.diagnostic.verify.StageResult
import com.jabauth.diagnostic.verify.StageState
import com.jabauth.diagnostic.verify.VerificationStage
import org.junit.Test

/**
 * The pure [StageResult] → [ErrorEntry] mapping that plumbs a verification stage failure into the Error
 * Log. Only WARN/FAIL stages produce an entry (PASS/SKIPPED do not); FAIL→ERROR, WARN→WARNING; the stage
 * maps to its [StageErrorTag]; the stage's real `reason` is carried into the entry's detail. Pure JVM.
 */
class StageResultToErrorEntryTest {

    @Test fun `FAIL maps to ERROR severity, correct stage tag, reason carried`() {
        val reason = "Access denied: missing region:EU"
        val entry = StageResult(VerificationStage.ABE, StageState.FAIL, reason = reason)
            .toErrorEntry(timestamp = 1_000L)

        assertThat(entry).isNotNull()
        assertThat(entry!!.severity).isEqualTo(ErrorSeverity.ERROR)
        assertThat(entry.stageTag).isEqualTo(StageErrorTag.ABE)
        assertThat(entry.details).isEqualTo(reason)
        assertThat(entry.timestamp).isEqualTo(1_000L)
    }

    @Test fun `WARN maps to WARNING severity`() {
        val reason = "revocation unknown offline"
        val entry = StageResult(VerificationStage.PKI, StageState.WARN, reason = reason)
            .toErrorEntry(timestamp = 2_000L)

        assertThat(entry).isNotNull()
        assertThat(entry!!.severity).isEqualTo(ErrorSeverity.WARNING)
        assertThat(entry.stageTag).isEqualTo(StageErrorTag.PKI)
        assertThat(entry.details).isEqualTo(reason)
    }

    @Test fun `each stage maps to its tag`() {
        fun tagOf(stage: VerificationStage) =
            StageResult(stage, StageState.FAIL, reason = "x").toErrorEntry(0L)!!.stageTag

        assertThat(tagOf(VerificationStage.DECODE)).isEqualTo(StageErrorTag.DECODE)
        assertThat(tagOf(VerificationStage.PKI)).isEqualTo(StageErrorTag.PKI)
        assertThat(tagOf(VerificationStage.JWT)).isEqualTo(StageErrorTag.JWT)
        assertThat(tagOf(VerificationStage.ABE)).isEqualTo(StageErrorTag.ABE)
    }

    @Test fun `PASS and SKIPPED produce no entry`() {
        assertThat(StageResult(VerificationStage.JWT, StageState.PASS).toErrorEntry(0L)).isNull()
        assertThat(StageResult(VerificationStage.DECODE, StageState.SKIPPED).toErrorEntry(0L)).isNull()
    }

    @Test fun `a null reason still yields an entry with a stage-derived message`() {
        // reason can be absent (e.g. a WARN without a set reason); the entry must still carry a message.
        val entry = StageResult(VerificationStage.JWT, StageState.FAIL, reason = null).toErrorEntry(0L)
        assertThat(entry).isNotNull()
        assertThat(entry!!.message).isNotEmpty()
    }
}
