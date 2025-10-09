package com.jabcode.tests;

import com.jabcode.internal.JABCodeNative;
import com.jabcode.internal.JABCodeNativePtr;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.*;

public class JABCodeGenerationTest {

    private static File outDir;

    @BeforeClass
    public static void setup() {
        // Ensure native is loadable from internal path
        org.bytedeco.javacpp.Loader.load(JABCodeNative.class);
        outDir = new File("test-output/junit");
        if (!outDir.exists()) outDir.mkdirs();
        System.out.println("LD_PRELOAD=" + System.getenv("LD_PRELOAD"));
    }

    @AfterClass
    public static void teardown() {
        // No-op
    }

    private static String ts() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    }

    private static long createData(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return JABCodeNativePtr.createDataFromBytes(bytes);
    }

    private static void destroyData(long dataPtr) {
        if (dataPtr != 0L) JABCodeNativePtr.destroyDataPtr(dataPtr);
    }

    private static void cleanup(long encPtr, long dataPtr) {
        destroyData(dataPtr);
        if (encPtr != 0L) JABCodeNativePtr.destroyEncodePtr(encPtr);
    }

    @Test
    public void testColorModesRange_generateSimpleCodes() {
        int[] colors = {4, 8, 16, 32, 64, 128, 256};
        for (int color : colors) {
            long enc = 0L, data = 0L;
            try {
                enc = JABCodeNativePtr.createEncodePtr(color, 1);
                assertNotEquals("createEncodePtr returned 0 for color=" + color, 0L, enc);

                // Increase module size per color tier to improve metadata decoding robustness
                int ms = 0;
                if (color >= 256) ms = 48;
                else if (color >= 128) ms = 44;
                else if (color >= 64) ms = 40;
                else if (color >= 32) ms = 36;
                else if (color >= 16) ms = 32;
                if (ms > 0) {
                    JABCodeNativePtr.setModuleSizePtr(enc, ms);
                }
                // Keep payload small; force versions >=6 to ensure alignment patterns exist for decoding
                data = createData("HELLO-COLOR-" + color);
                JABCodeNativePtr.setSymbolVersionPtr(enc, 0, 6, 6);
                int status = JABCodeNativePtr.generateJABCodePtr(enc, data);
                assertEquals("generateJABCodePtr failed for color=" + color, 0, status);

                long bmp = JABCodeNativePtr.getBitmapFromEncodePtr(enc);
                assertNotEquals("bitmap null for color=" + color, 0L, bmp);

                String name = new File(outDir, "color_" + color + "_" + ts() + ".png").getPath();
                boolean saved = JABCodeNativePtr.saveImagePtr(bmp, name);
                assertTrue("saveImagePtr failed for color=" + color + " -> " + name, saved);
            } finally {
                cleanup(enc, data);
            }
        }
    }

    @Test
    @Ignore
    public void testPrimarySymbolPayloads_variousSizes() {
        int color = 8;
        int[] sizes = {8, 64, 256};
        for (int sz : sizes) {
            long enc = 0L, data = 0L;
            try {
                enc = JABCodeNativePtr.createEncodePtr(color, 1);
                assertNotEquals(0L, enc);

                char[] chars = new char[sz];
                Arrays.fill(chars, 'A');
                String payload = new String(chars);
                data = JABCodeNativePtr.createDataFromBytes(payload.getBytes(StandardCharsets.UTF_8));
                // Force versions >=6 for stable alignment pattern detection
                JABCodeNativePtr.setSymbolVersionPtr(enc, 0, 6, 6);
                int status = JABCodeNativePtr.generateJABCodePtr(enc, data);
                assertEquals("generate failed for payload size=" + sz, 0, status);

                long bmp = JABCodeNativePtr.getBitmapFromEncodePtr(enc);
                assertNotEquals(0L, bmp);
                String name = new File(outDir, String.format("primary_payload_%d_%s.png", sz, ts())).getPath();
                assertTrue(JABCodeNativePtr.saveImagePtr(bmp, name));
            } finally {
                cleanup(enc, data);
            }
        }
    }

    @Test
    @Ignore
    public void testSymbolCascading_variousParameters() {
        int color = 8;
        int[] symbolCounts = {2, 3};
        int[] candidateSizes = {512, 1024, 2048};

        for (int sym : symbolCounts) {
            boolean anySucceededForSym = false;
            for (int sz : candidateSizes) {
                long enc = 0L, data = 0L;
                try {
                    enc = JABCodeNativePtr.createEncodePtr(color, sym);
                    assertNotEquals(0L, enc);

                    // For multi-symbol encoding we must set versions [1..32] and unique positions
                    for (int i = 0; i < sym; i++) {
                        JABCodeNativePtr.setSymbolVersionPtr(enc, i, 5, 5);
                        JABCodeNativePtr.setSymbolPositionPtr(enc, i, i);
                    }

                    char[] chars = new char[sz];
                    Arrays.fill(chars, 'C');
                    String payload = new String(chars);
                    data = JABCodeNativePtr.createDataFromBytes(payload.getBytes(StandardCharsets.UTF_8));

                    int status = JABCodeNativePtr.generateJABCodePtr(enc, data);
                    if (status == 0) {
                        long bmp = JABCodeNativePtr.getBitmapFromEncodePtr(enc);
                        assertNotEquals(0L, bmp);
                        String name = new File(outDir, String.format("cascade_sym%d_sz%d_%s.png", sym, sz, ts())).getPath();
                        assertTrue(JABCodeNativePtr.saveImagePtr(bmp, name));
                        anySucceededForSym = true;
                        break;
                    }
                } finally {
                    cleanup(enc, data);
                }
            }
            assertTrue("No cascading config succeeded for sym=" + sym, anySucceededForSym);
        }
    }
}
