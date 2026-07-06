package com.jabauth.diagnostic.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.security.cert.X509Certificate

/** In-memory fake so the repository's hydrate/import/remove logic is unit-tested without DataStore/Android. */
private class FakeAnchorPersistence : AnchorPersistence {
    val map = LinkedHashMap<String, X509Certificate>()
    private val count = MutableStateFlow(0)
    override val countFlow: Flow<Int> = count
    override suspend fun add(alias: String, cert: X509Certificate) { map[alias] = cert; count.value = map.size }
    override suspend fun remove(alias: String) { map.remove(alias); count.value = map.size }
    override suspend fun list(): List<Pair<String, X509Certificate>> = map.entries.map { it.key to it.value }
}

class TrustAnchorRepositoryTest {

    @Test fun `ensureHydrated loads persisted anchors into the live store`() = runBlocking<Unit> {
        val persistence = FakeAnchorPersistence()
        persistence.add("a", TestCerts.selfSignedEc("Anchor A", serial = 1))
        persistence.add("b", TestCerts.selfSignedEc("Anchor B", serial = 2))
        val repo = TrustAnchorRepository(persistence)
        assertThat(repo.store.getAllTrustedCAs()).isEmpty() // not hydrated yet
        repo.ensureHydrated()
        assertThat(repo.store.getAllTrustedCAs()).hasSize(2)
    }

    @Test fun `ensureHydrated is idempotent — a second call does not double-load`() = runBlocking<Unit> {
        val persistence = FakeAnchorPersistence()
        persistence.add("a", TestCerts.selfSignedEc("Anchor A"))
        val repo = TrustAnchorRepository(persistence)
        repo.ensureHydrated()
        repo.ensureHydrated()
        assertThat(repo.store.getAllTrustedCAs()).hasSize(1)
    }

    @Test fun `import writes through to both the live store and persistence (survives a restart)`() = runBlocking<Unit> {
        val persistence = FakeAnchorPersistence()
        val repo = TrustAnchorRepository(persistence)
        val cert = TestCerts.selfSignedEc("Imported")
        repo.import(cert)
        assertThat(repo.store.getAllTrustedCAs()).containsExactly(cert)
        assertThat(persistence.map.values).containsExactly(cert)
    }

    @Test fun `remove clears the anchor from both the store and persistence`() = runBlocking<Unit> {
        val persistence = FakeAnchorPersistence()
        val repo = TrustAnchorRepository(persistence)
        val cert = TestCerts.selfSignedEc("Doomed")
        val alias = TrustAnchorRepository.fingerprint(cert)
        repo.import(cert, alias)
        repo.remove(alias)
        assertThat(repo.store.getAllTrustedCAs()).isEmpty()
        assertThat(persistence.map).isEmpty()
    }

    @Test fun `fingerprint is a stable SHA-256 hex that differs across certs`() {
        val a = TestCerts.selfSignedEc("A", serial = 1)
        val b = TestCerts.selfSignedEc("B", serial = 2)
        assertThat(TrustAnchorRepository.fingerprint(a)).isEqualTo(TrustAnchorRepository.fingerprint(a))
        assertThat(TrustAnchorRepository.fingerprint(a)).isNotEqualTo(TrustAnchorRepository.fingerprint(b))
        assertThat(TrustAnchorRepository.fingerprint(a)).matches("[0-9a-f]{64}")
    }
}
