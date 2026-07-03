package com.jabauth.diagnostic.verify

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.authlete.sd.SDJWT
import com.google.common.truth.Truth.assertThat
import com.jabauth.client.jwt.SdJwtVcRequest
import com.jabauth.client.jwt.SdJwtVcService
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Duration

/**
 * JWT-stage taxonomy over REAL crypto (JCA EC keys + auth0 + Authlete SD-JWT, pure JVM — no device).
 * Mints/presents/tampers real SD-JWT VCs and asserts the binary PASS/FAIL mapping + the selective-
 * disclosure split. JWT never emits WARN.
 */
class JwtStageRunnerTest {

    private val service = SdJwtVcService()

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun credential(
        expiration: Duration = Duration.ofHours(1),
        alwaysVisible: Map<String, Any> = mapOf("origin" to "ai-generated"),
    ): SdJwtVcRequest =
        SdJwtVcRequest.builder()
            .issuer("https://issuer.jabauth.example")
            .subject("coa-12345")
            .vct("https://credentials.jabauth.example/coa")
            .expiration(expiration)
            .algorithm("ES256")
            .alwaysVisible(alwaysVisible)
            .selectivelyDisclosable(
                mapOf("licenseTerms" to "CC-BY-4.0", "ownerName" to "Acme Studios", "batchId" to "B-2026-06-30")
            )
            .build()

    /** A runner whose seams are inert — the taxonomy tests drive `evaluate(token, key)` directly. */
    private fun runner() = JwtStageRunner(extractToken = { null }, resolveIssuerKey = { null }, sdJwt = service)

    private fun detail(r: StageResult): JwtDetail = r.detail as JwtDetail

    // ── PASS paths ─────────────────────────────────────────────────────────────
    @Test fun `valid ES256 credential is PASS with all claims revealed and nothing withheld`() {
        val keys = ecKeyPair()
        val token = service.issue(credential(), keys.private)

        val r = runner().evaluate(token, keys.public)

        assertThat(r.state).isEqualTo(StageState.PASS)
        val d = detail(r)
        assertThat(d.algorithm).isEqualTo("ES256")
        assertThat(d.algorithmAllowed).isTrue()
        assertThat(d.issuer).isEqualTo("https://issuer.jabauth.example")
        assertThat(d.revealedClaims.keys).containsAtLeast("origin", "licenseTerms", "ownerName", "batchId")
        assertThat(d.withheldDigests).isEmpty()
    }

    @Test fun `holder presentation reveals the chosen claim and seals the rest as verified digests`() {
        val keys = ecKeyPair()
        val issued = service.issue(credential(), keys.private)
        val presentation = service.present(issued, setOf("licenseTerms"))

        val r = runner().evaluate(presentation, keys.public)

        assertThat(r.state).isEqualTo(StageState.PASS)
        val d = detail(r)
        assertThat(d.revealedClaims).containsKey("licenseTerms")
        assertThat(d.revealedClaims).containsKey("origin")      // always-visible stays revealed
        assertThat(d.revealedClaims).doesNotContainKey("ownerName")
        assertThat(d.revealedClaims).doesNotContainKey("batchId")
        // ownerName + batchId withheld — present only as digests (not missing, not tampered).
        assertThat(d.withheldDigests).hasSize(2)
        assertThat(d.withheldDigests.map { it.digest }).doesNotContain("")
        assertThat(d.disclosedCount).isEqualTo(d.revealedClaims.size)
        assertThat(d.totalClaims).isEqualTo(d.revealedClaims.size + 2)
    }

    @Test fun `token_class is surfaced in the detail and not mixed into the revealed claims`() {
        val keys = ecKeyPair()
        val token = service.issue(
            credential(alwaysVisible = mapOf("origin" to "ai-generated", "token_class" to "ARTIFACT")),
            keys.private,
        )

        val d = detail(runner().evaluate(token, keys.public))

        assertThat(d.tokenClass).isEqualTo("ARTIFACT")
        assertThat(d.revealedClaims).doesNotContainKey("token_class")
    }

