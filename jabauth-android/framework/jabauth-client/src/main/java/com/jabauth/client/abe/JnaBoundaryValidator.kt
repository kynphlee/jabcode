package com.jabauth.client.abe

/**
 * JNA boundary validation for native ABE operations.
 *
 * Faithful port of the framework `org.nexus.jabauth.abe.security.JnaBoundaryValidator`.
 * Validates all data crossing the JNA boundary to prevent buffer overflows, memory
 * corruption, DoS via oversized inputs, and invalid pointers. All ABE native calls
 * pass through this validator.
 */
internal object JnaBoundaryValidator {

    // Maximum sizes to prevent DoS and memory exhaustion.
    private const val MAX_PUBLIC_KEY_SIZE = 1024 * 1024        // 1MB
    private const val MAX_MASTER_KEY_SIZE = 1024 * 1024        // 1MB
    private const val MAX_USER_KEY_SIZE = 512 * 1024           // 512KB
    private const val MAX_CIPHERTEXT_SIZE = 2 * 1024 * 1024    // 2MB
    private const val MAX_POLICY_LENGTH = 10_000               // 10K chars
    private const val MAX_ATTRIBUTES_JSON_LENGTH = 50_000      // 50K chars
    private const val MAX_ATTRIBUTE_COUNT = 1000

    // Expected output sizes.
    private const val EXPECTED_KEM_KEY_SIZE = 32               // 256 bits

    fun validatePublicKey(pk: ByteArray?) {
        requireNotNull(pk) { "Public key cannot be null" }
        require(pk.isNotEmpty()) { "Public key cannot be empty" }
        require(pk.size <= MAX_PUBLIC_KEY_SIZE) {
            "Public key too large: ${pk.size} bytes (max $MAX_PUBLIC_KEY_SIZE)"
        }
    }

    fun validateMasterKey(msk: ByteArray?) {
        requireNotNull(msk) { "Master key cannot be null" }
        require(msk.isNotEmpty()) { "Master key cannot be empty" }
        require(msk.size <= MAX_MASTER_KEY_SIZE) {
            "Master key too large: ${msk.size} bytes (max $MAX_MASTER_KEY_SIZE)"
        }
    }

    fun validateUserKey(sk: ByteArray?) {
        requireNotNull(sk) { "User key cannot be null" }
        require(sk.isNotEmpty()) { "User key cannot be empty" }
        require(sk.size <= MAX_USER_KEY_SIZE) {
            "User key too large: ${sk.size} bytes (max $MAX_USER_KEY_SIZE)"
        }
    }

    fun validateCiphertext(ct: ByteArray?) {
        requireNotNull(ct) { "Ciphertext cannot be null" }
        require(ct.isNotEmpty()) { "Ciphertext cannot be empty" }
        require(ct.size <= MAX_CIPHERTEXT_SIZE) {
            "Ciphertext too large: ${ct.size} bytes (max $MAX_CIPHERTEXT_SIZE)"
        }
    }

    fun validatePolicy(policy: String?) {
        requireNotNull(policy) { "Policy cannot be null" }
        require(policy.isNotEmpty()) { "Policy cannot be empty" }
        require(policy.length <= MAX_POLICY_LENGTH) {
            "Policy too long: ${policy.length} chars (max $MAX_POLICY_LENGTH)"
        }
        require(policy.none { c -> c.code < 32 && c != '\n' && c != '\r' && c != '\t' }) {
            "Policy contains control characters"
        }
    }

    fun validateAttributesJson(attributesJson: String?) {
        requireNotNull(attributesJson) { "Attributes JSON cannot be null" }
        require(attributesJson.isNotEmpty()) { "Attributes JSON cannot be empty" }
        require(attributesJson.length <= MAX_ATTRIBUTES_JSON_LENGTH) {
            "Attributes JSON too long: ${attributesJson.length} chars (max $MAX_ATTRIBUTES_JSON_LENGTH)"
        }
        val trimmed = attributesJson.trim()
        require(trimmed.startsWith("{") || trimmed.startsWith("[")) {
            "Attributes JSON must be valid JSON object or array"
        }
    }

    fun validateAttributeCount(count: Int) {
        require(count >= 0) { "Attribute count cannot be negative" }
        require(count <= MAX_ATTRIBUTE_COUNT) {
            "Too many attributes: $count (max $MAX_ATTRIBUTE_COUNT)"
        }
    }

    fun validateKemKey(key: ByteArray?) {
        checkNotNull(key) { "Native function returned null KEM key" }
        check(key.size == EXPECTED_KEM_KEY_SIZE) {
            "Invalid KEM key size: ${key.size} bytes (expected $EXPECTED_KEM_KEY_SIZE)"
        }
        check(key.any { it.toInt() != 0 }) { "Native function returned all-zero KEM key" }
    }

    fun validateNativeOutput(output: ByteArray?, functionName: String) {
        checkNotNull(output) { "Native function $functionName returned null" }
        check(output.isNotEmpty()) { "Native function $functionName returned empty output" }
    }

    /** Defensive copy of an input buffer — prevents native code from modifying the original. */
    fun defensiveCopy(buffer: ByteArray): ByteArray = buffer.copyOf()
}
