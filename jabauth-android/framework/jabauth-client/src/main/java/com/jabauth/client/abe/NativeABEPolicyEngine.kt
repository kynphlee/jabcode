package com.jabauth.client.abe

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

/**
 * Production [ABEPolicyEngine] backed by the native Rabe CP-ABE engine.
 *
 * Replaces the Phase 3 stub: policy evaluation/parsing/validation reuse the existing
 * [ABEPolicy] AST, while [encrypt]/[decrypt] delegate to [NativeAbeEngine] (native Rabe KEM
 * + AES-GCM with the framework-exact AAD binding).
 *
 * The interface's flat `encrypt(data, policy): ByteArray` / `decrypt(ciphertext, attrs): ByteArray?`
 * shape does not carry the policy or KEM ciphertext separately, so [encrypt] serialises the
 * full [EncryptedData] (policy string, KEM ciphertext, AES-GCM ciphertext) into a single
 * self-describing envelope that [decrypt] reconstructs. The envelope is length-prefixed and
 * versioned; it is confidentiality-neutral (the policy is already public and AAD-bound).
 *
 * rabe HumanPolicy — and therefore the native KEM — has no threshold or negation operator, so
 * [encrypt] rejects [ABEPolicy.Threshold] policies. Boolean AND/OR/Leaf policies map directly.
 */
class NativeABEPolicyEngine(
    private val engine: NativeAbeEngine = NativeAbeEngine()
) : ABEPolicyEngine {

    // --- Policy evaluation / parsing / validation (reuse the ABEPolicy AST) ---

    override fun evaluatePolicy(policy: ABEPolicy, userAttributes: Set<String>): Boolean =
        when (policy) {
            is ABEPolicy.Leaf -> userAttributes.contains(policy.attribute)
            is ABEPolicy.And -> policy.children.all { evaluatePolicy(it, userAttributes) }
            is ABEPolicy.Or -> policy.children.any { evaluatePolicy(it, userAttributes) }
            is ABEPolicy.Threshold ->
                policy.children.count { evaluatePolicy(it, userAttributes) } >= policy.threshold
        }

    override fun parsePolicy(policyString: String): ABEPolicy? = try {
        parsePolicyFromJson(JSONObject(policyString))
    } catch (e: Exception) {
        null
    }

    private fun parsePolicyFromJson(json: JSONObject): ABEPolicy? {
        val type = if (json.has("type")) json.optString("type") else return null
        return when (type) {
            "LEAF" -> {
                if (!json.has("attribute")) return null
                ABEPolicy.Leaf(json.optString("attribute"))
            }
            "AND" -> parseChildrenArray(json.optJSONArray("children") ?: return null)?.let { ABEPolicy.And(it) }
            "OR" -> parseChildrenArray(json.optJSONArray("children") ?: return null)?.let { ABEPolicy.Or(it) }
            "THRESHOLD" -> {
                val threshold = json.optInt("threshold", -1)
                if (threshold < 0) return null
                val children = parseChildrenArray(json.optJSONArray("children") ?: return null) ?: return null
                ABEPolicy.Threshold(threshold, children)
            }
            else -> null
        }
    }

    private fun parseChildrenArray(array: JSONArray): List<ABEPolicy>? {
        val children = ArrayList<ABEPolicy>()
        for (i in 0 until array.length()) {
            val childJson = array.optJSONObject(i) ?: return null
            children.add(parsePolicyFromJson(childJson) ?: return null)
        }
        return children
    }

    override fun validatePolicySyntax(policyString: String): Boolean = parsePolicy(policyString) != null

    override fun getSupportedOperators(): List<String> = listOf("AND", "OR", "LEAF")

    // --- Native crypto ---

    override fun encrypt(data: ByteArray, policy: ABEPolicy): ByteArray {
        val policyString = toPolicyString(policy)
        val enc = engine.encrypt(data, policyString)
        return encodeEnvelope(enc)
    }

    override fun decrypt(ciphertext: ByteArray, userAttributes: Set<String>): ByteArray? {
        val enc = decodeEnvelope(ciphertext)
        val userKey = engine.generateUserKey(toAttributeSet(userAttributes))
        return try {
            engine.decrypt(enc, userKey)
        } catch (e: RuntimeException) {
            // Attributes not satisfying the policy (or any decryption failure) -> null,
            // per the interface contract.
            null
        }
    }

    // --- Conversions ---

    /**
     * Render an [ABEPolicy] into the `name:value AND/OR` string form accepted by
     * [RobustPolicyParser] / [RabeHumanPolicyRenderer]. Threshold nodes have no rabe
     * HumanPolicy equivalent and are rejected.
     */
    private fun toPolicyString(policy: ABEPolicy): String = when (policy) {
        is ABEPolicy.Leaf -> policy.attribute
        is ABEPolicy.And -> policy.children.joinToString(" AND ", "(", ")") { toPolicyString(it) }
        is ABEPolicy.Or -> policy.children.joinToString(" OR ", "(", ")") { toPolicyString(it) }
        is ABEPolicy.Threshold -> throw IllegalArgumentException(
            "rabe CP-ABE has no threshold operator; THRESHOLD policies are not supported natively"
        )
    }

    /** Convert `["role:admin", "dept:eng"]` into a [RabeAttributeSet] map keyed by attribute name. */
    private fun toAttributeSet(userAttributes: Set<String>): RabeAttributeSet {
        val map = LinkedHashMap<String, String>()
        for (attr in userAttributes) {
            val colon = attr.indexOf(':')
            require(colon in 1 until attr.length - 1) {
                "Attribute must be 'name:value' but was '$attr'"
            }
            map[attr.substring(0, colon)] = attr.substring(colon + 1)
        }
        return RabeAttributeSet(map)
    }

    // --- Envelope (version || policyUtf8 || kemCiphertext || aesGcmCiphertext) ---

    private fun encodeEnvelope(enc: EncryptedData): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { out ->
            out.writeByte(ENVELOPE_VERSION)
            val policyBytes = enc.policy.toByteArray(StandardCharsets.UTF_8)
            out.writeInt(policyBytes.size); out.write(policyBytes)
            out.writeInt(enc.policyData.size); out.write(enc.policyData)
            out.writeInt(enc.ciphertext.size); out.write(enc.ciphertext)
        }
        return bos.toByteArray()
    }

    private fun decodeEnvelope(bytes: ByteArray): EncryptedData {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val version = input.readByte().toInt()
            require(version == ENVELOPE_VERSION) { "Unsupported ABE envelope version: $version" }
            val policy = String(readBlock(input), StandardCharsets.UTF_8)
            val policyData = readBlock(input)
            val ciphertext = readBlock(input)
            return EncryptedData(ciphertext, policy, policyData)
        }
    }

    private fun readBlock(input: DataInputStream): ByteArray {
        val len = input.readInt()
        require(len in 0..MAX_BLOCK) { "Invalid ABE envelope block length: $len" }
        val buf = ByteArray(len)
        input.readFully(buf)
        return buf
    }

    private companion object {
        const val ENVELOPE_VERSION = 1
        const val MAX_BLOCK = 8 * 1024 * 1024 // 8MB per block guard
    }
}
