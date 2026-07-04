package com.jabauth.diagnostic.verify

import com.jabauth.client.v2.PayloadFormatV2
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

/**
 * Bridges a decoded scan into a [VerificationResult] via [CoaVerifier], parsing the on-device **Payload
 * Format v2** container to feed each stage its real input.
 *
 *  - Non-v2 payload → [TrustVerdict.NOT_A_COA] (the classic decode path is preserved).
 *  - v2 COA → the `SDJWT_VC` section is the credential and the `TRUST_CHAIN` section carries the issuer
 *    public key (SPKI), so the **JWT stage verifies the real SD-JWT VC with the real issuer key**. PKI
 *    stays interim (the current v2 `TRUST_CHAIN` is the leaf key, not a full chain → no path validation
 *    offline); ABE policy extraction from the sealed envelope is a follow-up. The on-device verdict is a
 *    pre-check — the server verifies authoritatively (Principle F).
 */
class ScanVerifier(
    private val orchestrator: VerificationOrchestrator = defaultOrchestrator(),
) {
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

        /** Parse an X.509 SubjectPublicKeyInfo (the `TRUST_CHAIN` section) into a public key (ES* or RS*). */
        internal fun parsePublicKey(spki: ByteArray): PublicKey? = runCatching {
            val spec = X509EncodedKeySpec(spki)
            runCatching { KeyFactory.getInstance("EC").generatePublic(spec) }.getOrNull()
                ?: KeyFactory.getInstance("RSA").generatePublic(spec)
        }.getOrNull()

        private fun defaultOrchestrator(): VerificationOrchestrator = CoaVerifier.orchestrator(
            extractToken = { sym -> v2Section(sym.payload, PayloadFormatV2.SectionType.SDJWT_VC)?.toString(Charsets.UTF_8) },
            resolveIssuerKey = { sym -> v2Section(sym.payload, PayloadFormatV2.SectionType.TRUST_CHAIN)?.let { parsePublicKey(it) } },
            extractPolicy = { null }, // ABE policy from the sealed envelope — follow-up (envelope decode is engine-private)
            verifierAttributes = { emptySet() },
        )
    }
}
