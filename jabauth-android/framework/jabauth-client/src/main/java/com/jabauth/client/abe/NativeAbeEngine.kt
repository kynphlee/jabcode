package com.jabauth.client.abe

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ciphertext-Policy ABE engine.
 *
 * Faithful Android port of the framework `org.nexus.jabauth.abe.encryption.CpAbeEngine`.
 * Data is sealed with AES/GCM/NoPadding under a symmetric key encapsulated by the native
 * Rabe CP-ABE KEM. The AAD binding — policy UTF-8 bytes, THEN sha256(kemCiphertext) — is
 * replicated exactly; interop with the JVM engine depends on this byte-for-byte match.
 *
 * Layout of [EncryptedData.ciphertext]: `[nonce(12) || ct+tag]`, GCM tag length 128 bits.
 */
class NativeAbeEngine(private val kem: RabeCpAbeKem = RabeCpAbeKem()) {

    private val policyParser = RobustPolicyParser()

    fun encrypt(data: ByteArray, policy: String): EncryptedData {
        try {
            // Generate symmetric key for data encryption via the KEM.
            val enc = kem.encapsulate(policy)
            val dataKey = SecretKeySpec(enc.symmetricKey, "AES")
            // Prepare KEM ciphertext and its digest for AAD binding.
            val policyData = enc.kemCiphertext
            val kemCtDigest = sha256(policyData)

            // Encrypt with AES/GCM (AEAD), 96-bit random nonce.
            val nonce = ByteArray(12)
            SecureRandom().nextBytes(nonce)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, dataKey, spec)
            cipher.updateAAD(policy.toByteArray(StandardCharsets.UTF_8))
            cipher.updateAAD(kemCtDigest)
            val ctAndTag = cipher.doFinal(data)

            // Combine nonce and ciphertext+tag.
            val ciphertext = ByteArray(nonce.size + ctAndTag.size)
            System.arraycopy(nonce, 0, ciphertext, 0, nonce.size)
            System.arraycopy(ctAndTag, 0, ciphertext, nonce.size, ctAndTag.size)

            return EncryptedData(ciphertext, policy, policyData)
        } catch (e: Exception) {
            throw RuntimeException("Encryption failed", e)
        }
    }

    fun decrypt(encryptedData: EncryptedData, userKey: UserKey): ByteArray {
        try {
            // Defense-in-depth: verify the holder's attributes satisfy the policy. The real
            // enforcement is cryptographic (a non-satisfying key yields a wrong KEM key that
            // fails GCM tag verification below); this gate short-circuits with a clear error.
            if (!policyParser.parse(encryptedData.policy).evaluate(userKey.attributes)) {
                throw SecurityException("User attributes do not satisfy policy")
            }

            // Decapsulate the data key via the KEM.
            val dataKeyBytes = kem.decapsulate(encryptedData.policyData, userKey, encryptedData.policy)
            val dataKey = SecretKeySpec(dataKeyBytes, "AES")

            // Extract nonce and ciphertext+tag.
            val full = encryptedData.ciphertext
            require(full.size >= 12 + 16) { "Ciphertext too short" } // nonce + tag
            val nonce = full.copyOfRange(0, 12)
            val ctAndTag = full.copyOfRange(12, full.size)

            // Decrypt with the recovered key (AEAD, same AAD).
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, nonce)
            cipher.init(Cipher.DECRYPT_MODE, dataKey, spec)
            val kemCtDigest = sha256(encryptedData.policyData)
            cipher.updateAAD(encryptedData.policy.toByteArray(StandardCharsets.UTF_8))
            cipher.updateAAD(kemCtDigest)
            return cipher.doFinal(ctAndTag)
        } catch (e: Exception) {
            throw RuntimeException("Decryption failed", e)
        }
    }

    fun generateUserKey(attributes: RabeAttributeSet): UserKey = kem.generateUserKey(attributes)

    private fun sha256(input: ByteArray): ByteArray = try {
        MessageDigest.getInstance("SHA-256").digest(input)
    } catch (e: Exception) {
        throw RuntimeException("SHA-256 not available", e)
    }
}
