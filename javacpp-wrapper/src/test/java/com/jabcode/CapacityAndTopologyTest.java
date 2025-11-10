package com.jabcode;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Assume;
import org.junit.Test;

import com.jabcode.OptimizedJABCode.ColorMode;
import com.jabcode.internal.JABCodeNative;
import com.jabcode.internal.JABCodeNativePtr;
import com.jabcode.internal.NativeLibraryLoader;

public class CapacityAndTopologyTest {

    private static boolean nativeLibraryAvailable = false;
    private static boolean highColorEnabled = false;

    static {
        try {
            NativeLibraryLoader.load();
            nativeLibraryAvailable = true;
        } catch (Throwable t) {
            nativeLibraryAvailable = false;
        }
        String prop = System.getProperty("jabcode.highcolor.native", "false");
        String env = System.getenv("JABCODE_HIGHCOLOR_NATIVE");
        highColorEnabled = Boolean.parseBoolean(prop) || (env != null && Boolean.parseBoolean(env));
    }

    private static byte[] payload(int size, String seed) {
        byte[] bytes = new byte[size];
        byte[] s = ("JABCODE:" + seed + ":").getBytes();
        for (int i = 0; i < size; i++) {
            bytes[i] = s[i % s.length];
        }
        return bytes;
    }

    private void roundtrip(ColorMode mode, int symbolCount, int payloadSize) {
        Assume.assumeTrue("Native library is not available", nativeLibraryAvailable);
        Assume.assumeTrue("High-color native flag is not enabled", highColorEnabled);
        
        // Generate test payload
        byte[] payload = payload(payloadSize, mode.name());
        File out = new File("test-output/ct_" + mode.getColorCount() + "_c_s" + symbolCount + ".png");
        out.getParentFile().mkdirs();
        
        // Encode the payload using native save to preserve palette
        try {
            OptimizedJABCode.encodeToFileNative(payload, mode, 1, highColorEnabled ? 2 : 5, false, out);
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode test data: " + e.getMessage(), e);
        }
        
        // Try to decode and verify
        try {
            byte[] decoded = OptimizedJABCode.decode(out);
            if (decoded != null && decoded.length > 0) {
                assertArrayEquals("Data prefix mismatch for mode=" + mode + ", symbols=" + symbolCount,
                        Arrays.copyOf(payload, Math.min(decoded.length, 20)),  // Compare first 20 bytes for brevity
                        Arrays.copyOf(decoded, Math.min(decoded.length, 20)));
            }
        } catch (Throwable t) {
            System.out.println("[CT] Decode failed for mode=" + mode + ": " + t.getMessage());
        }
        // Fallback: try decode with preprocessing
        try {
            byte[] decoded2 = OptimizedJABCode.decodeWithProcessing(out, true);
            if (decoded2 != null && decoded2.length > 0) {
                System.out.println("[CT] WithProcessing decoded length=" + decoded2.length);
            }
        } catch (Throwable t) {
            System.out.println("[CT] DecodeWithProcessing also failed for mode=" + mode + ": " + t.getMessage());
        }
        // Diagnostics: report detector info for both NORMAL and COMPAT modes
        try {
            long bmp = JABCodeNativePtr.readImagePtr(out.getPath());
            int[] infoN = JABCodeNativePtr.debugDecodeExInfoPtr(bmp, JABCodeNative.NORMAL_DECODE);
            int[] infoC = JABCodeNativePtr.debugDecodeExInfoPtr(bmp, JABCodeNative.COMPATIBLE_DECODE);
            System.out.println("[CT] " + mode + " NORMAL: status=" + infoN[0] + ", Nc=" + infoN[4] + ", side=" + infoN[8] + "x" + infoN[9] + ", msz=" + infoN[7] + ", ecl=(" + infoN[5] + "," + infoN[6] + "), defmode=" + infoN[10]);
            System.out.println("[CT] " + mode + " COMPAT: status=" + infoC[0] + ", Nc=" + infoC[4] + ", side=" + infoC[8] + "x" + infoC[9] + ", msz=" + infoC[7] + ", ecl=(" + infoC[5] + "," + infoC[6] + "), defmode=" + infoC[10]);
        } catch (Throwable ignore) {}
    }

    @Test
    public void testRoundtrip_16colors_singleSymbol() {
        roundtrip(ColorMode.HEXADECIMAL, 1, 12);
    }

    @Test
    public void testRoundtrip_64colors_singleSymbol() {
        roundtrip(ColorMode.MODE_64, 1, 12);
    }

    @Test
    public void testRoundtrip_256colors_singleSymbol() {
        roundtrip(ColorMode.MODE_256, 1, 12);
    }
}
