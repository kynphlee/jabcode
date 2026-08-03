package com.jabauth.client.abe

import android.util.Base64
import org.json.JSONObject

/**
 * A holder key an issuer-tenant provisioned to this device — the artifact that makes cross-party
 * decrypt possible.
 *
 * Byte-compatible with the server's `org.nexus.jabauth.abe.keys.ProvisionedUserKey` JSON:
 * ```json
 * { "keysetId": "...", "keyId": "...", "keyData": "<base64>", "attributes": { "role": "inspector" } }
 * ```
 *
 * The [keysetId] is the field a [UserKey] alone cannot carry: it names the authority this key descends
 * from, so a device holding several issuers' keys can match it against the stamp in a sealed envelope
 * ([AbeEnvelope.keysetIdOf]) and pick the right one instead of guessing.
 *
 * **This is a secret.** [keyData] is the CP-ABE secret key: whoever holds it can open every layer whose
 * policy its attributes satisfy. Store it under the platform keystore, never in logs or analytics —
 * [toString] is redacted for that reason.
 */
class ProvisionedUserKey(
    val keysetId: String,
    val keyId: String,
    val attributes: RabeAttributeSet,
    val keyData: ByteArray
) {

    init {
        require(keysetId.isNotBlank()) { "keysetId is required (names the issuing authority)" }
        require(keyId.isNotBlank()) { "keyId is required" }
        require(keyData.isNotEmpty()) { "keyData (the holder secret key) is required" }
    }

    /** The form [NativeAbeEngine.decrypt] consumes. */
    fun toUserKey(): UserKey = UserKey(keyId, attributes, keyData)

    /** True when this key was issued by the authority that sealed [envelope]. */
    fun matches(envelope: ByteArray?): Boolean = keysetId == AbeEnvelope.keysetIdOf(envelope)

    /** Redacted — this wraps a secret key and is easy to log by accident. */
    override fun toString(): String =
        "ProvisionedUserKey(keysetId=$keysetId, keyId=$keyId, " +
            "attributes=${attributes.attributes.keys}, keyData=<redacted>)"

    companion object {
        /**
         * Parse a provisioning blob. Returns null on anything malformed — a half-read key is a denial,
         * never a partially-usable key.
         *
         * [base64Decoder] is injectable because `android.util.Base64` is stubbed on the JVM unit-test
         * classpath; production callers use the default.
         */
        @JvmStatic
        @JvmOverloads
        fun fromJson(
            json: String,
            base64Decoder: (String) -> ByteArray = { Base64.decode(it, Base64.DEFAULT) }
        ): ProvisionedUserKey? = runCatching {
            val root = JSONObject(json)
            val attrs = LinkedHashMap<String, String>()
            root.optJSONObject("attributes")?.let { node ->
                node.keys().forEach { k -> attrs[k] = node.getString(k) }
            }
            ProvisionedUserKey(
                keysetId = root.getString("keysetId"),
                keyId = root.getString("keyId"),
                attributes = RabeAttributeSet(attrs),
                keyData = base64Decoder(root.getString("keyData"))
            )
        }.getOrNull()
    }
}
