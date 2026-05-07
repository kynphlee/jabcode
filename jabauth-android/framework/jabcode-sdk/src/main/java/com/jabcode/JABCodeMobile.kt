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
