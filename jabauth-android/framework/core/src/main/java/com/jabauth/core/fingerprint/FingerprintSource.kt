package com.jabauth.core.fingerprint

import java.util.Base64

/**
 * A source of physical-item fingerprints — the device half of the source-agnostic hook that
 * binds a record to the ITEM (the swap-attack defense, WP-D). Kotlin port of the server's
 * `FingerprintSource`: the framework owns the interface and the claim/comparison plumbing;
 * <b>the adapter owns the match function</b>. Tenant pipelines supply real sources (Entrupy
 * micro-image re-derived from the phone camera; Lumafield CT at intake); PUF is a deferred
 * source behind this same interface. The framework never captures anything.
 *
 * The load-bearing contract: <b>matching is a similarity decision, not equality</b> — a
 * re-captured photo never hashes bit-for-bit to enrollment. Exact equality is a valid special
 * case for stable identifiers only (a serial, a stable PUF response).
 */
interface FingerprintSource {

    /** The id written into the record's `fp.fp_alg` claim; verifiers dispatch on it. */
    fun algorithmId(): String

    /** Enrollment: derive the stored commitment (template/hash) from an inbound capture. */
    fun commit(capture: ByteArray): FingerprintCommitment

    /** Verification: does a fresh capture of the presented item match the commitment? */
    fun matches(commitment: FingerprintCommitment, capture: ByteArray): Boolean
}

/**
 * A physical fingerprint as it rides the signed record: `{fp_alg, fp_commitment}` — opaque to
 * the framework; only the [FingerprintSource] registered under [fpAlg] can interpret [value].
 */
data class FingerprintCommitment(val fpAlg: String, val value: ByteArray) {

    init {
        require(fpAlg.isNotBlank()) { "fp_alg must be present" }
        require(value.isNotEmpty()) { "fp_commitment must be non-empty" }
    }

    /** The claim-map form carried inside the record. */
    fun toClaim(): Map<String, String> = mapOf(
        CLAIM_FP_ALG to fpAlg,
        CLAIM_FP_COMMITMENT to Base64.getUrlEncoder().withoutPadding().encodeToString(value),
    )

    override fun equals(other: Any?): Boolean =
        other is FingerprintCommitment && other.fpAlg == fpAlg && other.value.contentEquals(value)

    override fun hashCode(): Int = 31 * fpAlg.hashCode() + value.contentHashCode()

    companion object {
        const val CLAIM_FP_ALG = "fp_alg"
        const val CLAIM_FP_COMMITMENT = "fp_commitment"

        /** Inverse of [toClaim]; throws on a malformed claim. */
        fun fromClaim(claim: Map<*, *>): FingerprintCommitment {
            val alg = claim[CLAIM_FP_ALG] as? String
                ?: throw IllegalArgumentException("fingerprint claim missing fp_alg")
            val encoded = claim[CLAIM_FP_COMMITMENT] as? String
                ?: throw IllegalArgumentException("fingerprint claim missing fp_commitment")
            return FingerprintCommitment(alg, Base64.getUrlDecoder().decode(encoded))
        }
    }
}
