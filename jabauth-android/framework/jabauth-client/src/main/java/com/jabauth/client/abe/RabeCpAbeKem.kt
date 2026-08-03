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
class RabeCpAbeKem private constructor(
    /**
     * An issuer's public parameters, when this instance is a **reader** rather than an authority.
     * Non-null puts the KEM in reader mode: it can decapsulate with a provisioned key and can never
     * mint one, because it holds no master secret to mint from.
     */
    private val importedPublicParams: ByteArray?
) {

    /** Authority mode: generates its own `(pk, msk)` on first use. Unchanged historical behaviour. */
    constructor() : this(null)

    private var pk: ByteArray? = null   // public key
    private var msk: ByteArray? = null  // master secret key

    /** True when this instance can only decrypt — no master secret, so no key issuance. */
    val isReaderOnly: Boolean get() = importedPublicParams != null

    companion object {
        /**
         * A decrypt-only KEM bound to [issuerPublicParams] — the cross-party reader.
         *
         * This is the shape a holder device wants: it never calls `setup()`, so it never fabricates a
         * master secret it would only misuse. Decapsulation succeeds exactly when the provisioned key
         * descends from the same authority and its attributes satisfy the policy; anything else is a
         * cryptographic denial, which is the honest answer.
         */
        @JvmStatic
        fun reader(issuerPublicParams: ByteArray): RabeCpAbeKem {
            require(issuerPublicParams.isNotEmpty()) { "issuerPublicParams must not be empty" }
            return RabeCpAbeKem(issuerPublicParams.copyOf())
        }
    }

    /** Encapsulate a fresh 32-byte symmetric key under [policy]. Authority mode only. */
    fun encapsulate(policy: String): EncapsulatedSecret {
        check(!isReaderOnly) { "Reader-only KEM cannot encapsulate: it holds no master secret" }
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

    /** Issue a user key for [attributes]. Authority mode only — minting requires the master secret. */
    fun generateUserKey(attributes: RabeAttributeSet): UserKey {
        check(!isReaderOnly) { "Reader-only KEM cannot issue user keys: it holds no master secret" }
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
        // Reader mode: adopt the issuer's public parameters and stop. No setup(), no master secret.
        importedPublicParams?.let {
            if (pk == null) pk = it
            return
        }
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
