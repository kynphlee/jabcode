package com.jabauth.core.fingerprint

import java.security.MessageDigest

/**
 * The verify-side comparison plumbing (device): read a record's `fp` claim, dispatch to the
 * [FingerprintSource] registered under its `fp_alg`, and return a bind verdict ALONGSIDE
 * (never instead of) the credential verdict. Backward compatible by construction: no claim →
 * [Verdict.NO_BINDING]. An unregistered source is surfaced ([Verdict.UNSUPPORTED_SOURCE]),
 * never silently upgraded to BOUND.
 */
class FingerprintBinding(sources: List<FingerprintSource>) {

    enum class Verdict {
        /** Fresh capture matches the enrolled commitment — record and item belong together. */
        BOUND,
        /** Capture does not match — the swap-attack signal. */
        UNBOUND,
        /** The record carries no fingerprint claim (pre-WP-D records; binding is opt-in). */
        NO_BINDING,
        /** Claim present but no source registered for its fp_alg — cannot judge. */
        UNSUPPORTED_SOURCE,
    }

    private val byId: Map<String, FingerprintSource> = sources.associateBy { it.algorithmId() }

    /** Judge a re-captured fingerprint against the record's `fp` claim. */
    fun judge(recordClaims: Map<String, Any?>, capture: ByteArray): Verdict {
        val raw = recordClaims[CLAIM_FP] ?: return Verdict.NO_BINDING
        val claim = raw as? Map<*, *> ?: return Verdict.UNBOUND
        val commitment = try {
            FingerprintCommitment.fromClaim(claim)
        } catch (malformed: IllegalArgumentException) {
            return Verdict.UNBOUND
        }
        val source = byId[commitment.fpAlg] ?: return Verdict.UNSUPPORTED_SOURCE
        return if (source.matches(commitment, capture)) Verdict.BOUND else Verdict.UNBOUND
    }

    companion object {
        /** The record claim carrying a [FingerprintCommitment]. */
        const val CLAIM_FP = "fp"
    }
}

/**
 * The exact-identifier stub source: SHA-256 over a STABLE identifier (serial, stable PUF
 * response) — the one case where exact equality is legitimate.
 */
class ExactIdFingerprintSource : FingerprintSource {

    companion object {
        const val ALGORITHM_ID = "exact-id-sha256"
    }

    override fun algorithmId(): String = ALGORITHM_ID

    override fun commit(capture: ByteArray): FingerprintCommitment =
        FingerprintCommitment(ALGORITHM_ID, sha256(capture))

    override fun matches(commitment: FingerprintCommitment, capture: ByteArray): Boolean =
        commitment.fpAlg == ALGORITHM_ID &&
            MessageDigest.isEqual(commitment.value, sha256(capture))

    private fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)
}

/**
 * The fuzzy-shaped stub source: a 64-bin byte histogram as the "template", normalized L1
 * distance under [threshold] as the match policy. Proves the hook with a source whose
 * re-captures do NOT reproduce enrollment bytes — the shape real image-derived (Entrupy)
 * sources have — with no hardware and no pretense of being a real feature extractor.
 * Mirrors the server stub bit-for-bit so cross-side test vectors agree.
 */
class FuzzyStubFingerprintSource(private val threshold: Double = 0.20) : FingerprintSource {

    companion object {
        const val ALGORITHM_ID = "fuzzy-stub-histogram64"
        private const val BINS = 64
    }

    init {
        require(threshold > 0 && threshold < 1) { "threshold must be in (0,1)" }
    }

    override fun algorithmId(): String = ALGORITHM_ID

    override fun commit(capture: ByteArray): FingerprintCommitment =
        FingerprintCommitment(ALGORITHM_ID, histogram(capture))

    override fun matches(commitment: FingerprintCommitment, capture: ByteArray): Boolean {
        if (commitment.fpAlg != ALGORITHM_ID) return false
        val enrolled = commitment.value
        val fresh = histogram(capture)
        if (enrolled.size != BINS || fresh.size != BINS) return false
        var diff = 0
        var total = 0
        for (i in 0 until BINS) {
            val e = enrolled[i].toInt() and 0xFF
            val f = fresh[i].toInt() and 0xFF
            diff += kotlin.math.abs(e - f)
            total += maxOf(e, f)
        }
        return total > 0 && diff.toDouble() / total <= threshold
    }

    private fun histogram(capture: ByteArray): ByteArray {
        val bins = IntArray(BINS)
        for (b in capture) {
            bins[(b.toInt() and 0xFF) / (256 / BINS)]++
        }
        return ByteArray(BINS) { i -> minOf(255, bins[i]).toByte() }
    }
}
