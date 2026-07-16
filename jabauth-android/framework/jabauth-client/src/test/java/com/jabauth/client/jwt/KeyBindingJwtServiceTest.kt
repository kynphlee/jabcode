package com.jabauth.client.jwt

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Duration

/**
 * WP-C mobile: holder binding on the device. A holder-bound record verifies ONLY with a valid
 * KB-JWT signed by the bound key; a copied record fails every substitute path; bearer records
 * unregressed. JVM tests use in-memory EC keypairs; production signs via the Android-Keystore
 * [HolderKeys] (same JCA surface — the flow is identical).
 */
class KeyBindingJwtServiceTest {

    private companion object {
        const val AUD = "https://verifier.jabauth.example"
        const val NONCE = "n-4471c9"
        const val ISSUER = "https://issuer.jabauth.example"
    }

    private val sdJwt = SdJwtVcService()
    private val kb = KeyBindingJwtService()
    private val issuerKeys = ecKeyPair()
    private val holderKeys = ecKeyPair()
    private val attackerKeys = ecKeyPair()

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

    private fun boundRequest() = SdJwtVcRequest(
        issuer = ISSUER,
        subject = "item-kb-1",
        vct = "jabauth:verification-event",
        alwaysVisible = mapOf(
            "event_type" to "human_inspection",
            SdJwtVcService.CLAIM_CNF to mapOf(
                SdJwtVcService.CNF_JKT to Jwk.thumbprintSha256Base64Url(holderKeys.public)
            ),
        ),
        selectivelyDisclosable = mapOf("inspector_tier" to "facility-senior"),
    )

    private fun bearerRequest() = SdJwtVcRequest(
        issuer = ISSUER,
        subject = "item-bearer",
        vct = "jabauth:verification-event",
        alwaysVisible = mapOf("event_type" to "ct_scan"),
        selectivelyDisclosable = emptyMap(),
    )

    @Test
    fun boundRecordWithValidKbJwtVerifies_andHolderReVerifies() {
        val record = sdJwt.issue(boundRequest(), issuerKeys.private)
        val kbJwt = kb.issue(holderKeys.private, holderKeys.public, AUD, NONCE, record)

        val v = sdJwt.verifyPresentation(record, issuerKeys.public, kbJwt, AUD, NONCE)
        assertThat(v.valid).isTrue()
        assertThat(v.disclosedClaims["event_type"]).isEqualTo("human_inspection")

        // The holder re-verifies their own record — a fresh KB-JWT per challenge.
        val again = kb.issue(holderKeys.private, holderKeys.public, AUD, "n-second", record)
        assertThat(
            sdJwt.verifyPresentation(record, issuerKeys.public, again, AUD, "n-second").valid
        ).isTrue()
    }

    @Test
    fun copiedRecordFailsEverySubstitutePath() {
        val record = sdJwt.issue(boundRequest(), issuerKeys.private)

        // 1. No KB-JWT at all.
        assertThat(
            sdJwt.verifyPresentation(record, issuerKeys.public, null, AUD, NONCE).valid
        ).isFalse()

        // 2. Plain verify() is not a bypass for a bound record.
        val plain = sdJwt.verify(record, issuerKeys.public)
        assertThat(plain.valid).isFalse()
        assertThat(plain.message).contains("holder-bound")

        // 3. A thief's own key: the thumbprint gate refuses before the signature matters.
        val forged = kb.issue(attackerKeys.private, attackerKeys.public, AUD, NONCE, record)
        val v = sdJwt.verifyPresentation(record, issuerKeys.public, forged, AUD, NONCE)
        assertThat(v.valid).isFalse()
        assertThat(v.message).contains("cnf.jkt")
    }

    @Test
    fun challengeDiscipline_nonceAudienceFreshnessAndSdHash() {
        val record = sdJwt.issue(boundRequest(), issuerKeys.private)
        val jkt = Jwk.thumbprintSha256Base64Url(holderKeys.public)
        val kbJwt = kb.issue(holderKeys.private, holderKeys.public, AUD, NONCE, record)

        assertThat(kb.verify(kbJwt, jkt, AUD, "other-nonce", record).valid).isFalse()
        assertThat(kb.verify(kbJwt, jkt, "https://other.example", NONCE, record).valid).isFalse()
        assertThat(kb.verify(kbJwt, jkt, AUD, NONCE, record, Duration.ZERO).valid)
            .isFalse() // zero freshness window refuses any iat

        // A proof minted for the full record cannot ride a different presentation.
        val presented = sdJwt.present(record, emptySet())
        if (presented != record) {
            assertThat(kb.verify(kbJwt, jkt, AUD, NONCE, presented).valid).isFalse()
        }
    }

    @Test
    fun bearerRecordsUnregressed() {
        val bearer = sdJwt.issue(bearerRequest(), issuerKeys.private)
        assertThat(sdJwt.verify(bearer, issuerKeys.public).valid).isTrue()
        assertThat(
            sdJwt.verifyPresentation(bearer, issuerKeys.public, null, AUD, NONCE).valid
        ).isTrue()
    }
}
