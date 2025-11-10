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
            JABCodeNativePtr.setClassifierDebug(1);
            JABCodeNativePtr.setClassifierMode(0);
            // Force expected ECL (wc, wr) for ECC=2 path (level=2 -> wc=3, wr=7)
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
            int[] cs = JABCodeNativePtr.getClassifierStats(6 + mode.getColorCount());
            if (cs != null && cs.length >= 6) {
                System.out.println("  - clf: total=" + cs[0] + ", black=" + cs[1] + ", nonblack=" + cs[2] + ", avg_margin_micro=" + cs[3] + ", colors=" + cs[4] + ", mode=" + cs[5]);
                StringBuilder hist = new StringBuilder();
                int bins = Math.min(mode.getColorCount(), cs.length - 6);
                for (int i = 0; i < bins; i++) { if (i > 0) hist.append(','); hist.append(cs[6 + i]); }
                System.out.println("  - clf_hist[0.." + (bins-1) + "]=" + hist);
            }
            // Pipeline debug
            try {
                int[] pipe = JABCodeNativePtr.getDecodePipelineDebug(12);
                if (pipe != null && pipe.length >= 11) {
                    System.out.println("  - pipe: p1=" + pipe[0] + ", p2=" + pipe[1] + ", bpm=" + pipe[2] + ", mask=" + pipe[3] + ", wc=" + pipe[4] + ", wr=" + pipe[5] + ", Pg=" + pipe[6] + ", Pn=" + pipe[7] + ", raw_mod_len=" + pipe[8] + ", raw_data_len=" + pipe[9] + ", clf_total=" + pipe[10] + ", interp=" + pipe[11]);
                }
                // LDPC pre/post bits (first 32)
                int[] ldpc_pre = JABCodeNativePtr.getLdpcInputDebug(0, 64);
                int[] ldpc_post = JABCodeNativePtr.getLdpcInputDebug(1, 64);
                if (ldpc_pre != null && ldpc_post != null) {
                    StringBuilder pre = new StringBuilder();
                    StringBuilder post = new StringBuilder();
                    int nb = 32;
                    for (int i = 0; i < nb && i < ldpc_pre.length; i++) pre.append(ldpc_pre[i]);
                    for (int i = 0; i < nb && i < ldpc_post.length; i++) post.append(ldpc_post[i]);
                    System.out.println("  - ldpc(pre)[0..31]=" + pre + ", ldpc(post)[0..31]=" + post);
                }
                int[] smp = JABCodeNativePtr.getRawModuleSample(32);
                if (smp != null && smp.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    int n = Math.min(32, smp.length);
                    for (int i = 0; i < n; i++) { if (i > 0) sb.append(','); sb.append(smp[i]); }
                    System.out.println("  - raw_mod_sample[0.." + (n-1) + "]=" + sb);
                }
                // Part II debug: [count, wc, wr, mask, bits...]
                int[] p2 = JABCodeNativePtr.getPart2Debug(68);
                if (p2 != null && p2.length >= 4) {
                    int cnt = p2[0], wc2 = p2[1], wr2 = p2[2], msk2 = p2[3];
                    StringBuilder bits = new StringBuilder();
                    int nbits = Math.min(32, Math.max(0, cnt));
                    for (int i = 0; i < nbits && 4 + i < p2.length; i++) { bits.append(p2[4 + i]); }
                    System.out.println("  - part2: count=" + cnt + ", wc=" + wc2 + ", wr=" + wr2 + ", mask=" + msk2 + ", bits[0.." + (nbits-1) + "]=" + bits);
                }
                // Palette comparison (decoder vs encoder default)
                try {
                    int colorCount = mode.getColorCount();
                    int len = Math.min(colorCount * 3, 256 * 3);
                    int[] decPal = JABCodeNativePtr.getDecoderPaletteDebug(len);
                    int[] encPal = JABCodeNativePtr.getEncoderDefaultPalette(colorCount, len);
                    if (decPal != null && encPal != null && decPal.length >= 3 && encPal.length >= 3) {
                        int trip = Math.min(32, len / 3);
                        StringBuilder dec = new StringBuilder();
                        StringBuilder enc = new StringBuilder();
                        for (int i = 0; i < trip; i++) {
                            if (i > 0) { dec.append(' '); enc.append(' '); }
                            dec.append('(').append(decPal[i*3]).append(',').append(decPal[i*3+1]).append(',').append(decPal[i*3+2]).append(')');
                            enc.append('(').append(encPal[i*3]).append(',').append(encPal[i*3+1]).append(',').append(encPal[i*3+2]).append(')');
                        }
                        System.out.println("  - pal(decoder)[0.." + (trip-1) + "]=" + dec);
                        System.out.println("  - pal(encoder)[0.." + (trip-1) + "]=" + enc);
                    }
                } catch (Throwable ignore2) {}
            } catch (Throwable ignore) {}
            int[] dsN = JABCodeNativePtr.debugDetectorStatsPtr(bmp, JABCodeNative.NORMAL_DECODE);
            int[] dsC = JABCodeNativePtr.debugDetectorStatsPtr(bmp, JABCodeNative.COMPATIBLE_DECODE);
            System.out.println("  - det: NORMAL  status=" + dsN[0] + ", Nc=" + dsN[1] + ", side=" + dsN[2] + "x" + dsN[3] + ", msz=" + dsN[4] + ", APs=[(" + dsN[5] + "," + dsN[6] + "),(" + dsN[7] + "," + dsN[8] + "),(" + dsN[9] + "," + dsN[10] + "),(" + dsN[11] + "," + dsN[12] + ")] ");
            System.out.println("  - det: COMPAT  status=" + dsC[0] + ", Nc=" + dsC[1] + ", side=" + dsC[2] + "x" + dsC[3] + ", msz=" + dsC[4] + ", APs=[(" + dsC[5] + "," + dsC[6] + "),(" + dsC[7] + "," + dsC[8] + "),(" + dsC[9] + "," + dsC[10] + "),(" + dsC[11] + "," + dsC[12] + ")] ");
            // Compare classifier mode 1 (raw RGB)
            try {
                JABCodeNativePtr.setClassifierDebug(1);
                JABCodeNativePtr.setClassifierMode(1);
                // invoke decode again to populate stats under raw classifier
                try { OptimizedJABCode.decode(new File(out)); } catch (Throwable ignore) {}
                int[] csRaw = JABCodeNativePtr.getClassifierStats(6 + mode.getColorCount());
                if (csRaw != null && csRaw.length >= 6) {
                    System.out.println("  - clf(raw): total=" + csRaw[0] + ", black=" + csRaw[1] + ", nonblack=" + csRaw[2] + ", avg_margin_micro=" + csRaw[3] + ", colors=" + csRaw[4] + ", mode=" + csRaw[5]);
                    StringBuilder histR = new StringBuilder();
                    int binsR = Math.min(mode.getColorCount(), csRaw.length - 6);
                    for (int i = 0; i < binsR; i++) { if (i > 0) histR.append(','); histR.append(csRaw[6 + i]); }
                    System.out.println("  - clf_raw_hist[0.." + (binsR-1) + "]=" + histR);
                }
                // Pipeline and Part II under raw classifier
                int[] pipeR = JABCodeNativePtr.getDecodePipelineDebug(12);
                if (pipeR != null && pipeR.length >= 11) {
                    System.out.println("  - pipe(raw): p1=" + pipeR[0] + ", p2=" + pipeR[1] + ", bpm=" + pipeR[2] + ", mask=" + pipeR[3] + ", wc=" + pipeR[4] + ", wr=" + pipeR[5] + ", Pg=" + pipeR[6] + ", Pn=" + pipeR[7] + ", raw_mod_len=" + pipeR[8] + ", raw_data_len=" + pipeR[9] + ", clf_total=" + pipeR[10] + ", interp=" + pipeR[11]);
                }
                int[] p2R = JABCodeNativePtr.getPart2Debug(68);
                if (p2R != null && p2R.length >= 4) {
                    int cntR = p2R[0], wcR = p2R[1], wrR = p2R[2], mskR = p2R[3];
                    StringBuilder bitsR = new StringBuilder();
                    int nbitsR = Math.min(32, Math.max(0, cntR));
                    for (int i = 0; i < nbitsR && 4 + i < p2R.length; i++) { bitsR.append(p2R[4 + i]); }
                    System.out.println("  - part2(raw): count=" + cntR + ", wc=" + wcR + ", wr=" + wrR + ", mask=" + mskR + ", bits[0.." + (nbitsR-1) + "]=" + bitsR);
                }
            } catch (Throwable ignore) {}
        } catch (Throwable t) {
            System.out.println("  - Decode not successful for " + mode.name() + ": " + t.getMessage());
            try {
                long bmp = JABCodeNativePtr.readImagePtr(out);
                int[] infoN = JABCodeNativePtr.debugDecodeExInfoPtr(bmp, JABCodeNative.NORMAL_DECODE);
                int[] infoC = JABCodeNativePtr.debugDecodeExInfoPtr(bmp, JABCodeNative.COMPATIBLE_DECODE);
                System.out.println("  - diag: NORMAL  status=" + infoN[0] + ", Nc=" + infoN[4] + ", side=" + infoN[8] + "x" + infoN[9] + ", msz=" + infoN[7] + ", ecl=(" + infoN[5] + "," + infoN[6] + "), defmode=" + infoN[10]);
                System.out.println("  - diag: COMPAT  status=" + infoC[0] + ", Nc=" + infoC[4] + ", side=" + infoC[8] + "x" + infoC[9] + ", msz=" + infoC[7] + ", ecl=(" + infoC[5] + "," + infoC[6] + "), defmode=" + infoC[10]);
                int[] cs = JABCodeNativePtr.getClassifierStats(6 + mode.getColorCount());
                if (cs != null && cs.length >= 6) {
                    System.out.println("  - clf: total=" + cs[0] + ", black=" + cs[1] + ", nonblack=" + cs[2] + ", avg_margin_micro=" + cs[3] + ", colors=" + cs[4] + ", mode=" + cs[5]);
                }
                int[] dsN = JABCodeNativePtr.debugDetectorStatsPtr(bmp, JABCodeNative.NORMAL_DECODE);
                int[] dsC = JABCodeNativePtr.debugDetectorStatsPtr(bmp, JABCodeNative.COMPATIBLE_DECODE);
                System.out.println("  - det: NORMAL  status=" + dsN[0] + ", Nc=" + dsN[1] + ", side=" + dsN[2] + "x" + dsN[3] + ", msz=" + dsN[4] + ", APs=[(" + dsN[5] + "," + dsN[6] + "),(" + dsN[7] + "," + dsN[8] + "),(" + dsN[9] + "," + dsN[10] + "),(" + dsN[11] + "," + dsN[12] + ")] ");
                System.out.println("  - det: COMPAT  status=" + dsC[0] + ", Nc=" + dsC[1] + ", side=" + dsC[2] + "x" + dsC[3] + ", msz=" + dsC[4] + ", APs=[(" + dsC[5] + "," + dsC[6] + "),(" + dsC[7] + "," + dsC[8] + "),(" + dsC[9] + "," + dsC[10] + "),(" + dsC[11] + "," + dsC[12] + ")] ");
                // Pipeline debug on failure
                try {
                    int[] pipe = JABCodeNativePtr.getDecodePipelineDebug(12);
                    if (pipe != null && pipe.length >= 11) {
                        System.out.println("  - pipe: p1=" + pipe[0] + ", p2=" + pipe[1] + ", bpm=" + pipe[2] + ", mask=" + pipe[3] + ", wc=" + pipe[4] + ", wr=" + pipe[5] + ", Pg=" + pipe[6] + ", Pn=" + pipe[7] + ", raw_mod_len=" + pipe[8] + ", raw_data_len=" + pipe[9] + ", clf_total=" + pipe[10] + ", interp=" + pipe[11]);
                    }
                    int[] smp = JABCodeNativePtr.getRawModuleSample(32);
                    if (smp != null && smp.length > 0) {
                        StringBuilder sb = new StringBuilder();
                        int n = Math.min(32, smp.length);
                        for (int i = 0; i < n; i++) { if (i > 0) sb.append(','); sb.append(smp[i]); }
                        System.out.println("  - raw_mod_sample[0.." + (n-1) + "]=" + sb);
                    }
                    int[] p2 = JABCodeNativePtr.getPart2Debug(68);
                    if (p2 != null && p2.length >= 4) {
                        int cnt = p2[0], wc2 = p2[1], wr2 = p2[2], msk2 = p2[3];
                        StringBuilder bits = new StringBuilder();
                        int nbits = Math.min(32, Math.max(0, cnt));
                        for (int i = 0; i < nbits && 4 + i < p2.length; i++) { bits.append(p2[4 + i]); }
                        System.out.println("  - part2: count=" + cnt + ", wc=" + wc2 + ", wr=" + wr2 + ", mask=" + msk2 + ", bits[0.." + (nbits-1) + "]=" + bits);
                    }
                    // Palette comparison (decoder vs encoder default)
                    try {
                        int colorCount = mode.getColorCount();
                        int len = Math.min(colorCount * 3, 256 * 3);
                        int[] decPal = JABCodeNativePtr.getDecoderPaletteDebug(len);
                        int[] encPal = JABCodeNativePtr.getEncoderDefaultPalette(colorCount, len);
                        if (decPal != null && encPal != null && decPal.length >= 3 && encPal.length >= 3) {
                            int trip = Math.min(32, len / 3);
                            StringBuilder dec = new StringBuilder();
                            StringBuilder enc = new StringBuilder();
                            for (int i = 0; i < trip; i++) {
                                if (i > 0) { dec.append(' '); enc.append(' '); }
                                dec.append('(').append(decPal[i*3]).append(',').append(decPal[i*3+1]).append(',').append(decPal[i*3+2]).append(')');
                                enc.append('(').append(encPal[i*3]).append(',').append(encPal[i*3+1]).append(',').append(encPal[i*3+2]).append(')');
                            }
                            System.out.println("  - pal(decoder)[0.." + (trip-1) + "]=" + dec);
                            System.out.println("  - pal(encoder)[0.." + (trip-1) + "]=" + enc);
                        }
                    } catch (Throwable ignore2) {}
                    // Sweep mask types to probe demask impact
                    try {
                        for (int m = 0; m < 8; m++) {
                            JABCodeNativePtr.setForceMask(m);
                            try { OptimizedJABCode.decode(new File(out)); } catch (Throwable ignoreM) {}
                            int[] pipeM = JABCodeNativePtr.getDecodePipelineDebug(12);
                            int[] p2M = JABCodeNativePtr.getPart2Debug(68);
                            String bitsM = "";
                            if (p2M != null && p2M.length >= 4) {
                                StringBuilder sbm = new StringBuilder();
                                int nbitsM = Math.min(24, Math.max(0, p2M[0]));
                                for (int i = 0; i < nbitsM && 4 + i < p2M.length; i++) { sbm.append(p2M[4 + i]); }
                                bitsM = sbm.toString();
                            }
                            if (pipeM != null && pipeM.length >= 11) {
                                System.out.println("  - mask=" + m + ": p1=" + pipeM[0] + ", p2=" + pipeM[1] + ", bpm=" + pipeM[2] + ", wc=" + pipeM[4] + ", wr=" + pipeM[5] + ", Pn=" + pipeM[7] + ", clf_total=" + pipeM[10] + ", interp=" + pipeM[11] + ", p2bits[0..]=" + bitsM);
                            }
                        }
                    } finally {
                        try { JABCodeNativePtr.setForceMask(-1); } catch (Throwable ignoreM2) {}
                    }
                } catch (Throwable ignore) {}
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
            JABCodeNativePtr.setClassifierDebug(1);
            JABCodeNativePtr.setClassifierMode(0);
            // Force expected ECL (wc, wr) for ECC=1 path (level=1 -> wc=3, wr=8)
            JABCodeNativePtr.setForceEcl(3, 8);
            // ECC=1 decode attempt
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
                int[] cs2 = JABCodeNativePtr.getClassifierStats(6 + mode.getColorCount());
                if (cs2 != null && cs2.length >= 6) {
                    System.out.println("  - ECC1 clf: total=" + cs2[0] + ", black=" + cs2[1] + ", nonblack=" + cs2[2] + ", avg_margin_micro=" + cs2[3] + ", colors=" + cs2[4] + ", mode=" + cs2[5]);
                }
                // Compare classifier mode 1 (raw RGB) under ECC1
                try {
                    JABCodeNativePtr.setClassifierDebug(1);
                    JABCodeNativePtr.setClassifierMode(1);
                    try { OptimizedJABCode.decode(new File(out2)); } catch (Throwable ignore) {}
                    int[] cs2Raw = JABCodeNativePtr.getClassifierStats(6 + mode.getColorCount());
                    if (cs2Raw != null && cs2Raw.length >= 6) {
                        System.out.println("  - ECC1 clf(raw): total=" + cs2Raw[0] + ", black=" + cs2Raw[1] + ", nonblack=" + cs2Raw[2] + ", avg_margin_micro=" + cs2Raw[3] + ", colors=" + cs2Raw[4] + ", mode=" + cs2Raw[5]);
                    }
                    // ECC1: Pipeline and Part II under raw classifier
                    int[] pipe2R = JABCodeNativePtr.getDecodePipelineDebug(12);
                    if (pipe2R != null && pipe2R.length >= 11) {
                        System.out.println("  - ECC1 pipe(raw): p1=" + pipe2R[0] + ", p2=" + pipe2R[1] + ", bpm=" + pipe2R[2] + ", mask=" + pipe2R[3] + ", wc=" + pipe2R[4] + ", wr=" + pipe2R[5] + ", Pg=" + pipe2R[6] + ", Pn=" + pipe2R[7] + ", raw_mod_len=" + pipe2R[8] + ", raw_data_len=" + pipe2R[9] + ", clf_total=" + pipe2R[10] + ", interp=" + pipe2R[11]);
                    }
                    int[] p2bR = JABCodeNativePtr.getPart2Debug(68);
                    if (p2bR != null && p2bR.length >= 4) {
                        int cntR = p2bR[0], wcR = p2bR[1], wrR = p2bR[2], mskR = p2bR[3];
                        StringBuilder bitsR = new StringBuilder();
                        int nbitsR = Math.min(32, Math.max(0, cntR));
                        for (int i = 0; i < nbitsR && 4 + i < p2bR.length; i++) { bitsR.append(p2bR[4 + i]); }
                        System.out.println("  - ECC1 part2(raw): count=" + cntR + ", wc=" + wcR + ", wr=" + wrR + ", mask=" + mskR + ", bits[0.." + (nbitsR-1) + "]=" + bitsR);
                    }
                } catch (Throwable ignore) {}
                int[] dsN2 = JABCodeNativePtr.debugDetectorStatsPtr(bmp2, JABCodeNative.NORMAL_DECODE);
                int[] dsC2 = JABCodeNativePtr.debugDetectorStatsPtr(bmp2, JABCodeNative.COMPATIBLE_DECODE);
                System.out.println("  - ECC1 det: NORMAL status=" + dsN2[0] + ", Nc=" + dsN2[1] + ", side=" + dsN2[2] + "x" + dsN2[3] + ", msz=" + dsN2[4] + ", APs=[(" + dsN2[5] + "," + dsN2[6] + "),(" + dsN2[7] + "," + dsN2[8] + "),(" + dsN2[9] + "," + dsN2[10] + "),(" + dsN2[11] + "," + dsN2[12] + ")] ");
                System.out.println("  - ECC1 det: COMPAT status=" + dsC2[0] + ", Nc=" + dsC2[1] + ", side=" + dsC2[2] + "x" + dsC2[3] + ", msz=" + dsC2[4] + ", APs=[(" + dsC2[5] + "," + dsC2[6] + "),(" + dsC2[7] + "," + dsC2[8] + "),(" + dsC2[9] + "," + dsC2[10] + "),(" + dsC2[11] + "," + dsC2[12] + ")] ");
                // Pipeline debug for ECC1 failure path
                try {
                    int[] pipe2 = JABCodeNativePtr.getDecodePipelineDebug(12);
                    if (pipe2 != null && pipe2.length >= 11) {
                        System.out.println("  - ECC1 pipe: p1=" + pipe2[0] + ", p2=" + pipe2[1] + ", bpm=" + pipe2[2] + ", mask=" + pipe2[3] + ", wc=" + pipe2[4] + ", wr=" + pipe2[5] + ", Pg=" + pipe2[6] + ", Pn=" + pipe2[7] + ", raw_mod_len=" + pipe2[8] + ", raw_data_len=" + pipe2[9] + ", clf_total=" + pipe2[10] + ", interp=" + pipe2[11]);
                    }
                    // ECC1 LDPC pre/post bits (first 32)
                    int[] ldpc_pre2 = JABCodeNativePtr.getLdpcInputDebug(0, 64);
                    int[] ldpc_post2 = JABCodeNativePtr.getLdpcInputDebug(1, 64);
                    if (ldpc_pre2 != null && ldpc_post2 != null) {
                        StringBuilder pre2 = new StringBuilder();
                        StringBuilder post2 = new StringBuilder();
                        int nb2 = 32;
                        for (int i = 0; i < nb2 && i < ldpc_pre2.length; i++) pre2.append(ldpc_pre2[i]);
                        for (int i = 0; i < nb2 && i < ldpc_post2.length; i++) post2.append(ldpc_post2[i]);
                        System.out.println("  - ECC1 ldpc(pre)[0..31]=" + pre2 + ", ldpc(post)[0..31]=" + post2);
                    }
                    int[] smp2 = JABCodeNativePtr.getRawModuleSample(32);
                    if (smp2 != null && smp2.length > 0) {
                        StringBuilder sb2 = new StringBuilder();
                        int n2 = Math.min(32, smp2.length);
                        for (int i = 0; i < n2; i++) { if (i > 0) sb2.append(','); sb2.append(smp2[i]); }
                        System.out.println("  - ECC1 raw_mod_sample[0.." + (n2-1) + "]=" + sb2);
                    }
                    int[] p2b = JABCodeNativePtr.getPart2Debug(68);
                    if (p2b != null && p2b.length >= 4) {
                        int cnt = p2b[0], wc2 = p2b[1], wr2 = p2b[2], msk2 = p2b[3];
                        StringBuilder bits = new StringBuilder();
                        int nbits = Math.min(32, Math.max(0, cnt));
                        for (int i = 0; i < nbits && 4 + i < p2b.length; i++) { bits.append(p2b[4 + i]); }
                        System.out.println("  - ECC1 part2: count=" + cnt + ", wc=" + wc2 + ", wr=" + wr2 + ", mask=" + msk2 + ", bits[0.." + (nbits-1) + "]=" + bits);
                    }
                    // Palette comparison (decoder vs encoder default) for ECC1
                    try {
                        int colorCount = mode.getColorCount();
                        int len = Math.min(colorCount * 3, 256 * 3);
                        int[] decPal = JABCodeNativePtr.getDecoderPaletteDebug(len);
                        int[] encPal = JABCodeNativePtr.getEncoderDefaultPalette(colorCount, len);
                        if (decPal != null && encPal != null && decPal.length >= 3 && encPal.length >= 3) {
                            int trip = Math.min(32, len / 3);
                            StringBuilder dec = new StringBuilder();
                            StringBuilder enc = new StringBuilder();
                            for (int i = 0; i < trip; i++) {
                                if (i > 0) { dec.append(' '); enc.append(' '); }
                                dec.append('(').append(decPal[i*3]).append(',').append(decPal[i*3+1]).append(',').append(decPal[i*3+2]).append(')');
                                enc.append('(').append(encPal[i*3]).append(',').append(encPal[i*3+1]).append(',').append(encPal[i*3+2]).append(')');
                            }
                            System.out.println("  - ECC1 pal(decoder)[0.." + (trip-1) + "]=" + dec);
                            System.out.println("  - ECC1 pal(encoder)[0.." + (trip-1) + "]=" + enc);
                        }
                    } catch (Throwable ignore2) {}
                    // ECC1: sweep mask types
                    try {
                        for (int m = 0; m < 8; m++) {
                            JABCodeNativePtr.setForceMask(m);
                            try { OptimizedJABCode.decode(new File(out2)); } catch (Throwable ignoreM) {}
                            int[] pipeM = JABCodeNativePtr.getDecodePipelineDebug(12);
                            int[] p2M = JABCodeNativePtr.getPart2Debug(68);
                            String bitsM = "";
                            if (p2M != null && p2M.length >= 4) {
                                StringBuilder sbm = new StringBuilder();
                                int nbitsM = Math.min(24, Math.max(0, p2M[0]));
                                for (int i = 0; i < nbitsM && 4 + i < p2M.length; i++) { sbm.append(p2M[4 + i]); }
                                bitsM = sbm.toString();
                            }
                            if (pipeM != null && pipeM.length >= 11) {
                                System.out.println("  - ECC1 mask=" + m + ": p1=" + pipeM[0] + ", p2=" + pipeM[1] + ", bpm=" + pipeM[2] + ", wc=" + pipeM[4] + ", wr=" + pipeM[5] + ", Pn=" + pipeM[7] + ", clf_total=" + pipeM[10] + ", interp=" + pipeM[11] + ", p2bits[0..]=" + bitsM);
                            }
                        }
                    } finally {
                        try { JABCodeNativePtr.setForceMask(-1); } catch (Throwable ignoreM2) {}
                    }
                } catch (Throwable ignore) {}
            } catch (Throwable ignore) {}
        } finally {
            try { JABCodeNativePtr.setForceNc(-1); } catch (Throwable ignore) {}
            try { JABCodeNativePtr.setUseDefaultPaletteHighColor(0); } catch (Throwable ignore) {}
        }
    }
}
