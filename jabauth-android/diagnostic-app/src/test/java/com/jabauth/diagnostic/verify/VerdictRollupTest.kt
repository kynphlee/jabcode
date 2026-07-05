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

    // ── TRUSTED_OFFLINE (A′ — opt-in teal tier; a valid chain to an imported anchor, never VERIFIED) ──
    private val trustedAnchorPki = CertChainDetail(
        nodes = emptyList(),
        revocation = RevocationInfo("OCSP + CRL", RevocationStatus.UNKNOWN_OFFLINE),
        reachedTrustedAnchor = true,
    )

    @Test fun `TRUST_ANCHOR opt-in with a trusted anchor and unknown-offline revocation yields TRUSTED_OFFLINE`() {
        assertThat(
            VerdictRollup.verdict(
                decode = P, isCoaFormat = true, pki = W, jwt = P, abe = P,
                pkiReachedTrustedAnchor = true, pkiRevocation = RevocationStatus.UNKNOWN_OFFLINE,
                offlinePolicy = OfflineTrustPolicy.TRUST_ANCHOR,
            ),
        ).isEqualTo(TrustVerdict.TRUSTED_OFFLINE)
    }

    @Test fun `the same scan under the default STRICT policy stays UNTRUSTED (opt-in is required)`() {
        assertThat(
            VerdictRollup.verdict(
                decode = P, isCoaFormat = true, pki = W, jwt = P, abe = P,
                pkiReachedTrustedAnchor = true, pkiRevocation = RevocationStatus.UNKNOWN_OFFLINE,
                offlinePolicy = OfflineTrustPolicy.STRICT,
            ),
        ).isEqualTo(TrustVerdict.UNTRUSTED)
    }

    @Test fun `TRUST_ANCHOR with an untrusted anchor stays UNTRUSTED (no green for a stranger's cert)`() {
        assertThat(
            VerdictRollup.verdict(
                decode = P, isCoaFormat = true, pki = W, jwt = P, abe = P,
                pkiReachedTrustedAnchor = false, pkiRevocation = RevocationStatus.UNKNOWN_OFFLINE,
                offlinePolicy = OfflineTrustPolicy.TRUST_ANCHOR,
            ),
        ).isEqualTo(TrustVerdict.UNTRUSTED)
    }

    @Test fun `a hard FAIL always beats the TRUSTED_OFFLINE tier`() {
        assertThat(
            VerdictRollup.verdict(
                decode = P, isCoaFormat = true, pki = W, jwt = F, abe = P,
                pkiReachedTrustedAnchor = true, pkiRevocation = RevocationStatus.UNKNOWN_OFFLINE,
                offlinePolicy = OfflineTrustPolicy.TRUST_ANCHOR,
            ),
        ).isEqualTo(TrustVerdict.FAILED)
    }

    @Test fun `a server-confirmed PKI PASS is VERIFIED, not the offline tier`() {
        // If PKI actually PASSes (revocation confirmed VALID), the scan is fully VERIFIED — the tier is moot.
        assertThat(
            VerdictRollup.verdict(
                decode = P, isCoaFormat = true, pki = P, jwt = P, abe = P,
                pkiReachedTrustedAnchor = true, pkiRevocation = RevocationStatus.VALID,
                offlinePolicy = OfflineTrustPolicy.TRUST_ANCHOR,
            ),
        ).isEqualTo(TrustVerdict.VERIFIED)
    }

    @Test fun `stage-list overload derives TRUSTED_OFFLINE from the PKI detail only under opt-in`() {
        val stages = listOf(
            StageResult(VerificationStage.DECODE, StageState.PASS),
            StageResult(VerificationStage.PKI, StageState.WARN, reason = "trusted anchor; revocation offline", detail = trustedAnchorPki),
            StageResult(VerificationStage.JWT, StageState.PASS),
            StageResult(VerificationStage.ABE, StageState.PASS),
        )
        assertThat(VerdictRollup.verdict(stages, isCoaFormat = true, offlinePolicy = OfflineTrustPolicy.TRUST_ANCHOR))
            .isEqualTo(TrustVerdict.TRUSTED_OFFLINE)
        // …the identical scan under the default strict policy stays UNTRUSTED.
        assertThat(VerdictRollup.verdict(stages, isCoaFormat = true)).isEqualTo(TrustVerdict.UNTRUSTED)
    }
}
