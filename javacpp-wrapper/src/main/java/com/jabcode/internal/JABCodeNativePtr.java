package com.jabcode.internal;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.annotation.Cast;

/**
 * Pointer-based JNI helpers for JABCode.
 * These methods are implemented manually in src/main/c/JABCodeNative_jni.cpp
 * under the JNI class name com.jabcode.internal.JABCodeNativePtr.
 */
public class JABCodeNativePtr {
    // Load the primary JNI first, then try to load a dedicated Ptr JNI library if present.
    // This allows two configurations to work:
    // 1) Single library: libjniJABCodeNative.so contains the Ptr JNI symbols
    // 2) Dual library:  libjniJABCodeNative.so + libjniJABCodeNativePtr.so (only Ptr symbols)
    static {
        Loader.load(JABCodeNative.class);
        try {
            Loader.load(); // attempts to load libjniJABCodeNativePtr.so by convention
        } catch (Throwable ignore) {
            // It's fine if a separate Ptr library isn't present; we rely on the primary library.
        }
        // Fallback: try explicit System.loadLibrary by canonical name
        try {
            System.loadLibrary("jniJABCodeNativePtr");
        } catch (Throwable ignore) {
            // ignore
        }
    }

    // Encoder / Data lifecycle
    public static native long createEncodePtr(@Cast("jab_int32") int color_number, @Cast("jab_int32") int symbol_number);
    public static native void destroyEncodePtr(long encPtr);
    public static native @Cast("jab_int32") int generateJABCodePtr(long encPtr, long dataPtr);
    public static native long createDataFromBytes(byte[] arr);
    public static native void destroyDataPtr(long dataPtr);

    // Decoding and image IO
    public static native long decodeJABCodePtr(long bitmapPtr, @Cast("jab_int32") int mode, int[] status);
    public static native long decodeJABCodeExPtr(long bitmapPtr, @Cast("jab_int32") int mode, int[] status, long symbolsPtr, @Cast("jab_int32") int max_symbol_number);
    public static native @Cast("jboolean") boolean saveImagePtr(long bitmapPtr, String filename);
    public static native long readImagePtr(String filename);

    // Utils
    public static native long getBitmapFromEncodePtr(long encPtr);
    public static native byte[] getDataBytes(long dataPtr);
    
    /**
     * Convert native bitmap to ARGB int array for direct BufferedImage creation.
     * Returns int[] with format: [width, height, pixel0, pixel1, ..., pixelN]
     * where each pixel is 0xAARRGGBB.
     * 
     * @param bitmapPtr pointer to jab_bitmap
     * @return int array with width, height, and ARGB pixels, or null if bitmapPtr is 0
     */
    public static native int[] bitmapToARGB(long bitmapPtr);

    // Encoder tuning
    public static native void setModuleSizePtr(long encPtr, int value);
    public static native void setMasterSymbolWidthPtr(long encPtr, int value);
    public static native void setMasterSymbolHeightPtr(long encPtr, int value);
    public static native void setSymbolVersionPtr(long encPtr, int index, int vx, int vy);
    public static native void setSymbolPositionPtr(long encPtr, int index, int pos);
    public static native void setSymbolEccLevelPtr(long encPtr, int index, int level);
    public static native void setAllEccLevelsPtr(long encPtr, int level);

    // Debug helper
    // Returns: [status, default_mode, side_version.x, side_version.y, Nc, ecl.x, ecl.y, module_size(int), side_size.x, side_size.y, data_ok]
    public static native int[] debugDecodeExInfoPtr(long bitmapPtr, int mode);
    // Detector stats: [status, Nc, side_x, side_y, msz, ap0x, ap0y, ap1x, ap1y, ap2x, ap2y, ap3x, ap3y]
    public static native int[] debugDetectorStatsPtr(long bitmapPtr, int mode);

    // Encode debug helper
    // Returns: [color_number, version_x, version_y, module_size]
    public static native int[] debugEncodeInfoPtr(long encPtr);

    // Experimental: adjust Nc detection thresholds (for tests)
    public static native void setNcThresholds(int thsBlack, double thsStd);
    // Experimental: force Nc in decoder (for tests); pass -1 to reset
    public static native void setForceNc(int nc);
    // Experimental: use default palette grid for >=16 colors during decode (for tests)
    public static native void setUseDefaultPaletteHighColor(int flag);
    // Experimental: force ECL (wc, wr) during decode (for tests); pass <=0 to disable
    public static native void setForceEcl(int wc, int wr);
    // Experimental: force mask type during demask (for tests). Use -1 to reset/disable.
    public static native void setForceMask(int mask);
    // Experimental: classifier debug controls and stats (for tests)
    public static native void setClassifierDebug(int enable);
    public static native void setClassifierMode(int mode);
    public static native int[] getClassifierStats(int len);

    // Experimental: pipeline debug (for tests)
    public static native int[] getDecodePipelineDebug(int len);
    public static native int[] getRawModuleSample(int len);
    // Experimental: Part II debug (for tests) -> [count, wc, wr, mask, bits...]
    public static native int[] getPart2Debug(int len);
    // Experimental: palette dumps
    public static native int[] getDecoderPaletteDebug(int len);
    public static native int[] getEncoderDefaultPalette(int colorNumber, int len);

    // Experimental: LDPC input bits before(0)/after(1) deinterleave (bits as ints)
    public static native int[] getLdpcInputDebug(int which, int len);
}
