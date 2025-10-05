package com.jabcode;

import org.bytedeco.javacpp.annotation.*;
import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.tools.*;

/**
 * Presets for JABCode native library
 * This class is used by JavaCPP to generate the native interface
 */
@Properties(
    target = "com.jabcode.internal.JABCodeNative",
    value = {
        @Platform(
            include = {"jabcode.h", "jabcode_c_wrapper.h"}
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
    }
}
