package com.jabauth.core.validation

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Production implementation of CertificateValidator
 *
 * Uses Java's built-in CertificateFactory for X.509 certificate validation.
 * Thread-safe and suitable for production use.
 */
class CertificateValidatorImpl : CertificateValidator {

    override fun validateFormat(certificateBytes: ByteArray): ValidationResult {
        return try {
            parseCertificate(certificateBytes)
            ValidationResult.success()
        } catch (e: Exception) {
            ValidationResult.failure(
                "Invalid certificate format: ${e.message}",
                "INVALID_FORMAT"
            )
        }
    }

    override fun parseCertificate(certificateBytes: ByteArray): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        val inputStream = ByteArrayInputStream(certificateBytes)
        return factory.generateCertificate(inputStream) as X509Certificate
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

    override fun getSubjectDN(certificate: X509Certificate): String {
        return certificate.subjectX500Principal.name
    }

    override fun getIssuerDN(certificate: X509Certificate): String {
        return certificate.issuerX500Principal.name
    }

    override fun isSelfSigned(certificate: X509Certificate): Boolean {
        return getSubjectDN(certificate) == getIssuerDN(certificate)
    }
}
