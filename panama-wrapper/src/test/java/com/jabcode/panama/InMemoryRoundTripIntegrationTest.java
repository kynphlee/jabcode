package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the in-memory (no temp file) encode/decode paths:
 * {@link JABCodeEncoder#encodeWithConfig} -> PNG {@code byte[]} via the native
 * {@code saveImageToMemory}, and {@link JABCodeDecoder#decode(byte[])} ->
 * {@code String} via {@code readImageFromMemory}.
 *
 * <p>The whole round trip stays in memory -- no {@link java.nio.file.Path} is
 * ever passed in or out -- so sensitive auth/COA payloads never touch disk. This
 * is the security motivation for the in-memory path; correctness is verified
 * across the polychrome colour suite (4..256).</p>
 */
@EnabledIf("isNativeLibraryAvailable")
class InMemoryRoundTripIntegrationTest {

    private final JABCodeEncoder encoder = new JABCodeEncoder();
    private final JABCodeDecoder decoder = new JABCodeDecoder();

    /** A framework-realistic COA payload (synthetic test data, not a real token). */
    private static final String PAYLOAD =
        "JABCode-COA|sn=AB12-CD34-EF56-GH78|iss=rhabi|ts=2026-06-13T12:00:00Z";

    /** Polychrome colour modes the swift-lineage .so round-trips on pristine PNGs. */
    private static final int[] COLOR_MODES = {4, 8, 16, 32, 64, 128, 256};

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

    @Test
    void roundTripInMemoryAcrossColorModes() {
        for (int colorMode : COLOR_MODES) {
            // Encode straight to PNG bytes -- no file path involved.
            byte[] png = encoder.encode(PAYLOAD, colorMode, 5);
            assertNotNull(png, colorMode + "-colour encode should return PNG bytes");
            assertTrue(png.length > 0, colorMode + "-colour PNG must be non-empty");
            assertTrue(isPng(png), colorMode + "-colour output must be a valid PNG");

            // Decode straight from those bytes -- still no file.
            JABCodeDecoder.resetDecoderState();
            String decoded = decoder.decode(png);
            assertEquals(PAYLOAD, decoded,
                colorMode + "-colour in-memory round trip must preserve the payload");
        }
    }

    @Test
    void encodeRejectsNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(null, 8, 5));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode("", 8, 5));
    }

    @Test
    void decodeRejectsNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> decoder.decode((byte[]) null));
        assertThrows(IllegalArgumentException.class, () -> decoder.decode(new byte[0]));
    }

    @Test
    void decodeFailsGracefullyOnNonPngBytes() {
        // Non-PNG bytes can't be read into a bitmap: the in-memory path fails
        // gracefully (null / unsuccessful result) rather than throwing.
        byte[] notAPng = {1, 2, 3};
        assertNull(decoder.decode(notAPng));
        assertFalse(decoder.decodeEx(notAPng).isSuccess());
    }

    /** PNG 8-byte signature: 89 50 4E 47 0D 0A 1A 0A. */
    private static boolean isPng(byte[] b) {
        return b.length >= 8
            && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
            && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
            && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A;
    }
}
