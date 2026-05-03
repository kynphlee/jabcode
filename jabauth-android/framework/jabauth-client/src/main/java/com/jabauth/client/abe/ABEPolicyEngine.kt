package com.jabauth.client.abe

/**
 * Attribute-Based Encryption (ABE) policy evaluation engine
 * 
 * Evaluates CP-ABE access policies against user attributes.
 * 
 * **Phase 3 Stub:** This interface defines the contract for ABE operations.
 * Production implementation will integrate the native Rust `rabe_kem` library
 * via JNI (see RABE-BUILD-GUIDE.md).
 * 
 * @see ABEPolicy for policy data structure
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
