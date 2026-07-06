package com.jabauth.diagnostic.verify

/**
 * Runs the four ordered, fail-closed verification stages and rolls them up into a [VerificationResult].
 *
 * Ordering + short-circuit (the fail-closed contract):
 *  - DECODE first. A decode failure ⇒ DECODE `FAIL`, the three crypto stages `SKIPPED`, verdict FAILED.
 *  - A cleanly-decoded but non-COA symbol ⇒ DECODE `PASS`, crypto stages `SKIPPED`, verdict NOT_A_COA
 *    (the classic decode path is never degraded).
 *  - Otherwise run PKI → JWT → ABE. A stage `FAIL` short-circuits: later stages are not run and are
 *    marked `SKIPPED`. A stage `WARN` (degraded/indeterminate, e.g. revocation offline) does NOT
 *    short-circuit — later stages still run, and the rollup lands on UNTRUSTED unless something harder fails.
 *
 * The crypto stages are injected as [StageRunner]s so the orchestration logic (order, short-circuit,
 * rollup, latency) is unit-testable with fakes — no real crypto, camera, or Android dependency. The real
 * adapters (binding to the on-device JWT/ABE engines, and the PKI validator once it lands) implement
 * [StageRunner] and live outside this class.
 */
class VerificationOrchestrator(
    private val pki: StageRunner,
    private val jwt: StageRunner,
    private val abe: StageRunner,
    private val offlinePolicy: () -> OfflineTrustPolicy = { OfflineTrustPolicy.STRICT },
) {
    /** Produces one crypto stage's [StageResult] for a decoded COA symbol. SAM — trivially faked in tests. */
    fun interface StageRunner {
        fun run(symbol: DecodedSymbol): StageResult
    }

    fun verify(input: DecodeInput): VerificationResult = when (input) {
        is DecodeInput.DecodeFailed -> build(
            stages = listOf(
                StageResult(VerificationStage.DECODE, StageState.FAIL, reason = input.reason, latencyMs = input.latencyMs),
                skipped(VerificationStage.PKI),
                skipped(VerificationStage.JWT),
                skipped(VerificationStage.ABE),
            ),
            // decode FAIL short-circuits to FAILED regardless of isCoaFormat.
            formatProfile = FormatProfile("unknown", null),
            isCoaFormat = false,
        )

        is DecodeInput.Decoded -> {
            val sym = input.symbol
            val decode = StageResult(VerificationStage.DECODE, StageState.PASS, latencyMs = sym.decodeLatencyMs)
            if (!sym.isCoaFormat) {
                build(
                    stages = listOf(decode, skipped(VerificationStage.PKI), skipped(VerificationStage.JWT), skipped(VerificationStage.ABE)),
                    formatProfile = sym.formatProfile,
                    isCoaFormat = false,
                )
            } else {
                val stages = mutableListOf(decode)
                val pkiR = pki.run(sym).also { stages += it }
                if (pkiR.state == StageState.FAIL) {
                    stages += skipped(VerificationStage.JWT); stages += skipped(VerificationStage.ABE)
                } else {
                    val jwtR = jwt.run(sym).also { stages += it }
                    if (jwtR.state == StageState.FAIL) {
                        stages += skipped(VerificationStage.ABE)
                    } else {
                        abe.run(sym).also { stages += it }
                    }
                }
                build(stages, sym.formatProfile, isCoaFormat = true)
            }
        }
    }

    private fun skipped(stage: VerificationStage) = StageResult(stage, StageState.SKIPPED)

    private fun build(stages: List<StageResult>, formatProfile: FormatProfile, isCoaFormat: Boolean) =
        VerificationResult(
            verdict = VerdictRollup.verdict(stages, isCoaFormat, offlinePolicy()),
            stages = stages,
            formatProfile = formatProfile,
            totalLatencyMs = stages.sumOf { it.latencyMs },
        )
}

/** The decoded symbol handed to the crypto stages. Android-free and payload-carrying only for the stages
 *  that need it; the orchestrator itself never inspects the bytes. */
data class DecodedSymbol(
    val payload: ByteArray,
    val isCoaFormat: Boolean,
    val decodeLatencyMs: Long,
    val formatProfile: FormatProfile,
) {
    // ByteArray in a data class: value-based equality so tests can compare symbols meaningfully.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedSymbol) return false
        return payload.contentEquals(other.payload) &&
            isCoaFormat == other.isCoaFormat &&
            decodeLatencyMs == other.decodeLatencyMs &&
            formatProfile == other.formatProfile
    }

    override fun hashCode(): Int {
        var r = payload.contentHashCode()
        r = 31 * r + isCoaFormat.hashCode()
        r = 31 * r + decodeLatencyMs.hashCode()
        r = 31 * r + formatProfile.hashCode()
        return r
    }
}

/** The DECODE-stage input to a verification run: either a decoded symbol or a decode failure. */
sealed interface DecodeInput {
    data class Decoded(val symbol: DecodedSymbol) : DecodeInput
    data class DecodeFailed(val reason: String, val latencyMs: Long) : DecodeInput
}
