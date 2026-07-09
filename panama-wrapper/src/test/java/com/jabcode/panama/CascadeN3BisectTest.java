package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JVM-level regression guard for the >8207-byte byte-run continuation bug
 * (encoder.c: the numeric shift-to-byte token was written 5 bits wide instead
 * of 6, shearing the remaining stream — "Not enough bits to decode").
 *
 * <p>Historical note: this began as a bisection harness for a downstream
 * "N>=3 v31 16c cascade decodes empty" report. The cascade geometry was
 * innocent — the trigger is the payload (java.util.Random(0xFA0003)) whose
 * mode plan enters a giant byte run from NUMERIC mode. Kept at the exact
 * failing configuration, through both the direct native-PNG leg and the
 * consumer-realistic ImageIO re-encode leg.</p>
 */
@EnabledIf("isNativeLibraryAvailable")
class CascadeN3BisectTest {

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

    private byte[] encodeCascade(byte[] payload, int n, int version) {
        var config = JABCodeEncoder.Config.builder()
            .colorNumber(16)
            .eccLevel(5)
            .symbolNumber(n)
            .symbolVersions(java.util.Collections.nCopies(n, new SymbolVersion(version)))
            .build();
        return encoder.encodeBytes(payload, config);
    }

    @Test
    void nativePngDirect_n3v31() {
        byte[] payload = new byte[9 * 1024];
        new Random(0xFA_0003).nextBytes(payload);

        byte[] png = encodeCascade(payload, 3, 31);
        assertNotNull(png, "encode failed");

        byte[] decoded = decoder.decodeBytes(png);
        assertArrayEquals(payload, decoded, "DIRECT native-PNG leg failed");
    }

    @Test
    void imageIoRoundTrip_n3v31() throws Exception {
        byte[] payload = new byte[9 * 1024];
        new Random(0xFA_0003).nextBytes(payload);

        byte[] png = encodeCascade(payload, 3, 31);
        assertNotNull(png, "encode failed");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image, "ImageIO could not read the native PNG");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "PNG", out), "ImageIO PNG write failed");

        byte[] decoded = decoder.decodeBytes(out.toByteArray());
        assertArrayEquals(payload, decoded, "ImageIO round-trip leg failed (type="
            + image.getType() + ", " + image.getWidth() + "x" + image.getHeight() + ")");
    }
}
