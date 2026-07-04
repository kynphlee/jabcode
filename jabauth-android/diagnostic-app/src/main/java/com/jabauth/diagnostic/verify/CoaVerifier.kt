package com.jabauth.diagnostic.verify

import com.jabauth.client.abe.ABEPolicy
import com.jabauth.client.jwt.SdJwtVcService
import com.jabauth.client.pki.CertificateChainValidator
import com.jabauth.client.pki.TrustStoreManager
import java.security.PublicKey
import java.security.cert.X509Certificate

/**
 * Assembles the four-stage on-device pre-check pipeline from the real stage adapters, wiring the
 * extraction seams that — until Payload v2 parsing lands (Phase 6) — pull each stage's input out of a
 * decoded symbol.
 *
 * The PKI stage is interim (`WARN` / `UNKNOWN_OFFLINE`) until a real on-device validator lands; JWT and
 * ABE run the real on-device crypto / policy adjudication. Given extracted inputs, this composes a fully
 * functional [VerificationOrchestrator] — the pipeline works end-to-end today; only the container parsing
 * behind the seams remains for Phase 6.
 */
object CoaVerifier {
    fun orchestrator(
        extractToken: (DecodedSymbol) -> String?,
        resolveIssuerKey: (DecodedSymbol) -> PublicKey?,
        extractPolicy: (DecodedSymbol) -> ABEPolicy?,
        verifierAttributes: () -> Set<String>,
        extractChain: (DecodedSymbol) -> List<X509Certificate>? = { null },
        pkiValidator: CertificateChainValidator? = null,
        pkiTrustStore: TrustStoreManager? = null,
        sdJwt: SdJwtVcService = SdJwtVcService(),
    ): VerificationOrchestrator = VerificationOrchestrator(
        pki = PkiStageRunner(extractChain, pkiValidator, pkiTrustStore),
        jwt = JwtStageRunner(extractToken, resolveIssuerKey, sdJwt),
        abe = AbeStageRunner(extractPolicy, verifierAttributes),
    )
}
