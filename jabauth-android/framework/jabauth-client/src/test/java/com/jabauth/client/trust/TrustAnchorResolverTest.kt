package com.jabauth.client.trust

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.common.truth.Truth.assertThat
import com.jabauth.client.jwt.Jwk
import com.jabauth.core.storage.SecureStorage
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date

/**
 * Device-side trust-list resolution (WP-B mobile parity): the resolver's three verdicts —
 * TRUSTED / UNTRUSTED / STALE, never a silent "valid" — against a cached signed list, with
 * the signed max-staleness floor enforced even inside `exp`, and the cache keeping
 * last-known-good on origin failure.
 */
class TrustAnchorResolverTest {

    private companion object {
        const val LIST_URI = "https://trust.jabauth.example/trust-list.jwt"
    }

    private val listSigner = ecKeyPair()
    private val issuerA = ecKeyPair()
    private val issuerB = ecKeyPair()
    private val resolver = TrustAnchorResolver()

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

    /** In-memory SecureStorage fake — the cache contract, no Android. */
    private class FakeStorage : SecureStorage {
        private val map = HashMap<String, Any>()
        override fun putString(key: String, value: String) { map[key] = value }
        override fun getString(key: String, defaultValue: String?): String? =
            map[key] as? String ?: defaultValue
        override fun putInt(key: String, value: Int) { map[key] = value }
        override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            map[key] as? Boolean ?: defaultValue
        override fun remove(key: String) { map.remove(key) }
        override fun clear() { map.clear() }
        override fun contains(key: String): Boolean = map.containsKey(key)
    }

