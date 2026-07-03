package com.jabauth.client.abe

import org.json.JSONObject
import java.util.UUID

/**
 * Native CP-ABE KEM via JNA (Rabe-backed).
 *
 * Faithful Android port of the framework `org.nexus.jabauth.abe.encryption.kem.NativeCpAbeKem`.
 * Performs lazy `setup()` on first use, issues user keys via `keygen`, and encapsulates/
 * decapsulates the 32-byte symmetric key via the native Rabe KEM. Policy strings are converted
 * to the rabe HumanPolicy form with [toRabeHumanPolicy] before crossing the FFI boundary.
 *
 * Deviations from the framework source (see report): the Jackson `ObjectMapper` attribute
 * serialisation is replaced with Android's built-in `org.json.JSONObject` (same
 * `{"k":"v",...}` shape the native `rabe_keygen` parses), and the `AbeOperationLimiter`
 * timeout/executor wrapping is omitted — it is JVM-side DoS hardening, not part of the KEM
 * crypto. Input-size limits are still enforced by [JnaBoundaryValidator].
 */
class RabeCpAbeKem {

    private var pk: ByteArray? = null   // public key
    private var msk: ByteArray? = null  // master secret key

    /** Encapsulate a fresh 32-byte symmetric key under [policy]. */
    fun encapsulate(policy: String): EncapsulatedSecret {
        try {
            ensureInitialized()
            val rabePolicy = toRabeHumanPolicy(policy)
            val out = RabeKemNative.kemEnc(pk!!, rabePolicy)
            check(out.size == 2) { "Invalid kemEnc result" }
            val key32 = out[0]
            val kemCt = out[1]
            check(key32.size == 32) { "Invalid KEM key length" }
            return EncapsulatedSecret(key32, kemCt)
        } catch (e: UnsatisfiedLinkError) {
            throw UnsupportedOperationException("Rabe native library not available (kemEnc)", e)
        } catch (e: NoClassDefFoundError) {
            throw UnsupportedOperationException("Rabe native library not available (kemEnc)", e)
        } catch (e: Exception) {
            throw RuntimeException("Rabe kemEnc failed", e)
        }
    }

    /** Recover the 32-byte symmetric key from [kemCiphertext] using [userKey]. */
    fun decapsulate(kemCiphertext: ByteArray, userKey: UserKey, policy: String): ByteArray {
        try {
            ensureInitialized()
            val rabePolicy = toRabeHumanPolicy(policy)
            return RabeKemNative.kemDec(pk!!, userKey.keyData, rabePolicy, kemCiphertext)
        } catch (e: UnsatisfiedLinkError) {
            throw UnsupportedOperationException("Rabe native library not available (kemDec)", e)
        } catch (e: NoClassDefFoundError) {
            throw UnsupportedOperationException("Rabe native library not available (kemDec)", e)
        } catch (e: Exception) {
            throw RuntimeException("Rabe kemDec failed", e)
        }
    }

    /** Issue a user key for [attributes]. */
    fun generateUserKey(attributes: RabeAttributeSet): UserKey {
        JnaBoundaryValidator.validateAttributeCount(attributes.attributes.size)
        try {
            ensureInitialized()
            val json = JSONObject(attributes.attributes as Map<*, *>).toString()
            val sk = RabeKemNative.keygen(pk!!, msk!!, json)
            return UserKey(UUID.randomUUID().toString(), attributes, sk)
        } catch (e: UnsatisfiedLinkError) {
            throw UnsupportedOperationException("Rabe native library not available (keygen)", e)
        } catch (e: NoClassDefFoundError) {
            throw UnsupportedOperationException("Rabe native library not available (keygen)", e)
        } catch (e: Exception) {
            throw RuntimeException("Rabe keygen failed", e)
        }
    }

    private fun ensureInitialized() {
        if (pk == null || msk == null) {
            try {
                val keys = RabeKemNative.setup()
                check(keys.size == 2) { "Invalid setup result" }
                pk = keys[0]
                msk = keys[1]
                check(pk!!.isNotEmpty() && msk!!.isNotEmpty()) { "Null/empty PK/MSK from setup" }
            } catch (e: UnsatisfiedLinkError) {
                throw UnsupportedOperationException("Rabe native library not available (setup)", e)
            } catch (e: NoClassDefFoundError) {
                throw UnsupportedOperationException("Rabe native library not available (setup)", e)
            }
        }
    }

    /**
     * Convert a policy string into a rabe HumanPolicy string for the native KEM.
     *
     * Delegates to [RabeHumanPolicyRenderer], which parses with the canonical
     * [RobustPolicyParser] and emits a fully-parenthesized policy. This gives a flat
     * mixed AND/OR policy explicit precedence (so the rabe grammar accepts it), normalises
     * already-quoted tokens, and rejects a genuinely malformed policy with a clean
     * [IllegalArgumentException] rather than handing it to the native layer.
     */
    private fun toRabeHumanPolicy(policy: String): String = RabeHumanPolicyRenderer.render(policy)
}
