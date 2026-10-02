package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression test for the native leak in
 * {@link JABCodeDecoder#decodeFromFileEx(Path, int)}.
 *
 * <p>{@code readImage} returns a {@code calloc}'d bitmap and {@code decodeJABCode}
 * a {@code malloc}'d {@code jab_data}, both owned by the caller. The method used
 * to free neither, so every {@code decodeFromFile} call stranded a bitmap of
 * 20 + 4 x width x height bytes (254 KB for the 252x252 symbol here).</p>
 *
 * <p>The check reads glibc's {@code mallinfo2} in-use bytes
 * ({@code uordblks + hblkhd}), which leave out the Java heap. HotSpot's own
 * malloc use still moves: JIT compilations of the JaCoCo-instrumented decode
 * path take megabytes of arena chunks, and a few seconds after start-up the
 * JVM frees tens of megabytes of pooled chunks (NMT's "Arena Chunk"). A single
 * before/after window can therefore hide the leak. The test warms up, measures
 * many short batches, and asserts on the median batch, so no single such event
 * decides the result. The result block (18 bytes for this payload) is too
 * small to resolve this way; the assertion guards the bitmap. Where
 * {@code mallinfo2} is absent (a non-glibc libc, or glibc before 2.33) the
 * test is skipped.</p>
 */
@EnabledIf("isNativeLibraryAvailable")
class DecodeFromFileLeakIntegrationTest {

    private static final String PAYLOAD = "Hello JABCode!";
    private static final int WARMUP_DECODES = 100;
    private static final int BATCHES = 15;
    private static final int BATCH_SIZE = 20;

    /** glibc {@code struct mallinfo2}: ten {@code size_t} fields. */
    private static final StructLayout MALLINFO2 = MemoryLayout.structLayout(
        JAVA_LONG.withName("arena"),
        JAVA_LONG.withName("ordblks"),
        JAVA_LONG.withName("smblks"),
        JAVA_LONG.withName("hblks"),
        JAVA_LONG.withName("hblkhd"),
        JAVA_LONG.withName("usmblks"),
        JAVA_LONG.withName("fsmblks"),
        JAVA_LONG.withName("uordblks"),
        JAVA_LONG.withName("fordblks"),
        JAVA_LONG.withName("keepcost"));

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

    /** Bytes malloc currently has handed out: heap chunks plus mmap'd chunks. */
    private static long mallocInUse(MethodHandle mallinfo2) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = (MemorySegment) mallinfo2.invokeExact((SegmentAllocator) arena);
            return info.get(JAVA_LONG, MALLINFO2.byteOffset(groupElement("uordblks")))
                 + info.get(JAVA_LONG, MALLINFO2.byteOffset(groupElement("hblkhd")));
        }
    }

    @Test
    void decodeFromFileFreesTheNativeBitmap(@TempDir Path tempDir) throws Throwable {
        Linker linker = Linker.nativeLinker();
        Optional<MemorySegment> symbol = linker.defaultLookup().find("mallinfo2");
        assumeTrue(symbol.isPresent(), "mallinfo2 needs glibc 2.33+");
        MethodHandle mallinfo2 = linker.downcallHandle(symbol.get(), FunctionDescriptor.of(MALLINFO2));

        byte[] png = new JABCodeEncoder().encode(PAYLOAD, 8, 5);
        assertNotNull(png);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        // jab_bitmap: 5 x int32 header + RGBA pixels, one calloc in readImage
        long bitmapBytes = 20 + 4L * image.getWidth() * image.getHeight();

        Path file = tempDir.resolve("leak.png");
        Files.write(file, png);
        JABCodeDecoder decoder = new JABCodeDecoder();

        // Warm-up
        for (int i = 0; i < WARMUP_DECODES; i++) {
            assertEquals(PAYLOAD, decoder.decodeFromFile(file));
        }

        long[] growth = new long[BATCHES];
        for (int b = 0; b < BATCHES; b++) {
            long before = mallocInUse(mallinfo2);
            for (int i = 0; i < BATCH_SIZE; i++) {
                assertEquals(PAYLOAD, decoder.decodeFromFile(file));
            }
            growth[b] = mallocInUse(mallinfo2) - before;
        }
        long[] sorted = growth.clone();
        Arrays.sort(sorted);
        long median = sorted[BATCHES / 2];

        System.out.printf(
            "[DECODE-LEAK] symbol %dx%d, %d batches of %d decodeFromFile: median growth %d bytes "
                + "per batch (a leaked bitmap per call is %d); per batch in KB: %s%n",
            image.getWidth(), image.getHeight(), BATCHES, BATCH_SIZE, median,
            BATCH_SIZE * bitmapBytes, Arrays.toString(Arrays.stream(growth).map(g -> g / 1024).toArray()));

        // Leaking the bitmap grows every quiet batch by BATCH_SIZE x bitmapBytes
        // (about 5 MB); a freed one leaves it near zero.
        assertTrue(median < BATCH_SIZE * bitmapBytes / 2, String.format(
            "median batch of %d decodeFromFile calls grew malloc in-use by %d bytes; "
                + "readImage's bitmap is %d bytes, so each call is leaking it",
            BATCH_SIZE, median, bitmapBytes));
    }
}
