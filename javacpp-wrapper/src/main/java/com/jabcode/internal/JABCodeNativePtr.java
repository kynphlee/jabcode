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

    // Encoder tuning
    public static native void setModuleSizePtr(long encPtr, int value);
    public static native void setMasterSymbolWidthPtr(long encPtr, int value);
    public static native void setMasterSymbolHeightPtr(long encPtr, int value);
    public static native void setSymbolVersionPtr(long encPtr, int index, int vx, int vy);
    public static native void setSymbolPositionPtr(long encPtr, int index, int pos);

    // Debug helper
    // Returns: [status, default_mode, side_version.x, side_version.y, Nc, ecl.x, ecl.y, module_size(int), side_size.x, side_size.y, data_ok]
    public static native int[] debugDecodeExInfoPtr(long bitmapPtr, int mode);
}
