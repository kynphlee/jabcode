package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Binary-fidelity round-trip tests for the {@code byte[]}-first API:
 * {@link JABCodeEncoder#encodeBytes} → PNG → {@link JABCodeDecoder#decodeBytes}.
 *
 * <p>The native codec's payload model ({@code jab_data} = length + bytes) is
 * binary-clean; these tests guard that the Java layer now preserves it. The
 * driving use case is high-entropy ciphertext (CP-ABE payloads for the
 * FSMA-204 authentication label), which the former String-typed API silently
 * corrupted: any byte sequence invalid in UTF-8 became U+FFFD.</p>
 */
@EnabledIf("isNativeLibraryAvailable")
class BinaryRoundTripIntegrationTest {

    private final JABCodeEncoder encoder = new JABCodeEncoder();
    private final JABCodeDecoder decoder = new JABCodeDecoder();

    static boolean isNativeLibraryAvailable() {
        String[] candidates = {
            "../src/jabcode/build/libjabcode.so",
            "../lib/libjabcode.so",
            "libjabcode.so"
        };
        for (String p : candidates) {
            if (java.nio.file.Files.exists(java.nio.file.Path.of(p))) {
                return true;
            }
        }
        String ld = System.getenv("LD_LIBRARY_PATH");
        return ld != null && !ld.isEmpty();
    }

    /** Deterministic high-entropy payload — ciphertext-like, reproducible. */
    private static byte[] highEntropyPayload(int size) {
        byte[] payload = new byte[size];
        new Random(0xC0A_FEED).nextBytes(payload);
        return payload;
    }

    @Test
    void highEntropyBytesRoundTripExactly() {
        byte[] payload = highEntropyPayload(512);

        for (int colorMode : new int[] {8, 16}) {
            var config = JABCodeEncoder.Config.builder()
                .colorNumber(colorMode)
                .eccLevel(5)
                .build();

            byte[] png = encoder.encodeBytes(payload, config);
            assertNotNull(png, "encodeBytes returned null at " + colorMode + "-color");

            byte[] decoded = decoder.decodeBytes(png);
            assertArrayEquals(payload, decoded,
                "binary payload did not round-trip byte-identically at " + colorMode + "-color");
        }
    }

    @Test
    void everyByteValueRoundTripsExactly() {
        // 0x00..0xFF twice — covers every value, including sequences that are
        // invalid UTF-8 (0xFF, lone continuation bytes, truncated multibyte).
        byte[] payload = new byte[512];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }

        byte[] png = encoder.encodeBytes(payload,
            JABCodeEncoder.Config.builder().colorNumber(8).eccLevel(5).build());
        assertNotNull(png);

        assertArrayEquals(payload, decoder.decodeBytes(png));
    }

    @Test
    void stringViewIsLossyOnBinaryButByteViewIsNot() {
        // Documents the defect the byte[] API retires: the same decode, read
        // through getData() (UTF-8 String view), does NOT reproduce the payload,
        // while getDataBytes() does.
        byte[] payload = highEntropyPayload(256);

        byte[] png = encoder.encodeBytes(payload,
            JABCodeEncoder.Config.builder().colorNumber(8).eccLevel(5).build());
        assertNotNull(png);

        JABCodeDecoder.DecodedResult result = decoder.decodeEx(png);
        assertTrue(result.isSuccess());

        assertArrayEquals(payload, result.getDataBytes(),
            "byte view must be exact");
        assertFalse(java.util.Arrays.equals(
                payload, result.getData().getBytes(StandardCharsets.UTF_8)),
            "String view is expected to be lossy on high-entropy content — if this "
                + "starts passing, the fixture stopped containing invalid UTF-8");
    }

    @Test
    void textPayloadsStillRoundTripThroughStringOverloads() {
        // The UTF-8 conveniences remain correct for their intended use.
        String text = "JABCode-COA|sn=AB12-CD34|iss=rhabi|binary-api-compat-check";

        byte[] png = encoder.encodeWithConfig(text,
            JABCodeEncoder.Config.builder().colorNumber(8).eccLevel(5).build());
        assertNotNull(png);

        assertEquals(text, decoder.decode(png));
    }
}
