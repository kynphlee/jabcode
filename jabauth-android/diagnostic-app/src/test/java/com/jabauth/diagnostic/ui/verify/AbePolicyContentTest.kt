package com.jabauth.diagnostic.ui.verify

import com.google.common.truth.Truth.assertThat
import com.jabauth.diagnostic.verify.AbeDetail
import com.jabauth.diagnostic.verify.PolicyAttribute
import org.junit.Test

/**
 * ABE Policy drill-down content: the plain-language granted/denied verdict line and the attribute chip
 * models. Pure JVM — no rendering. The deny wording is Principle A (name the failing clause), so it is the
 * most load-bearing assertion here.
 */
class AbePolicyContentTest {

    @Test fun `verdictLine on grant echoes the whole policy expression`() {
        val d = AbeDetail(
            policyExpression = "(role:inspector AND region:EU)",
            verifierAttributes = listOf(
                PolicyAttribute("role:inspector", true),
                PolicyAttribute("region:EU", true),
            ),
            granted = true,
        )
        assertThat(AbePolicyContent.verdictLine(d))
            .isEqualTo("Access granted: satisfies (role:inspector AND region:EU)")
    }

    @Test fun `verdictLine on deny names the failing clause`() {
        val d = AbeDetail(
            policyExpression = "(role:inspector AND region:EU)",
            verifierAttributes = listOf(PolicyAttribute("role:inspector", true)),
            granted = false,
            failingClause = "region:EU",
        )
        assertThat(AbePolicyContent.verdictLine(d)).isEqualTo("Access denied: missing region:EU")
    }

    @Test fun `attributes returns the chips with their satisfied flags for a mixed example`() {
        val d = AbeDetail(
            policyExpression = "(role:inspector AND region:EU)",
            verifierAttributes = listOf(
                PolicyAttribute("role:inspector", true),
                PolicyAttribute("region:EU", false),
            ),
            granted = false,
            failingClause = "region:EU",
        )
        val chips = AbePolicyContent.attributes(d)
        assertThat(chips).hasSize(2)
        assertThat(chips).containsExactly(
            PolicyAttribute("role:inspector", true),
            PolicyAttribute("region:EU", false),
        ).inOrder()
    }

    // ── deny example (shown only on a granted verdict — Principle A by counter-example) ────────────────

    private fun granted(policy: String) = AbeDetail(
        policyExpression = policy,
        verifierAttributes = emptyList(), // irrelevant to the fabricated example
        granted = true,
    )

    @Test fun `denyExample is null when the real verdict is already denied`() {
        val denied = AbeDetail(
            policyExpression = "(role:inspector AND region:EU)",
            verifierAttributes = listOf(PolicyAttribute("role:inspector", true)),
            granted = false,
            failingClause = "region:EU",
        )
        assertThat(AbePolicyContent.denyExample(denied)).isNull()
    }

    @Test fun `denyExample on an AND policy drops exactly one leaf and names it`() {
        val e = AbePolicyContent.denyExample(granted("(role:inspector AND region:EU)"))!!
        assertThat(e.illustrative).isFalse()
        // same policy, all-but-one satisfied: the first leaf present, the second the near-miss
        assertThat(e.attributes).containsExactly(
            PolicyAttribute("role:inspector", true),
            PolicyAttribute("region:EU", false),
        ).inOrder()
        assertThat(e.failingClause).isEqualTo("region:EU")
        assertThat(AbePolicyContent.denyExampleLine(e))
            .isEqualTo("missing region:EU — names the failing clause, not a generic \"denied\" (principle a).")
    }

    @Test fun `denyExample derives from the actual policy, not a fixed template`() {
        val e = AbePolicyContent.denyExample(granted("(clearance:secret AND dept:engineering)"))!!
        assertThat(e.illustrative).isFalse()
        assertThat(e.attributes.map { it.name }).containsExactly("clearance:secret", "dept:engineering").inOrder()
        // exactly one leaf is the near-miss, and it is the named failing clause
        val missing = e.attributes.filterNot { it.satisfied }
        assertThat(missing).hasSize(1)
        assertThat(e.failingClause).isEqualTo(missing.single().name)
    }

    @Test fun `denyExample names the sub-expression when the failing clause is a nested OR`() {
        // role:inspector is satisfiable alone; dropping it forces the AND to fail on the OR branch.
        val e = AbePolicyContent.denyExample(
            granted("(role:inspector AND (dept:eng OR dept:research))"),
        )!!
        assertThat(e.illustrative).isFalse()
        assertThat(e.failingClause).isEqualTo("role:inspector")
    }

    @Test fun `denyExample falls back to a labelled illustrative example for a pure OR policy`() {
        // A pure OR still grants when only one branch is dropped, so no honest all-but-one deny exists.
        val e = AbePolicyContent.denyExample(granted("(role:admin OR role:manager)"))!!
        assertThat(e.illustrative).isTrue()
        assertThat(e.failingClause).isEqualTo("region:EU")
        assertThat(e.attributes).containsExactly(
            PolicyAttribute("role:inspector", true),
            PolicyAttribute("region:US", false),
        ).inOrder()
    }

    @Test fun `denyExample falls back to illustrative when the policy cannot be parsed`() {
        val e = AbePolicyContent.denyExample(granted("(role:inspector AND"))!! // unbalanced → unparseable
        assertThat(e.illustrative).isTrue()
        assertThat(e.failingClause).isEqualTo("region:EU")
    }
}
