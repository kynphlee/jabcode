package com.jabauth.client.pki

import com.jabauth.core.validation.ValidationResult
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Production [CertificateChainValidator] — real X.509 path validation via JCA `CertPathValidator` (PKIX)
 * against the anchors in a [TrustStoreManager]. Replaces the interface-only / test-double PKI: builds and
 * validates the chain (issuer linkage, validity windows, basic constraints, key usage) up to a configured
 * trust anchor, so a chain to an *unknown* root is rejected — the C2PA/mDL "trusted vs merely valid"
 * distinction (Principle B).
 *
 * Revocation (OCSP/CRL) requires the network and is left to the online path; offline it is indeterminate,
 * which the PKI stage surfaces as `UNKNOWN_OFFLINE` — this validator does not falsely claim "not revoked".
 * PKIX revocation is therefore disabled here (handled out of band via [checkRevocation]).
 *
 * @param trustStore the device's trust anchors (verification is local/offline against exactly these).
 * @param clock injectable for deterministic validity-window tests.
 */
class CertificateChainValidatorImpl(
    private val trustStore: TrustStoreManager,
    private val clock: () -> Date = { Date() },
) : CertificateChainValidator {

    override fun validateChain(certificateChain: List<X509Certificate>): ValidationResult {
        if (certificateChain.isEmpty()) return invalid("Certificate chain is empty", "EMPTY_CHAIN")
        val anchors = trustStore.getAllTrustedCAs().map { TrustAnchor(it, null) }.toSet()
        if (anchors.isEmpty()) return invalid("No trust anchors configured", "NO_ANCHORS")
        return try {
            val anchorCerts = anchors.map { it.trustedCert }.toSet()
            // A CertPath must not include a trust anchor — drop any anchor certs from the presented chain.
            val pathCerts = certificateChain.filterNot { it in anchorCerts }
            if (pathCerts.isEmpty()) return ValidationResult(isValid = true) // chain was a bare anchor
            val certPath = CertificateFactory.getInstance("X.509").generateCertPath(pathCerts)
            val params = PKIXParameters(anchors).apply {
                isRevocationEnabled = false // revocation is out of band (offline → UNKNOWN, see checkRevocation)
                date = clock()
            }
            CertPathValidator.getInstance("PKIX").validate(certPath, params)
            ValidationResult(isValid = true)
        } catch (e: CertPathValidatorException) {
            invalid("Chain validation failed: ${e.message}", "INVALID_CHAIN")
        } catch (e: Exception) {
            invalid("Chain validation error: ${e.message}", "VALIDATION_ERROR")
        }
    }

    override fun validateCertificate(
        certificate: X509Certificate,
        trustedCAs: List<X509Certificate>,
    ): ValidationResult {
        if (!isNotExpired(certificate)) return invalid("Certificate expired", "EXPIRED")
        val trusted = trustedCAs.any { runCatching { certificate.verify(it.publicKey) }.isSuccess }
        return if (trusted) ValidationResult(isValid = true)
        else invalid("Certificate signed by an untrusted CA (not a configured anchor)", "UNTRUSTED")
    }

    override fun isNotExpired(certificate: X509Certificate): Boolean =
        runCatching { certificate.checkValidity(clock()) }.isSuccess

    override fun verifySignature(certificate: X509Certificate, issuerCertificate: X509Certificate): Boolean =
        runCatching { certificate.verify(issuerCertificate.publicKey) }.isSuccess

    override fun checkRevocation(certificate: X509Certificate): Boolean {
        // Offline: OCSP/CRL is unreachable, so revocation is indeterminate — NOT proven un-revoked. We do
        // not assert a value here; the PKI stage renders UNKNOWN_OFFLINE. An online path would attach a
        // PKIXRevocationChecker. Returning true means only "no local evidence of revocation".
        return true
    }

    private fun invalid(message: String, code: String) =
        ValidationResult(isValid = false, errorMessage = message, errorCode = code)
}
