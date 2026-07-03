package com.jabauth.diagnostic.verify

import com.auth0.jwt.JWT
import com.authlete.sd.SDJWT
import com.jabauth.client.jwt.JwtAlgorithms
import com.jabauth.client.jwt.SdJwtVcService
import java.security.PublicKey

/**
 * The JWT verification stage: verifies the SD-JWT VC signature against the issuer key, enforces the
 * algorithm allowlist and expiry (via the real on-device [SdJwtVcService]), and partitions the
 * credential's claims into revealed vs withheld-but-digest-verified (selective disclosure, Principle C).
 *
 * JWT is **binary** — a signature either proves authenticity or it does not — so this stage emits only
 * [StageState.PASS] or [StageState.FAIL], never WARN. Every FAIL carries the specific reason from the
 * verifier (bad signature / disallowed algorithm / key mismatch / expired / malformed), per Principle A.
 *
 * The token and issuer key arrive via injected seams so the crypto stays real and JVM-testable now, with
 * the container/parse concerns deferred:
 *  - [extractToken] pulls the SD-JWT VC from the decoded symbol (the Payload v2 `SDJWT_VC` section — an
 *    interim seam until v2 lands, Phase 6).
 *  - [resolveIssuerKey] yields the issuer public key, which in the full pipeline comes from the
 *    PKI-validated leaf certificate (interim until the PKI impl lands, Phase 6).
 */
class JwtStageRunner(
    private val extractToken: (DecodedSymbol) -> String?,
    private val resolveIssuerKey: (DecodedSymbol) -> PublicKey?,
    private val sdJwt: SdJwtVcService = SdJwtVcService(),
) : VerificationOrchestrator.StageRunner {

    override fun run(symbol: DecodedSymbol): StageResult {
        val token = extractToken(symbol)
            ?: return fail("No SD-JWT VC section in the payload (unparseable credential).")
        val key = resolveIssuerKey(symbol)
            ?: return fail("No issuer public key available — the PKI stage yielded no leaf certificate.")
        return evaluate(token, key)
    }

    /**
     * Verify [token] against [issuerKey] and map the outcome to a binary JWT [StageResult]. Exposed for
     * direct unit testing with minted tokens (no symbol/seam plumbing).
     */
    fun evaluate(token: String, issuerKey: PublicKey): StageResult {
        val verification = sdJwt.verify(token, issuerKey)
        val detail = buildDetail(token, verification)
        return if (verification.valid) {
            StageResult(VerificationStage.JWT, StageState.PASS, detail = detail)
        } else {
            // JWT is binary: any non-valid outcome is a hard FAIL carrying the verifier's specific reason.
            StageResult(VerificationStage.JWT, StageState.FAIL, reason = verification.message, detail = detail)
        }
    }

    /** Read the (unverified) header + claims for the forensic drill-down and the selective-disclosure split.
     *  Best-effort — a malformed token returns null detail; the FAIL verdict already reflects it. */
    private fun buildDetail(token: String, verification: SdJwtVcService.Verification): JwtDetail? = try {
        val parsed = SDJWT.parse(token)
        val decoded = JWT.decode(parsed.credentialJwt)
        val algorithm = decoded.algorithm ?: "unknown"
        val allowed = runCatching { JwtAlgorithms.validate(algorithm) }.isSuccess

        // Revealed = the decoder's reconstructed claims (always-visible + presented), minus JWT/SD machinery.
        val revealed = verification.disclosedClaims
            .filterKeys { it !in STANDARD_CLAIMS }
            .mapValues { it.value.toString() }

        // Withheld = _sd digests in the signed payload with no present Disclosure (name unrecoverable without
        // the vct schema — the digest is the forensic anchor: not missing, not tampered).
        val presentDigests = parsed.disclosures.mapNotNull { runCatching { it.digest() }.getOrNull() }.toSet()
        val allSdDigests = decoded.getClaim(CLAIM_SD).asList(String::class.java) ?: emptyList<String>()
        val withheld = allSdDigests.filterNot { it in presentDigests }.map { WithheldClaim(name = "", digest = it) }

        JwtDetail(
            algorithm = algorithm,
            algorithmAllowed = allowed,
            tokenClass = decoded.getClaim(CLAIM_TOKEN_CLASS).asString(),
            issuer = decoded.issuer,
            expiry = decoded.expiresAt?.toInstant()?.toString(),
            revealedClaims = revealed,
            withheldDigests = withheld,
        )
    } catch (e: Exception) {
        null
    }

    private fun fail(reason: String) = StageResult(VerificationStage.JWT, StageState.FAIL, reason = reason)

    private companion object {
        const val CLAIM_SD = "_sd"
        const val CLAIM_TOKEN_CLASS = "token_class"
        // JWT-registered + SD machinery claims shown in their own detail fields, not as credential claims.
        val STANDARD_CLAIMS = setOf("_sd", "_sd_alg", "iss", "sub", "iat", "exp", "nbf", "vct", "token_class")
    }
}
