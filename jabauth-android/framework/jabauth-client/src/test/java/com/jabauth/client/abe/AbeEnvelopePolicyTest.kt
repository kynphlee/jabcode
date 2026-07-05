package com.jabauth.client.abe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * The offline ABE seam: recover the cleartext access policy from an `ABE_SEALED` envelope
 * ([AbeEnvelope.policyOf]) and parse its infix form into the adjudicable AST ([AbePolicyParser]).
 *
 * The envelope bytes here are built with the exact server layout
 * (`org.nexus.jabauth.abe.encryption.AbeEnvelope.encode`) so this is a byte-parity guard, not a
 * self-fulfilling round-trip.
 */
class AbeEnvelopePolicyTest {

    /** Mirror of the server encoder — big-endian magic/version/suite + len-prefixed policy/ct/policyData. */
    private fun serverEnvelope(policy: String, ct: ByteArray = byteArrayOf(9, 9), policyData: ByteArray = byteArrayOf(7)): ByteArray {
        val p = policy.toByteArray(StandardCharsets.UTF_8)
        val buf = ByteBuffer.allocate(4 + 1 + 1 + 2 + 4 + 2 + p.size + ct.size + policyData.size)
        buf.put('A'.code.toByte()).put('B'.code.toByte()).put('E'.code.toByte()).put('1'.code.toByte())
        buf.put(0x01).put(0x01)
        buf.putShort(p.size.toShort()); buf.putInt(ct.size); buf.putShort(policyData.size.toShort())
        buf.put(p).put(ct).put(policyData)
        return buf.array()
    }

    @Test
    fun `policyOf recovers the cleartext policy from a server-format envelope`() {
        val env = serverEnvelope("role:inspector AND region:EU")
        assertEquals("role:inspector AND region:EU", AbeEnvelope.policyOf(env))
    }

    @Test
    fun `policyOf returns null for non-ABE1 bytes, truncated header, and null`() {
        assertNull(AbeEnvelope.policyOf(null))
        assertNull(AbeEnvelope.policyOf("not an envelope".toByteArray()))
        assertNull(AbeEnvelope.policyOf(byteArrayOf('A'.code.toByte(), 'B'.code.toByte()))) // too short
        val zeroPolicy = serverEnvelope("").copyOf() // policy_len = 0 → no adjudicable policy
        assertNull(AbeEnvelope.policyOf(zeroPolicy))
    }

    @Test
    fun `parser builds an AND of two leaves and adjudicates correctly`() {
        val policy = AbePolicyParser.parse("role:inspector AND region:EU")
        assertEquals(ABEPolicy.And(listOf(ABEPolicy.Leaf("role:inspector"), ABEPolicy.Leaf("region:EU"))), policy)
        assertEquals("(role:inspector AND region:EU)", policy.toString())
    }

    @Test
    fun `parser handles OR, nesting, single leaf, and flattens runs`() {
        assertEquals(ABEPolicy.Leaf("role:inspector"), AbePolicyParser.parse("role:inspector"))
        assertEquals(
            ABEPolicy.Or(listOf(ABEPolicy.Leaf("a"), ABEPolicy.Leaf("b"))),
            AbePolicyParser.parse("a OR b"),
        )
        // a AND b AND c → one n-ary AND, not nested pairs
        assertEquals(
            ABEPolicy.And(listOf(ABEPolicy.Leaf("a"), ABEPolicy.Leaf("b"), ABEPolicy.Leaf("c"))),
            AbePolicyParser.parse("a AND b AND c"),
        )
        // (role:inspector OR role:auditor) AND region:EU
        val nested = AbePolicyParser.parse("(role:inspector OR role:auditor) AND region:EU")
        assertEquals(
            ABEPolicy.And(
                listOf(
                    ABEPolicy.Or(listOf(ABEPolicy.Leaf("role:inspector"), ABEPolicy.Leaf("role:auditor"))),
                    ABEPolicy.Leaf("region:EU"),
                ),
            ),
            nested,
        )
    }

    @Test
    fun `parser is case-insensitive on keywords but preserves attribute case`() {
        assertEquals(
            ABEPolicy.And(listOf(ABEPolicy.Leaf("Role:Inspector"), ABEPolicy.Leaf("region:EU"))),
            AbePolicyParser.parse("Role:Inspector and region:EU"),
        )
    }

    @Test
    fun `parser rejects malformed input`() {
        assertNull(AbePolicyParser.parse(null))
        assertNull(AbePolicyParser.parse("   "))
        assertNull(AbePolicyParser.parse("role:inspector AND"))       // dangling operator
        assertNull(AbePolicyParser.parse("(role:inspector AND region:EU")) // unbalanced
        assertNull(AbePolicyParser.parse("role:inspector region:EU"))  // two attributes, no operator
    }

    @Test
    fun `end to end - envelope policy parsed then satisfied by the verifier attribute set`() {
        val env = serverEnvelope("role:inspector AND region:EU")
        val policy = AbePolicyParser.parse(AbeEnvelope.policyOf(env))!!
        assertTrue(satisfied(policy, setOf("role:inspector", "region:EU")))
        assertTrue(!satisfied(policy, setOf("role:inspector"))) // missing region:EU → denied
    }

    private fun satisfied(p: ABEPolicy, attrs: Set<String>): Boolean = when (p) {
        is ABEPolicy.Leaf -> p.attribute in attrs
        is ABEPolicy.And -> p.children.all { satisfied(it, attrs) }
        is ABEPolicy.Or -> p.children.any { satisfied(it, attrs) }
        is ABEPolicy.Threshold -> p.children.count { satisfied(it, attrs) } >= p.threshold
    }
}
