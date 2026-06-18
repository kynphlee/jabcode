package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-Nc round-trip validation matrix for the Panama wrapper against the current
 * C core.
 *
 * <p><b>Why this exists.</b> {@code panama-wrapper/REBUILD_NOTES.md} (2026-05-28)
 * recorded that "Nc=6 (128-color) round-trip tests fail" and that Nc=7 (256-color)
 * was excluded for an encoder malloc issue; {@link ColorMode7Test} is hard-{@code
 * @Disabled} on the same "malloc corruption" claim. Those notes predate recent
 * core fixes. The C-core {@code bench_codec} now reports {@code dec_ok 50/50} for
 * every Nc 2..256. This test settles whether the <em>wrapper</em> round-trips Nc6
 * (128c) and Nc7 (256c) cleanly through both code paths, exercising 256-color
 * explicitly (the disabled test never runs it).</p>
 *
 * <p>Security: this asserts equality but never logs decoded payload plaintext;
 * only the Nc value and a pass/fail flag are surfaced.</p>
 */
@EnabledIf("isNativeLibraryAvailable")
class NcRoundTripMatrixTest {

    /** Every polychrome colour mode (Nc 1..7). */
    private static final int[] COLOR_MODES = {4, 8, 16, 32, 64, 128, 256};

    /** Synthetic COA-shaped payload (not a real token). */
    private static final String PAYLOAD =
        "JABCode-COA|sn=ZZ99-YY88-XX77-WW66|iss=rhabi|ts=2026-06-18T00:00:00Z|nc-matrix";

    private final JABCodeEncoder encoder = new JABCodeEncoder();
    private final JABCodeDecoder decoder = new JABCodeDecoder();

    /** Mirrors InMemoryRoundTripIntegrationTest so both tests gate identically. */
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

    /** Nc=6 (128 colours) must round-trip clean in memory. */
    @Test
    void nc6InMemoryRoundTripsClean() {
        assertInMemoryRoundTrip(128);
    }

    /** Nc=7 (256 colours) must round-trip clean in memory (formerly @Disabled). */
    @Test
    void nc7InMemoryRoundTripsClean() {
        assertInMemoryRoundTrip(256);
    }

    /** Nc=6 (128 colours) must round-trip clean through the file path. */
    @Test
    void nc6FileRoundTripsClean(@TempDir Path tempDir) {
        assertFileRoundTrip(128, tempDir);
    }

    /** Nc=7 (256 colours) must round-trip clean through the file path (formerly @Disabled). */
    @Test
    void nc7FileRoundTripsClean(@TempDir Path tempDir) {
        assertFileRoundTrip(256, tempDir);
    }

    /**
     * Full matrix across both paths. Aggregates pass/fail so a single failing Nc
     * does not mask the others, then prints a security-clean per-Nc table.
     */
    @Test
    void fullMatrixAllNcBothPaths(@TempDir Path tempDir) {
        Map<Integer, String> memResult = new LinkedHashMap<>();
        Map<Integer, String> fileResult = new LinkedHashMap<>();

        for (int nc : COLOR_MODES) {
            memResult.put(nc, safeRoundTrip(() -> inMemoryRoundTrip(nc)));
            fileResult.put(nc, safeRoundTrip(() -> fileRoundTrip(nc, tempDir)));
        }

        StringBuilder table = new StringBuilder();
        table.append(String.format("%n%-6s %-12s %-12s%n", "Nc", "in-memory", "file"));
        table.append("------ ------------ ------------\n");
        boolean allPass = true;
        for (int nc : COLOR_MODES) {
            table.append(String.format("%-6d %-12s %-12s%n",
                nc, memResult.get(nc), fileResult.get(nc)));
            allPass &= "PASS".equals(memResult.get(nc)) && "PASS".equals(fileResult.get(nc));
        }
        System.out.println(table);

        assertTrue(allPass, "All Nc must round-trip clean on both paths; table:\n" + table);
    }

    // --- helpers -------------------------------------------------------------

    private void assertInMemoryRoundTrip(int nc) {
        inMemoryRoundTrip(nc);
    }

    private void assertFileRoundTrip(int nc, Path tempDir) {
        fileRoundTrip(nc, tempDir);
    }

    /** Encode to PNG bytes, decode from bytes, assert payload preserved. */
    private void inMemoryRoundTrip(int nc) {
        byte[] png = encoder.encode(PAYLOAD, nc, 5);
        assertTrue(png != null && png.length > 0, nc + "-colour encode must produce PNG bytes");
        JABCodeDecoder.resetDecoderState();
        String decoded = decoder.decode(png);
        assertEquals(PAYLOAD, decoded, nc + "-colour in-memory round trip must preserve payload");
    }

    /** Encode to a PNG file, decode from that file, assert payload preserved. */
    private void fileRoundTrip(int nc, Path tempDir) {
        Path out = tempDir.resolve("nc-" + nc + ".png");
        JABCodeEncoder.Config config = JABCodeEncoder.Config.builder()
            .colorNumber(nc)
            .eccLevel(5)
            .moduleSize(16)
            .build();
        boolean encoded = encoder.encodeToPNG(PAYLOAD, out.toString(), config);
        assertTrue(encoded, nc + "-colour encodeToPNG must succeed");
        assertTrue(out.toFile().exists() && out.toFile().length() > 0,
            nc + "-colour PNG file must be non-empty");
        JABCodeDecoder.resetDecoderState();
        String decoded = decoder.decodeFromFile(out);
        assertEquals(PAYLOAD, decoded, nc + "-colour file round trip must preserve payload");
    }

    /** Runs a round trip and maps any AssertionError/exception to a "FAIL" cell. */
    private String safeRoundTrip(Runnable r) {
        try {
            r.run();
            return "PASS";
        } catch (Throwable t) {
            return "FAIL";
        }
    }
}
