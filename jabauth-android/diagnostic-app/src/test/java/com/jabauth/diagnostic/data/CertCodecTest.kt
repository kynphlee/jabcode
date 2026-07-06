package com.jabauth.diagnostic.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** DER↔hex round-trip + raw-bytes parse for trust-anchor persistence. Pure JVM (BouncyCastle cert, no Android). */
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

    @Test fun `malformed hex decodes to null, never throws`() {
        assertThat(CertCodec.decode("zzzz")).isNull()      // even length, non-hex chars
        assertThat(CertCodec.decode("abc")).isNull()       // odd length
        assertThat(CertCodec.decode("deadbeef")).isNull()  // valid hex, invalid DER
        assertThat(CertCodec.decode("")).isNull()          // empty
    }

    @Test fun `parse reads raw DER cert bytes (the file-import path)`() {
        val cert = TestCerts.selfSignedEc("File Import")
        assertThat(CertCodec.parse(cert.encoded)).isEqualTo(cert)
    }

    @Test fun `parse returns null for non-certificate bytes`() {
        assertThat(CertCodec.parse("not a cert".toByteArray())).isNull()
        assertThat(CertCodec.parse(ByteArray(0))).isNull()
    }
}
