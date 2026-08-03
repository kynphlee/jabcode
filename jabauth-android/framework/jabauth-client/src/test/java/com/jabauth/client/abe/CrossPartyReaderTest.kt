package com.jabauth.client.abe

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Base64

/**
 * The cross-party interop proof: a layer **sealed by the server runtime** is opened **here** with a
 * key that server provisioned.
 *
 * The fixture (`cross-party-fixture.json`) is emitted by the framework's `CrossPartyFixtureExportTest`
 * and checked in, so these bytes are genuinely foreign to this runtime. Sealing and opening in one
 * runtime would prove almost nothing; it is the cross-runtime pass that shows the ABE1 envelope layout,
 * the rabe policy rendering, and the AES-GCM AAD binding all agree byte-for-byte.
 *
 * Wire-format assertions run unconditionally. The decrypt assertions need a host `librabe_kem`
 * (`RABE_NATIVE_DIR`) and are skipped without it — [nativeAvailable] reports which mode ran so a skip
 * is never mistaken for a pass.
 */
@RunWith(RobolectricTestRunner::class)
class CrossPartyReaderTest {

    private val fixture: JSONObject by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("cross-party-fixture.json")
        requireNotNull(stream) { "cross-party-fixture.json missing from test resources" }
        JSONObject(stream.bufferedReader().use { it.readText() })
    }

    private fun b64(name: String): ByteArray = Base64.getDecoder().decode(fixture.getString(name))
    private val envelope: ByteArray get() = b64("envelopeBase64")
    private val issuerPk: ByteArray get() = b64("issuerPublicParamsBase64")

    /** JVM base64 — `android.util.Base64` is a Robolectric stub for the key blobs' payload. */
    private fun importKey(field: String): ProvisionedUserKey =
        requireNotNull(
            ProvisionedUserKey.fromJson(fixture.getString(field)) { Base64.getDecoder().decode(it) }
        ) { "fixture key '$field' failed to import" }

    private fun nativeAvailable(): Boolean = runCatching {
        RabeKemNative.setup(); true
    }.getOrElse { false }

    // ---- Wire format: always runs (this is the crux-2 half — the envelope the device used to reject) ----

    @Test
    fun serverEnvelope_decodesOnDevice() {
        val decoded = AbeEnvelope.decode(envelope)
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.policy).isEqualTo(fixture.getString("policy"))
        assertThat(decoded.ciphertext).isNotEmpty()
        assertThat(decoded.policyData).isNotEmpty()
    }

    @Test
    fun serverEnvelope_carriesTheSealingAuthority() {
        assertThat(AbeEnvelope.keysetIdOf(envelope)).isEqualTo(fixture.getString("keysetId"))
        assertThat(AbeEnvelope.policyOf(envelope)).isEqualTo(fixture.getString("policy"))
    }

    @Test
    fun provisionedKey_importsAndMatchesTheEnvelopeAuthority() {
        val key = importKey("authorisedKeyJson")
        assertThat(key.keysetId).isEqualTo(fixture.getString("keysetId"))
        assertThat(key.attributes.attributes).containsEntry("role", "inspector")
        assertThat(key.matches(envelope)).isTrue()
        // Redaction guard: the secret must never reach a log line.
        assertThat(key.toString()).contains("<redacted>")
        assertThat(key.toString()).doesNotContain(fixture.getString("keysetId") + "keyData")
    }

    @Test
    fun readerKem_cannotMintOrSeal() {
        val reader = RabeCpAbeKem.reader(issuerPk)
        assertThat(reader.isReaderOnly).isTrue()
        // A reader holds no master secret, so both authority operations must fail closed.
        runCatching { reader.generateUserKey(RabeAttributeSet(mapOf("role" to "inspector"))) }
            .also { assertThat(it.isFailure).isTrue() }
        runCatching { reader.encapsulate("role:inspector") }
            .also { assertThat(it.isFailure).isTrue() }
    }

    // ---- Real decrypt: needs the host native lib (crux-1 half) ----

    @Test
    fun authorisedHolder_decryptsServerSealedLayer() {
        assumeTrue("librabe_kem unavailable (set RABE_NATIVE_DIR)", nativeAvailable())

        val engine = NativeAbeEngine(RabeCpAbeKem.reader(issuerPk))
        val sealed = requireNotNull(AbeEnvelope.decode(envelope))
        val plaintext = engine.decrypt(sealed, importKey("authorisedKeyJson").toUserKey())

        assertThat(String(plaintext, Charsets.UTF_8)).isEqualTo(fixture.getString("expectedPlaintext"))
    }

    @Test
    fun nonSatisfyingHolder_isDenied() {
        assumeTrue("librabe_kem unavailable (set RABE_NATIVE_DIR)", nativeAvailable())

        val engine = NativeAbeEngine(RabeCpAbeKem.reader(issuerPk))
        val sealed = requireNotNull(AbeEnvelope.decode(envelope))
        val result = runCatching { engine.decrypt(sealed, importKey("nonSatisfyingKeyJson").toUserKey()) }

        assertThat(result.isFailure).isTrue()
    }
}
