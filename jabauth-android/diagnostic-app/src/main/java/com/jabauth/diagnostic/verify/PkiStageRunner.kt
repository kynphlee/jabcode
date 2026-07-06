package com.jabauth.diagnostic.verify

import com.jabauth.client.pki.CertificateChainValidator
import com.jabauth.client.pki.TrustStoreManager
import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * The PKI verification stage.
 *
 * When a real [validator] and a certificate [extractChain] are wired (Phase 6), it does real X.509 path
 * validation and renders the actual chain. Otherwise — no chain in the payload, or no validator — it falls
 * back to the honest interim [StageState.WARN] / [RevocationStatus.UNKNOWN_OFFLINE].
 *
 * **Offline posture (open-Q3 + Principle F):** revocation (OCSP/CRL) needs the network, so offline it is
 * *indeterminate*, never confirmed. A chain that validates to a *trusted anchor* is therefore still
 * [StageState.WARN] on-device — trusted, but revocation unconfirmed — which drives `UNTRUSTED`. The device
 * pre-checks; the server confirms `VALID` and lands `VERIFIED`. Hard integrity failures (expired, broken
 * signature linkage) are [StageState.FAIL].
 */
class PkiStageRunner(
    private val extractChain: (DecodedSymbol) -> List<X509Certificate>? = { null },
    private val validator: CertificateChainValidator? = null,
    private val trustStore: TrustStoreManager? = null,
) : VerificationOrchestrator.StageRunner {

    override fun run(symbol: DecodedSymbol): StageResult {
        val chain = extractChain(symbol)
        val validator = this.validator
        if (chain.isNullOrEmpty() || validator == null) return interim()

        val detail = CertChainDetail(
            nodes = chain.map { it.toCertNode() },
            revocation = RevocationInfo("OCSP + CRL", RevocationStatus.UNKNOWN_OFFLINE, checkedLabel = null),
        )

        // Hard integrity failures first: an expired cert, or a broken issuer-signature link.
        chain.firstOrNull { !validator.isNotExpired(it) }?.let {
            return fail("Certificate expired: ${it.subjectX500Principal.name}", detail)
        }
        for (i in 0 until chain.size - 1) {
            if (!validator.verifySignature(chain[i], chain[i + 1])) {
                return fail("Broken chain: ${chain[i].subjectX500Principal.name} is not signed by its issuer.", detail)
            }
        }

        // Integrity OK. Trusted anchor or not, revocation is indeterminate offline → WARN (UNTRUSTED, amber).
        val trusted = validator.validateChain(chain).isValid
        val reason = if (trusted) {
            "Chain validates to a trusted anchor; revocation is indeterminate offline (UNKNOWN·offline) — " +
                "the server confirms revocation authoritatively."
        } else {
            "Chain is internally valid but its root is not in the trust store (untrusted anchor)."
        }
        return StageResult(
            VerificationStage.PKI, StageState.WARN, reason = reason,
            detail = detail.copy(reachedTrustedAnchor = trusted),
        )
    }

    private fun interim() = StageResult(
        VerificationStage.PKI, StageState.WARN,
        reason = "No certificate chain to validate (no v2 TRUST_CHAIN, or no on-device validator wired); " +
            "trust anchor and revocation are indeterminate offline.",
        detail = CertChainDetail(emptyList(), RevocationInfo("OCSP + CRL", RevocationStatus.UNKNOWN_OFFLINE, null)),
    )

    private fun fail(reason: String, detail: CertChainDetail) =
        StageResult(VerificationStage.PKI, StageState.FAIL, reason = reason, detail = detail)

    private fun X509Certificate.toCertNode(): CertNode {
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(encoded).joinToString(":") { "%02X".format(it) }
        val inStore = trustStore?.getAllTrustedCAs()?.any { it == this } ?: false
        return CertNode(
            subject = subjectX500Principal.name,
            issuer = issuerX500Principal.name,
            serial = serialNumber.toString(16).uppercase(),
            validFrom = notBefore.toInstant().toString(),
            validTo = notAfter.toInstant().toString(),
            keyUsage = if (basicConstraints >= 0) "CA (keyCertSign)" else "end-entity (digitalSignature)",
            sha256Fingerprint = fingerprint,
            inTrustStore = inStore,
        )
    }
}
