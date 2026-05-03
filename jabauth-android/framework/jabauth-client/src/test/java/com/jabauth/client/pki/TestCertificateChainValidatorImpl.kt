package com.jabauth.client.pki

import com.jabauth.core.validation.ValidationResult
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Test implementation of CertificateChainValidator
 * 
 * Used for unit testing without Android dependencies.
 * Production code uses CertificateChainValidatorImpl.
 */
class TestCertificateChainValidatorImpl : CertificateChainValidator {
    
    override fun validateChain(certificateChain: List<X509Certificate>): ValidationResult {
        if (certificateChain.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Certificate chain is empty"
            )
        }
        
        // Check each certificate in the chain
        for (i in certificateChain.indices) {
            val cert = certificateChain[i]
            
            // Check expiration
            if (!isNotExpired(cert)) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Certificate expired: ${cert.subjectX500Principal.name}"
                )
            }
            
            // Verify signature against next certificate in chain (if not root)
            if (i < certificateChain.size - 1) {
                val issuerCert = certificateChain[i + 1]
                if (!verifySignature(cert, issuerCert)) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Invalid signature for certificate: ${cert.subjectX500Principal.name}"
                    )
                }
            }
        }
        
        return ValidationResult(isValid = true)
    }
    
    override fun validateCertificate(
        certificate: X509Certificate,
        trustedCAs: List<X509Certificate>
    ): ValidationResult {
        // Check if certificate is signed by any trusted CA
        val isTrusted = trustedCAs.any { ca ->
            try {
                certificate.verify(ca.publicKey)
                true
            } catch (e: Exception) {
                false
            }
        }
        
        return if (isTrusted) {
            ValidationResult(isValid = true)
        } else {
            ValidationResult(
                isValid = false,
                errorMessage = "Certificate not signed by trusted CA"
            )
        }
    }
    
    override fun isNotExpired(certificate: X509Certificate): Boolean {
        return try {
            val now = Date()
            certificate.checkValidity(now)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun verifySignature(
        certificate: X509Certificate,
        issuerCertificate: X509Certificate
    ): Boolean {
        return try {
            certificate.verify(issuerCertificate.publicKey)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun checkRevocation(certificate: X509Certificate): Boolean {
        // Stub implementation - always returns true (not revoked)
        // Production implementation would check CRL/OCSP
        return true
    }
}
