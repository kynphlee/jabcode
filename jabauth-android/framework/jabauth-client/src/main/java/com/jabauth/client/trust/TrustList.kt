package com.jabauth.client.trust

import com.jabauth.client.jwt.Jwk
import java.security.PublicKey
import java.util.Base64

/**
 * The device-side view of the signed trust list (the D-trust-anchor-direction ADR; VICAL /
 * C2PA shape): a versioned list of trusted issuer anchors — RFC 7638 thumbprint (`kid`,
 * base64url) → the issuer's public JWK + an optional policy label — with a <b>signed</b>
 * `max_staleness` freshness floor the verifier must enforce against its cached copy.
 *
 * [keyFor] is self-certifying: it re-derives the thumbprint from the anchor's own JWK and
 * returns the key only when it equals the requested thumbprint — a tampered or mis-mapped
 * entry yields null (→ UNTRUSTED), never a wrong key. Parity with the server's `TrustList`.
 */
data class TrustList(
    val version: Long,
    val maxStalenessSeconds: Long,
    val anchors: List<Anchor>,
) {

    data class Anchor(val kid: String, val jwk: Map<*, *>, val policy: String?)

    init {
        require(version >= 0) { "trust-list version must be non-negative" }
        require(maxStalenessSeconds > 0) { "max-staleness must be positive" }
    }

    /** The issuer key for [thumbprint], iff an anchor's JWK re-derives exactly it. */
    fun keyFor(thumbprint: ByteArray): PublicKey? {
        val kid = Base64.getUrlEncoder().withoutPadding().encodeToString(thumbprint)
        val anchor = anchors.firstOrNull { it.kid == kid } ?: return null
        return runCatching {
            val key = Jwk.publicKeyOf(anchor.jwk)
            if (Jwk.thumbprintSha256(key).contentEquals(thumbprint)) key else null
        }.getOrNull()
    }

    companion object {
        const val CLAIM_VER = "ver"
        const val CLAIM_MAX_STALENESS = "max_staleness"
        const val CLAIM_ANCHORS = "anchors"

        /** Decode the `trust_list` claim map; throws on a malformed structure. */
        fun fromClaims(claims: Map<*, *>): TrustList {
            val ver = (claims[CLAIM_VER] as? Number)?.toLong()
                ?: throw IllegalArgumentException("trust-list claims missing ver")
            val staleness = (claims[CLAIM_MAX_STALENESS] as? Number)?.toLong()
                ?: throw IllegalArgumentException("trust-list claims missing max_staleness")
            val rawAnchors = claims[CLAIM_ANCHORS] as? List<*>
                ?: throw IllegalArgumentException("trust-list claims missing anchors")
            val anchors = rawAnchors.map { entry ->
                val m = entry as? Map<*, *>
                    ?: throw IllegalArgumentException("trust-list anchor malformed")
                Anchor(
                    kid = m["kid"] as? String
                        ?: throw IllegalArgumentException("trust-list anchor missing kid"),
                    jwk = m["jwk"] as? Map<*, *>
                        ?: throw IllegalArgumentException("trust-list anchor missing jwk"),
                    policy = m["policy"] as? String,
                )
            }
            return TrustList(ver, staleness, anchors)
        }
    }
}
