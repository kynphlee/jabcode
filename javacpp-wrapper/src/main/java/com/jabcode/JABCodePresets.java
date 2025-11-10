package com.jabcode;

import org.bytedeco.javacpp.annotation.*;
import org.bytedeco.javacpp.tools.*;

/**
 * Presets for JABCode native library
 * This class is used by JavaCPP to generate the native interface
 */
@Properties(
    target = "com.jabcode.internal.JABCodeNative",
    value = {
        @Platform(
            include = {"jabcode.h", "jabcode_c_wrapper.h"},
            resource = {"com/jabcode/jabcode_c_wrapper.c", "com/jabcode/JABCodeNative_jni.cpp"},
            // Order reversed by JavaCPP at link time; specify in reverse to obtain:
            // final linker order: -ljabcode -lpng16 -lz (so --as-needed retains png/z)
            link = {"z", "png16", "jabcode"}
        )
    }
)
public class JABCodePresets implements InfoMapper {
    public void map(InfoMap infoMap) {
        // Avoid generating problematic macro constants
        infoMap.put(new Info("VERSION", "BUILD_DATE").skip());

        // Map functions to use C wrapper functions instead of C++ functions directly
        infoMap.put(new Info("createEncode").javaNames("createEncode").cppNames("createEncode_c"));
        infoMap.put(new Info("destroyEncode").javaNames("destroyEncode").cppNames("destroyEncode_c"));
        infoMap.put(new Info("generateJABCode").javaNames("generateJABCode").cppNames("generateJABCode_c"));
        infoMap.put(new Info("decodeJABCode").javaNames("decodeJABCode").cppNames("decodeJABCode_c"));
        infoMap.put(new Info("decodeJABCodeEx").javaNames("decodeJABCodeEx").cppNames("decodeJABCodeEx_c"));
        infoMap.put(new Info("saveImage").javaNames("saveImage").cppNames("saveImage_c"));
        infoMap.put(new Info("readImage").javaNames("readImage").cppNames("readImage_c"));

        // Our custom pointer-based JNI functions are implemented manually in
        // src/main/c/JABCodeNative_jni.cpp. Instruct JavaCPP to skip generating
        // wrappers for these names to avoid unresolved symbol calls like
        // createEncodePtr() in jniJABCodeNative.cpp.
        infoMap.put(new Info(
            "createEncodePtr",
            "destroyEncodePtr",
            "generateJABCodePtr",
            "decodeJABCodePtr",
            "decodeJABCodeExPtr",
            "saveImagePtr",
            "readImagePtr",
            "createDataFromBytes",
            "destroyDataPtr",
            "getBitmapFromEncodePtr",
            "getDataBytes",
            "setModuleSizePtr",
            "setMasterSymbolWidthPtr",
            "setMasterSymbolHeightPtr",
            "setSymbolVersionPtr",
            "setSymbolPositionPtr",
            "debugDecodeExInfoPtr"
        ).skip());
    }
}
