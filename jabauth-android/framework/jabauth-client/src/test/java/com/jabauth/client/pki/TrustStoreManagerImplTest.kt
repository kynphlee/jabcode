package com.jabauth.client.pki

import com.google.common.truth.Truth.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

/** In-memory trust-anchor store: add / retrieve / remove / clear. Pure JVM. */
class TrustStoreManagerImplTest {

    companion object {
        init {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val store = TrustStoreManagerImpl()

    private fun selfSigned(cn: String): X509Certificate {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=$cn")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            name, BigInteger.valueOf(now), Date(now), Date(now + 31536000000L), name, kp.public,
        )
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(kp.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    @Test fun `add then retrieve by alias`() {
        val ca = selfSigned("Root A")
        assertThat(store.addTrustedCA("a", ca)).isTrue()
        assertThat(store.getTrustedCA("a")).isEqualTo(ca)
        assertThat(store.isTrusted("a")).isTrue()
        assertThat(store.isTrusted("missing")).isFalse()
    }

    @Test fun `getAll returns every anchor`() {
        store.addTrustedCA("a", selfSigned("Root A"))
        store.addTrustedCA("b", selfSigned("Root B"))
        assertThat(store.getAllTrustedCAs()).hasSize(2)
    }

    @Test fun `remove and clear`() {
        store.addTrustedCA("a", selfSigned("Root A"))
        assertThat(store.removeTrustedCA("a")).isTrue()
        assertThat(store.removeTrustedCA("a")).isFalse()
        store.addTrustedCA("b", selfSigned("Root B"))
        assertThat(store.clear()).isTrue()
        assertThat(store.getAllTrustedCAs()).isEmpty()
    }
}
