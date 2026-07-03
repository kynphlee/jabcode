package com.jabauth.client.abe

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets

/**
 * Instrumented round-trip test for the native Rabe CP-ABE engine.
 *
 * Android mirror of the framework `RabeKemSmokeTest`. It exercises the real native
 * `librabe_kem.so` (packaged in `jniLibs/<abi>/`) end-to-end: setup -> keygen ->
 * encapsulate -> AES-GCM seal -> decapsulate -> AES-GCM open. Runs on a device/emulator
 * only (the native lib is not present on the JVM test classpath).
 */
@RunWith(AndroidJUnit4::class)
class RabeCpAbeRoundTripTest {

    private val policy = "clearance:auditor AND domain:provenance"

    @Test
    fun roundTrip_withSatisfyingAttributes_succeeds() {
        val engine = NativeAbeEngine()
        val data = "secret-rabe".toByteArray(StandardCharsets.UTF_8)

        val attrs = RabeAttributeSet.builder()
            .attribute("clearance", "auditor")
            .attribute("domain", "provenance")
            .build()

        val enc = engine.encrypt(data, policy)
        val sk = engine.generateUserKey(attrs)
        val out = engine.decrypt(enc, sk)

        assertArrayEquals(data, out)
    }

    @Test
    fun roundTrip_withMismatchingAttributes_fails() {
        val engine = NativeAbeEngine()
        val data = "secret-rabe".toByteArray(StandardCharsets.UTF_8)

        val good = RabeAttributeSet.builder()
            .attribute("clearance", "auditor")
            .attribute("domain", "provenance")
            .build()
        val bad = RabeAttributeSet.builder()
            .attribute("clearance", "auditor")
            .attribute("domain", "logistics")
            .build()

        val enc = engine.encrypt(data, policy)
        val badSk = engine.generateUserKey(bad)

        // Non-satisfying attributes must not decrypt (policy gate + KEM/AAD mismatch).
        assertThrows(RuntimeException::class.java) { engine.decrypt(enc, badSk) }

        // Control: a satisfying key decrypts successfully.
        val goodSk = engine.generateUserKey(good)
        val out = engine.decrypt(enc, goodSk)
        assertArrayEquals(data, out)
    }
}
