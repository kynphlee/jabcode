package com.jabauth.core.validation

import java.security.cert.X509Certificate

/**
 * Certificate validator for X.509 certificates
 *
 * Provides validation of certificate format, expiration, and basic properties.
 * Used for PKI operations in JABAuth authentication flow.
 *
 * Usage:
 * ```
 * val validator = CertificateValidatorImpl()
 * val result = validator.validateFormat(certBytes)
 * if (result.isValid) {
 *     val cert = validator.parseCertificate(certBytes)
 *     if (validator.isNotExpired(cert)) {
 *         // Proceed with authentication
 *     }
 * }
 * ```
 */
interface CertificateValidator {

    /**
     * Validate X.509 certificate format
     *
     * Checks if the provided bytes are a valid DER-encoded X.509 certificate.
     *
     * @param certificateBytes DER-encoded certificate bytes
     * @return ValidationResult indicating success or failure with error details
     */
    fun validateFormat(certificateBytes: ByteArray): ValidationResult

    /**
     * Parse DER-encoded certificate bytes to X509Certificate
     *
     * @param certificateBytes DER-encoded certificate bytes
     * @return X509Certificate instance
     * @throws java.security.cert.CertificateException if parsing fails
     */
    fun parseCertificate(certificateBytes: ByteArray): X509Certificate

    /**
     * Check if certificate is not expired
     *
     * Validates certificate is within its validity period (notBefore to notAfter).
     *
     * @param certificate X509Certificate to check
     * @return true if certificate is currently valid (not expired, not not-yet-valid)
     */
    fun isNotExpired(certificate: X509Certificate): Boolean

    /**
     * Extract subject distinguished name (DN) from certificate
     *
     * @param certificate X509Certificate to extract DN from
     * @return Subject DN as string (e.g., "CN=John Doe, O=Example Corp")
     */
    fun getSubjectDN(certificate: X509Certificate): String

    /**
     * Extract issuer distinguished name (DN) from certificate
     *
     * @param certificate X509Certificate to extract DN from
     * @return Issuer DN as string
     */
    fun getIssuerDN(certificate: X509Certificate): String

    /**
     * Check if certificate is self-signed
     *
     * A certificate is self-signed if its issuer DN matches its subject DN.
     *
     * @param certificate X509Certificate to check
     * @return true if certificate is self-signed
     */
    fun isSelfSigned(certificate: X509Certificate): Boolean
}
