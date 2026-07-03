package com.jabauth.client.abe

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ABEPolicy.toString] must yield the human-readable boolean expression the class documents — e.g.
 * `(role:inspector AND region:EU)` — for every node type and nested structures.
 *
 * Regression guard for a Kotlin gotcha: `ABEPolicy` declares an `override fun toString()`, but its
 * subclasses are `data class`es. A non-`final` supertype `toString()` is SHADOWED by the data classes'
 * generated `toString()`, which would return `And(children=[Leaf(attribute=…)])` instead. These
 * assertions fail unless the supertype implementation is made authoritative (`final`).
 */
class ABEPolicyTest {

    private fun leaf(a: String) = ABEPolicy.Leaf(a)

    @Test
    fun `leaf renders as the bare attribute`() {
        assertEquals("role:admin", leaf("role:admin").toString())
    }

    @Test
    fun `AND renders as a parenthesised AND expression`() {
        val policy = ABEPolicy.And(listOf(leaf("role:inspector"), leaf("region:EU")))
        assertEquals("(role:inspector AND region:EU)", policy.toString())
    }

    @Test
    fun `OR renders as a parenthesised OR expression`() {
        val policy = ABEPolicy.Or(listOf(leaf("role:admin"), leaf("role:manager")))
        assertEquals("(role:admin OR role:manager)", policy.toString())
    }

    @Test
    fun `threshold renders as k OF the child list`() {
        val policy = ABEPolicy.Threshold(2, listOf(leaf("a"), leaf("b"), leaf("c")))
        assertEquals("(2 OF [a, b, c])", policy.toString())
    }

    @Test
    fun `nested policy renders recursively`() {
        val policy = ABEPolicy.And(
            listOf(leaf("role:inspector"), ABEPolicy.Or(listOf(leaf("dept:eng"), leaf("dept:research")))),
        )
        assertEquals("(role:inspector AND (dept:eng OR dept:research))", policy.toString())
    }

    @Test
    fun `toJson is unaffected and remains structural`() {
        assertEquals("""{"type":"LEAF","attribute":"role:admin"}""", leaf("role:admin").toJson())
    }
}
