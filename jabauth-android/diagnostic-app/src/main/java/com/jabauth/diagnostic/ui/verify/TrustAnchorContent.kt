package com.jabauth.diagnostic.ui.verify

import com.jabauth.diagnostic.verify.CertChainDetail
import com.jabauth.diagnostic.verify.CertNode

/**
 * Pure content logic for the Trust Anchor / certificate-detail drill-down (Phase 4, "Flow B"): a
 * [CertChainDetail] → the per-certificate label/value rows, the revocation rows, and the plain-language
 * trust-store statement. Kept separate from the composables so the section content is unit-tested on the
 * JVM (module convention: logic in unit tests, rendering is instrumented — mirrors [StageSummaries]). The
 * composable ([TrustAnchorScreen]) renders these rows in the existing `DiagnosticRow` idiom.
 */
object TrustAnchorContent {

    /** Label/value rows for one certificate in the chain (subject → issuer → serial → validity → key usage → fingerprint). */
    fun certRows(node: CertNode): List<Pair<String, String>> = listOf(
        "subject" to node.subject,
        "issuer" to node.issuer,
        "serial" to node.serial,
        "validity" to "${node.validFrom} → ${node.validTo}",
        "key usage" to node.keyUsage,
        "sha-256" to node.sha256Fingerprint,
    )

    /** Revocation rows: method, result (the status name), and — when present — the checked-at label. */
    fun revocationRows(d: CertChainDetail): List<Pair<String, String>> = buildList {
        add("method" to d.revocation.method)
        add("result" to d.revocation.status.name)
        d.revocation.checkedLabel?.let { add("checked" to it) }
    }

    /** The plain-language trust-store statement for the chain's root anchor (Principle B). */
    fun trustStatement(d: CertChainDetail): String =
        if (d.rootTrusted) "in trust store" else "not in trust store"
}
