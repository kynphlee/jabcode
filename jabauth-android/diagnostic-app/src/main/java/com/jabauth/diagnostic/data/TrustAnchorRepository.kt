package com.jabauth.diagnostic.data

import android.content.Context
import com.jabauth.client.pki.TrustStoreManager
import com.jabauth.client.pki.TrustStoreManagerImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * The single, process-wide owner of the device's trust anchors (A′ Stage 2). It holds the in-memory
 * [TrustStoreManager] the verify path validates against AND writes every change through [AnchorPersistence],
 * so the anchor set survives restarts and is shared by the import UI (writes) and every scan (reads).
 *
 * Obtain the app-scoped instance via [get]; call [ensureHydrated] (idempotent) from a ViewModel's init to
 * load persisted anchors into [store] on first use.
 */
class TrustAnchorRepository internal constructor(private val persistence: AnchorPersistence) {

    /** The live anchor set the [com.jabauth.client.pki.CertificateChainValidator] validates against. */
    val store: TrustStoreManager = TrustStoreManagerImpl()

    /** The persisted anchor count (drives the Settings "N anchors" row and the empty-trust-store banner). */
    val countFlow: Flow<Int> get() = persistence.countFlow

    private val mutex = Mutex()

    @Volatile
    private var hydrated = false

    /** Idempotently load the persisted anchors into [store]. Safe to call from every ViewModel's init. */
    suspend fun ensureHydrated() {
        if (hydrated) return
        mutex.withLock {
            if (hydrated) return
            persistence.list().forEach { (alias, cert) -> store.addTrustedCA(alias, cert) }
            hydrated = true
        }
    }

    /** Import an anchor, write-through to both the live [store] and persistence. Alias defaults to the fingerprint. */
    suspend fun import(cert: X509Certificate, alias: String = fingerprint(cert)) {
        store.addTrustedCA(alias, cert)
        persistence.add(alias, cert)
    }

    /** Remove an anchor from both the live [store] and persistence. */
    suspend fun remove(alias: String) {
        store.removeTrustedCA(alias)
        persistence.remove(alias)
    }

    companion object {
        @Volatile
        private var instance: TrustAnchorRepository? = null

        /** The app-scoped singleton, backed by the real DataStore persistence ([TrustAnchorStore]). */
        fun get(context: Context): TrustAnchorRepository =
            instance ?: synchronized(this) {
                instance ?: TrustAnchorRepository(TrustAnchorStore(context.applicationContext)).also { instance = it }
            }

        /** A stable alias for an anchor: its SHA-256 fingerprint (lowercase hex). */
        fun fingerprint(cert: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
