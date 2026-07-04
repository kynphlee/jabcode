package com.jabauth.diagnostic.ui.verify

import com.google.common.truth.Truth.assertThat
import com.jabauth.diagnostic.verify.CertChainDetail
import com.jabauth.diagnostic.verify.CertNode
import com.jabauth.diagnostic.verify.RevocationInfo
import com.jabauth.diagnostic.verify.RevocationStatus
import org.junit.Test

/**
 * Trust Anchor drill-down content: per-certificate rows, revocation rows, and the trust-store statement,
 * drawn from a [CertChainDetail]. Pure JVM — mirrors [StageSummariesTest] (no rendering, no `@RunWith`).
 */
class TrustAnchorContentTest {

    private fun node(
        subject: String = "CN=Leaf",
        issuer: String = "CN=Issuing CA",
        serial: String = "0A1B2C",
        validFrom: String = "2026-01-01",
        validTo: String = "2027-01-01",
        keyUsage: String = "digitalSignature",
        sha256Fingerprint: String = "AB:CD:EF",
        inTrustStore: Boolean = false,
    ) = CertNode(subject, issuer, serial, validFrom, validTo, keyUsage, sha256Fingerprint, inTrustStore)

    private fun chain(
        nodes: List<CertNode>,
        revocation: RevocationInfo = RevocationInfo("OCSP + CRL", RevocationStatus.VALID, "14s ago"),
    ) = CertChainDetail(nodes, revocation)

    @Test fun `certRows include subject, issuer and fingerprint`() {
        val rows = TrustAnchorContent.certRows(
            node(subject = "CN=Leaf", issuer = "CN=Issuing CA", sha256Fingerprint = "AB:CD:EF"),
        )
        assertThat(rows).contains("subject" to "CN=Leaf")
        assertThat(rows).contains("issuer" to "CN=Issuing CA")
        assertThat(rows).contains("sha-256" to "AB:CD:EF")
    }

    @Test fun `certRows render validity as a from-to range`() {
        val rows = TrustAnchorContent.certRows(node(validFrom = "2026-01-01", validTo = "2027-01-01"))
        assertThat(rows).contains("validity" to "2026-01-01 → 2027-01-01")
    }

    @Test fun `revocationRows show method and result`() {
        val rows = TrustAnchorContent.revocationRows(
            chain(listOf(node()), RevocationInfo("OCSP + CRL", RevocationStatus.VALID, "14s ago")),
        )
        assertThat(rows).contains("method" to "OCSP + CRL")
        assertThat(rows).contains("result" to "VALID")
    }

    @Test fun `revocationRows render UNKNOWN_OFFLINE as its enum name`() {
        val rows = TrustAnchorContent.revocationRows(
            chain(listOf(node()), RevocationInfo("OCSP", RevocationStatus.UNKNOWN_OFFLINE, null)),
        )
        assertThat(rows).contains("result" to "UNKNOWN_OFFLINE")
    }

    @Test fun `revocationRows include the checked label when present`() {
        val rows = TrustAnchorContent.revocationRows(
            chain(listOf(node()), RevocationInfo("OCSP + CRL", RevocationStatus.VALID, "14s ago")),
        )
        assertThat(rows).contains("checked" to "14s ago")
    }

    @Test fun `revocationRows skip the checked row when checkedLabel is null`() {
        val rows = TrustAnchorContent.revocationRows(
            chain(listOf(node()), RevocationInfo("OCSP", RevocationStatus.NOT_CHECKED, null)),
        )
        assertThat(rows.map { it.first }).doesNotContain("checked")
    }

    @Test fun `trustStatement reads in trust store when the root anchor is trusted`() {
        // rootTrusted = nodes.last().inTrustStore
        val d = chain(listOf(node(inTrustStore = false), node(subject = "CN=Root", inTrustStore = true)))
        assertThat(d.rootTrusted).isTrue()
        assertThat(TrustAnchorContent.trustStatement(d)).isEqualTo("in trust store")
    }

    @Test fun `trustStatement reads not in trust store when the root anchor is untrusted`() {
        val d = chain(listOf(node(inTrustStore = true), node(subject = "CN=Root", inTrustStore = false)))
        assertThat(d.rootTrusted).isFalse()
        assertThat(TrustAnchorContent.trustStatement(d)).isEqualTo("not in trust store")
    }
}
