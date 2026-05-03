package com.jabauth.client.abe

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for ABEPolicyEngine
 * 
 * Tests ABE policy evaluation, parsing, and validation.
 * Coverage Target: 80%+
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ABEPolicyEngineTest {
    
    private lateinit var engine: ABEPolicyEngine
    
    @Before
    fun setup() {
        engine = TestABEPolicyEngineImpl()
    }
    
    @Test
    fun `evaluatePolicy returns true for leaf node with matching attribute`() {
        val policy = ABEPolicy.Leaf("role:admin")
        val userAttributes = setOf("role:admin", "dept:engineering")
        
        val result = engine.evaluatePolicy(policy, userAttributes)
        
        assertThat(result).isTrue()
    }
    
    @Test
    fun `evaluatePolicy returns false for leaf node without matching attribute`() {
        val policy = ABEPolicy.Leaf("role:admin")
        val userAttributes = setOf("role:user", "dept:engineering")
        
        val result = engine.evaluatePolicy(policy, userAttributes)
        
        assertThat(result).isFalse()
    }
    
    @Test
    fun `evaluatePolicy returns true for AND node when all children satisfied`() {
        val policy = ABEPolicy.And(
            listOf(
                ABEPolicy.Leaf("role:admin"),
                ABEPolicy.Leaf("dept:engineering")
            )
        )
        val userAttributes = setOf("role:admin", "dept:engineering", "clearance:secret")
        
        val result = engine.evaluatePolicy(policy, userAttributes)
        
        assertThat(result).isTrue()
    }
    
    @Test
    fun `evaluatePolicy returns false for AND node when one child unsatisfied`() {
        val policy = ABEPolicy.And(
            listOf(
                ABEPolicy.Leaf("role:admin"),
                ABEPolicy.Leaf("dept:engineering")
            )
        )
        val userAttributes = setOf("role:admin", "dept:sales")
        
        val result = engine.evaluatePolicy(policy, userAttributes)
        
        assertThat(result).isFalse()
    }
    
    @Test
    fun `evaluatePolicy returns true for OR node when one child satisfied`() {
        val policy = ABEPolicy.Or(
            listOf(
                ABEPolicy.Leaf("role:admin"),
                ABEPolicy.Leaf("role:manager")
            )
        )
        val userAttributes = setOf("role:manager", "dept:sales")
        
        val result = engine.evaluatePolicy(policy, userAttributes)
        
        assertThat(result).isTrue()
    }
    
    @Test
    fun `evaluatePolicy returns false for OR node when no children satisfied`() {
        val policy = ABEPolicy.Or(
            listOf(
                ABEPolicy.Leaf("role:admin"),
                ABEPolicy.Leaf("role:manager")
            )
        )
        val userAttributes = setOf("role:user", "dept:sales")
        
        val result = engine.evaluatePolicy(policy, userAttributes)
        
        assertThat(result).isFalse()
    }
    
    @Test
    fun `evaluatePolicy returns true for threshold node when k children satisfied`() {
        val policy = ABEPolicy.Threshold(
            threshold = 2,
            children = listOf(
                ABEPolicy.Leaf("role:admin"),
                ABEPolicy.Leaf("dept:engineering"),
                ABEPolicy.Leaf("clearance:secret")
            )
        )
        val userAttributes = setOf("role:admin", "dept:engineering")
        
        val result = engine.evaluatePolicy(policy, userAttributes)
        
        assertThat(result).isTrue()
    }
    
    @Test
    fun `evaluatePolicy returns false for threshold node when fewer than k children satisfied`() {
        val policy = ABEPolicy.Threshold(
            threshold = 2,
            children = listOf(
                ABEPolicy.Leaf("role:admin"),
                ABEPolicy.Leaf("dept:engineering"),
                ABEPolicy.Leaf("clearance:secret")
            )
        )
        val userAttributes = setOf("role:admin")
        
        val result = engine.evaluatePolicy(policy, userAttributes)
        
        assertThat(result).isFalse()
    }
    
    @Test
    fun `evaluatePolicy handles nested policies correctly`() {
        val policy = ABEPolicy.And(
            listOf(
                ABEPolicy.Leaf("role:admin"),
                ABEPolicy.Or(
                    listOf(
                        ABEPolicy.Leaf("dept:engineering"),
                        ABEPolicy.Leaf("dept:research")
                    )
                )
            )
        )
        val userAttributes = setOf("role:admin", "dept:research")
        
        val result = engine.evaluatePolicy(policy, userAttributes)
        
        assertThat(result).isTrue()
    }
    
    @Test
    fun `parsePolicy parses leaf node from JSON`() {
        val json = """{"type":"LEAF","attribute":"role:admin"}"""
        
        val policy = engine.parsePolicy(json)
        
        assertThat(policy).isInstanceOf(ABEPolicy.Leaf::class.java)
        assertThat((policy as ABEPolicy.Leaf).attribute).isEqualTo("role:admin")
    }
    
    @Test
    fun `parsePolicy parses AND node from JSON`() {
        val json = """
            {
                "type":"AND",
                "children":[
                    {"type":"LEAF","attribute":"role:admin"},
                    {"type":"LEAF","attribute":"dept:engineering"}
                ]
            }
        """.trimIndent()
        
        val policy = engine.parsePolicy(json)
        
        assertThat(policy).isInstanceOf(ABEPolicy.And::class.java)
        val andPolicy = policy as ABEPolicy.And
        assertThat(andPolicy.children).hasSize(2)
    }
    
    @Test
    fun `parsePolicy parses OR node from JSON`() {
        val json = """
            {
                "type":"OR",
                "children":[
                    {"type":"LEAF","attribute":"role:admin"},
                    {"type":"LEAF","attribute":"role:manager"}
                ]
            }
        """.trimIndent()
        
        val policy = engine.parsePolicy(json)
        
        assertThat(policy).isInstanceOf(ABEPolicy.Or::class.java)
        val orPolicy = policy as ABEPolicy.Or
        assertThat(orPolicy.children).hasSize(2)
    }
    
    @Test
    fun `parsePolicy parses THRESHOLD node from JSON`() {
        val json = """
            {
                "type":"THRESHOLD",
                "threshold":2,
                "children":[
                    {"type":"LEAF","attribute":"role:admin"},
                    {"type":"LEAF","attribute":"dept:engineering"},
                    {"type":"LEAF","attribute":"clearance:secret"}
                ]
            }
        """.trimIndent()
        
        val policy = engine.parsePolicy(json)
        
        assertThat(policy).isInstanceOf(ABEPolicy.Threshold::class.java)
        val thresholdPolicy = policy as ABEPolicy.Threshold
        assertThat(thresholdPolicy.threshold).isEqualTo(2)
        assertThat(thresholdPolicy.children).hasSize(3)
    }
    
    @Test
    fun `parsePolicy returns null for invalid JSON`() {
        val json = "invalid json"
        
        val policy = engine.parsePolicy(json)
        
        assertThat(policy).isNull()
    }
    
    @Test
    fun `parsePolicy returns null for missing type field`() {
        val json = """{"attribute":"role:admin"}"""
        
        val policy = engine.parsePolicy(json)
        
        assertThat(policy).isNull()
    }
    
    @Test
    fun `validatePolicySyntax returns true for valid policy`() {
        val json = """{"type":"LEAF","attribute":"role:admin"}"""
        
        val isValid = engine.validatePolicySyntax(json)
        
        assertThat(isValid).isTrue()
    }
    
    @Test
    fun `validatePolicySyntax returns false for invalid policy`() {
        val json = "invalid"
        
        val isValid = engine.validatePolicySyntax(json)
        
        assertThat(isValid).isFalse()
    }
    
    @Test
    fun `encrypt produces different output than input`() {
        val data = "test data".toByteArray()
        val policy = ABEPolicy.Leaf("role:admin")
        
        val ciphertext = engine.encrypt(data, policy)
        
        assertThat(ciphertext).isNotEqualTo(data)
    }
    
    @Test
    fun `getSupportedOperators returns expected operators`() {
        val operators = engine.getSupportedOperators()
        
        assertThat(operators).containsExactly("AND", "OR", "THRESHOLD", "LEAF")
    }
}
