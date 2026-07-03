package com.jabauth.diagnostic.verify

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Taxonomy tests for the fail-closed [VerdictRollup] — the trust-verdict rules from Part C of the design
 * handoff, encoded as executable assertions. Pure JVM (no Robolectric, no crypto) — the CI-gated core.
 */
class VerdictRollupTest {

    private val P = StageState.PASS
    private val W = StageState.WARN
    private val F = StageState.FAIL
    private val S = StageState.SKIPPED

    // ── VERIFIED ────────────────────────────────────────────────────────────
    @Test fun `all stages pass on a COA yields VERIFIED`() {
        assertThat(VerdictRollup.verdict(decode = P, isCoaFormat = true, pki = P, jwt = P, abe = P))
            .isEqualTo(TrustVerdict.VERIFIED)
    }

    @Test fun `decode WARN but all crypto pass still yields VERIFIED (capture quality does not downgrade trust)`() {
        assertThat(VerdictRollup.verdict(decode = W, isCoaFormat = true, pki = P, jwt = P, abe = P))
            .isEqualTo(TrustVerdict.VERIFIED)
    }

    // ── NOT_A_COA ───────────────────────────────────────────────────────────
    @Test fun `cleanly decoded non-COA payload yields NOT_A_COA`() {
        assertThat(VerdictRollup.verdict(decode = P, isCoaFormat = false, pki = S, jwt = S, abe = S))
            .isEqualTo(TrustVerdict.NOT_A_COA)
    }

    @Test fun `non-COA takes precedence over any crypto state (crypto never ran)`() {
        // Even if crypto fields carry stale non-SKIPPED values, a non-COA symbol is NOT_A_COA.
        assertThat(VerdictRollup.verdict(decode = P, isCoaFormat = false, pki = P, jwt = P, abe = P))
            .isEqualTo(TrustVerdict.NOT_A_COA)
    }

    // ── UNTRUSTED (amber — Principle D: not a fail) ──────────────────────────
    @Test fun `PKI untrusted-anchor WARN with valid JWT and ABE yields UNTRUSTED`() {
        assertThat(VerdictRollup.verdict(decode = P, isCoaFormat = true, pki = W, jwt = P, abe = P))
            .isEqualTo(TrustVerdict.UNTRUSTED)
    }

    @Test fun `PKI revocation-unknown offline WARN yields UNTRUSTED, never FAILED`() {
        val v = VerdictRollup.verdict(decode = P, isCoaFormat = true, pki = W, jwt = P, abe = P)
        assertThat(v).isEqualTo(TrustVerdict.UNTRUSTED)
        assertThat(v).isNotEqualTo(TrustVerdict.FAILED)
    }

    @Test fun `multiple WARNs and no FAIL yields UNTRUSTED`() {
        assertThat(VerdictRollup.verdict(decode = W, isCoaFormat = true, pki = W, jwt = P, abe = W))
            .isEqualTo(TrustVerdict.UNTRUSTED)
    }

    // ── FAILED (magenta — hard fail short-circuits) ──────────────────────────
    @Test fun `PKI hard-fail (revoked) yields FAILED with JWT and ABE skipped`() {
        assertThat(VerdictRollup.verdict(decode = P, isCoaFormat = true, pki = F, jwt = S, abe = S))
            .isEqualTo(TrustVerdict.FAILED)
    }

    @Test fun `JWT hard-fail (bad signature) yields FAILED with ABE skipped`() {
        assertThat(VerdictRollup.verdict(decode = P, isCoaFormat = true, pki = P, jwt = F, abe = S))
            .isEqualTo(TrustVerdict.FAILED)
    }

    @Test fun `ABE hard-fail (policy deny) yields FAILED`() {
        assertThat(VerdictRollup.verdict(decode = P, isCoaFormat = true, pki = P, jwt = P, abe = F))
            .isEqualTo(TrustVerdict.FAILED)
    }

    @Test fun `decode hard-fail yields FAILED (fail-closed — nothing was read)`() {
        assertThat(VerdictRollup.verdict(decode = F, isCoaFormat = true, pki = S, jwt = S, abe = S))
            .isEqualTo(TrustVerdict.FAILED)
    }

    @Test fun `a hard FAIL beats a WARN (fail-closed precedence)`() {
        // PKI degraded (amber) but JWT hard-fails ⇒ the verdict is FAILED, not UNTRUSTED.
        assertThat(VerdictRollup.verdict(decode = P, isCoaFormat = true, pki = W, jwt = F, abe = S))
            .isEqualTo(TrustVerdict.FAILED)
    }

    // ── convenience overload operates on a stage list ────────────────────────
    @Test fun `stage-list overload agrees with the primitive overload`() {
        val stages = listOf(
            StageResult(VerificationStage.DECODE, StageState.PASS),
            StageResult(VerificationStage.PKI, StageState.WARN, reason = "revocation unknown - offline"),
            StageResult(VerificationStage.JWT, StageState.PASS),
            StageResult(VerificationStage.ABE, StageState.PASS),
        )
        assertThat(VerdictRollup.verdict(stages, isCoaFormat = true)).isEqualTo(TrustVerdict.UNTRUSTED)
    }

    @Test fun `stage-list overload treats a missing stage as SKIPPED`() {
        val stages = listOf(
            StageResult(VerificationStage.DECODE, StageState.PASS),
            StageResult(VerificationStage.PKI, StageState.FAIL, reason = "certificate revoked"),
            // JWT and ABE absent ⇒ treated as SKIPPED ⇒ still FAILED from PKI.
        )
        assertThat(VerdictRollup.verdict(stages, isCoaFormat = true)).isEqualTo(TrustVerdict.FAILED)
    }
}
