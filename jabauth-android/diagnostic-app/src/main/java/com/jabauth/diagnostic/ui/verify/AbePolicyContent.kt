package com.jabauth.diagnostic.ui.verify

import com.jabauth.client.abe.ABEPolicy
import com.jabauth.client.abe.AbePolicyParser
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
 * [denyExample] teaches that same principle on a *granted* verdict by fabricating a same-policy near-miss.
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

    /**
     * A hypothetical "DENY EXAMPLE · SAME POLICY" — teaches Principle A (name the failing clause, not a
     * generic "denied") on a *granted* verdict by showing what a near-miss against the **same** policy
     * looks like.
     *
     * Derived from [AbeDetail.policyExpression]: parse it, then fabricate a verifier attribute set that
     * satisfies every leaf *except one*, chosen so the policy denies (an all-but-one near-miss). The
     * named [DenyExample.failingClause] is what the real deny card would surface for that set.
     *
     * Returns `null` unless the real verdict is **granted** — when the credential was already denied, the
     * real deny card already teaches the principle, so a fabricated one would only duplicate it.
     *
     * Honesty: the returned example is always clearly hypothetical (rendered under a "DENY EXAMPLE" header,
     * never presented as the real verdict). When the policy cannot be parsed, or no single-attribute
     * omission can make it deny (e.g. a pure `OR`, where dropping one branch still grants), a clearly
     * labelled [DenyExample.illustrative] fallback is returned instead of forcing a dishonest example.
     */
    fun denyExample(d: AbeDetail): DenyExample? {
        if (!d.granted) return null
        val policy = AbePolicyParser.parse(d.policyExpression) ?: return illustrativeFallback()

        val leaves = policy.leaves()
        if (leaves.isEmpty()) return illustrativeFallback()

        // Find one leaf whose omission (all others present) makes the whole policy deny — the honest
        // all-but-one near-miss. Denying leaves inside an AND qualify; a lone OR/threshold branch does not.
        // Prefer the *last* such leaf: it satisfies the leading clauses and fails on the trailing one, the
        // friendliest "you have everything but this" near-miss and the one the design mock illustrates.
        val omitted = leaves.lastOrNull { candidate ->
            val present = leaves.toSet() - candidate
            !policy.satisfiedBy(present)
        } ?: return illustrativeFallback()

        val present = leaves.toSet() - omitted
        val chips = leaves.map { PolicyAttribute(it, it in present) }
        return DenyExample(
            attributes = chips,
            failingClause = policy.firstUnsatisfiedClause(present),
            illustrative = false,
        )
    }

    /**
     * The canonical illustrative near-miss, used when the real policy cannot be parsed or has no
     * single-attribute deny. Mirrors the design mock's example verbatim and is explicitly flagged
     * [DenyExample.illustrative] so the UI can mark it as generic rather than same-policy-derived.
     */
    private fun illustrativeFallback(): DenyExample = DenyExample(
        attributes = listOf(
            PolicyAttribute("role:inspector", true),
            PolicyAttribute("region:US", false),
        ),
        failingClause = "region:EU",
        illustrative = true,
    )

    /**
     * A fabricated same-policy near-miss for the deny-example section.
     *
     * @property attributes the hypothetical verifier attribute set (green = satisfied, red = missing)
     * @property failingClause the clause the real deny card would name for [attributes] (Principle A)
     * @property illustrative true when this is the generic fallback (policy unparseable / no single-attr
     *   deny) rather than a set derived from the screen's actual policy — the UI marks it as such
     */
    data class DenyExample(
        val attributes: List<PolicyAttribute>,
        val failingClause: String,
        val illustrative: Boolean,
    )

    /** The plain-language deny line for the example — mirrors [verdictLine]'s deny wording (Principle A). */
    fun denyExampleLine(e: DenyExample): String =
        "missing ${e.failingClause} — names the failing clause, not a generic \"denied\" (principle a)."

    // ── pure adjudication over the monotone AND/OR/threshold AST ─────────────────────────────────────
    // Local re-derivation of the same evaluation AbeStageRunner performs, so the deny example is computed
    // exactly as a real verdict would be — kept here (not reused across modules) to leave the runner's
    // adjudication private and this content object self-contained + JVM-unit-testable.

    private fun ABEPolicy.satisfiedBy(attrs: Set<String>): Boolean = when (this) {
        is ABEPolicy.Leaf -> attribute in attrs
        is ABEPolicy.And -> children.all { it.satisfiedBy(attrs) }
        is ABEPolicy.Or -> children.any { it.satisfiedBy(attrs) }
        is ABEPolicy.Threshold -> children.count { it.satisfiedBy(attrs) } >= threshold
    }

    /** Every leaf attribute, in policy order, de-duplicated — the chip set the example renders. */
    private fun ABEPolicy.leaves(): List<String> = when (this) {
        is ABEPolicy.Leaf -> listOf(attribute)
        is ABEPolicy.And -> children.flatMap { it.leaves() }
        is ABEPolicy.Or -> children.flatMap { it.leaves() }
        is ABEPolicy.Threshold -> children.flatMap { it.leaves() }
    }.distinct()

    /**
     * Name the clause that caused a deny (Principle A): the first unsatisfied leaf inside an AND, or the
     * whole OR / threshold sub-expression that fell short. Only meaningful when the policy is unsatisfied.
     */
    private fun ABEPolicy.firstUnsatisfiedClause(attrs: Set<String>): String = when (this) {
        is ABEPolicy.Leaf -> attribute
        is ABEPolicy.And -> children.first { !it.satisfiedBy(attrs) }.firstUnsatisfiedClause(attrs)
        is ABEPolicy.Or -> readable()
        is ABEPolicy.Threshold -> readable()
    }

    /** Render the policy as a human-readable boolean expression (mirrors the runner's `readable`). */
    private fun ABEPolicy.readable(): String = when (this) {
        is ABEPolicy.Leaf -> attribute
        is ABEPolicy.And -> "(" + children.joinToString(" AND ") { it.readable() } + ")"
        is ABEPolicy.Or -> "(" + children.joinToString(" OR ") { it.readable() } + ")"
        is ABEPolicy.Threshold -> "($threshold OF [" + children.joinToString(", ") { it.readable() } + "])"
    }
}
