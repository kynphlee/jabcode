package com.jabauth.core.fingerprint

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Random

/**
 * WP-D mobile: the source-agnostic re-derive/matches hook. A matching re-capture → BOUND; a
 * swapped item → UNBOUND — via the adapter's similarity decision, proven for BOTH a
 * fuzzy-shaped source and an exact-id source through the same interface. No claim →
 * NO_BINDING (backward compatible); unknown source surfaced, never trusted.
 */
class FingerprintBindingTest {

    private val fuzzy = FuzzyStubFingerprintSource()
    private val exact = ExactIdFingerprintSource()
    private val binding = FingerprintBinding(listOf(fuzzy, exact))

    /**
     * A synthetic "item surface" with per-item STRUCTURE — uniform random bytes would make
     * every item histogram-identical (the server-side test proved that the hard way). Each
     * item concentrates its bytes in a characteristic region; re-captures add sparse noise.
     */
    private fun capture(itemSeed: Long, noiseSeed: Long): ByteArray {
        val surface = ByteArray(4096)
        val item = Random(itemSeed)
        val characteristicBase = ((itemSeed % 4) * 64).toInt()
        for (i in surface.indices) {
            surface[i] = (characteristicBase + item.nextInt(64)).toByte()
        }
        val noise = Random(noiseSeed)
        var i = 0
        while (i < surface.size) {
            surface[i] = noise.nextInt(256).toByte()
            i += 37
        }
        return surface
    }

    private fun claimsWith(commitment: FingerprintCommitment): Map<String, Any?> =
        mapOf(FingerprintBinding.CLAIM_FP to commitment.toClaim())

    @Test
    fun fuzzySource_sameItemNoisyRecaptureBound_swappedItemUnbound() {
        val enrollment = capture(42L, 1L)
        val commitment = fuzzy.commit(enrollment)
        val claims = claimsWith(commitment)

        val sameItemRecapture = capture(42L, 2L)
        assertThat(sameItemRecapture).isNotEqualTo(enrollment) // noisy, not byte-identical
        assertThat(binding.judge(claims, sameItemRecapture))
            .isEqualTo(FingerprintBinding.Verdict.BOUND)

        val swappedItem = capture(1337L, 3L)
        assertThat(binding.judge(claims, swappedItem))
            .isEqualTo(FingerprintBinding.Verdict.UNBOUND)
    }

    @Test
    fun exactSource_stableIdBound_differentIdUnbound_sameInterface() {
        val commitment = exact.commit("SN-55913-PUF-STABLE".toByteArray())
        val claims = claimsWith(commitment)

        assertThat(binding.judge(claims, "SN-55913-PUF-STABLE".toByteArray()))
            .isEqualTo(FingerprintBinding.Verdict.BOUND)
        assertThat(binding.judge(claims, "SN-99999-FORGED".toByteArray()))
            .isEqualTo(FingerprintBinding.Verdict.UNBOUND)
    }

    @Test
    fun claimRoundTripsAndCrossSideShapeHolds() {
        val commitment = exact.commit("SN-1".toByteArray())
        val restored = FingerprintCommitment.fromClaim(commitment.toClaim())
        assertThat(restored).isEqualTo(commitment)
        assertThat(commitment.toClaim().keys)
            .containsExactly(
                FingerprintCommitment.CLAIM_FP_ALG,
                FingerprintCommitment.CLAIM_FP_COMMITMENT,
            )
    }

    @Test
    fun backwardCompatibleAndGuarded() {
        // No claim: pre-WP-D records judge NO_BINDING — the credential verdict stands alone.
        assertThat(binding.judge(mapOf("event_type" to "ct_scan"), byteArrayOf(1)))
            .isEqualTo(FingerprintBinding.Verdict.NO_BINDING)

        // A source the verifier doesn't have must be surfaced, never trusted.
        val foreign = claimsWith(FingerprintCommitment("entrupy-micro-v1", byteArrayOf(9)))
        assertThat(binding.judge(foreign, byteArrayOf(9)))
            .isEqualTo(FingerprintBinding.Verdict.UNSUPPORTED_SOURCE)

        // Malformed claims never pass.
        assertThat(
            binding.judge(mapOf(FingerprintBinding.CLAIM_FP to mapOf("fp_alg" to "x")), byteArrayOf(1))
        ).isEqualTo(FingerprintBinding.Verdict.UNBOUND)
    }
}
