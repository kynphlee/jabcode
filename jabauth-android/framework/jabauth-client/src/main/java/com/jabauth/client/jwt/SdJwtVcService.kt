package com.jabauth.client.jwt

import com.auth0.jwt.JWT
import com.authlete.sd.SDJWT
import com.authlete.sd.SDObjectBuilder
import com.authlete.sd.SDObjectDecoder
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Instant
import java.util.Date

/**
 * Issues, presents, and verifies SD-JWT VCs (SD-JWT-based Verifiable
 * Credentials, RFC 9901 + draft-ietf-oauth-sd-jwt-vc) — the selective-disclosure
 * credential format the EU Digital Identity Wallet mandates (eIDAS2).
 *
 * Kotlin port of the server `SdJwtVcService`, mirroring its issue / holder-present
 * / verify flow so a server-minted (JVM) VC verifies unchanged on Android. The
 * only stack difference is the JWS primitive: the server signs with Nimbus,
 * Android signs/verifies with auth0 `java-jwt` (ES256 baseline) — there is no
 * Nimbus on Android. The `_sd` digest / Disclosure machinery is Authlete's
 * `com.authlete.sd.*` (identical on both sides), so the selective-disclosure
 * bytes are interoperable.
 *
 * All signing/verifying routes through [JwtAlgorithms], which enforces the
 * asymmetric-only allowlist (blocks `none`/HS*) and the fail-closed key/alg
 * type check — the same algorithm-confusion protections as the server.
 *
 * Key Binding (holder proof-of-possession via a KB-JWT) is intentionally out of
 * scope for this increment, matching the server.
 */
class SdJwtVcService {

    private val decoder = SDObjectDecoder()

    /**
     * Issue an SD-JWT VC. The returned combined serialization
     * (`<JWT>~<Disclosure>~...~`) carries ALL Disclosures — this is what an
     * issuer hands to a holder, who later presents a subset via [present].
     */
    fun issue(request: SdJwtVcRequest, privateKey: PrivateKey): String {
        try {
            val builder = SDObjectBuilder()
            val disclosures = ArrayList<com.authlete.sd.Disclosure>()

            // Selectively-disclosable claims -> _sd digests + their Disclosures.
            for ((k, v) in request.selectivelyDisclosable) {
                disclosures.add(builder.putSDClaim(k, v))
            }
            // Always-visible custom claims, signed in the clear.
            for ((k, v) in request.alwaysVisible) {
                builder.putClaim(k, v)
            }

            // build(true) appends the _sd_alg hash-algorithm claim required for decode.
            val sdPayload: Map<String, Any> = builder.build(true)

            val now = Instant.now()
            val payload = LinkedHashMap<String, Any?>()
            request.issuer?.let { payload[CLAIM_ISS] = it }
            request.subject?.let { payload[CLAIM_SUB] = it }
            payload[CLAIM_IAT] = now.epochSecond
            payload[CLAIM_EXP] = now.plus(request.expiration).epochSecond
            request.vct?.let { payload[CLAIM_VCT] = it }
            // _sd array and _sd_alg from Authlete, plus any always-visible claims.
            payload.putAll(sdPayload)

            val algorithm = JwtAlgorithms.signer(request.algorithm, privateKey)
            val credentialJwt = JWT.create()
                .withPayload(payload)
                .sign(algorithm)

            return SDJWT(credentialJwt, disclosures).toString()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to issue SD-JWT VC", e)
        }
    }

    /**
     * Holder-side selective disclosure: from an issued SD-JWT, keep only the
     * Disclosures for [claimsToReveal] and drop the rest. The signed JWT is
     * unchanged; withheld claims remain only as digests a verifier cannot invert.
     */
    fun present(issuedSdJwt: String, claimsToReveal: Set<String>): String {
        val parsed = SDJWT.parse(issuedSdJwt)
        val revealed = parsed.disclosures.filter {
            it.claimName != null && claimsToReveal.contains(it.claimName)
        }
        return SDJWT(parsed.credentialJwt, revealed).toString()
    }

