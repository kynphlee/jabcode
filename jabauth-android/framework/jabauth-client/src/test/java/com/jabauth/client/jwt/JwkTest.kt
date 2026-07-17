package com.jabauth.client.jwt

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

/**
 * RFC 7638 thumbprints on the device. The RSA case is the RFC's own §3.1 example vector —
 * matching it proves the hand-rolled canonical JSON agrees with every conformant
 * implementation, including the server's Nimbus-computed thumbprints (the cross-side
 * interop guarantee the trust-list anchor and cnf.jkt both depend on).
 */
class JwkTest {

    @Test
    fun rfc7638ExampleVectorMatches() {
        // RFC 7638 §3.1: the example RSA JWK and its published thumbprint.
        val jwk = mapOf(
            "kty" to "RSA",
            "n" to "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAt" +
                "VT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn6" +
                "4tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FD" +
                "W2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n9" +
                "1CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINH" +
                "aQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
            "e" to "AQAB",
        )
        val key = Jwk.publicKeyOf(jwk)
        assertThat(Jwk.thumbprintSha256Base64Url(key))
            .isEqualTo("NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs")
    }

    @Test
    fun ecRoundTripAndDeterminism() {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val key = generator.generateKeyPair().public

        // toMap -> publicKeyOf round-trips to the same key material.
        val restored = Jwk.publicKeyOf(Jwk.toMap(key))
        assertThat(restored.encoded).isEqualTo(key.encoded)

        // Thumbprints are 32 bytes, deterministic, and key-distinct.
        val print = Jwk.thumbprintSha256(key)
        assertThat(print).hasLength(Jwk.THUMBPRINT_LENGTH)
        assertThat(Jwk.thumbprintSha256(restored)).isEqualTo(print)
        val other = generator.generateKeyPair().public
        assertThat(Jwk.thumbprintSha256(other)).isNotEqualTo(print)
    }
}
