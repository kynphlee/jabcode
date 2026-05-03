package com.jabauth.client.pki

import java.security.cert.X509Certificate

/**
 * Manages trusted Certificate Authority (CA) certificates
 * 
 * Provides storage and retrieval of trusted CA certificates used
 * for certificate chain validation.
 */
interface TrustStoreManager {
    
    /**
     * Add a trusted CA certificate
     * 
     * @param alias Unique identifier for the certificate
     * @param certificate CA certificate to trust
     * @return true if successfully added
     */
    fun addTrustedCA(alias: String, certificate: X509Certificate): Boolean
    
    /**
     * Remove a trusted CA certificate
     * 
     * @param alias Identifier of the certificate to remove
     * @return true if successfully removed
     */
    fun removeTrustedCA(alias: String): Boolean
    
    /**
     * Get a trusted CA certificate by alias
     * 
     * @param alias Identifier of the certificate
     * @return Certificate if found, null otherwise
     */
    fun getTrustedCA(alias: String): X509Certificate?
    
    /**
     * Get all trusted CA certificates
     * 
     * @return List of all trusted CA certificates
     */
    fun getAllTrustedCAs(): List<X509Certificate>
    
    /**
     * Check if a CA is trusted
     * 
     * @param alias Identifier of the certificate
     * @return true if the CA is trusted
     */
    fun isTrusted(alias: String): Boolean
    
    /**
     * Clear all trusted CAs
     * 
     * @return true if successfully cleared
     */
    fun clear(): Boolean
}
