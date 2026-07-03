package com.jabauth.diagnostic.verify

/**
 * Bridges a decoded scan into a [VerificationResult] via [CoaVerifier], using interim extraction seams
 * (Payload v2 parsing, an on-device PKI trust store, and the verifier attribute set all land in Phase 6).
 *
 * Today:
 *  - a non-credential symbol (payload not an SD-JWT VC) → [TrustVerdict.NOT_A_COA], preserving the classic
 *    decode path (never make the plain decode worse);
 *  - a credential-bearing symbol → the pipeline runs, but with no on-device trust anchor the JWT stage
 *    honestly reports it cannot resolve an issuer key (verification is completed authoritatively server-side).
 */
class ScanVerifier(
    private val orchestrator: VerificationOrchestrator = defaultOrchestrator(),
) {
    /** Verify a decoded symbol's raw [payload] bytes carrying its [decodeLatencyMs]. */
    fun verify(payload: ByteArray, decodeLatencyMs: Long): VerificationResult =
        orchestrator.verify(
            DecodeInput.Decoded(
                DecodedSymbol(
                    payload = payload,
                    isCoaFormat = looksLikeCoaCredential(payload),
                    decodeLatencyMs = decodeLatencyMs,
                    formatProfile = FormatProfile("unknown", null),
                ),
            ),
        )

    companion object {
        /** Interim COA-credential heuristic: an SD-JWT VC begins with a base64url JWS header (`eyJ`).
         *  Phase 6 replaces this with the PayloadFormatV2 `JAC2` magic check. */
        internal fun looksLikeCoaCredential(payload: ByteArray): Boolean =
            payloadAsText(payload)?.startsWith("eyJ") == true

        private fun payloadAsText(payload: ByteArray): String? =
            runCatching { String(payload, Charsets.UTF_8) }.getOrNull()

        private fun defaultOrchestrator(): VerificationOrchestrator = CoaVerifier.orchestrator(
            extractToken = { sym -> payloadAsText(sym.payload)?.takeIf { it.startsWith("eyJ") } },
            resolveIssuerKey = { null },   // no on-device trust store yet (Phase 6)
            extractPolicy = { null },      // no v2 ABE_SEALED section yet (Phase 6)
            verifierAttributes = { emptySet() },
        )
    }
}
