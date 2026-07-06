package com.jabauth.diagnostic.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.cert.X509Certificate

/**
 * The persistence seam for trust anchors, abstracted so [TrustAnchorRepository] is unit-tested against an
 * in-memory fake (no Android). The DataStore implementation is [TrustAnchorStore].
 */
interface AnchorPersistence {
    suspend fun add(alias: String, cert: X509Certificate)
    suspend fun remove(alias: String)
    suspend fun list(): List<Pair<String, X509Certificate>>

    /** Reactive count of persisted anchors — drives the Settings row and the empty-trust-store banner. */
    val countFlow: Flow<Int>
}

private val Context.trustAnchorDataStore: DataStore<Preferences> by preferencesDataStore(name = "trust_anchors")
private const val ANCHOR_PREFIX = "anchor::"

/**
 * DataStore-backed [AnchorPersistence]: each anchor is one preference keyed `anchor::<alias>` whose value is
 * the hex-encoded DER cert ([CertCodec]). A store separate from settings, so anchors survive process death.
 */
class TrustAnchorStore(private val context: Context) : AnchorPersistence {

    override suspend fun add(alias: String, cert: X509Certificate) {
        context.trustAnchorDataStore.edit { it[stringPreferencesKey(ANCHOR_PREFIX + alias)] = CertCodec.encode(cert) }
    }

    override suspend fun remove(alias: String) {
        context.trustAnchorDataStore.edit { it.remove(stringPreferencesKey(ANCHOR_PREFIX + alias)) }
    }

    override suspend fun list(): List<Pair<String, X509Certificate>> =
        context.trustAnchorDataStore.data.first().asMap().entries
            .filter { it.key.name.startsWith(ANCHOR_PREFIX) }
            .mapNotNull { entry ->
                val cert = CertCodec.decode(entry.value as? String ?: return@mapNotNull null) ?: return@mapNotNull null
                entry.key.name.removePrefix(ANCHOR_PREFIX) to cert
            }

    override val countFlow: Flow<Int> = context.trustAnchorDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs.asMap().keys.count { it.name.startsWith(ANCHOR_PREFIX) } }
}
