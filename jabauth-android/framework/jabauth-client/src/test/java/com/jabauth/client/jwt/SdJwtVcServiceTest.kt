package com.jabauth.client.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Duration

/**
 * SD-JWT VC (RFC 9901) selective disclosure over the ES256 baseline, plus the
 * algorithm-allowlist parity checks. Mirrors the server `SdJwtVcServiceTest` and
 * `EcdsaSupportTest`.
 *
 * Pure-JVM (JCA EC keys + auth0 + Authlete), no Android APIs — runs as a plain
 * unit test.
 */
class SdJwtVcServiceTest {

    private lateinit var service: SdJwtVcService

    @Before
    fun setup() {
        service = SdJwtVcService()
    }

    private fun ecKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    private fun sampleCredential(): SdJwtVcRequest =
        SdJwtVcRequest.builder()
            .issuer("https://issuer.jabauth.example")
            .subject("coa-12345")
            .vct("https://credentials.jabauth.example/coa")
            .expiration(Duration.ofHours(1))
            .algorithm("ES256")
            .alwaysVisible(mapOf("origin" to "ai-generated"))
            .selectivelyDisclosable(
                mapOf(
                    "licenseTerms" to "CC-BY-4.0",
                    "ownerName" to "Acme Studios",
                    "batchId" to "B-2026-06-30",
                )
            )
            .build()

    @Test
    fun `issue ES256 credential carries Disclosures and full verify sees all claims`() {
        val keys = ecKeyPair()
        val sdJwt = service.issue(sampleCredential(), keys.private)

        val v = service.verify(sdJwt, keys.public)
        assertTrue(v.message, v.valid)
        // Every claim reconstructable when all Disclosures present.
        assertTrue(v.disclosedClaims["origin"] == "ai-generated")
        assertTrue(v.disclosedClaims["licenseTerms"] == "CC-BY-4.0")
        assertTrue(v.disclosedClaims["ownerName"] == "Acme Studios")
        assertTrue(v.disclosedClaims["batchId"] == "B-2026-06-30")
        assertTrue(v.disclosedClaims.containsKey("vct"))

        // The credential JWT header really is ES256.
        val parsed = com.authlete.sd.SDJWT.parse(sdJwt)
        assertTrue(JWT.decode(parsed.credentialJwt).algorithm == "ES256")
    }

    @Test
    fun `holder present subset - verifier sees revealed but not withheld`() {
        val keys = ecKeyPair()
        val issued = service.issue(sampleCredential(), keys.private)

        // Holder reveals only licenseTerms; withholds ownerName + batchId.
        val presentation = service.present(issued, setOf("licenseTerms"))

        val v = service.verify(presentation, keys.public)
        assertTrue(v.message, v.valid)
        assertTrue(v.disclosedClaims["origin"] == "ai-generated")       // always-visible
        assertTrue(v.disclosedClaims["licenseTerms"] == "CC-BY-4.0")    // revealed
        assertNull(v.disclosedClaims["ownerName"])                      // withheld
        assertNull(v.disclosedClaims["batchId"])                        // withheld
    }

    @Test
    fun `wrong key fails closed`() {
        val issuerKeys = ecKeyPair()
        val issued = service.issue(sampleCredential(), issuerKeys.private)

        // A verifier holding a different EC key must reject it.
        val otherKeys = ecKeyPair()
        val v = service.verify(issued, otherKeys.public)
        assertFalse(v.valid)
        assertTrue(v.message.contains("signature"))
    }

    @Test
    fun `allowlist rejects a token asserting none`() {
        // Craft an SD-JWT whose credential JWT declares alg=none.
        val noneJwt = JWT.create()
            .withIssuer("attacker")
            .withSubject("coa-x")
            .withClaim("_sd_alg", "sha-256")
            .sign(Algorithm.none())
        val sdJwt = com.authlete.sd.SDJWT(noneJwt, emptyList()).toString()

        val v = service.verify(sdJwt, ecKeyPair().public)
        assertFalse(v.valid)
        assertTrue(v.message.contains("Algorithm validation failed"))
    }

    @Test
    fun `allowlist rejects a token asserting HS256`() {
        val hsJwt = JWT.create()
            .withIssuer("attacker")
            .withSubject("coa-x")
            .withClaim("_sd_alg", "sha-256")
            .sign(Algorithm.HMAC256("public-key-treated-as-secret"))
        val sdJwt = com.authlete.sd.SDJWT(hsJwt, emptyList()).toString()

        val v = service.verify(sdJwt, ecKeyPair().public)
        assertFalse(v.valid)
        assertTrue(v.message.contains("Algorithm validation failed"))
    }

    @Test
    fun `JwtAlgorithms validate blocks none and HS but permits RS and ES`() {
        // none blocked
        assertThrows { JwtAlgorithms.validate("none") }
        assertThrows { JwtAlgorithms.validate("NONE") }
        // HS* blocked
        assertThrows { JwtAlgorithms.validate("HS256") }
        assertThrows { JwtAlgorithms.validate("HS512") }
        // RS*/ES* permitted (no throw)
        JwtAlgorithms.validate("RS256")
        JwtAlgorithms.validate("ES256")
        JwtAlgorithms.validate("ES512")
    }

    @Test
    fun `verifier fails closed on key alg mismatch`() {
        val ec = ecKeyPair()
        // ES256 header against ES public key is fine; RS256 against EC key mismatches.
        var mismatchRejected = false
        try {
            JwtAlgorithms.verifier("RS256", ec.public)
        } catch (e: IllegalArgumentException) {
            mismatchRejected = true
        }
        assertTrue("RS256 over an EC key must be rejected", mismatchRejected)

        // Sanity: ES private/public really are EC.
        assertTrue(ec.private is ECPrivateKey)
        assertTrue(ec.public is ECPublicKey)
    }

    private fun assertThrows(block: () -> Unit) {
        var threw = false
        try {
            block()
        } catch (e: SecurityException) {
            threw = true
        }
        assertTrue("expected SecurityException", threw)
    }
}
