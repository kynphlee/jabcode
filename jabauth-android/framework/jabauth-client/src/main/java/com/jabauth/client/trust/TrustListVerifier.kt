package com.jabauth.client.trust

import com.auth0.jwt.JWT
import com.jabauth.client.jwt.JwtAlgorithms
import java.security.PublicKey
import java.time.Instant

/**
 * Verifies the signed trust-list token on-device — the sibling of the server's
 * `TrustListTokenService.verify`, on auth0 java-jwt: algorithm allowlist (none/HS* refused),
 * signature against the JABAuth list-signing public key (provisioned with the device like
 * any trust anchor), subject-match against the list URI, expiry enforced. Staleness (against
 * `iat` and the list's own signed `max_staleness`) is [TrustAnchorResolver]'s job — it needs
 * a clock policy, not a signature policy.
 */
class TrustListVerifier {

    companion object {
        const val TYP = "trust-list+jwt"
        const val CLAIM_TRUST_LIST = "trust_list"
    }

    data class Verification(
        val valid: Boolean,
        val reason: String?,
        val list: TrustList?,
        val issuedAt: Instant?,
    ) {
        companion object {
            fun valid(list: TrustList, issuedAt: Instant) = Verification(true, null, list, issuedAt)
            fun invalid(reason: String) = Verification(false, reason, null, null)
        }
    }

    fun verify(token: String, expectedListUri: String, listSignerKey: PublicKey): Verification {
        return try {
            val decoded = JWT.decode(token)
            try {
                JwtAlgorithms.validate(decoded.algorithm)
            } catch (e: SecurityException) {
                return Verification.invalid("Algorithm validation failed: ${e.message}")
            }
            val algorithm = try {
                JwtAlgorithms.verifier(decoded.algorithm, listSignerKey)
            } catch (e: SecurityException) {
                return Verification.invalid("Algorithm/key mismatch: ${e.message}")
            }
            // Signature + structural verification. auth0 enforces exp when present, but the
            // trust list REQUIRES one — an unexpiring list would defeat rotation.
            val verified = try {
                JWT.require(algorithm).withSubject(expectedListUri).build().verify(token)
            } catch (e: Exception) {
                return Verification.invalid("Trust-list verification failed: ${e.message}")
            }
            val exp = verified.expiresAtAsInstant
                ?: return Verification.invalid("Trust list has no expiry")
            if (exp.isBefore(Instant.now())) {
                return Verification.invalid("Trust list is expired")
            }
            val iat = verified.issuedAtAsInstant
                ?: return Verification.invalid("Trust list has no issue time")
            val claim = verified.getClaim(CLAIM_TRUST_LIST).asMap()
                ?: return Verification.invalid("Trust-list claim malformed")
            Verification.valid(TrustList.fromClaims(claim), iat)
        } catch (e: Exception) {
            Verification.invalid("Malformed trust-list token: ${e.message}")
        }
    }
}
