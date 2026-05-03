package com.jabauth.client.pki

import com.jabauth.core.validation.ValidationResult
import java.security.cert.X509Certificate

/**
 * Validates X.509 certificate chains against trusted Certificate Authorities
 * 
 * Performs comprehensive validation including:
 * - Certificate chain verification
 * - Expiration checking
 * - Revocation status (CRL/OCSP)
 * - Trust anchor validation
 * 
 * @see TrustStoreManager for CA certificate management
 */
interface CertificateChainValidator {
    
    /**
     * Validate a complete certificate chain
     * 
     * @param certificateChain Chain of certificates from leaf to root
     * @return ValidationResult with success/failure and detailed errors
     */
    fun validateChain(certificateChain: List<X509Certificate>): ValidationResult
    
    /**
     * Validate a single certificate against trust store
     * 
     * @param certificate Certificate to validate
     * @param trustedCAs List of trusted CA certificates
     * @return ValidationResult with success/failure
     */
    fun validateCertificate(
        certificate: X509Certificate,
        trustedCAs: List<X509Certificate>
    ): ValidationResult
    
    /**
     * Check if certificate is expired
     * 
     * @param certificate Certificate to check
     * @return true if valid (not expired), false otherwise
     */
    fun isNotExpired(certificate: X509Certificate): Boolean
    
    /**
     * Verify certificate signature against issuer
     * 
     * @param certificate Certificate to verify
     * @param issuerCertificate Issuer's certificate
     * @return true if signature is valid
     */
    fun verifySignature(
        certificate: X509Certificate,
        issuerCertificate: X509Certificate
    ): Boolean
    
    /**
     * Check certificate revocation status
     * 
     * @param certificate Certificate to check
     * @return true if certificate is not revoked
     */
    fun checkRevocation(certificate: X509Certificate): Boolean
}
