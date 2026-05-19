package com.jabcode.test;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.jabcode.JABCodeMobile;

/**
 * All-Nc Self-Test Activity.
 *
 * Performs an in-memory encode → decode roundtrip for each JABCode color mode
 * (Nc=0..7, i.e. colorNumber ∈ {2, 4, 8, 16, 32, 64, 128, 256}) using the
 * fixed payload "HELLO". This mirrors the desktop reference test
 * src/jabcode/test/test_roundtrip_all_nc.c, but runs against the Android-ABI
 * build of libjabcode-mobile.so. No camera, no bitmap conversion, no
 * confounding pipeline stages — pure C-library correctness on Android.
 *
 * Verifies WS-0/2/3 checklist items:
 *   0.11  Android library Mode 0 (Nc=0) verification
 *   2.4   Android logcat DIAG_* marker visibility (markers route through
 *         __JAB_ANDROID_LOG_INFO automatically and appear with tag "JABCode")
 *   3.11  Android library Nc=7 verification via shared CMake source
 *
 * Log filter (adb logcat):
 *   adb logcat -d -s WS-0-2-3-VERIFY:I JABCode:I
 *
 * See: docs/jabcode-all-nc-plan/00-CHECKLIST.md
 */
public class AllNcSelfTestActivity extends Activity {

    private static final String TAG = "WS-0-2-3-VERIFY";
    private static final String PAYLOAD = "HELLO";
    private static final int ECC_LEVEL = 3;

    /** Per-Nc mapping: {colorNumber, Nc, descriptiveName}. */
    private static final int[][] MODES = {
        {2,   0},
        {4,   1},
        {8,   2},
        {16,  3},
        {32,  4},
        {64,  5},
        {128, 6},
        {256, 7},
    };

    private static final String[] MODE_NAMES = {
        "2-color/monochrome", "4-color", "8-color", "16-color",
        "32-color", "64-color", "128-color", "256-color",
    };

    private TextView resultsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_nc_selftest);
        resultsView = findViewById(R.id.all_nc_results);

        String version = JABCodeMobile.getVersion();
        appendLine("================================================");
        appendLine("All-Nc Self-Test (Android ABI)");
        appendLine("libjabcode-mobile version: " + version);
        appendLine("Payload: \"" + PAYLOAD + "\"  ECC: " + ECC_LEVEL);
        appendLine("================================================");

        Log.i(TAG, "================================================");
        Log.i(TAG, "All-Nc Self-Test starting on Android ABI");
        Log.i(TAG, "libjabcode-mobile version: " + version);
        Log.i(TAG, "================================================");

        new Thread(this::runAllModes, "all-nc-selftest").start();
    }

    /** Runs the Nc=0..7 roundtrip loop on a worker thread. */
    private void runAllModes() {
        int passes = 0;
        int failures = 0;

        for (int i = 0; i < MODES.length; i++) {
            int colorNumber = MODES[i][0];
            int nc = MODES[i][1];
            String name = MODE_NAMES[i];

            String header = String.format("--- Nc=%d (%s) ---", nc, name);
            appendLine(header);
            Log.i(TAG, header);

            String result = runOneMode(colorNumber);
            if (result.startsWith("PASS")) {
                passes++;
            } else {
                failures++;
            }
            appendLine("  " + result);
            Log.i(TAG, "  " + result);
        }

        String summary = String.format(
            "Summary: %d/%d Nc levels PASS",
            passes, MODES.length);
        appendLine("================================================");
        appendLine(summary);
        appendLine("================================================");
        Log.i(TAG, "================================================");
        Log.i(TAG, summary);
        Log.i(TAG, "================================================");
    }

    /**
     * Runs one encode↔decode roundtrip at the given colorNumber.
     * Returns a "PASS: …" or "FAIL: …" string, mirroring desktop test format.
     */
    private String runOneMode(int colorNumber) {
        JABCodeMobile.clearError();

        JABCodeMobile.EncodeResult enc = null;
        try {
            enc = JABCodeMobile.encode(PAYLOAD, colorNumber, ECC_LEVEL);
            if (enc == null) {
                String err = JABCodeMobile.getLastError();
                return "FAIL: encode returned null (lastError=" + err + ")";
            }

            String decoded = JABCodeMobile.decode(enc, colorNumber, ECC_LEVEL);
            if (decoded == null) {
                String err = JABCodeMobile.getLastError();
                return "FAIL: decode returned null (lastError=" + err + ")";
            }

            if (!decoded.equals(PAYLOAD)) {
                return String.format(
                    "FAIL: roundtrip mismatch (expected=\"%s\" got=\"%s\")",
                    PAYLOAD, decoded);
            }

            return String.format(
                "PASS: %d bytes decoded correctly", decoded.length());
        } catch (Throwable t) {
            return "FAIL: exception " + t.getClass().getSimpleName()
                + ": " + t.getMessage();
        } finally {
            if (enc != null) {
                try {
                    enc.free();
                } catch (Throwable ignored) {
                    // Best-effort cleanup; don't mask a real failure
                }
            }
        }
    }

    private void appendLine(final String line) {
        runOnUiThread(() -> {
            if (resultsView != null) {
                resultsView.append(line + "\n");
            }
        });
    }
}
