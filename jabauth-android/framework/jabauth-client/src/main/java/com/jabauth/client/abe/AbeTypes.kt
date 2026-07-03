package com.jabauth.client.abe

/**
 * Result of CP-ABE key encapsulation. Port of the framework `EncapsulatedSecret`.
 *
 * @property symmetricKey the 32-byte data-encryption key (KEM output).
 * @property kemCiphertext the KEM ciphertext that recovers [symmetricKey] under a satisfying key.
 */
class EncapsulatedSecret(
    val symmetricKey: ByteArray,
    val kemCiphertext: ByteArray
)

/**
 * ABE-encrypted data with the embedded access policy. Port of the framework `EncryptedData`.
 *
 * @property ciphertext AES-GCM `[nonce(12) || ct+tag]`.
 * @property policy the access policy string (bound as AAD; also used for the decrypt-time gate).
 * @property policyData the KEM ciphertext (its SHA-256 is bound as AAD).
 */
class EncryptedData(
    val ciphertext: ByteArray,
    val policy: String,
    val policyData: ByteArray
) {
    fun size(): Int = ciphertext.size + policyData.size
}

/**
 * A user key derived from attributes. Port of the framework `UserKey`.
 *
 * @property keyId opaque identifier.
 * @property attributes the attribute set the key was issued for (used by the decrypt-time gate).
 * @property keyData the native Rabe secret key bytes.
 */
class UserKey(
    val keyId: String,
    val attributes: RabeAttributeSet,
    val keyData: ByteArray
) {
    fun hasAttribute(name: String): Boolean = attributes.hasAttribute(name)
}
