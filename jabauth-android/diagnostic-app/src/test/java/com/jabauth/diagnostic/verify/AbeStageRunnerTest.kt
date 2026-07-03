package com.jabauth.diagnostic.verify

import com.google.common.truth.Truth.assertThat
import com.jabauth.client.abe.ABEPolicy
import org.junit.Test

/**
 * ABE-stage policy adjudication: granted/denied over a monotone AND/OR/threshold policy, the per-attribute
 * chips, and the named failing clause on deny (Principle A). Pure JVM — no native KEM.
 */
class AbeStageRunnerTest {

    private fun runner() = AbeStageRunner(extractPolicy = { null }, verifierAttributes = { emptySet() })
    private fun detail(r: StageResult) = r.detail as AbeDetail

    private fun leaf(a: String) = ABEPolicy.Leaf(a)
    private fun and(vararg c: ABEPolicy) = ABEPolicy.And(c.toList())
    private fun or(vararg c: ABEPolicy) = ABEPolicy.Or(c.toList())

    // ── granted ────────────────────────────────────────────────────────────────
    @Test fun `satisfied AND policy is granted with all chips satisfied`() {
        val policy = and(leaf("role:inspector"), leaf("region:EU"))
        val r = runner().evaluate(policy, setOf("role:inspector", "region:EU"))

        assertThat(r.state).isEqualTo(StageState.PASS)
        val d = detail(r)
        assertThat(d.granted).isTrue()
        assertThat(d.failingClause).isNull()
        assertThat(d.policyExpression).isEqualTo("(role:inspector AND region:EU)")
        assertThat(d.verifierAttributes.filter { it.satisfied }.map { it.name })
            .containsExactly("role:inspector", "region:EU")
    }

    // ── the design's canonical deny case ───────────────────────────────────────
    @Test fun `AND policy missing an attribute is denied and names the failing clause`() {
        val policy = and(leaf("role:inspector"), leaf("region:EU"))
        val r = runner().evaluate(policy, setOf("role:inspector", "region:US"))

        assertThat(r.state).isEqualTo(StageState.FAIL)
        val d = detail(r)
        assertThat(d.granted).isFalse()
        assertThat(d.failingClause).isEqualTo("region:EU")
        assertThat(r.reason).contains("region:EU")
        // the missing attribute chip is unsatisfied, the present one satisfied
        assertThat(d.verifierAttributes).containsExactly(
            PolicyAttribute("role:inspector", true),
            PolicyAttribute("region:EU", false),
        )
    }

    // ── OR ──────────────────────────────────────────────────────────────────────
    @Test fun `OR policy satisfied by one branch is granted`() {
        val r = runner().evaluate(or(leaf("role:admin"), leaf("role:manager")), setOf("role:manager"))
        assertThat(r.state).isEqualTo(StageState.PASS)
        assertThat(detail(r).granted).isTrue()
    }

    @Test fun `OR policy with no branch satisfied is denied, naming the disjunction`() {
        val r = runner().evaluate(or(leaf("role:admin"), leaf("role:manager")), setOf("role:guest"))
        assertThat(r.state).isEqualTo(StageState.FAIL)
        assertThat(detail(r).failingClause).contains("OR")
    }

    // ── threshold ────────────────────────────────────────────────────────────────
    @Test fun `threshold policy is granted at or above k and denied below`() {
        val policy = ABEPolicy.Threshold(2, listOf(leaf("a"), leaf("b"), leaf("c")))
        assertThat(runner().evaluate(policy, setOf("a", "b")).state).isEqualTo(StageState.PASS)
        assertThat(runner().evaluate(policy, setOf("a")).state).isEqualTo(StageState.FAIL)
    }

    // ── nested: chips enumerate every leaf ───────────────────────────────────────
    @Test fun `nested policy enumerates every leaf attribute as a chip`() {
        val policy = and(leaf("role:inspector"), or(leaf("dept:eng"), leaf("dept:research")))
        val d = detail(runner().evaluate(policy, setOf("role:inspector", "dept:eng")))
        assertThat(d.granted).isTrue()
        assertThat(d.verifierAttributes.map { it.name })
            .containsExactly("role:inspector", "dept:eng", "dept:research")
    }

    // ── run(symbol) seams ────────────────────────────────────────────────────────
    @Test fun `run passes when the symbol carries no sealed layer`() {
        val r = AbeStageRunner(extractPolicy = { null }, verifierAttributes = { emptySet() }).run(coaSymbol())
        assertThat(r.state).isEqualTo(StageState.PASS)
        assertThat(r.reason).contains("No sealed ABE layer")
    }

    @Test fun `run denies when the resolved policy is not satisfied by the verifier attributes`() {
        val policy = and(leaf("role:inspector"), leaf("region:EU"))
        val runner = AbeStageRunner(extractPolicy = { policy }, verifierAttributes = { setOf("role:inspector") })
        val r = runner.run(coaSymbol())
        assertThat(r.state).isEqualTo(StageState.FAIL)
        assertThat(detail(r).failingClause).isEqualTo("region:EU")
    }

    private fun coaSymbol() = DecodedSymbol(byteArrayOf(1), true, 10L, FormatProfile("v2", "FIELD"))
}
