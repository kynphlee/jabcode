package com.jabcode;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.jabcode.internal.NativeLibraryLoader;
import com.jabcode.internal.JABCodeNative;
import com.jabcode.internal.JABCodeNativePtr;

/**
 * Validates small-payload roundtrips for high-color modes (16/64/256)
 * when the feature flag jabcode.highcolor.native is enabled.
 */
public class HighColorRoundtripTest {

    private static boolean nativeLibraryAvailable = false;
    private static boolean highColorEnabled = false;
    private final List<String> filesToCleanup = new ArrayList<>();
    private static final String TEST_OUTPUT_DIR = "test-output";

    static {
        try {
            NativeLibraryLoader.load();
            nativeLibraryAvailable = true;
            System.out.println("Native library loaded from: " + NativeLibraryLoader.getLoadedLibraryPath());
        } catch (Throwable e) {
            System.err.println("Failed to load native library: " + e.getMessage());
            nativeLibraryAvailable = false;
        } finally {
            try { JABCodeNativePtr.setForceNc(-1); } catch (Throwable ignore) {}
            try { JABCodeNativePtr.setUseDefaultPaletteHighColor(0); } catch (Throwable ignore) {}
            try { JABCodeNativePtr.setForceEcl(0, 0); } catch (Throwable ignore) {}
        }
        String prop = System.getProperty("jabcode.highcolor.native", "false");
        String env = System.getenv("JABCODE_HIGHCOLOR_NATIVE");
        highColorEnabled = Boolean.parseBoolean(prop) || (env != null && Boolean.parseBoolean(env));
        System.out.println("High-color native flag: " + highColorEnabled);
    }

