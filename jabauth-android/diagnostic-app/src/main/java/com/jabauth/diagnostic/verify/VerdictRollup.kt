package com.jabauth.diagnostic.verify

/**
 * The single source of truth for the [TrustVerdict] rollup. Pure, deterministic, fail-closed — so the
 * verdict shown to the user can never disagree with the per-stage states. Kept separate from
 * [VerificationOrchestrator] so the whole trust taxonomy is unit-testable without any crypto, camera,
 * or Android dependency (Part C of the design handoff is encoded as tests against this function).
 */
object VerdictRollup {

    /**
     * Roll the four ordered stage states up into a [TrustVerdict], fail-closed.
     *
     * Ordering of the rules is load-bearing:
     *  1. `decode == FAIL` ⇒ [TrustVerdict.FAILED] — nothing was read; fail-closed (Principle A).
     *  2. `!isCoaFormat` ⇒ [TrustVerdict.NOT_A_COA] — a cleanly-decoded plain symbol; the classic path.
     *  3. any crypto stage `FAIL` ⇒ [TrustVerdict.FAILED] — a hard fail beats any warn.
     *  4. A′ opt-in: `offlinePolicy == TRUST_ANCHOR` and PKI is a trusted-anchor `WARN` whose only reservation
     *     is `UNKNOWN_OFFLINE` revocation, with JWT+ABE `PASS` ⇒ [TrustVerdict.TRUSTED_OFFLINE] (teal, a
     *     distinct lesser tier — never the VERIFIED green).
     *  5. any crypto stage `WARN` ⇒ [TrustVerdict.UNTRUSTED] — degraded/indeterminate, amber, not a fail.
     *  6. otherwise ⇒ [TrustVerdict.VERIFIED].
     *
     * `DECODE == WARN` (e.g. low focus but decoded) does not downgrade a crypto verdict — capture quality
     * is surfaced separately, not as distrust.
     *
     * @param isCoaFormat whether the decoded payload is COA-format (determined at DECODE).
     * @param pkiReachedTrustedAnchor whether PKI PKIX-validated the chain to a locally-imported anchor.
     * @param pkiRevocation the PKI revocation status (the A′ tier requires `UNKNOWN_OFFLINE`, not `REVOKED`).
     * @param offlinePolicy the operator's offline trust posture; default [OfflineTrustPolicy.STRICT].
     */
    fun verdict(
        decode: StageState,
        isCoaFormat: Boolean,
        pki: StageState,
        jwt: StageState,
        abe: StageState,
        pkiReachedTrustedAnchor: Boolean = false,
        pkiRevocation: RevocationStatus? = null,
        offlinePolicy: OfflineTrustPolicy = OfflineTrustPolicy.STRICT,
    ): TrustVerdict {
        if (decode == StageState.FAIL) return TrustVerdict.FAILED
        if (!isCoaFormat) return TrustVerdict.NOT_A_COA
        val crypto = listOf(pki, jwt, abe)
        if (crypto.any { it == StageState.FAIL }) return TrustVerdict.FAILED
        // A′ (opt-in): a distinct offline TRUSTED tier — the leaf chains to an imported anchor, integrity +
        // JWT + ABE hold, and PKI's *only* reservation is unconfirmable-offline revocation. Never VERIFIED
        // (unreachable offline: PKI never PASSes without server-confirmed revocation).
        if (offlinePolicy == OfflineTrustPolicy.TRUST_ANCHOR &&
            pki == StageState.WARN && pkiReachedTrustedAnchor &&
            pkiRevocation == RevocationStatus.UNKNOWN_OFFLINE &&
            jwt == StageState.PASS && abe == StageState.PASS
        ) {
            return TrustVerdict.TRUSTED_OFFLINE
        }
        if (crypto.any { it == StageState.WARN }) return TrustVerdict.UNTRUSTED
        return TrustVerdict.VERIFIED
    }

    /** Convenience: roll up a completed [VerificationResult]'s stages under an [offlinePolicy] (default strict). */
    fun verdict(
        stages: List<StageResult>,
        isCoaFormat: Boolean,
        offlinePolicy: OfflineTrustPolicy = OfflineTrustPolicy.STRICT,
    ): TrustVerdict {
        fun s(stage: VerificationStage) =
            stages.firstOrNull { it.stage == stage }?.state ?: StageState.SKIPPED
        val pkiDetail = stages.firstOrNull { it.stage == VerificationStage.PKI }?.detail as? CertChainDetail
        return verdict(
            decode = s(VerificationStage.DECODE),
            isCoaFormat = isCoaFormat,
            pki = s(VerificationStage.PKI),
            jwt = s(VerificationStage.JWT),
            abe = s(VerificationStage.ABE),
            pkiReachedTrustedAnchor = pkiDetail?.reachedTrustedAnchor ?: false,
            pkiRevocation = pkiDetail?.revocation?.status,
            offlinePolicy = offlinePolicy,
        )
    }
}