    /**
     * Verify an issued or presented SD-JWT VC: validate the issuer signature
     * against [publicKey], enforce the algorithm allowlist and expiry, and
     * reconstruct the disclosed claim set. Never throws on a bad token — returns
     * a failed [Verification].
     */
    fun verify(sdJwt: String, publicKey: PublicKey): Verification {
        return try {
            val parsed = SDJWT.parse(sdJwt)
            // Decode (not verify) first to read the alg header for the allowlist.
            val decoded = JWT.decode(parsed.credentialJwt)

            // Algorithm allowlist (blocks none/HS*), then key/alg-typed verify.
            val algName = decoded.algorithm
            try {
                JwtAlgorithms.validate(algName)
            } catch (e: SecurityException) {
                return Verification.invalid("Algorithm validation failed: ${e.message}")
            }
            val algorithm = try {
                JwtAlgorithms.verifier(algName, publicKey)
            } catch (e: IllegalArgumentException) {
                return Verification.invalid("Algorithm/key mismatch: ${e.message}")
            }
            try {
                JWT.require(algorithm).build().verify(parsed.credentialJwt)
            } catch (e: Exception) {
                return Verification.invalid("Invalid issuer signature: ${e.message}")
            }

            // Enforce expiry (JWT.require above does not, since we verify manually).
            val exp = decoded.expiresAt
            if (exp != null && exp.toInstant().isBefore(Instant.now())) {
                return Verification.invalid("Credential expired")
            }

            // Reconstruct the disclosed object: claims whose Disclosure is present
            // are filled in; withheld _sd digests are dropped.
            val signedClaims = signedClaimsAsMap(parsed.credentialJwt)
            val disclosed = decoder.decode(signedClaims, parsed.disclosures)
            Verification.valid(java.util.Collections.unmodifiableMap(disclosed))
        } catch (e: Exception) {
            Verification.invalid("Malformed SD-JWT: ${e.message}")
        }
    }

    /**
     * Read the credential JWT's signed payload as a claim map for the Authlete
     * decoder. Uses the auth0-decoded claims, coercing each to the plain Java
     * type the decoder expects (the `_sd` list of digest strings and the
     * `_sd_alg` string in particular).
     */
    private fun signedClaimsAsMap(credentialJwt: String): Map<String, Any> {
        val decoded = JWT.decode(credentialJwt)
        val out = LinkedHashMap<String, Any>()
        for ((name, claim) in decoded.claims) {
            when (name) {
                CLAIM_SD -> claim.asList(String::class.java)?.let { out[name] = ArrayList(it) }
                CLAIM_SD_ALG -> claim.asString()?.let { out[name] = it }
                CLAIM_EXP, CLAIM_IAT, CLAIM_NBF ->
                    claim.asLong()?.let { out[name] = it }
                else -> {
                    val v: Any? = claim.asString()
                        ?: claim.asBoolean()
                        ?: claim.asLong()
                        ?: claim.asDouble()
                        ?: runCatching { claim.asMap() }.getOrNull()
                        ?: runCatching { claim.asList(Any::class.java) }.getOrNull()
                    if (v != null) out[name] = v
                }
            }
        }
        return out
    }

    /**
     * Outcome of [verify]: validity, a message, and the disclosed claims.
     * Mirrors the server's `Verification` record.
     */
    data class Verification(
        val valid: Boolean,
        val message: String,
        val disclosedClaims: Map<String, Any>,
    ) {
        companion object {
            fun valid(disclosed: Map<String, Any>): Verification =
                Verification(true, "OK", disclosed)

            fun invalid(message: String): Verification =
                Verification(false, message, emptyMap())
        }
    }

    private companion object {
        const val CLAIM_VCT = "vct"
        const val CLAIM_ISS = "iss"
        const val CLAIM_SUB = "sub"
        const val CLAIM_IAT = "iat"
        const val CLAIM_EXP = "exp"
        const val CLAIM_NBF = "nbf"
        const val CLAIM_SD = "_sd"
        const val CLAIM_SD_ALG = "_sd_alg"
    }
}
