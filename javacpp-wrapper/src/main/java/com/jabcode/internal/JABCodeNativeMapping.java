package com.jabcode.internal;

import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.annotation.*;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;

/**
 * JABCodeNativeMapping - Maps the native methods to the C wrapper functions
 * This class provides the mapping between the Java native methods and the C wrapper functions
 */
@Properties(target = "com.jabcode.internal.JABCodeNative", global = "com.jabcode.JABCodePresets")
public class JABCodeNativeMapping {
    static { Loader.load(); }
    
    // Import the types from JABCodeNative
    public static class jab_encode extends JABCodeNative.jab_encode {}
    public static class jab_data extends JABCodeNative.jab_data {}
    public static class jab_bitmap extends JABCodeNative.jab_bitmap {}
    public static class jab_decoded_symbol extends JABCodeNative.jab_decoded_symbol {}
    public static class jab_metadata extends JABCodeNative.jab_metadata {}
    public static class jab_vector2d extends JABCodeNative.jab_vector2d {}

    // Map the native methods to the C wrapper functions
    @Name("createEncode_c") public static native jab_encode createEncode(@Cast("jab_int32") int color_number, @Cast("jab_int32") int symbol_number);
    @Name("destroyEncode_c") public static native void destroyEncode(jab_encode enc);
    @Name("generateJABCode_c") public static native @Cast("jab_int32") int generateJABCode(jab_encode enc, jab_data data);
    @Name("decodeJABCode_c") public static native jab_data decodeJABCode(jab_bitmap bitmap, @Cast("jab_int32") int mode, @Cast("jab_int32*") IntPointer status);
    @Name("decodeJABCode_c") public static native jab_data decodeJABCode(jab_bitmap bitmap, @Cast("jab_int32") int mode, @Cast("jab_int32*") IntBuffer status);
    @Name("decodeJABCode_c") public static native jab_data decodeJABCode(jab_bitmap bitmap, @Cast("jab_int32") int mode, @Cast("jab_int32*") int[] status);
    @Name("decodeJABCodeEx_c") public static native jab_data decodeJABCodeEx(jab_bitmap bitmap, @Cast("jab_int32") int mode, @Cast("jab_int32*") IntPointer status, jab_decoded_symbol symbols, @Cast("jab_int32") int max_symbol_number);
    @Name("decodeJABCodeEx_c") public static native jab_data decodeJABCodeEx(jab_bitmap bitmap, @Cast("jab_int32") int mode, @Cast("jab_int32*") IntBuffer status, jab_decoded_symbol symbols, @Cast("jab_int32") int max_symbol_number);
    @Name("decodeJABCodeEx_c") public static native jab_data decodeJABCodeEx(jab_bitmap bitmap, @Cast("jab_int32") int mode, @Cast("jab_int32*") int[] status, jab_decoded_symbol symbols, @Cast("jab_int32") int max_symbol_number);
    @Name("saveImage_c") public static native @Cast("jab_boolean") byte saveImage(jab_bitmap bitmap, @Cast("jab_char*") BytePointer filename);
    @Name("saveImage_c") public static native @Cast("jab_boolean") byte saveImage(jab_bitmap bitmap, @Cast("jab_char*") ByteBuffer filename);
    @Name("saveImage_c") public static native @Cast("jab_boolean") byte saveImage(jab_bitmap bitmap, @Cast("jab_char*") byte[] filename);
    @Name("readImage_c") public static native jab_bitmap readImage(@Cast("jab_char*") BytePointer filename);
    @Name("readImage_c") public static native jab_bitmap readImage(@Cast("jab_char*") ByteBuffer filename);
    @Name("readImage_c") public static native jab_bitmap readImage(@Cast("jab_char*") byte[] filename);
}
