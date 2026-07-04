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
}
