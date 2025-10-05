package com.jabcode.tests;

import com.jabcode.internal.JABCodeNative;
import org.junit.AfterClass;
import org.junit.BeforeClass;
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
        return JABCodeNative.createDataFromBytes(bytes);
    }

    private static void destroyData(long dataPtr) {
        if (dataPtr != 0L) JABCodeNative.destroyDataPtr(dataPtr);
    }

    private static void cleanup(long encPtr, long dataPtr) {
        destroyData(dataPtr);
        if (encPtr != 0L) JABCodeNative.destroyEncodePtr(encPtr);
    }

    @Test
    public void testColorModesRange_generateSimpleCodes() {
        int[] colors = {4, 8, 16, 32, 64, 128, 256};
        for (int color : colors) {
            long enc = 0L, data = 0L;
            try {
                enc = JABCodeNative.createEncodePtr(color, 1);
                assertNotEquals("createEncodePtr returned 0 for color=" + color, 0L, enc);

                // Use defaults from library; keep payload small
                data = createData("HELLO-COLOR-" + color);
                int status = JABCodeNative.generateJABCodePtr(enc, data);
                assertEquals("generateJABCodePtr failed for color=" + color, 0, status);

                long bmp = JABCodeNative.getBitmapFromEncodePtr(enc);
                assertNotEquals("bitmap null for color=" + color, 0L, bmp);

                String name = new File(outDir, "color_" + color + "_" + ts() + ".png").getPath();
                boolean saved = JABCodeNative.saveImagePtr(bmp, name);
                assertTrue("saveImagePtr failed for color=" + color + " -> " + name, saved);
            } finally {
                cleanup(enc, data);
            }
        }
    }

    @Test
    public void testPrimarySymbolPayloads_variousSizes() {
        int color = 8;
        int[] sizes = {8, 64, 256};
        for (int sz : sizes) {
            long enc = 0L, data = 0L;
            try {
                enc = JABCodeNative.createEncodePtr(color, 1);
                assertNotEquals(0L, enc);

                char[] chars = new char[sz];
                Arrays.fill(chars, 'A');
                String payload = new String(chars);
                data = JABCodeNative.createDataFromBytes(payload.getBytes(StandardCharsets.UTF_8));
                int status = JABCodeNative.generateJABCodePtr(enc, data);
                assertEquals("generate failed for payload size=" + sz, 0, status);

                long bmp = JABCodeNative.getBitmapFromEncodePtr(enc);
                assertNotEquals(0L, bmp);
                String name = new File(outDir, String.format("primary_payload_%d_%s.png", sz, ts())).getPath();
                assertTrue(JABCodeNative.saveImagePtr(bmp, name));
            } finally {
                cleanup(enc, data);
            }
        }
    }

    @Test
    public void testSymbolCascading_variousParameters() {
        int color = 8;
        int[] symbolCounts = {2, 3};
        int[] candidateSizes = {512, 1024, 2048};

        for (int sym : symbolCounts) {
            boolean anySucceededForSym = false;
            for (int sz : candidateSizes) {
                long enc = 0L, data = 0L;
                try {
                    enc = JABCodeNative.createEncodePtr(color, sym);
                    assertNotEquals(0L, enc);

                    // For multi-symbol encoding we must set versions [1..32] and unique positions
                    for (int i = 0; i < sym; i++) {
                        JABCodeNative.setSymbolVersionPtr(enc, i, 5, 5);
                        JABCodeNative.setSymbolPositionPtr(enc, i, i);
                    }

                    char[] chars = new char[sz];
                    Arrays.fill(chars, 'C');
                    String payload = new String(chars);
                    data = JABCodeNative.createDataFromBytes(payload.getBytes(StandardCharsets.UTF_8));

                    int status = JABCodeNative.generateJABCodePtr(enc, data);
                    if (status == 0) {
                        long bmp = JABCodeNative.getBitmapFromEncodePtr(enc);
                        assertNotEquals(0L, bmp);
                        String name = new File(outDir, String.format("cascade_sym%d_sz%d_%s.png", sym, sz, ts())).getPath();
                        assertTrue(JABCodeNative.saveImagePtr(bmp, name));
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
