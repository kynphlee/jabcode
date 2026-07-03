package com.jabauth.diagnostic.ui.verify

import com.jabauth.diagnostic.verify.AbeDetail
import com.jabauth.diagnostic.verify.CertChainDetail
import com.jabauth.diagnostic.verify.FormatProfile
import com.jabauth.diagnostic.verify.JwtDetail
import com.jabauth.diagnostic.verify.StageResult

/**
 * Pure content logic for the Verification HUD's per-stage sections: a [StageResult] → a header line and a
 * list of label/value rows drawn from its typed forensic payload. Kept separate from the composables so
 * the section content is unit-tested on the JVM (module convention: logic in unit tests, rendering is
 * instrumented). The composables render these rows in the existing `DiagnosticRow` idiom.
 */
object StageSummaries {

    /** The stage header, e.g. `PKI · WARN`. */
    fun header(result: StageResult): String = "${result.stage} · ${result.state}"

    /** Label/value detail rows: the stage's reason (when present) followed by its typed payload fields. */
    fun rows(result: StageResult): List<Pair<String, String>> = buildList {
        result.reason?.let { add("reason" to it) }
        when (val d = result.detail) {
            is CertChainDetail -> {
                add("chain" to "${d.nodes.size} cert(s) · root ${if (d.rootTrusted) "trusted" else "untrusted"}")
                add("revocation" to "${d.revocation.status} (${d.revocation.method})")
            }
            is JwtDetail -> {
                add("algorithm" to "${d.algorithm} (${if (d.algorithmAllowed) "allowed" else "rejected"})")
                d.tokenClass?.let { add("token_class" to it) }
                d.issuer?.let { add("issuer" to it) }
                d.expiry?.let { add("expiry" to it) }
                add("disclosed" to "${d.disclosedCount} of ${d.totalClaims} claims")
            }
            is AbeDetail -> {
                add("policy" to d.policyExpression)
                add("access" to if (d.granted) "granted" else "denied: missing ${d.failingClause}")
            }
            null -> {}
        }
    }

    /** The payload-format + capture-profile line, e.g. `Payload v2 · FIELD` or `Payload unknown`. */
    fun formatLine(fp: FormatProfile): String =
        "Payload ${fp.payloadVersion}" + (fp.coaProfile?.let { " · $it" } ?: "")
}
