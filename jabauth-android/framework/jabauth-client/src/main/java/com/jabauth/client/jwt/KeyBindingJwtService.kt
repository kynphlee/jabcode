package com.jabauth.client.jwt

import com.auth0.jwt.JWT
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * RFC 9901 Key-Binding JWTs on the device — the holder's proof-of-possession that closes
 * clone-and-replay (WP-C mobile). Kotlin port of the server's `KeyBindingJwtService` on auth0
 * java-jwt: the record carries only `cnf.jkt` (the holder key's RFC 7638 thumbprint); at
 * presentation the holder signs the verifier's nonce/audience with the bound private key
 * (an Android-Keystore key via [HolderKeys] — the private half never leaves the device).
 *
 * Self-certifying key transport, the house idiom: the KB-JWT carries the holder's public JWK
 * in its header; the verifier re-derives the JWK's thumbprint and requires it to equal the
 * record's `cnf.jkt` BEFORE the signature is consulted — a copied record plus an attacker's
 * own KB-JWT fails the gate, never reaches crypto. `sd_hash` pins the proof to the exact
 * presented SD-JWT.
 */
class KeyBindingJwtService {

    companion object {
        const val TYP = "kb+jwt"
        const val CLAIM_NONCE = "nonce"
        const val CLAIM_SD_HASH = "sd_hash"

        /** Default freshness window for a presented KB-JWT's `iat`. */
        val DEFAULT_MAX_AGE: Duration = Duration.ofMinutes(5)

        /** SHA-256 of the presented SD-JWT, base64url (RFC 9901 `sd_hash`). */
        fun sdHash(presentedSdJwt: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(presentedSdJwt.toByteArray(Charsets.US_ASCII))
            )
    }

    /** Holder side: sign a KB-JWT over the verifier's challenge and the presented SD-JWT. */
    fun issue(
        holderPrivateKey: PrivateKey,
        holderPublicKey: PublicKey,
        audience: String,
        nonce: String,
        presentedSdJwt: String,
    ): String {
        val algorithm = JwtAlgorithms.signer(JwtAlgorithms.DEFAULT_ALGORITHM, holderPrivateKey)
        return JWT.create()
            .withHeader(mapOf("typ" to TYP, "jwk" to Jwk.toMap(holderPublicKey)))
            .withAudience(audience)
            .withClaim(CLAIM_NONCE, nonce)
            .withIssuedAt(Date.from(Instant.now()))
            .withClaim(CLAIM_SD_HASH, sdHash(presentedSdJwt))
            .sign(algorithm)
    }

    data class Verification(val valid: Boolean, val reason: String?) {
        companion object {
            fun ok() = Verification(true, null)
            fun invalid(reason: String) = Verification(false, reason)
        }
    }

    /**
     * Verifier side: validate a presented KB-JWT against the record's `cnf.jkt`. The
     * thumbprint gate comes FIRST — the signature is only ever checked against the key the
     * record actually bound.
     */
    fun verify(
        kbJwt: String,
        expectedJkt: String,
        expectedAudience: String,
        expectedNonce: String,
        presentedSdJwt: String,
        maxAge: Duration = DEFAULT_MAX_AGE,
    ): Verification {
        return try {
            val decoded = JWT.decode(kbJwt)
            try {
                JwtAlgorithms.validate(decoded.algorithm)
            } catch (e: SecurityException) {
                return Verification.invalid("Algorithm validation failed: ${e.message}")
            }
            if (decoded.type != TYP) {
                return Verification.invalid("Not a KB-JWT (typ must be $TYP)")
            }

            // 1. The header key must re-derive the record's cnf.jkt — the binding gate.
            val headerJwk = decoded.getHeaderClaim("jwk").asMap()
                ?: return Verification.invalid("KB-JWT carries no holder JWK")
            val holderKey = try {
                Jwk.publicKeyOf(headerJwk)
            } catch (e: IllegalArgumentException) {
                return Verification.invalid("KB-JWT holder JWK malformed: ${e.message}")
            }
            if (Jwk.thumbprintSha256Base64Url(holderKey) != expectedJkt) {
                return Verification.invalid(
                    "KB-JWT key does not match the record's cnf.jkt (holder-key mismatch)"
                )
            }

            // 2. Only now is the signature meaningful.
            val algorithm = try {
                JwtAlgorithms.verifier(decoded.algorithm, holderKey)
            } catch (e: IllegalArgumentException) {
                return Verification.invalid("Algorithm/key mismatch: ${e.message}")
            }
            try {
                JWT.require(algorithm).build().verify(kbJwt)
            } catch (e: Exception) {
                return Verification.invalid("Invalid KB-JWT signature: ${e.message}")
            }

            // 3. Challenge freshness + binding to THIS presentation.
            if (!decoded.audience.orEmpty().contains(expectedAudience)) {
                return Verification.invalid("KB-JWT audience mismatch")
            }
            if (decoded.getClaim(CLAIM_NONCE).asString() != expectedNonce) {
                return Verification.invalid("KB-JWT nonce mismatch")
            }
            val iat = decoded.issuedAtAsInstant
            val now = Instant.now()
            if (iat == null || iat.isBefore(now.minus(maxAge))
                || iat.isAfter(now.plus(Duration.ofMinutes(1)))
            ) {
                return Verification.invalid("KB-JWT is not fresh")
            }
            if (decoded.getClaim(CLAIM_SD_HASH).asString() != sdHash(presentedSdJwt)) {
                return Verification.invalid("KB-JWT sd_hash does not match the presented record")
            }
            Verification.ok()
        } catch (e: Exception) {
            Verification.invalid("Malformed KB-JWT: ${e.message}")
        }
    }
}
