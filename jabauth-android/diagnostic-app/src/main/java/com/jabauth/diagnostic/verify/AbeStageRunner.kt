package com.jabauth.diagnostic.verify

import com.jabauth.client.abe.ABEPolicy

/**
 * The ABE verification stage: adjudicates whether the verifier's attributes satisfy the sealed layer's
 * CP-ABE access policy — "who is authorized to open the sealed credential." Produces the policy
 * expression, per-attribute satisfied/missing chips, and — on deny — the **named failing clause** (the
 * single most user-hostile thing to get wrong, Principle A).
 *
 * This is offline **policy adjudication** — pure, monotone AND/OR/threshold over the attribute set, which
 * is exactly what determines whether the native CP-ABE decapsulation would succeed. The authoritative
 * cryptographic decap (native Rabe KEM) is a device-only confirmation, out of scope for this
 * JVM-testable adapter.
 *
 * Policy + verifier attributes arrive via injected seams:
 *  - [extractPolicy] pulls the access policy from the decoded symbol's `ABE_SEALED` section (interim
 *    until Payload v2 parsing lands — Phase 6).
 *  - [verifierAttributes] is the verifier's own attribute set (from Settings — interim).
 */
class AbeStageRunner(
    private val extractPolicy: (DecodedSymbol) -> ABEPolicy?,
    private val verifierAttributes: () -> Set<String>,
) : VerificationOrchestrator.StageRunner {

    override fun run(symbol: DecodedSymbol): StageResult {
        val policy = extractPolicy(symbol)
            ?: return StageResult(
                VerificationStage.ABE, StageState.PASS,
                reason = "No sealed ABE layer — nothing to authorize.",
            )
        return evaluate(policy, verifierAttributes())
    }

    /** Adjudicate [policy] against [attrs] → granted/denied ABE [StageResult]. Exposed for unit testing. */
    fun evaluate(policy: ABEPolicy, attrs: Set<String>): StageResult {
        val granted = policy.satisfiedBy(attrs)
        val detail = AbeDetail(
            policyExpression = policy.readable(),
            verifierAttributes = policy.leaves().map { PolicyAttribute(it, it in attrs) },
            granted = granted,
            failingClause = if (granted) null else policy.firstUnsatisfiedClause(attrs),
        )
        return if (granted) {
            StageResult(VerificationStage.ABE, StageState.PASS, detail = detail)
        } else {
            StageResult(
                VerificationStage.ABE, StageState.FAIL,
                reason = "Access denied: missing ${detail.failingClause}", detail = detail,
            )
        }
    }
}

// ── pure policy adjudication over the monotone AND/OR/threshold AST ──────────────────────────────────

private fun ABEPolicy.satisfiedBy(attrs: Set<String>): Boolean = when (this) {
    is ABEPolicy.Leaf -> attribute in attrs
    is ABEPolicy.And -> children.all { it.satisfiedBy(attrs) }
    is ABEPolicy.Or -> children.any { it.satisfiedBy(attrs) }
    is ABEPolicy.Threshold -> children.count { it.satisfiedBy(attrs) } >= threshold
}

/** Every leaf attribute, in policy order, de-duplicated — the chip set the ABE Policy view renders. */
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

/**
 * Render the policy as a human-readable boolean expression. Built here rather than via [ABEPolicy.toString]
 * because the AST nodes are `data class`es whose generated `toString()` shadows the sealed class's
 * intended readable one — so we render explicitly and deterministically.
 */
private fun ABEPolicy.readable(): String = when (this) {
    is ABEPolicy.Leaf -> attribute
    is ABEPolicy.And -> "(" + children.joinToString(" AND ") { it.readable() } + ")"
    is ABEPolicy.Or -> "(" + children.joinToString(" OR ") { it.readable() } + ")"
    is ABEPolicy.Threshold -> "($threshold OF [" + children.joinToString(", ") { it.readable() } + "])"
}
