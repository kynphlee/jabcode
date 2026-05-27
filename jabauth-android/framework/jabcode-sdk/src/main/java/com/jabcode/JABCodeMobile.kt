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
}
