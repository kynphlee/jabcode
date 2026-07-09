package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the raw-RGBA decode path:
 * {@link JABCodeDecoder#decodeRgbaEx(byte[], int, int)}.
 *
 * <p>The PNG decode paths pay a full PNG compress (caller side) + decompress
 * (native side) per call purely to move pixels across the FFM boundary. The
 * RGBA path assembles the {@code jab_bitmap} struct directly in Arena memory,
 * so callers already holding pixels (camera frame, BufferedImage raster) skip
 * the codec work entirely. These tests pin (1) byte-exact agreement with the
 * PNG path and (2) that the payload survives identically.</p>
 */
@EnabledIf("isNativeLibraryAvailable")
class RgbaDecodeIntegrationTest {

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

    /** Extract row-major RGBA8888 bytes from a BufferedImage. */
    private static byte[] toRgba(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        byte[] rgba = new byte[w * h * 4];
        int i = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                rgba[i++] = (byte) ((argb >> 16) & 0xFF); // R
                rgba[i++] = (byte) ((argb >> 8) & 0xFF);  // G
                rgba[i++] = (byte) (argb & 0xFF);         // B
                rgba[i++] = (byte) ((argb >> 24) & 0xFF); // A
            }
        }
        return rgba;
    }

    @Test
    void rgbaPathDecodesBinaryPayloadExactly() throws Exception {
        byte[] payload = new byte[384];
        new Random(0x46BA).nextBytes(payload);

        byte[] png = encoder.encodeBytes(payload,
            JABCodeEncoder.Config.builder().colorNumber(8).eccLevel(5).build());
        assertNotNull(png);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image);

        byte[] decoded = decoder.decodeRgba(toRgba(image), image.getWidth(), image.getHeight());
        assertArrayEquals(payload, decoded,
            "RGBA path must decode the payload byte-identically");
    }

    @Test
    void rgbaPathAgreesWithPngPath() throws Exception {
        byte[] payload = "rgba-vs-png-agreement|sn=0001|the same symbol, two transports"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] png = encoder.encodeBytes(payload,
            JABCodeEncoder.Config.builder().colorNumber(16).eccLevel(5).build());
        assertNotNull(png);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        byte[] viaPng = decoder.decodeBytes(png);
        byte[] viaRgba = decoder.decodeRgba(toRgba(image), image.getWidth(), image.getHeight());

        assertArrayEquals(payload, viaPng);
        assertArrayEquals(viaPng, viaRgba, "both transports must yield identical bytes");
    }

    @Test
    void rejectsMismatchedDimensions() {
        assertThrows(IllegalArgumentException.class,
            () -> decoder.decodeRgbaEx(new byte[16], 3, 3));
    }

    /**
     * Relative timing of the two transports on the same symbol. Informational —
     * printed, not asserted — perf assertions are flaky on shared runners.
     */
    @Test
    void reportPngVsRgbaDecodeTiming() throws Exception {
        byte[] payload = new byte[1024];
        new Random(42).nextBytes(payload);

        byte[] png = encoder.encodeBytes(payload,
            JABCodeEncoder.Config.builder().colorNumber(8).eccLevel(5).build());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        byte[] rgba = toRgba(image);
        int w = image.getWidth(), h = image.getHeight();

        // Warm-up
        for (int i = 0; i < 5; i++) {
            decoder.decodeBytes(png);
            decoder.decodeRgba(rgba, w, h);
        }

        int n = 30;
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            assertNotNull(decoder.decodeBytes(png));
        }
        long pngNs = (System.nanoTime() - t0) / n;

        t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            assertNotNull(decoder.decodeRgba(rgba, w, h));
        }
        long rgbaNs = (System.nanoTime() - t0) / n;

        System.out.printf(
            "[RGBA-TIMING] symbol %dx%d, payload 1024B, n=%d: png-path %.2f ms/decode, "
                + "rgba-path %.2f ms/decode (%.1f%% of png)%n",
            w, h, n, pngNs / 1e6, rgbaNs / 1e6, 100.0 * rgbaNs / pngNs);

        // The consumer-realistic chain starts from a BufferedImage (camera frame,
        // decoded upload): the PNG route additionally pays ImageIO.write PNG
        // COMPRESSION per call; the RGBA route pays only a pixel walk.
        t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            assertNotNull(decoder.decodeBytes(out.toByteArray()));
        }
        long chainPngNs = (System.nanoTime() - t0) / n;

        t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            byte[] px = toRgba(image);
            assertNotNull(decoder.decodeRgba(px, w, h));
        }
        long chainRgbaNs = (System.nanoTime() - t0) / n;

        System.out.printf(
            "[RGBA-TIMING] full chain from BufferedImage, n=%d: via-PNG %.2f ms "
                + "(ImageIO.write + decode), via-RGBA %.2f ms (pixel walk + decode) "
                + "(%.1f%% of png chain)%n",
            n, chainPngNs / 1e6, chainRgbaNs / 1e6, 100.0 * chainRgbaNs / chainPngNs);
    }
}
