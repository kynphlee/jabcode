package com.jabauth.client.pki

import java.security.cert.X509Certificate

/**
 * Test implementation of TrustStoreManager
 * 
 * Uses in-memory storage for unit testing.
 * Production code uses SecureStorage from :core module.
 */
class TestTrustStoreManagerImpl : TrustStoreManager {
    
    private val trustedCAs = mutableMapOf<String, X509Certificate>()
    
    override fun addTrustedCA(alias: String, certificate: X509Certificate): Boolean {
        return try {
            trustedCAs[alias] = certificate
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun removeTrustedCA(alias: String): Boolean {
        return trustedCAs.remove(alias) != null
    }
    
    override fun getTrustedCA(alias: String): X509Certificate? {
        return trustedCAs[alias]
    }
    
    override fun getAllTrustedCAs(): List<X509Certificate> {
        return trustedCAs.values.toList()
    }
    
    override fun isTrusted(alias: String): Boolean {
        return trustedCAs.containsKey(alias)
    }
    
    override fun clear(): Boolean {
        return try {
            trustedCAs.clear()
            true
        } catch (e: Exception) {
            false
        }
    }
}
