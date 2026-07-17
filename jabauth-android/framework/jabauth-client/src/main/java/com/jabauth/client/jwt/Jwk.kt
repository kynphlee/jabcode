package com.jabauth.client.jwt

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * Minimal JWK support for the device — RFC 7638 thumbprints and the JWK-map ⇄ [PublicKey]
 * bridge that both the trust-list anchor and KB-JWT holder binding need. Computed by hand
 * because Nimbus is not on the Android classpath (auth0 java-jwt carries no JWK model): the
 * thumbprint input is the canonical JSON with the REQUIRED members in lexicographic order —
 * `{"crv","kty","x","y"}` for EC, `{"e","kty","n"}` for RSA — exactly as RFC 7638 §3
 * prescribes, so device and server (Nimbus-computed) thumbprints agree byte-for-byte.
 *
 * <p>A thumbprint is self-certifying: a verifier that resolves a candidate key re-derives the
 * thumbprint and compares — the mark binds to the key, never to a name a list could mis-map.</p>
 */
object Jwk {

    /** Raw length of a SHA-256 thumbprint. */
    const val THUMBPRINT_LENGTH = 32

    private val B64U = Base64.getUrlEncoder().withoutPadding()
    private val B64D = Base64.getUrlDecoder()

    /** The RFC 7638 SHA-256 thumbprint of [key]'s canonical JWK — raw 32 bytes. */
    fun thumbprintSha256(key: PublicKey): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(canonicalJson(key).toByteArray(Charsets.US_ASCII))

    /** The thumbprint as base64url (the `kid` / `cnf.jkt` form). */
    fun thumbprintSha256Base64Url(key: PublicKey): String = B64U.encodeToString(thumbprintSha256(key))

    /** The public JWK for [key], as the claim map carried in trust lists / KB-JWT headers. */
    fun toMap(key: PublicKey): Map<String, String> = when (key) {
        is ECPublicKey -> {
            val (crv, size) = curveOf(key.params)
            mapOf(
                "kty" to "EC",
                "crv" to crv,
                "x" to B64U.encodeToString(fixedLength(key.w.affineX, size)),
                "y" to B64U.encodeToString(fixedLength(key.w.affineY, size)),
            )
        }
        is RSAPublicKey -> mapOf(
            "kty" to "RSA",
            "n" to B64U.encodeToString(unsignedBytes(key.modulus)),
            "e" to B64U.encodeToString(unsignedBytes(key.publicExponent)),
        )
        else -> throw IllegalArgumentException("Unsupported key type for JWK: ${key.algorithm}")
    }

    /** The inverse of [toMap]: the JCA public key inside a public EC/RSA JWK map. */
    fun publicKeyOf(jwk: Map<*, *>): PublicKey = when (jwk["kty"]) {
        "EC" -> {
            val crv = jwk["crv"] as? String
                ?: throw IllegalArgumentException("EC JWK missing crv")
            val x = BigInteger(1, B64D.decode(jwk["x"] as? String ?: missing("x")))
            val y = BigInteger(1, B64D.decode(jwk["y"] as? String ?: missing("y")))
            val params = AlgorithmParameters.getInstance("EC").apply {
                init(ECGenParameterSpec(stdNameOf(crv)))
            }.getParameterSpec(ECParameterSpec::class.java)
            KeyFactory.getInstance("EC")
                .generatePublic(ECPublicKeySpec(ECPoint(x, y), params))
        }
        "RSA" -> {
            val n = BigInteger(1, B64D.decode(jwk["n"] as? String ?: missing("n")))
            val e = BigInteger(1, B64D.decode(jwk["e"] as? String ?: missing("e")))
            KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, e))
        }
        else -> throw IllegalArgumentException("Unsupported JWK kty: ${jwk["kty"]}")
    }

    /**
     * RFC 7638 §3 canonical JSON: required members only, lexicographic order, no whitespace.
     * String values here are base64url/curve names — no characters needing JSON escaping.
     */
    private fun canonicalJson(key: PublicKey): String = when (key) {
        is ECPublicKey -> {
            val m = toMap(key)
            """{"crv":"${m["crv"]}","kty":"EC","x":"${m["x"]}","y":"${m["y"]}"}"""
        }
        is RSAPublicKey -> {
            val m = toMap(key)
            """{"e":"${m["e"]}","kty":"RSA","n":"${m["n"]}"}"""
        }
        else -> throw IllegalArgumentException("Unsupported key type for JWK: ${key.algorithm}")
    }

    /** Curve name + coordinate byte length from the JCA parameter spec. */
    private fun curveOf(params: ECParameterSpec): Pair<String, Int> =
        when (val bits = params.curve.field.fieldSize) {
            256 -> "P-256" to 32
            384 -> "P-384" to 48
            521 -> "P-521" to 66
            else -> throw IllegalArgumentException("Unsupported EC curve size: $bits")
        }

    private fun stdNameOf(crv: String): String = when (crv) {
        "P-256" -> "secp256r1"
        "P-384" -> "secp384r1"
        "P-521" -> "secp521r1"
        else -> throw IllegalArgumentException("Unsupported JWK crv: $crv")
    }

    /** RFC 7518 fixed-width coordinate encoding (left-padded, sign byte stripped). */
    private fun fixedLength(value: BigInteger, size: Int): ByteArray {
        val raw = value.toByteArray()
        val out = ByteArray(size)
        val start = if (raw.size > size) raw.size - size else 0 // strip a leading sign byte
        val copyLen = minOf(raw.size, size)
        System.arraycopy(raw, start, out, size - copyLen, copyLen)
        return out
    }

    /** Minimal big-endian encoding without a leading zero sign byte (RFC 7518 for n/e). */
    private fun unsignedBytes(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        return if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
    }

    private fun missing(member: String): Nothing =
        throw IllegalArgumentException("JWK missing required member '$member'")
}
