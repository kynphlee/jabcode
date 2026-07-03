package com.jabauth.client.jwt

import com.auth0.jwt.algorithms.Algorithm
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * JWT signature-algorithm allowlist and fail-closed signer/verifier selection.
 *
 * Mirrors the server's `JwtAlgorithmValidator` (the allowlist) and `JwsCrypto`
 * (algorithm -> signer/verifier, key-type checked) so the Android COA verify
 * path has the same algorithm-confusion protections. auth0 `java-jwt` is used
 * for the JWS primitives; there is no Nimbus on Android.
 *
 * Security posture (identical to the server):
 *  - Only the asymmetric RSA (RS256/384/512) and ECDSA (ES256/384/512) families
 *    are permitted. ES* is the SD-JWT VC / eIDAS2 baseline.
 *  - The `none` algorithm is explicitly rejected — it bypasses signature
 *    verification.
 *  - Symmetric HS* (HMAC) algorithms are rejected — admitting them against a
 *    public verification key is an RS/HS algorithm-confusion vector.
 *  - The key type is re-checked against the algorithm family at select time, so
 *    an admitted ES* header presented against an RSA key is rejected (never
 *    coerced) and vice-versa. No silent downgrade / fallback.
 */
object JwtAlgorithms {

    /** Allowed algorithms — asymmetric RSA + ECDSA families only. */
    val ALLOWED: Set<String> = setOf(
        "RS256", "RS384", "RS512",
        "ES256", "ES384", "ES512",
    )

    /** The COA-token default: ES256 (matches the server + SD-JWT VC baseline). */
    const val DEFAULT_ALGORITHM: String = "ES256"

    /**
     * Validate that a token's `alg` header is on the allowlist.
     *
     * @throws SecurityException if the algorithm is null/blank, `none`, HS*, or
     *   otherwise not on the allowlist.
     */
    fun validate(algorithm: String?) {
        if (algorithm.isNullOrBlank()) {
            throw SecurityException("JWT algorithm cannot be null or blank")
        }
        if (algorithm.equals("none", ignoreCase = true)) {
            throw SecurityException("None algorithm is forbidden - signature required")
        }
        if (algorithm.uppercase().startsWith("HS")) {
            throw SecurityException(
                "Symmetric algorithm $algorithm is forbidden - " +
                    "asymmetric signature required (algorithm-confusion guard)"
            )
        }
        if (algorithm !in ALLOWED) {
            throw SecurityException(
                "Algorithm $algorithm not allowed. Allowed algorithms: $ALLOWED"
            )
        }
    }

    /** True for the ECDSA family (ES256/384/512). */
    private fun isEc(algorithm: String): Boolean = algorithm.startsWith("ES")

    /** True for the RSA family (RS256/384/512). */
    private fun isRsa(algorithm: String): Boolean = algorithm.startsWith("RS")

    /**
     * Build the auth0 [Algorithm] used to VERIFY a token that declares
     * [algorithm], over [publicKey]. Runs the allowlist first, then requires the
     * key type to match the algorithm family — rejecting a key/alg mismatch
     * (algorithm-confusion safe) instead of coercing it.
     *
     * @throws SecurityException if [algorithm] is not on the allowlist.
     * @throws IllegalArgumentException if the key type does not match the
     *   algorithm family, or the algorithm is unsupported.
     */
    fun verifier(algorithm: String, publicKey: PublicKey): Algorithm {
        validate(algorithm)
        if (isEc(algorithm)) {
            val ecKey = publicKey as? ECPublicKey
                ?: throw IllegalArgumentException(
                    "Token declares $algorithm but the verification key is " +
                        "${keyTypeName(publicKey)} (algorithm/key mismatch)."
                )
            return when (algorithm) {
                "ES256" -> Algorithm.ECDSA256(ecKey, null)
                "ES384" -> Algorithm.ECDSA384(ecKey, null)
                "ES512" -> Algorithm.ECDSA512(ecKey, null)
                else -> throw IllegalArgumentException("Unsupported EC algorithm: $algorithm")
            }
        }
        if (isRsa(algorithm)) {
            val rsaKey = publicKey as? RSAPublicKey
                ?: throw IllegalArgumentException(
                    "Token declares $algorithm but the verification key is " +
                        "${keyTypeName(publicKey)} (algorithm/key mismatch)."
                )
            return when (algorithm) {
                "RS256" -> Algorithm.RSA256(rsaKey, null)
                "RS384" -> Algorithm.RSA384(rsaKey, null)
                "RS512" -> Algorithm.RSA512(rsaKey, null)
                else -> throw IllegalArgumentException("Unsupported RSA algorithm: $algorithm")
            }
        }
        throw IllegalArgumentException("Unsupported verification algorithm: $algorithm")
    }

    /**
     * Build the auth0 [Algorithm] used to SIGN with [algorithm] over
     * [privateKey]. Runs the allowlist first, then requires the key type to
     * match the algorithm family — no silent downgrade.
     *
     * @throws SecurityException if [algorithm] is not on the allowlist.
     * @throws IllegalArgumentException if the key type does not match the
     *   algorithm family, or the algorithm is unsupported.
     */
    fun signer(algorithm: String, privateKey: PrivateKey): Algorithm {
        validate(algorithm)
        if (isEc(algorithm)) {
            val ecKey = privateKey as? ECPrivateKey
                ?: throw IllegalArgumentException(
                    "$algorithm requires an EC private key, but the provided key is " +
                        "${keyTypeName(privateKey)}."
                )
            return when (algorithm) {
                "ES256" -> Algorithm.ECDSA256(null, ecKey)
                "ES384" -> Algorithm.ECDSA384(null, ecKey)
                "ES512" -> Algorithm.ECDSA512(null, ecKey)
                else -> throw IllegalArgumentException("Unsupported EC algorithm: $algorithm")
            }
        }
        if (isRsa(algorithm)) {
            val rsaKey = privateKey as? RSAPrivateKey
                ?: throw IllegalArgumentException(
                    "$algorithm requires an RSA private key, but the provided key is " +
                        "${keyTypeName(privateKey)}."
                )
            return when (algorithm) {
                "RS256" -> Algorithm.RSA256(null, rsaKey)
                "RS384" -> Algorithm.RSA384(null, rsaKey)
                "RS512" -> Algorithm.RSA512(null, rsaKey)
                else -> throw IllegalArgumentException("Unsupported RSA algorithm: $algorithm")
            }
        }
        throw IllegalArgumentException("Unsupported signing algorithm: $algorithm")
    }

    private fun keyTypeName(key: java.security.Key?): String =
        if (key == null) "null" else "${key.algorithm} (${key.javaClass.simpleName})"
}