    /** Mint a signed trust-list token the way the server's registry does. */
    private fun issueList(
        signer: KeyPair,
        anchorsOf: List<KeyPair>,
        maxStalenessSeconds: Long = Duration.ofHours(24).seconds,
        ttl: Duration = Duration.ofHours(1),
        version: Long = 1,
    ): String {
        val anchors = anchorsOf.map {
            mapOf(
                "kid" to Jwk.thumbprintSha256Base64Url(it.public),
                "jwk" to Jwk.toMap(it.public),
                "policy" to "verification-event-issuer",
            )
        }
        val now = Instant.now()
        return JWT.create()
            .withHeader(mapOf("typ" to TrustListVerifier.TYP))
            .withSubject(LIST_URI)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plus(ttl)))
            .withClaim(
                TrustListVerifier.CLAIM_TRUST_LIST,
                mapOf(
                    TrustList.CLAIM_VER to version,
                    TrustList.CLAIM_MAX_STALENESS to maxStalenessSeconds,
                    TrustList.CLAIM_ANCHORS to anchors,
                ),
            )
            .sign(
                Algorithm.ECDSA256(
                    signer.public as ECPublicKey,
                    signer.private as ECPrivateKey,
                )
            )
    }

    private fun seededCache(token: String): TrustListCache =
        TrustListCache(FakeStorage()).apply { seed(LIST_URI, token) }

    @Test
    fun trusted_freshCachedListWithAnchoredIssuer() {
        val cache = seededCache(issueList(listSigner, listOf(issuerA, issuerB)))
        val r = resolver.resolve(
            Jwk.thumbprintSha256(issuerA.public), LIST_URI, cache, listSigner.public
        )
        assertThat(r.outcome).isEqualTo(TrustAnchorResolver.Outcome.TRUSTED)
        assertThat(r.issuerKey!!.encoded).isEqualTo(issuerA.public.encoded)
        assertThat(r.listVersion).isEqualTo(1)
    }

    @Test
    fun untrusted_issuerNotAnchored_theDelistCase() {
        val cache = seededCache(issueList(listSigner, listOf(issuerA)))
        val r = resolver.resolve(
            Jwk.thumbprintSha256(issuerB.public), LIST_URI, cache, listSigner.public
        )
        assertThat(r.outcome).isEqualTo(TrustAnchorResolver.Outcome.UNTRUSTED)
        assertThat(r.issuerKey).isNull()
    }

    @Test
    fun untrusted_listSignedByTheWrongKey() {
        val forger = ecKeyPair()
        val cache = seededCache(issueList(forger, listOf(issuerB)))
        val r = resolver.resolve(
            Jwk.thumbprintSha256(issuerB.public), LIST_URI, cache, listSigner.public
        )
        assertThat(r.outcome).isEqualTo(TrustAnchorResolver.Outcome.UNTRUSTED)
    }

    @Test
    fun untrusted_anchorWhoseKidLiesAboutItsJwk() {
        // kid claims issuerA, JWK is issuerB's: the self-certifying check must refuse.
        val lying = mapOf(
            "kid" to Jwk.thumbprintSha256Base64Url(issuerA.public),
            "jwk" to Jwk.toMap(issuerB.public),
        )
        val now = Instant.now()
        val token = JWT.create()
            .withSubject(LIST_URI)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plus(Duration.ofHours(1))))
            .withClaim(
                TrustListVerifier.CLAIM_TRUST_LIST,
                mapOf(
                    TrustList.CLAIM_VER to 1L,
                    TrustList.CLAIM_MAX_STALENESS to 86400L,
                    TrustList.CLAIM_ANCHORS to listOf(lying),
                ),
            )
            .sign(
                Algorithm.ECDSA256(
                    listSigner.public as ECPublicKey,
                    listSigner.private as ECPrivateKey,
                )
            )
        val r = resolver.resolve(
            Jwk.thumbprintSha256(issuerA.public), LIST_URI, seededCache(token), listSigner.public
        )
        assertThat(r.outcome).isEqualTo(TrustAnchorResolver.Outcome.UNTRUSTED)
    }

    @Test
    fun stale_noCachedList() {
        val r = resolver.resolve(
            Jwk.thumbprintSha256(issuerA.public), LIST_URI,
            TrustListCache(FakeStorage()), listSigner.public
        )
        assertThat(r.outcome).isEqualTo(TrustAnchorResolver.Outcome.STALE)
    }

    @Test
    fun stale_cacheOlderThanTheSignedFloor_evenBeforeExp() {
        // max_staleness 60s, exp 1h: a clock 10 minutes ahead sits inside exp but past the
        // signed floor — STALE, never silently valid.
        val cache = seededCache(
            issueList(listSigner, listOf(issuerA), maxStalenessSeconds = 60, ttl = Duration.ofHours(1))
        )
        val aged = TrustAnchorResolver(
            clock = Clock.fixed(Instant.now().plus(Duration.ofMinutes(10)), ZoneOffset.UTC)
        )
        val r = aged.resolve(
            Jwk.thumbprintSha256(issuerA.public), LIST_URI, cache, listSigner.public
        )
        assertThat(r.outcome).isEqualTo(TrustAnchorResolver.Outcome.STALE)
        assertThat(r.reason).contains("max-staleness")
    }

    @Test
    fun cacheKeepsLastKnownGoodWhenOriginFails() {
        val cache = TrustListCache(FakeStorage())
        assertThat(cache.refresh(LIST_URI) { "good-token" }).isTrue()
        assertThat(cache.refresh(LIST_URI) { throw IllegalStateException("origin down") }).isFalse()
        assertThat(cache.read(LIST_URI)).isEqualTo("good-token") // never evicted
    }

    @Test
    fun delistTakesEffectOnRefresh() {
        val cache = TrustListCache(FakeStorage())
        cache.seed(LIST_URI, issueList(listSigner, listOf(issuerA, issuerB)))
        val bPrint = Jwk.thumbprintSha256(issuerB.public)

        // Within the staleness window the old cache still trusts B — documented, expected.
        assertThat(resolver.resolve(bPrint, LIST_URI, cache, listSigner.public).outcome)
            .isEqualTo(TrustAnchorResolver.Outcome.TRUSTED)

        // The registry delists B (v2); the device's next refresh flips the verdict.
        cache.refresh(LIST_URI) { issueList(listSigner, listOf(issuerA), version = 2) }
        assertThat(resolver.resolve(bPrint, LIST_URI, cache, listSigner.public).outcome)
            .isEqualTo(TrustAnchorResolver.Outcome.UNTRUSTED)
        assertThat(
            resolver.resolve(
                Jwk.thumbprintSha256(issuerA.public), LIST_URI, cache, listSigner.public
            ).outcome
        ).isEqualTo(TrustAnchorResolver.Outcome.TRUSTED)
    }
}
