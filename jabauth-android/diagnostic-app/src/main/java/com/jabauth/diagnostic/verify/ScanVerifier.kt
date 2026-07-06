package com.jabauth.diagnostic.verify

import com.jabauth.client.abe.ABEPolicy
import com.jabauth.client.abe.AbeEnvelope
import com.jabauth.client.abe.AbePolicyParser
import com.jabauth.client.pki.CertificateChainValidatorImpl
import com.jabauth.client.pki.TrustStoreManager
import com.jabauth.client.pki.TrustStoreManagerImpl
import com.jabauth.client.v2.PayloadFormatV2
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.X509EncodedKeySpec

/**
 * Bridges a decoded scan into a [VerificationResult] via [CoaVerifier], parsing the on-device **Payload
 * Format v2** container to feed each stage its real input.
 *
 *  - Non-v2 payload → [TrustVerdict.NOT_A_COA] (the classic decode path is preserved).
 *  - v2 COA → every stage runs on real data pulled from the container:
 *     - `SDJWT_VC` → the **JWT stage** verifies the real SD-JWT VC.
 *     - `TRUST_CHAIN` → a DER X.509 **leaf certificate**: its public key verifies the JWT signature, and
 *       the same cert drives the **PKI stage**'s real path validation + rendered cert node (subject,
 *       serial, validity, key usage, SHA-256 fingerprint). A legacy bare-SPKI `TRUST_CHAIN` still yields
 *       the issuer key (no cert node) — backward compatible.
 *     - `ABE_SEALED` → the sealed envelope's cleartext access policy is parsed to an [ABEPolicy] so the
 *       **ABE stage** adjudicates it against the verifier's [verifierAttributes] (from Settings).
 *
 * The trust store is empty until anchors are imported, so a self-signed / unknown issuer lands honestly at
 * `UNTRUSTED` (trusted-anchor + offline-revocation → `WARN`). The on-device verdict is a pre-check — the
 * server verifies authoritatively (Principle F).
 */
class ScanVerifier(
    private val orchestrator: VerificationOrchestrator,
) {
    /** Convenience: build the real four-stage pipeline, sourcing the verifier's ABE attributes from [verifierAttributes]. */
    constructor(
        verifierAttributes: () -> Set<String> = { emptySet() },
        trustStore: TrustStoreManager = TrustStoreManagerImpl(),
    ) : this(defaultOrchestrator(verifierAttributes, trustStore))

    /** Verify a decoded symbol's raw [payload] bytes carrying its [decodeLatencyMs]. */
    fun verify(payload: ByteArray, decodeLatencyMs: Long): VerificationResult {
        val isV2 = PayloadFormatV2.isV2(payload)
        return orchestrator.verify(
            DecodeInput.Decoded(
                DecodedSymbol(
                    payload = payload,
                    isCoaFormat = isV2,
                    decodeLatencyMs = decodeLatencyMs,
                    formatProfile = FormatProfile(if (isV2) "v2" else "unknown", null),
                ),
            ),
        )
    }

    companion object {
        /** The bytes of a v2 section, or null if the payload is not a v2 COA / the section is absent. */
        internal fun v2Section(payload: ByteArray, type: PayloadFormatV2.SectionType): ByteArray? = runCatching {
            if (PayloadFormatV2.isV2(payload)) PayloadFormatV2.decode(payload).first(type) else null
        }.getOrNull()

        /** The `TRUST_CHAIN` section as an X.509 leaf certificate, or null if it is a bare SPKI / malformed. */
        internal fun parseLeafCertificate(trustChain: ByteArray): X509Certificate? = runCatching {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(trustChain)) as? X509Certificate
        }.getOrNull()

        /**
         * The issuer public key that verifies the SD-JWT: the leaf certificate's key when `TRUST_CHAIN`
         * carries a full cert, else the bare SPKI (legacy). Tries EC then RSA for the SPKI path.
         */
        internal fun resolveIssuerKey(trustChain: ByteArray): PublicKey? =
            parseLeafCertificate(trustChain)?.publicKey ?: parsePublicKey(trustChain)

        /** Parse an X.509 SubjectPublicKeyInfo (a legacy bare `TRUST_CHAIN`) into a public key (ES* or RS*). */
        internal fun parsePublicKey(spki: ByteArray): PublicKey? = runCatching {
            val spec = X509EncodedKeySpec(spki)
            runCatching { KeyFactory.getInstance("EC").generatePublic(spec) }.getOrNull()
                ?: KeyFactory.getInstance("RSA").generatePublic(spec)
        }.getOrNull()

        /** The CP-ABE access policy sealed in `ABE_SEALED`, parsed to the adjudicable AST (null if absent/malformed). */
        internal fun extractPolicy(payload: ByteArray): ABEPolicy? =
            AbeEnvelope.policyOf(v2Section(payload, PayloadFormatV2.SectionType.ABE_SEALED))
                ?.let { AbePolicyParser.parse(it) }

        private fun defaultOrchestrator(
            verifierAttributes: () -> Set<String>,
            trustStore: TrustStoreManager,
        ): VerificationOrchestrator {
            return CoaVerifier.orchestrator(
                extractToken = { sym -> v2Section(sym.payload, PayloadFormatV2.SectionType.SDJWT_VC)?.toString(Charsets.UTF_8) },
                resolveIssuerKey = { sym -> v2Section(sym.payload, PayloadFormatV2.SectionType.TRUST_CHAIN)?.let { resolveIssuerKey(it) } },
                extractPolicy = { sym -> extractPolicy(sym.payload) },
                verifierAttributes = verifierAttributes,
                extractChain = { sym -> v2Section(sym.payload, PayloadFormatV2.SectionType.TRUST_CHAIN)?.let { parseLeafCertificate(it) }?.let { listOf(it) } },
                pkiValidator = CertificateChainValidatorImpl(trustStore),
                pkiTrustStore = trustStore,
            )
        }
    }
}