    // ── FAIL paths (binary — never WARN) ───────────────────────────────────────
    @Test fun `wrong issuer key fails closed on the signature`() {
        val issued = service.issue(credential(), ecKeyPair().private)
        val r = runner().evaluate(issued, ecKeyPair().public)      // different key
        assertThat(r.state).isEqualTo(StageState.FAIL)
        assertThat(r.reason).contains("signature")
    }

    @Test fun `a token asserting alg none is rejected by the allowlist`() {
        val noneJwt = JWT.create().withIssuer("attacker").withClaim("_sd_alg", "sha-256").sign(Algorithm.none())
        val sdJwt = SDJWT(noneJwt, emptyList()).toString()

        val r = runner().evaluate(sdJwt, ecKeyPair().public)

        assertThat(r.state).isEqualTo(StageState.FAIL)
        assertThat(r.reason).contains("Algorithm validation failed")
        assertThat(detail(r).algorithmAllowed).isFalse()
    }

    @Test fun `a token asserting HS256 is rejected (algorithm-confusion guard)`() {
        val hsJwt = JWT.create().withIssuer("attacker").withClaim("_sd_alg", "sha-256")
            .sign(Algorithm.HMAC256("public-key-as-secret"))
        val sdJwt = SDJWT(hsJwt, emptyList()).toString()

        val r = runner().evaluate(sdJwt, ecKeyPair().public)

        assertThat(r.state).isEqualTo(StageState.FAIL)
        assertThat(r.reason).contains("Algorithm validation failed")
    }

    @Test fun `an expired credential fails closed`() {
        val keys = ecKeyPair()
        val expired = service.issue(credential(expiration = Duration.ofMinutes(-1)), keys.private)
        val r = runner().evaluate(expired, keys.public)
        assertThat(r.state).isEqualTo(StageState.FAIL)
        assertThat(r.reason).ignoringCase().contains("expired")
    }

    @Test fun `a malformed token fails closed`() {
        val r = runner().evaluate("not.a.real.sdjwt", ecKeyPair().public)
        assertThat(r.state).isEqualTo(StageState.FAIL)
    }

    @Test fun `JWT stage never emits WARN`() {
        // Representative failing tokens must be FAIL, never the amber WARN state.
        val keys = ecKeyPair()
        val wrongKey = runner().evaluate(service.issue(credential(), keys.private), ecKeyPair().public)
        val none = runner().evaluate(
            SDJWT(JWT.create().withClaim("_sd_alg", "sha-256").sign(Algorithm.none()), emptyList()).toString(),
            keys.public,
        )
        assertThat(wrongKey.state).isNotEqualTo(StageState.WARN)
        assertThat(none.state).isNotEqualTo(StageState.WARN)
        assertThat(listOf(wrongKey.state, none.state)).containsExactly(StageState.FAIL, StageState.FAIL)
    }

    // ── run(symbol) seams ──────────────────────────────────────────────────────
    @Test fun `run fails closed when the payload carries no SD-JWT VC`() {
        val runner = JwtStageRunner(extractToken = { null }, resolveIssuerKey = { ecKeyPair().public })
        val r = runner.run(coaSymbol())
        assertThat(r.state).isEqualTo(StageState.FAIL)
        assertThat(r.reason).contains("No SD-JWT VC")
    }

    @Test fun `run fails closed when no issuer key can be resolved`() {
        val keys = ecKeyPair()
        val token = service.issue(credential(), keys.private)
        val runner = JwtStageRunner(extractToken = { token }, resolveIssuerKey = { null }, sdJwt = service)
        val r = runner.run(coaSymbol())
        assertThat(r.state).isEqualTo(StageState.FAIL)
        assertThat(r.reason).contains("issuer public key")
    }

    @Test fun `run passes for a valid token resolved from the symbol`() {
        val keys = ecKeyPair()
        val token = service.issue(credential(), keys.private)
        val runner = JwtStageRunner(extractToken = { token }, resolveIssuerKey = { keys.public }, sdJwt = service)
        val r = runner.run(coaSymbol())
        assertThat(r.state).isEqualTo(StageState.PASS)
    }

    private fun coaSymbol() = DecodedSymbol(
        payload = byteArrayOf(1, 2, 3), isCoaFormat = true, decodeLatencyMs = 10L,
        formatProfile = FormatProfile("v2", "FIELD"),
    )
}
