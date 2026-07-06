package com.jabauth.diagnostic.data

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Pure DER↔hex codec for persisting X.509 trust anchors as DataStore strings, plus a raw-bytes parser for
 * file import. No Android or DataStore dependency, so it is unit-tested on the plain JVM. Hex (rather than
 * `android.util.Base64`) keeps persistence pure-JVM and minSdk-24 safe (`java.util.Base64` is API 26+); the
 * size overhead is negligible for a handful of anchors.
 */
object CertCodec {

    /** The certificate's DER encoding as lowercase hex. */
    fun encode(cert: X509Certificate): String =
        cert.encoded.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    /** Parse a hex-encoded DER cert back to an [X509Certificate], or null if the hex/DER is malformed. */
    fun decode(hex: String): X509Certificate? = runCatching {
        require(hex.length % 2 == 0) { "odd-length hex" }
        val der = ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as? X509Certificate
    }.getOrNull()

    /** Parse raw certificate file bytes (DER or PEM) into an [X509Certificate], or null if not a cert. */
    fun parse(bytes: ByteArray): X509Certificate? = runCatching {
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as? X509Certificate
    }.getOrNull()
}
