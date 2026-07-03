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
     *  4. any crypto stage `WARN` ⇒ [TrustVerdict.UNTRUSTED] — degraded/indeterminate, amber, not a fail.
     *  5. otherwise ⇒ [TrustVerdict.VERIFIED].
     *
     * `DECODE == WARN` (e.g. low focus but decoded) does not downgrade a crypto verdict — capture quality
     * is surfaced separately, not as distrust.
     *
     * @param isCoaFormat whether the decoded payload is COA-format (determined at DECODE).
     */
    fun verdict(
        decode: StageState,
        isCoaFormat: Boolean,
        pki: StageState,
        jwt: StageState,
        abe: StageState,
    ): TrustVerdict {
        if (decode == StageState.FAIL) return TrustVerdict.FAILED
        if (!isCoaFormat) return TrustVerdict.NOT_A_COA
        val crypto = listOf(pki, jwt, abe)
        if (crypto.any { it == StageState.FAIL }) return TrustVerdict.FAILED
        if (crypto.any { it == StageState.WARN }) return TrustVerdict.UNTRUSTED
        return TrustVerdict.VERIFIED
    }

    /** Convenience: roll up a completed [VerificationResult]'s stages. */
    fun verdict(stages: List<StageResult>, isCoaFormat: Boolean): TrustVerdict {
        fun s(stage: VerificationStage) =
            stages.firstOrNull { it.stage == stage }?.state ?: StageState.SKIPPED
        return verdict(
            decode = s(VerificationStage.DECODE),
            isCoaFormat = isCoaFormat,
            pki = s(VerificationStage.PKI),
            jwt = s(VerificationStage.JWT),
            abe = s(VerificationStage.ABE),
        )
    }
}