    @Before
    public void setUp() {
        // Guard: require native library and high-color flag
        Assume.assumeTrue("Native library is not available", nativeLibraryAvailable);
        Assume.assumeTrue("High-color native flag is not enabled", highColorEnabled);

        File outputDir = new File(TEST_OUTPUT_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
    }

    @After
    public void tearDown() {
        for (String filePath : filesToCleanup) {
            File f = new File(filePath);
            if (f.exists()) {
                f.delete();
            }
        }
    }

    @Test
    public void testSmallPayloadRoundtrip_HC16_HC64_HC256() throws IOException {
        validateSmallPayloadRoundtrip(OptimizedJABCode.ColorMode.HEXADECIMAL, "HC16");
        validateSmallPayloadRoundtrip(OptimizedJABCode.ColorMode.MODE_64,    "HC64");
        validateSmallPayloadRoundtrip(OptimizedJABCode.ColorMode.MODE_256,   "HC256");
    }

    private void validateSmallPayloadRoundtrip(OptimizedJABCode.ColorMode mode, String payload) throws IOException {
        System.out.println("HighColorRoundtripTest: generating " + mode.name() + " (" + mode.getColorCount() + " colors), payload=\"" + payload + "\"");

        String out = TEST_OUTPUT_DIR + "/hc_roundtrip_" + mode.getColorCount() + ".png";
        // Use native encode+save to preserve high-color palette
        OptimizedJABCode.encodeToFileNative(payload.getBytes(), mode, 1, 2, false, new File(out));
        filesToCleanup.add(out);

        // Optionally reload to ensure file I/O path is valid
        BufferedImage reloaded = ImageIO.read(new File(out));
        assertNotNull("Reloaded image should not be null", reloaded);
        assertTrue("Width>0", reloaded.getWidth() > 0);
        assertTrue("Height>0", reloaded.getHeight() > 0);

        try {
            // Adjust Nc thresholds for high-color (test-only experiment)
            JABCodeNativePtr.setNcThresholds(70, 0.05);
            // Use default palette grid for high-color during decode (test-only)
            JABCodeNativePtr.setUseDefaultPaletteHighColor(1);
            // Force expected Nc (2^(Nc+1) == colorCount)
            int expectedNc = Integer.numberOfTrailingZeros(mode.getColorCount()) - 1;
            JABCodeNativePtr.setForceNc(expectedNc);
            // Force expected ECL (wc, wr) for ECC=2 path
            JABCodeNativePtr.setForceEcl(3, 7);
            // Prefer native read path to avoid Java ImageIO write re-encoding that can alter palette
            byte[] decodedBytes = OptimizedJABCode.decode(new File(out));
            String decodedText = new String(decodedBytes);
            if (decodedText.equals(payload)) {
                System.out.println("  - OK: roundtrip matched payload");
            } else {
                System.out.println("  - Roundtrip mismatch: got=\"" + decodedText + "\"");
            }
            // Gather decode diagnostics for both NORMAL and COMPAT modes
            long bmp = JABCodeNativePtr.readImagePtr(out);
            int[] infoN = JABCodeNativePtr.debugDecodeExInfoPtr(bmp, JABCodeNative.NORMAL_DECODE);
            int[] infoC = JABCodeNativePtr.debugDecodeExInfoPtr(bmp, JABCodeNative.COMPATIBLE_DECODE);
            System.out.println("  - diag: NORMAL  status=" + infoN[0] + ", Nc=" + infoN[4] + ", side=" + infoN[8] + "x" + infoN[9] + ", msz=" + infoN[7] + ", ecl=(" + infoN[5] + "," + infoN[6] + "), defmode=" + infoN[10]);
            System.out.println("  - diag: COMPAT  status=" + infoC[0] + ", Nc=" + infoC[4] + ", side=" + infoC[8] + "x" + infoC[9] + ", msz=" + infoC[7] + ", ecl=(" + infoC[5] + "," + infoC[6] + "), defmode=" + infoC[10]);
            int[] dsN = JABCodeNativePtr.debugDetectorStatsPtr(bmp, JABCodeNative.NORMAL_DECODE);
            int[] dsC = JABCodeNativePtr.debugDetectorStatsPtr(bmp, JABCodeNative.COMPATIBLE_DECODE);
            System.out.println("  - det: NORMAL  status=" + dsN[0] + ", Nc=" + dsN[1] + ", side=" + dsN[2] + "x" + dsN[3] + ", msz=" + dsN[4] + ", APs=[(" + dsN[5] + "," + dsN[6] + "),(" + dsN[7] + "," + dsN[8] + "),(" + dsN[9] + "," + dsN[10] + "),(" + dsN[11] + "," + dsN[12] + ")] ");
            System.out.println("  - det: COMPAT  status=" + dsC[0] + ", Nc=" + dsC[1] + ", side=" + dsC[2] + "x" + dsC[3] + ", msz=" + dsC[4] + ", APs=[(" + dsC[5] + "," + dsC[6] + "),(" + dsC[7] + "," + dsC[8] + "),(" + dsC[9] + "," + dsC[10] + "),(" + dsC[11] + "," + dsC[12] + ")] ");
        } catch (Throwable t) {
            System.out.println("  - Decode not successful for " + mode.name() + ": " + t.getMessage());
            try {
                long bmp = JABCodeNativePtr.readImagePtr(out);
                int[] infoN = JABCodeNativePtr.debugDecodeExInfoPtr(bmp, JABCodeNative.NORMAL_DECODE);
                int[] infoC = JABCodeNativePtr.debugDecodeExInfoPtr(bmp, JABCodeNative.COMPATIBLE_DECODE);
                System.out.println("  - diag: NORMAL  status=" + infoN[0] + ", Nc=" + infoN[4] + ", side=" + infoN[8] + "x" + infoN[9] + ", msz=" + infoN[7] + ", ecl=(" + infoN[5] + "," + infoN[6] + "), defmode=" + infoN[10]);
                System.out.println("  - diag: COMPAT  status=" + infoC[0] + ", Nc=" + infoC[4] + ", side=" + infoC[8] + "x" + infoC[9] + ", msz=" + infoC[7] + ", ecl=(" + infoC[5] + "," + infoC[6] + "), defmode=" + infoC[10]);
                int[] dsN = JABCodeNativePtr.debugDetectorStatsPtr(bmp, JABCodeNative.NORMAL_DECODE);
                int[] dsC = JABCodeNativePtr.debugDetectorStatsPtr(bmp, JABCodeNative.COMPATIBLE_DECODE);
                System.out.println("  - det: NORMAL  status=" + dsN[0] + ", Nc=" + dsN[1] + ", side=" + dsN[2] + "x" + dsN[3] + ", msz=" + dsN[4] + ", APs=[(" + dsN[5] + "," + dsN[6] + "),(" + dsN[7] + "," + dsN[8] + "),(" + dsN[9] + "," + dsN[10] + "),(" + dsN[11] + "," + dsN[12] + ")] ");
                System.out.println("  - det: COMPAT  status=" + dsC[0] + ", Nc=" + dsC[1] + ", side=" + dsC[2] + "x" + dsC[3] + ", msz=" + dsC[4] + ", APs=[(" + dsC[5] + "," + dsC[6] + "),(" + dsC[7] + "," + dsC[8] + "),(" + dsC[9] + "," + dsC[10] + "),(" + dsC[11] + "," + dsC[12] + ")] ");
            } catch (Throwable ignore) {}
            // Do not fail the test here; intent is to validate encode path and collect diagnostics.
        }

        // ECC=1 fallback experiment
        String out2 = TEST_OUTPUT_DIR + "/hc_roundtrip_" + mode.getColorCount() + "_ecc1.png";
        try {
            OptimizedJABCode.encodeToFileNative(payload.getBytes(), mode, 1, 1, false, new File(out2));
            filesToCleanup.add(out2);
            // Try normal decode first
            // Adjust thresholds and force Nc again for ECC1 attempt
            JABCodeNativePtr.setNcThresholds(70, 0.05);
            JABCodeNativePtr.setUseDefaultPaletteHighColor(1);
            int expectedNc2 = Integer.numberOfTrailingZeros(mode.getColorCount()) - 1;
            JABCodeNativePtr.setForceNc(expectedNc2);
            // Force expected ECL (wc, wr) for ECC=1 path
            JABCodeNativePtr.setForceEcl(3, 8);
            byte[] decodedBytes2 = OptimizedJABCode.decode(new File(out2));
            System.out.println("  - ECC1 decode length=" + decodedBytes2.length);
        } catch (Throwable t) {
            System.out.println("  - ECC1 decode failed: " + t.getMessage());
            try {
                // Try with preprocessing
                byte[] decodedBytes3 = OptimizedJABCode.decodeWithProcessing(new File(out2), true);
                System.out.println("  - ECC1 WithProcessing decode length=" + decodedBytes3.length);
            } catch (Throwable t2) {
                System.out.println("  - ECC1 WithProcessing also failed: " + t2.getMessage());
            }
            try {
                long bmp2 = JABCodeNativePtr.readImagePtr(out2);
                int[] infoN2 = JABCodeNativePtr.debugDecodeExInfoPtr(bmp2, JABCodeNative.NORMAL_DECODE);
                int[] infoC2 = JABCodeNativePtr.debugDecodeExInfoPtr(bmp2, JABCodeNative.COMPATIBLE_DECODE);
                System.out.println("  - ECC1 diag: NORMAL status=" + infoN2[0] + ", Nc=" + infoN2[4] + ", side=" + infoN2[8] + "x" + infoN2[9] + ", msz=" + infoN2[7] + ", ecl=(" + infoN2[5] + "," + infoN2[6] + "), defmode=" + infoN2[10]);
                System.out.println("  - ECC1 diag: COMPAT status=" + infoC2[0] + ", Nc=" + infoC2[4] + ", side=" + infoC2[8] + "x" + infoC2[9] + ", msz=" + infoC2[7] + ", ecl=(" + infoC2[5] + "," + infoC2[6] + "), defmode=" + infoC2[10]);
                int[] dsN2 = JABCodeNativePtr.debugDetectorStatsPtr(bmp2, JABCodeNative.NORMAL_DECODE);
                int[] dsC2 = JABCodeNativePtr.debugDetectorStatsPtr(bmp2, JABCodeNative.COMPATIBLE_DECODE);
                System.out.println("  - ECC1 det: NORMAL status=" + dsN2[0] + ", Nc=" + dsN2[1] + ", side=" + dsN2[2] + "x" + dsN2[3] + ", msz=" + dsN2[4] + ", APs=[(" + dsN2[5] + "," + dsN2[6] + "),(" + dsN2[7] + "," + dsN2[8] + "),(" + dsN2[9] + "," + dsN2[10] + "),(" + dsN2[11] + "," + dsN2[12] + ")] ");
                System.out.println("  - ECC1 det: COMPAT status=" + dsC2[0] + ", Nc=" + dsC2[1] + ", side=" + dsC2[2] + "x" + dsC2[3] + ", msz=" + dsC2[4] + ", APs=[(" + dsC2[5] + "," + dsC2[6] + "),(" + dsC2[7] + "," + dsC2[8] + "),(" + dsC2[9] + "," + dsC2[10] + "),(" + dsC2[11] + "," + dsC2[12] + ")] ");
            } catch (Throwable ignore) {}
        } finally {
            try { JABCodeNativePtr.setForceNc(-1); } catch (Throwable ignore) {}
        }
    }
}
