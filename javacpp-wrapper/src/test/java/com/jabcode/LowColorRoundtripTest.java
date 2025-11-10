package com.jabcode;

import static org.junit.Assert.*;

import com.jabcode.internal.NativeLibraryLoader;
import org.junit.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LowColorRoundtripTest {
    private static boolean nativeLibraryAvailable = false;
    private static final String TEST_OUTPUT_DIR = "test-output";
    private final List<String> filesToCleanup = new ArrayList<>();

    static {
        try {
            NativeLibraryLoader.load();
            nativeLibraryAvailable = true;
            System.out.println("Native library loaded from: " + NativeLibraryLoader.getLoadedLibraryPath());
        } catch (Throwable e) {
            System.err.println("Failed to load native library: " + e.getMessage());
            nativeLibraryAvailable = false;
        }
    }

    @Before
    public void setUp() {
        Assume.assumeTrue("Native library is not available", nativeLibraryAvailable);
        File outDir = new File(TEST_OUTPUT_DIR);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
    }

    @After
    public void tearDown() {
        for (String path : filesToCleanup) {
            try {
                File f = new File(path);
                if (f.exists()) f.delete();
            } catch (Throwable ignore) {}
        }
    }

    @Test
    public void testRoundtrip_4_and_8_colors() throws IOException {
        roundtrip("LC4", OptimizedJABCode.ColorMode.QUATERNARY);
        roundtrip("LC8", OptimizedJABCode.ColorMode.OCTAL);
    }

    private void roundtrip(String payload, OptimizedJABCode.ColorMode mode) throws IOException {
        String out = TEST_OUTPUT_DIR + "/lowcolor_roundtrip_" + mode.getColorCount() + ".png";
        System.out.println("LowColorRoundtripTest: generating " + mode.name() + " (" + mode.getColorCount() + " colors), payload=\"" + payload + "\"");

        // Use native encode+save to avoid Java ImageIO palette alterations
        OptimizedJABCode.encodeToFileNative(payload.getBytes(), mode, 1, 2, false, new File(out));
        filesToCleanup.add(out);

        byte[] decoded = OptimizedJABCode.decode(new File(out));
        String decodedText = new String(decoded);
        System.out.println("  - decoded: " + decodedText);
        assertEquals("Roundtrip text mismatch for " + mode.name(), payload, decodedText);
    }
}
