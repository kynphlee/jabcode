package com.jabauth.client.pki

import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory production [TrustStoreManager]: the set of X.509 trust anchors the device trusts, keyed by
 * alias. Verification is local and offline against exactly these anchors (mDL/ISO 18013-5 model). Backed
 * by a [ConcurrentHashMap] so reads from the verify path and writes from Settings don't race.
 *
 * (A persistent, `KeyStore`-backed variant can layer on top for import/export; this covers the on-device
 * verify need — a fast, thread-safe anchor set.)
 */
class TrustStoreManagerImpl : TrustStoreManager {

    private val anchors = ConcurrentHashMap<String, X509Certificate>()

    override fun addTrustedCA(alias: String, certificate: X509Certificate): Boolean {
        anchors[alias] = certificate
        return true
    }

    override fun removeTrustedCA(alias: String): Boolean = anchors.remove(alias) != null

    override fun getTrustedCA(alias: String): X509Certificate? = anchors[alias]

    override fun getAllTrustedCAs(): List<X509Certificate> = anchors.values.toList()

    override fun isTrusted(alias: String): Boolean = anchors.containsKey(alias)

    override fun clear(): Boolean {
        anchors.clear()
        return true
    }
}
