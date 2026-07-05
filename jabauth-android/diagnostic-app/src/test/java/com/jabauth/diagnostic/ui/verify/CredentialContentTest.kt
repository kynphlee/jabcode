package com.jabauth.diagnostic.ui.verify

import com.google.common.truth.Truth.assertThat
import com.jabauth.diagnostic.verify.JwtDetail
import com.jabauth.diagnostic.verify.WithheldClaim
import org.junit.Test

/**
 * Credential drill-down content: the disclosure header, the revealed/sealed row split, and the envelope
 * meta rows drawn from a [JwtDetail]. Pure JVM (JUnit4 + Truth) — mirrors StageSummariesTest.
 */
class CredentialContentTest {

    /** A partly-disclosed credential: 2 revealed claims, 2 withheld digests, all meta fields set. */
    private fun partial() = JwtDetail(
        algorithm = "ES256",
        algorithmAllowed = true,
        tokenClass = "coa.field.v2",
        issuer = "did:example:issuer",
        issuedAt = "2026-01-01T00:00:00Z",
        expiry = "2027-01-01T00:00:00Z",
        revealedClaims = linkedMapOf("given_name" to "Ada", "role" to "inspector"),
        withheldDigests = listOf(WithheldClaim("birthdate", "sha256:aa"), WithheldClaim("address", "sha256:bb")),
    )

    @Test fun `header reads N of M claims disclosed`() {
        assertThat(CredentialContent.header(partial())).isEqualTo("2 of 4 claims disclosed")
    }

    @Test fun `revealedRows are the revealed claims in map order`() {
        assertThat(CredentialContent.revealedRows(partial()))
            .containsExactly("given_name" to "Ada", "role" to "inspector")
            .inOrder()
    }

    @Test fun `sealedRows are exactly the withheld digests`() {
        val d = partial()
        assertThat(CredentialContent.sealedRows(d)).isEqualTo(d.withheldDigests)
    }

    @Test fun `metaRows include token_class, issuer and the iat to exp range`() {
        assertThat(CredentialContent.metaRows(partial())).containsExactly(
            "token_class" to "coa.field.v2",
            "issuer" to "did:example:issuer",
            "issued/expiry" to "2026-01-01T00:00:00Z → 2027-01-01T00:00:00Z",
        ).inOrder()
    }

    @Test fun `issued-expiry falls back to expiry alone when iat is absent`() {
        assertThat(CredentialContent.metaRows(partial().copy(issuedAt = null)))
            .contains("issued/expiry" to "2027-01-01T00:00:00Z")
    }

    @Test fun `metaRows skip null envelope fields`() {
        val onlyIssuer = JwtDetail(
            algorithm = "ES256", algorithmAllowed = true,
            tokenClass = null, issuer = "iss", issuedAt = null, expiry = null,
            revealedClaims = emptyMap(), withheldDigests = emptyList(),
        )
        assertThat(CredentialContent.metaRows(onlyIssuer)).containsExactly("issuer" to "iss")

        val none = JwtDetail(
            algorithm = "ES256", algorithmAllowed = true,
            tokenClass = null, issuer = null, issuedAt = null, expiry = null,
            revealedClaims = emptyMap(), withheldDigests = emptyList(),
        )
        assertThat(CredentialContent.metaRows(none)).isEmpty()
    }

    @Test fun `a fully-disclosed credential has empty sealedRows`() {
        val full = JwtDetail(
            algorithm = "ES256", algorithmAllowed = true,
            tokenClass = "coa.field.v2", issuer = "iss", issuedAt = "iat", expiry = "exp",
            revealedClaims = linkedMapOf("given_name" to "Ada", "role" to "inspector"),
            withheldDigests = emptyList(),
        )
        assertThat(CredentialContent.sealedRows(full)).isEmpty()
        assertThat(CredentialContent.header(full)).isEqualTo("2 of 2 claims disclosed")
    }
}
