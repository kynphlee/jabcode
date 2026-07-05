package com.jabauth.diagnostic.verify

/**
 * The result model for the on-device COA verification **pre-check**.
 *
 * <h2>Pre-check, not authoritative verify</h2>
 * This is deliberately distinct from [com.jabauth.diagnostic.metric.DeviceVerifyAttempt], whose
 * `authOutcome` is a byte-exact mirror of the host record and is *always all-SKIP on the device leg*
 * (the device is codec/capture-only; the server is the authority). The types here model something
 * different: the local, offline, fail-closed pre-check the device now *can* run (JWT + ABE crypto
 * landed on-device; PKI is still indeterminate — see [RevocationStatus.UNKNOWN_OFFLINE]). The verdict
 * is computed on-device against a local trust store and adjudicates the *credential*, not the *bearer*
 * (mDL/ISO 18013-5 Principle F). It never overwrites the host-mirror telemetry record.
 *
 * <h2>Four ordered, fail-closed stages</h2>
 * `DECODE → PKI → JWT → ABE`. A hard failure short-circuits the remaining stages (they become
 * [StageState.SKIPPED]). The rollup is the [TrustVerdict] (see [VerdictRollup]).
 */

/** The four ordered stages of the verification pipeline. */
enum class VerificationStage { DECODE, PKI, JWT, ABE }

/**
 * Per-stage state.
 *  - [PASS]    — the stage succeeded.
 *  - [WARN]    — degraded-but-usable / indeterminate (e.g. revocation `UNKNOWN·offline`, untrusted
 *                anchor). Amber. Drives [TrustVerdict.UNTRUSTED], never a hard fail. JWT never emits
 *                WARN (a signature either proves authenticity or it does not — see [JwtDetail]).
 *  - [FAIL]    — a hard failure. Magenta. Short-circuits the pipeline (Principle D — scarce).
 *  - [SKIPPED] — the stage never ran because an earlier stage hard-failed (fail-closed).
 */
enum class StageState { PASS, WARN, FAIL, SKIPPED }

/**
 * The fail-closed rollup verdict shown on the Scanner.
 *  - [VERIFIED]   — all stages pass (green).
 *  - [UNTRUSTED]  — signature-valid but off-trust-list or revocation-indeterminate (amber). NOT a fail.
 *  - [FAILED]     — any hard failure; the failing stage is always named (magenta).
 *  - [NOT_A_COA]  — a cleanly-decoded but non-COA symbol; the classic decode path, never degraded (neutral).
 */
enum class TrustVerdict { VERIFIED, UNTRUSTED, FAILED, NOT_A_COA }

/** The stage a log entry / failure is attributed to. Mirrors the design's `StageErrorTag`. */
enum class StageErrorTag { DECODE, PKI, JWT, ABE }

/** Revocation status for a certificate. `UNKNOWN_OFFLINE` is first-class (amber) — the honest state when
 *  OCSP/CRL is unreachable on-device (and, today, before a PKI revocation impl lands). */
enum class RevocationStatus { VALID, REVOKED, UNKNOWN_OFFLINE, NOT_CHECKED }

/**
 * One stage's outcome. [reason] is required for [StageState.WARN] and [StageState.FAIL] (Principle A —
 * every warn/fail names *why*). [detail] carries the typed forensic payload the drill-down screens render
 * (null for DECODE, or when a stage was SKIPPED).
 */
data class StageResult(
    val stage: VerificationStage,
    val state: StageState,
    val reason: String? = null,
    val latencyMs: Long = 0L,
    val detail: StageDetail? = null,
)

/** Typed per-stage forensic payload. Sealed so each stage's drill-down binds to exactly one shape. */
sealed interface StageDetail

/** PKI payload — the certificate chain + revocation (Trust Anchor drill-down, Phase 4). */
data class CertChainDetail(
    val nodes: List<CertNode>,
    val revocation: RevocationInfo,
) : StageDetail {
    /** True when the chain's root is an anchor in the app's local trust store (Principle B). */
    val rootTrusted: Boolean get() = nodes.lastOrNull()?.inTrustStore == true
}

/** One certificate in the chain (leaf → intermediate(s) → root). */
data class CertNode(
    val subject: String,
    val issuer: String,
    val serial: String,
    val validFrom: String,
    val validTo: String,
    val keyUsage: String,
    val sha256Fingerprint: String,
    val inTrustStore: Boolean,
)

/** Revocation method + result for the leaf. */
data class RevocationInfo(
    val method: String,          // e.g. "OCSP + CRL"
    val status: RevocationStatus,
    val checkedLabel: String? = null, // e.g. "14s ago"; null when NOT_CHECKED
)

/** JWT payload — signature/algorithm verdict + selective-disclosure (Credential drill-down, Phase 4). */
data class JwtDetail(
    val algorithm: String,           // e.g. "ES256"
    val algorithmAllowed: Boolean,   // allowlist verdict (RS*/ES* only; none/HS* blocked)
    val tokenClass: String?,         // "coa.field.v2" / SESSION / ARTIFACT; null if absent
    val issuer: String?,
    val issuedAt: String?,           // iat — ISO instant, paired with expiry in the credential meta row
    val expiry: String?,
    val revealedClaims: Map<String, String>,
    val withheldDigests: List<WithheldClaim>,
) : StageDetail {
    val disclosedCount: Int get() = revealedClaims.size
    val totalClaims: Int get() = revealedClaims.size + withheldDigests.size
}

/** A selectively-disclosed claim the holder withheld — the digest still verified (not missing, not tampered). */
data class WithheldClaim(val name: String, val digest: String)

/** ABE payload — policy vs verifier attributes (ABE Policy drill-down, Phase 4). */
data class AbeDetail(
    val policyExpression: String,               // e.g. "(role:inspector AND region:EU)"
    val verifierAttributes: List<PolicyAttribute>,
    val granted: Boolean,
    val failingClause: String? = null,          // named on deny (Principle A); null when granted
) : StageDetail

/** One verifier attribute evaluated against the policy. */
data class PolicyAttribute(val name: String, val satisfied: Boolean)

/** Payload-format + capture-profile identity for the symbol (HUD format line, Phase 3). */
data class FormatProfile(
    val payloadVersion: String, // "v1" / "v2" / "unknown"
    val coaProfile: String?,    // FIELD / FIELD_HOSTILE / CONTROLLED / SERVER; null until Payload v2 lands
)

/**
 * The complete pre-check result: the ordered stage results, the rolled-up [verdict], the format/profile
 * identity, and total wall-clock. Construct via [VerificationOrchestrator]; the [verdict] is derived by
 * [VerdictRollup] so it can never disagree with the stage states.
 */
data class VerificationResult(
    val verdict: TrustVerdict,
    val stages: List<StageResult>,
    val formatProfile: FormatProfile,
    val totalLatencyMs: Long,
) {
    /** The result for a given stage, or null if not present. */
    fun stage(stage: VerificationStage): StageResult? = stages.firstOrNull { it.stage == stage }
}
