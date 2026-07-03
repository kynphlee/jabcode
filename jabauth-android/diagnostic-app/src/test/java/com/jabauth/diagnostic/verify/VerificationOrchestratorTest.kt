package com.jabauth.diagnostic.verify

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Orchestration tests for [VerificationOrchestrator] — the fail-closed order, short-circuit, warn-continues,
 * latency sum, and rollup integration. Uses fake [VerificationOrchestrator.StageRunner]s (no crypto) so the
 * whole structural spine is CI-gated without a device.
 */
class VerificationOrchestratorTest {

    /** A stage runner that records whether it was invoked and returns a canned result. */
    private class Runner(private val result: StageResult) : VerificationOrchestrator.StageRunner {
        var invoked = false
        override fun run(symbol: DecodedSymbol): StageResult { invoked = true; return result }
    }

    private fun result(stage: VerificationStage, state: StageState, ms: Long = 0L) =
        StageResult(stage, state, reason = if (state == StageState.PASS) null else "$state", latencyMs = ms)

    private fun coa(latencyMs: Long = 10L) = DecodeInput.Decoded(
        DecodedSymbol(byteArrayOf(1, 2, 3), isCoaFormat = true, decodeLatencyMs = latencyMs,
            formatProfile = FormatProfile("v2", "FIELD"))
    )

    private fun plain() = DecodeInput.Decoded(
        DecodedSymbol(byteArrayOf(9), isCoaFormat = false, decodeLatencyMs = 5L,
            formatProfile = FormatProfile("v1", null))
    )

    private fun orchestrator(pki: Runner, jwt: Runner, abe: Runner) =
        VerificationOrchestrator(pki, jwt, abe)

    // ── happy path ───────────────────────────────────────────────────────────
    @Test fun `all stages pass yields VERIFIED with four PASS stages`() {
        val r = orchestrator(
            Runner(result(VerificationStage.PKI, StageState.PASS)),
            Runner(result(VerificationStage.JWT, StageState.PASS)),
            Runner(result(VerificationStage.ABE, StageState.PASS)),
        ).verify(coa())

        assertThat(r.verdict).isEqualTo(TrustVerdict.VERIFIED)
        assertThat(r.stages.map { it.stage })
            .containsExactly(VerificationStage.DECODE, VerificationStage.PKI, VerificationStage.JWT, VerificationStage.ABE)
            .inOrder()
        assertThat(r.stages.map { it.state }.toSet()).containsExactly(StageState.PASS)
    }

    // ── non-COA: crypto never runs ─────────────────────────────────────────────
    @Test fun `non-COA symbol yields NOT_A_COA and never invokes the crypto runners`() {
        val pki = Runner(result(VerificationStage.PKI, StageState.PASS))
        val jwt = Runner(result(VerificationStage.JWT, StageState.PASS))
        val abe = Runner(result(VerificationStage.ABE, StageState.PASS))

        val r = orchestrator(pki, jwt, abe).verify(plain())

        assertThat(r.verdict).isEqualTo(TrustVerdict.NOT_A_COA)
        assertThat(r.stage(VerificationStage.DECODE)!!.state).isEqualTo(StageState.PASS)
        assertThat(r.stage(VerificationStage.PKI)!!.state).isEqualTo(StageState.SKIPPED)
        assertThat(pki.invoked).isFalse()
        assertThat(jwt.invoked).isFalse()
        assertThat(abe.invoked).isFalse()
    }

    // ── decode failure ─────────────────────────────────────────────────────────
    @Test fun `decode failure yields FAILED with DECODE FAIL and crypto SKIPPED`() {
        val pki = Runner(result(VerificationStage.PKI, StageState.PASS))
        val jwt = Runner(result(VerificationStage.JWT, StageState.PASS))
        val abe = Runner(result(VerificationStage.ABE, StageState.PASS))

        val r = orchestrator(pki, jwt, abe)
            .verify(DecodeInput.DecodeFailed(reason = "LDPC: too many errors", latencyMs = 42L))

        assertThat(r.verdict).isEqualTo(TrustVerdict.FAILED)
        assertThat(r.stage(VerificationStage.DECODE)!!.state).isEqualTo(StageState.FAIL)
        assertThat(r.stage(VerificationStage.ABE)!!.state).isEqualTo(StageState.SKIPPED)
        assertThat(pki.invoked).isFalse()
    }

