package com.jabauth.client.abe

import org.json.JSONArray
import org.json.JSONObject

/**
 * Test double for ABEPolicyEngine
 * 
 * Stub implementation for unit testing without native Rust library.
 * Implements policy evaluation using simple boolean logic.
 * 
 * **Production Note:** Replace with native `rabe_kem` integration via JNI.
 */
class TestABEPolicyEngineImpl : ABEPolicyEngine {
    
    override fun evaluatePolicy(policy: ABEPolicy, userAttributes: Set<String>): Boolean {
        return when (policy) {
            is ABEPolicy.Leaf -> userAttributes.contains(policy.attribute)
            is ABEPolicy.And -> policy.children.all { evaluatePolicy(it, userAttributes) }
            is ABEPolicy.Or -> policy.children.any { evaluatePolicy(it, userAttributes) }
            is ABEPolicy.Threshold -> {
                val satisfiedCount = policy.children.count { evaluatePolicy(it, userAttributes) }
                satisfiedCount >= policy.threshold
            }
        }
    }
    
    override fun parsePolicy(policyString: String): ABEPolicy? {
        return try {
            val json = JSONObject(policyString)
            parsePolicyFromJson(json)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parsePolicyFromJson(json: JSONObject): ABEPolicy? {
        val type = json.optString("type") ?: return null
        
        return when (type) {
            "LEAF" -> {
                val attribute = json.optString("attribute") ?: return null
                ABEPolicy.Leaf(attribute)
            }
            "AND" -> {
                val childrenArray = json.optJSONArray("children") ?: return null
                val children = parseChildrenArray(childrenArray) ?: return null
                ABEPolicy.And(children)
            }
            "OR" -> {
                val childrenArray = json.optJSONArray("children") ?: return null
                val children = parseChildrenArray(childrenArray) ?: return null
                ABEPolicy.Or(children)
            }
            "THRESHOLD" -> {
                val threshold = json.optInt("threshold", -1)
                if (threshold < 0) return null
                val childrenArray = json.optJSONArray("children") ?: return null
                val children = parseChildrenArray(childrenArray) ?: return null
                ABEPolicy.Threshold(threshold, children)
            }
            else -> null
        }
    }
    
    private fun parseChildrenArray(array: JSONArray): List<ABEPolicy>? {
        val children = mutableListOf<ABEPolicy>()
        for (i in 0 until array.length()) {
            val childJson = array.optJSONObject(i) ?: return null
            val child = parsePolicyFromJson(childJson) ?: return null
            children.add(child)
        }
        return children
    }
    
    override fun validatePolicySyntax(policyString: String): Boolean {
        return parsePolicy(policyString) != null
    }
    
    override fun encrypt(data: ByteArray, policy: ABEPolicy): ByteArray {
        // Stub: XOR encryption with policy hash as key (NOT SECURE - for testing only)
        val policyHash = policy.toJson().hashCode()
        return data.mapIndexed { index, byte ->
            (byte.toInt() xor (policyHash shr (index % 4) * 8)).toByte()
        }.toByteArray()
    }
    
    override fun decrypt(ciphertext: ByteArray, userAttributes: Set<String>): ByteArray? {
        // Stub: Cannot verify policy without storing it with ciphertext
        // For testing, we just XOR decrypt (assumes caller verified policy separately)
        // Real implementation would extract policy from ciphertext header
        return ciphertext  // Return as-is for stub
    }
    
    override fun getSupportedOperators(): List<String> {
        return listOf("AND", "OR", "THRESHOLD", "LEAF")
    }
}
