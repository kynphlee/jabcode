package com.jabauth.client.jwt

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

/**
 * The holder's key material for RFC 9901 Key Binding (WP-C mobile).
 *
 * The seam is an interface so the KB-JWT flow is JVM-testable with an in-memory keypair;
 * production uses [AndroidKeystoreHolderKeys] — a hardware-backed, <b>non-extractable</b>
 * EC P-256 key in the Android Keystore. This deliberately upgrades the handoff's
 * store-the-private-key-in-EncryptedSharedPreferences suggestion: a Keystore key never
 * exists as bytes the app (or an attacker with the prefs file) can read, the same
 * non-extractable ethos as the server's KMS keys. `SecureStorage` holds only metadata
 * (the alias is deterministic; nothing secret needs storing at all).
 */
interface HolderKeys {

    /** The holder's public key (what `cnf.jkt` is derived from at issuance). */
    fun publicKey(): PublicKey

    /** The private-key handle for KB-JWT signing (Keystore reference in production). */
    fun privateKey(): PrivateKey
}

/**
 * Android-Keystore-backed [HolderKeys]: one EC P-256 signing key under [alias], generated on
 * first use, hardware-backed where the device supports it (StrongBox/TEE), non-extractable
 * always. Requires API 23+ (the framework min SDK is 24).
 */
class AndroidKeystoreHolderKeys(
    private val alias: String = DEFAULT_ALIAS,
) : HolderKeys {

    companion object {
        const val DEFAULT_ALIAS = "jabauth_holder_binding_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    override fun publicKey(): PublicKey = ensureKey().let {
        keyStore.getCertificate(alias).publicKey
    }

    override fun privateKey(): PrivateKey = ensureKey().let {
        keyStore.getKey(alias, null) as PrivateKey
    }

    private fun ensureKey() {
        if (keyStore.containsAlias(alias)) {
            return
        }
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        generator.generateKeyPair()
    }
}