    // ── short-circuit on hard fail ─────────────────────────────────────────────
    @Test fun `PKI hard-fail short-circuits — JWT and ABE skipped and never invoked`() {
        val jwt = Runner(result(VerificationStage.JWT, StageState.PASS))
        val abe = Runner(result(VerificationStage.ABE, StageState.PASS))
        val r = orchestrator(
            Runner(result(VerificationStage.PKI, StageState.FAIL)), jwt, abe
        ).verify(coa())

        assertThat(r.verdict).isEqualTo(TrustVerdict.FAILED)
        assertThat(r.stage(VerificationStage.JWT)!!.state).isEqualTo(StageState.SKIPPED)
        assertThat(r.stage(VerificationStage.ABE)!!.state).isEqualTo(StageState.SKIPPED)
        assertThat(jwt.invoked).isFalse()
        assertThat(abe.invoked).isFalse()
    }

    @Test fun `JWT hard-fail short-circuits ABE`() {
        val abe = Runner(result(VerificationStage.ABE, StageState.PASS))
        val r = orchestrator(
            Runner(result(VerificationStage.PKI, StageState.PASS)),
            Runner(result(VerificationStage.JWT, StageState.FAIL)),
            abe,
        ).verify(coa())

        assertThat(r.verdict).isEqualTo(TrustVerdict.FAILED)
        assertThat(r.stage(VerificationStage.ABE)!!.state).isEqualTo(StageState.SKIPPED)
        assertThat(abe.invoked).isFalse()
    }

    @Test fun `ABE hard-fail yields FAILED`() {
        val r = orchestrator(
            Runner(result(VerificationStage.PKI, StageState.PASS)),
            Runner(result(VerificationStage.JWT, StageState.PASS)),
            Runner(result(VerificationStage.ABE, StageState.FAIL)),
        ).verify(coa())
        assertThat(r.verdict).isEqualTo(TrustVerdict.FAILED)
    }

    // ── warn continues (does NOT short-circuit) ────────────────────────────────
    @Test fun `PKI WARN does not short-circuit — JWT and ABE still run, verdict UNTRUSTED`() {
        val jwt = Runner(result(VerificationStage.JWT, StageState.PASS))
        val abe = Runner(result(VerificationStage.ABE, StageState.PASS))
        val r = orchestrator(
            Runner(result(VerificationStage.PKI, StageState.WARN)), jwt, abe
        ).verify(coa())

        assertThat(r.verdict).isEqualTo(TrustVerdict.UNTRUSTED)
        assertThat(jwt.invoked).isTrue()
        assertThat(abe.invoked).isTrue()
    }

    @Test fun `PKI WARN followed by a JWT hard-fail still yields FAILED (fail beats warn)`() {
        val r = orchestrator(
            Runner(result(VerificationStage.PKI, StageState.WARN)),
            Runner(result(VerificationStage.JWT, StageState.FAIL)),
            Runner(result(VerificationStage.ABE, StageState.PASS)),
        ).verify(coa())
        assertThat(r.verdict).isEqualTo(TrustVerdict.FAILED)
        assertThat(r.stage(VerificationStage.ABE)!!.state).isEqualTo(StageState.SKIPPED)
    }

    // ── latency is summed across the stages that ran ───────────────────────────
    @Test fun `total latency sums decode plus the stages that ran`() {
        val r = orchestrator(
            Runner(result(VerificationStage.PKI, StageState.PASS, ms = 41L)),
            Runner(result(VerificationStage.JWT, StageState.PASS, ms = 18L)),
            Runner(result(VerificationStage.ABE, StageState.PASS, ms = 88L)),
        ).verify(coa(latencyMs = 63L))
        // 63 decode + 41 + 18 + 88 = 210 (the design's FIELD-profile total)
        assertThat(r.totalLatencyMs).isEqualTo(210L)
    }

    // ── format/profile is carried through ──────────────────────────────────────
    @Test fun `format profile from the decoded symbol is carried into the result`() {
        val r = orchestrator(
            Runner(result(VerificationStage.PKI, StageState.PASS)),
            Runner(result(VerificationStage.JWT, StageState.PASS)),
            Runner(result(VerificationStage.ABE, StageState.PASS)),
        ).verify(coa())
        assertThat(r.formatProfile).isEqualTo(FormatProfile("v2", "FIELD"))
    }
}
