package com.jabauth.client.trust

import com.jabauth.core.storage.SecureStorage

/**
 * The device's last-known-good trust-list cache over [SecureStorage] (EncryptedSharedPreferences
 * in production; any fake in JVM tests). The verify path calls [read] — storage only, never
 * network; refresh writes via [refresh]/[seed], out-of-band. A failed refresh keeps the old
 * copy: staleness is judged by the resolver's signed floor, never by cache eviction. Parity
 * with the server's `CachingTrustListSource`.
 */
class TrustListCache(private val storage: SecureStorage) {

    /** The origin fetcher (HTTP/CDN) used ONLY by [refresh]; throws when unreachable. */
    fun interface Origin {
        @Throws(Exception::class)
        fun fetch(listUri: String): String
    }

    /** The cached token for [listUri], or null when the device has never seen the list. */
    fun read(listUri: String): String? = storage.getString(key(listUri))

    /**
     * Pull a fresh copy from [origin] into the cache; keep last-known-good on failure.
     *
     * @return true when the cache was updated
     */
    fun refresh(listUri: String, origin: Origin): Boolean = runCatching {
        val fresh = origin.fetch(listUri)
        if (fresh.isNotBlank()) {
            storage.putString(key(listUri), fresh)
            true
        } else {
            false
        }
    }.getOrDefault(false)

    /** Seed directly (provisioning: the app ships/imports the current list). */
    fun seed(listUri: String, token: String) = storage.putString(key(listUri), token)

    private fun key(listUri: String) = "trust_list_cache:$listUri"
}
