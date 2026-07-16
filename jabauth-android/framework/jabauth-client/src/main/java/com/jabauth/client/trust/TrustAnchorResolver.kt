package com.jabauth.client.trust

import java.security.PublicKey
import java.time.Clock
import java.time.Duration

/**
 * Resolves a mark's `ISSUER_KEY_ID` thumbprint to a trusted issuer key from the <b>cached</b>
 * signed trust list — the device half of the trust-list anchor, mirroring the server's
 * `TrustAnchorResolver` verdict-for-verdict. No network on this path; [TrustListCache.read]
 * is the only source.
 *
 * Verdicts (never a silent "valid"):
 *  - [Outcome.TRUSTED] — list verified + fresh within its signed `max_staleness`; anchor
 *    present and self-consistent; issuer key returned.
 *  - [Outcome.UNTRUSTED] — list verifiable but the thumbprint is not anchored (the delist
 *    case), or the list fails integrity checks. Reject.
 *  - [Outcome.STALE] — no cached list, expired cache, or cache older than the signed floor.
 *    The trust-list analogue of `TRUSTED_OFFLINE`: refuse/degrade per policy, never upgrade.
 */
class TrustAnchorResolver(
    private val verifier: TrustListVerifier = TrustListVerifier(),
    private val clock: Clock = Clock.systemUTC(),
) {

    enum class Outcome { TRUSTED, UNTRUSTED, STALE }

    data class Resolution(
        val outcome: Outcome,
        val issuerKey: PublicKey?,
        val listVersion: Long,
        val reason: String?,
    ) {
        companion object {
            fun trusted(key: PublicKey, version: Long) = Resolution(Outcome.TRUSTED, key, version, null)
            fun untrusted(reason: String) = Resolution(Outcome.UNTRUSTED, null, -1, reason)
            fun stale(reason: String) = Resolution(Outcome.STALE, null, -1, reason)
        }
    }

    fun resolve(
        thumbprint: ByteArray,
        listUri: String,
        cache: TrustListCache,
        listSignerKey: PublicKey,
    ): Resolution {
        val token = cache.read(listUri)
            ?: return Resolution.stale("no cached trust list for $listUri")

        val v = verifier.verify(token, listUri, listSignerKey)
        if (!v.valid) {
            // Freshness failures degrade to STALE (a refresh problem); integrity failures are
            // UNTRUSTED (an attack surface). Same split as the server.
            return if (v.reason?.contains("expired") == true) {
                Resolution.stale(v.reason)
            } else {
                Resolution.untrusted(v.reason ?: "trust list failed verification")
            }
        }
        val list = requireNotNull(v.list)
        val age = Duration.between(requireNotNull(v.issuedAt), clock.instant())
        if (age.seconds > list.maxStalenessSeconds) {
            return Resolution.stale(
                "cached trust list is ${age.seconds}s old, exceeding its signed " +
                    "max-staleness of ${list.maxStalenessSeconds}s"
            )
        }
        val key = list.keyFor(thumbprint)
            ?: return Resolution.untrusted("issuer thumbprint is not anchored in the trust list")
        return Resolution.trusted(key, list.version)
    }
}
