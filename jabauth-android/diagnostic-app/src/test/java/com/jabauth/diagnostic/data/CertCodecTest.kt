package com.jabauth.diagnostic.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** DER↔hex round-trip for trust-anchor persistence. Pure JVM (BouncyCastle-minted cert, no Android). */
class CertCodecTest {

    @Test fun `a certificate survives an encode-decode round-trip byte-for-byte`() {
        val cert = TestCerts.selfSignedEc("Round Trip Issuer")
        val decoded = CertCodec.decode(CertCodec.encode(cert))
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.encoded).isEqualTo(cert.encoded)
        assertThat(decoded).isEqualTo(cert)
    }

    @Test fun `encode is lowercase hex of the DER, twice its byte length`() {
        val cert = TestCerts.selfSignedEc("Hex Issuer")
        val hex = CertCodec.encode(cert)
        assertThat(hex).matches("[0-9a-f]+")
        assertThat(hex.length).isEqualTo(cert.encoded.size * 2)
    }

    @Test fun `malformed input decodes to null, never throws`() {
        assertThat(CertCodec.decode("zzzz")).isNull()      // even length, non-hex chars
        assertThat(CertCodec.decode("abc")).isNull()       // odd length
        assertThat(CertCodec.decode("deadbeef")).isNull()  // valid hex, invalid DER
        assertThat(CertCodec.decode("")).isNull()          // empty
    }
}
