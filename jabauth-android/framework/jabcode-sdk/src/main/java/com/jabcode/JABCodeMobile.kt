package com.jabcode

import android.graphics.Bitmap

/**
 * JNI Bridge to native JABCode library
 * 
 * This class matches the package/class names that the native library expects.
 * DO NOT RENAME - JNI bindings depend on exact package/class name.
 */
internal class JABCodeMobile {
    
    companion object {
        init {
            System.loadLibrary("jabcode-mobile")
        }
    }
    
    /**
     * Native decode function
     * 
     * @param rgbaData RGBA byte array
     * @param width Image width
     * @param height Image height
     * @param timeout Decode timeout in ms
     * @return Decoded data as byte array, or null if no code found
     */
    external fun nativeDecode(
        rgbaData: ByteArray,
        width: Int,
        height: Int,
        timeout: Long
    ): ByteArray?
    
    /**
     * Decode from Bitmap directly
     */
    external fun nativeDecodeFromBitmap(
        bitmap: Bitmap,
        timeout: Long
    ): ByteArray?

    /**
     * Decode from Bitmap directly AND capture the decoded color mode.
     *
     * Same decode pipeline as `nativeDecodeFromBitmap`, but additionally
     * writes the detected color count (one of {2, 4, 8, 16, 32, 64, 128, 256})
     * into `outColorNumber[0]` on success. On failure (return null), the
     * out param is set to 0.
     *
     * Backed by `jabMobileDecodeCameraWithMeta` in mobile_bridge.c — a
     * parallel implementation to the regular camera decode. The existing
     * `nativeDecodeFromBitmap` path is preserved and unchanged.
     *
     * @param bitmap RGBA_8888 bitmap to decode
     * @param timeout Decode timeout in ms (currently unused by native side;
     *                reserved for future timeout enforcement)
     * @param outColorNumber Size-≥1 IntArray; on success, [0] = color count
     * @return Decoded bytes, or null if no JABCode was found
     */
    external fun nativeDecodeFromBitmapWithMeta(
        bitmap: Bitmap,
        timeout: Long,
        outColorNumber: IntArray
    ): ByteArray?
    
    /**
     * Get library version
     */
    external fun nativeGetVersion(): String
    
    /**
     * Get last error message
     */
    external fun nativeGetLastError(): String?
    
    /**
     * Clear last error
     */
    external fun nativeClearError()

    /**
     * Toggle verbose diagnostic logging in the native decoder/detector.
     *
     * WS-5 Heisenberg gate: by default, per-iteration markers
     * (DIAG_PALETTE_LEARNED, DIAG_PARTII_RESULT, Nc_FALLBACK retries,
     * DIAG_MODE0_DETECT, DETECT SUCCESS, GRID_REF) are suppressed to
     * preserve the camera-thread decode budget. Terminal markers
     * (FAIL_ATTR, DECODE_OK, DIAG_SYMBOL_DECODE final result) always fire.
     *
     * Diagnostic apps may enable verbose mode before a capture window and
     * disable it afterward. Production SDK consumers should leave this at
     * the default. Thread-local; safe across concurrent decoders.
     *
     * @param verbose true to emit chatty markers, false to suppress
     */
    external fun nativeSetDiagVerbose(verbose: Boolean)

    /**
     * Path β permissive color classification toggle.
     *
     * When TRUE, the C decoder's master metadata Part I module-color
     * stage substitutes rgb=5 (Magenta) with rgb=6 (Yellow). Compensates
     * for camera green-channel under-capture documented in the
     * 2026-05-30 H_nc2 trace where 77/78 PartI failures concentrated at
     * module[0] rgb=5 after the AWB/AE convergence-lock crushed
     * rgb=7 (white-washout) failures from 104 to 1.
     *
     * @param permissive true to substitute rgb=5 → rgb=6 at metadata
     *                   modules; false to enforce strict {0, 3, 6}
     */
    external fun nativeSetPermissiveColorClassification(permissive: Boolean)

    /**
     * Path β preferred color count override.
     *
     * Pins the C decoder to a specific Nc by collapsing the Nc fallback
     * ladder to a single deterministic attempt at the chosen Nc. Closes
     * the Settings UI "Preferred Color Mode" dropdown wiring gap — the
     * dropdown was previously cosmetic for decoder steering.
     *
     * Valid values: 0 (auto), 2, 4, 8, 16, 32, 64, 128, 256.
     * Invalid values are clamped to no-op at the C layer (auto remains).
     *
     * @param count 0 for auto-detect, or the desired palette color count
     */
    external fun nativeSetPreferredColorCount(count: Int)
}
