package com.jabauth.client.abe

/**
 * Attribute-Based Encryption (ABE) policy evaluation engine
 * 
 * Evaluates CP-ABE access policies against user attributes.
 * 
 * The production implementation is [NativeABEPolicyEngine], backed by the native Rust
 * `rabe_kem` CP-ABE library (via JNA) and AES-GCM. [TestABEPolicyEngineImpl] remains a
 * non-cryptographic test double for unit tests that must run without the native library.
 *
 * @see ABEPolicy for policy data structure
 * @see NativeABEPolicyEngine for the native Rabe-backed implementation
 */
interface ABEPolicyEngine {
    
    /**
     * Evaluate if user attributes satisfy an access policy
     * 
     * @param policy ABE access policy to evaluate
     * @param userAttributes Set of user attributes (e.g., ["role:admin", "dept:engineering"])
     * @return true if attributes satisfy policy, false otherwise
     */
    fun evaluatePolicy(policy: ABEPolicy, userAttributes: Set<String>): Boolean
    
    /**
     * Parse a policy string into an ABEPolicy object
     * 
     * @param policyString JSON-based policy string
     * @return Parsed ABEPolicy or null if invalid
     */
    fun parsePolicy(policyString: String): ABEPolicy?
    
    /**
     * Validate policy syntax
     * 
     * @param policyString JSON-based policy string
     * @return true if policy syntax is valid
     */
    fun validatePolicySyntax(policyString: String): Boolean
    
    /**
     * Encrypt data with ABE policy
     * 
     * @param data Data to encrypt
     * @param policy Access policy for decryption
     * @return Encrypted ciphertext
     */
    fun encrypt(data: ByteArray, policy: ABEPolicy): ByteArray
    
    /**
     * Decrypt ABE-encrypted data with user attributes
     * 
     * @param ciphertext Encrypted data
     * @param userAttributes User attributes for decryption
     * @return Decrypted plaintext or null if attributes don't satisfy policy
     */
    fun decrypt(ciphertext: ByteArray, userAttributes: Set<String>): ByteArray?
    
    /**
     * Get supported policy operators
     * 
     * @return List of supported operators (e.g., "AND", "OR", "OF")
     */
    fun getSupportedOperators(): List<String>
}
