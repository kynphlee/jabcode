package com.jabauth.diagnostic.ui.verify

import com.jabauth.diagnostic.verify.AbeDetail
import com.jabauth.diagnostic.verify.PolicyAttribute

/**
 * Pure content logic for the **ABE Policy / access** drill-down (Phase 4, "Flow B"): an [AbeDetail] →
 * the one plain-language verdict line and the verifier's attribute chip models. Kept free of Compose so
 * the wording — the most user-hostile thing to get wrong — is unit-tested on the JVM (module convention:
 * logic in unit tests, rendering is instrumented). The composables in [AbePolicyScreen] render these.
 *
 * Principle A — a deny always *names* the failing clause. [verdictLine] surfaces [AbeDetail.failingClause]
 * verbatim on deny so the operator sees exactly which attribute the credential lacks, never a bare "denied".
 */
object AbePolicyContent {

    /**
     * The plain-language access verdict.
     *  - granted: `Access granted: satisfies (role:inspector AND region:EU)` — echoes the whole policy.
     *  - denied:  `Access denied: missing region:EU` — names the failing clause (Principle A).
     */
    fun verdictLine(d: AbeDetail): String =
        if (d.granted) "Access granted: satisfies ${d.policyExpression}"
        else "Access denied: missing ${d.failingClause}"

    /** The verifier's attributes as chip models (pass-through of [AbeDetail.verifierAttributes]). */
    fun attributes(d: AbeDetail): List<PolicyAttribute> = d.verifierAttributes
}
