/**
 * JABCode JNI Interface Implementation
 * This file provides the JNI interface for the JABCode library
 */

#include <jni.h>
#include "jabcode_c_wrapper.h"
#include <cstdlib>
#include <cstring>
#include <cstddef>

// JNI method implementations
extern "C" {

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNativePtr_createEncodePtr(JNIEnv *env, jclass cls, jint colorNumber, jint symbolNumber) {
    return (jlong)createEncode_c(colorNumber, symbolNumber);
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_destroyEncodePtr(JNIEnv *env, jclass cls, jlong encPtr) {
    destroyEncode_c((jab_encode*)encPtr);
}

JNIEXPORT jint JNICALL Java_com_jabcode_internal_JABCodeNativePtr_generateJABCodePtr(JNIEnv *env, jclass cls, jlong encPtr, jlong dataPtr) {
    return generateJABCode_c((jab_encode*)encPtr, (jab_data*)dataPtr);
}

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNativePtr_decodeJABCodePtr(JNIEnv *env, jclass cls, jlong bitmapPtr, jint mode, jintArray statusArray) {
    jint status = 0;
    jab_data* result = decodeJABCode_c((jab_bitmap*)bitmapPtr, mode, (jab_int32*)&status);
    
    // Set the status value in the statusArray
    if (statusArray != NULL) {
        jint* statusPtr = env->GetIntArrayElements(statusArray, NULL);
        statusPtr[0] = status;
        env->ReleaseIntArrayElements(statusArray, statusPtr, 0);
    }
    
    return (jlong)result;
}

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNativePtr_decodeJABCodeExPtr(JNIEnv *env, jclass cls, jlong bitmapPtr, jint mode, jintArray statusArray, jlong symbolsPtr, jint maxSymbolNumber) {
    jint status = 0;
    jab_data* result = decodeJABCodeEx_c((jab_bitmap*)bitmapPtr, mode, (jab_int32*)&status, (jab_decoded_symbol*)symbolsPtr, maxSymbolNumber);
    
    // Set the status value in the statusArray
    if (statusArray != NULL) {
        jint* statusPtr = env->GetIntArrayElements(statusArray, NULL);
        statusPtr[0] = status;
        env->ReleaseIntArrayElements(statusArray, statusPtr, 0);
    }
    
    return (jlong)result;
}

JNIEXPORT jboolean JNICALL Java_com_jabcode_internal_JABCodeNativePtr_saveImagePtr(JNIEnv *env, jclass cls, jlong bitmapPtr, jstring filename) {
    const char* filenameChars = env->GetStringUTFChars(filename, NULL);
    jboolean result = saveImage_c((jab_bitmap*)bitmapPtr, (jab_char*)filenameChars);
    env->ReleaseStringUTFChars(filename, filenameChars);
    return result;
}

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNativePtr_readImagePtr(JNIEnv *env, jclass cls, jstring filename) {
    const char* filenameChars = env->GetStringUTFChars(filename, NULL);
    jab_bitmap* result = readImage_c((jab_char*)filenameChars);
    env->ReleaseStringUTFChars(filename, filenameChars);
    return (jlong)result;
}

JNIEXPORT jintArray JNICALL Java_com_jabcode_internal_JABCodeNativePtr_debugDecodeExInfoPtr(JNIEnv *env, jclass, jlong bitmapPtr, jint mode) {
    // Return layout:
    // [status, default_mode, side_version.x, side_version.y, Nc, ecl.x, ecl.y, module_size(int), side_size.x, side_size.y, data_ok]
    jint out[11] = {0};
    if (!bitmapPtr) {
        jintArray arr = env->NewIntArray(11);
        env->SetIntArrayRegion(arr, 0, 11, out);
        return arr;
    }
    jab_decoded_symbol sym[1];
    memset(sym, 0, sizeof(sym));
    jab_int32 status = 0;
    jab_data* data = decodeJABCodeEx_c((jab_bitmap*)bitmapPtr, (jab_int32)mode, &status, sym, 1);

    out[0]  = (jint)status;
    out[1]  = (jint)sym[0].metadata.default_mode;
    out[2]  = (jint)sym[0].metadata.side_version.x;
    out[3]  = (jint)sym[0].metadata.side_version.y;
    out[4]  = (jint)sym[0].metadata.Nc;
    out[5]  = (jint)sym[0].metadata.ecl.x;
    out[6]  = (jint)sym[0].metadata.ecl.y;
    // Round module size to nearest int for compact reporting
    out[7]  = (jint)(sym[0].module_size + (sym[0].module_size >= 0 ? 0.5f : -0.5f));
    out[8]  = (jint)sym[0].side_size.x;
    out[9]  = (jint)sym[0].side_size.y;
    out[10] = (jint)(data != NULL ? 1 : 0);

    // Note: decodeJABCodeEx_c frees internal symbol palette/data as needed; nothing to free here.
    jintArray arr = env->NewIntArray(11);
    env->SetIntArrayRegion(arr, 0, 11, out);
    return arr;
}

} // extern "C"

extern "C" {

JNIEXPORT jintArray JNICALL Java_com_jabcode_internal_JABCodeNativePtr_debugDetectorStatsPtr(JNIEnv* env, jclass, jlong bitmapPtr, jint mode) {
    // Layout: [status, Nc, side_x, side_y, msz, ap0x, ap0y, ap1x, ap1y, ap2x, ap2y, ap3x, ap3y]
    const int OUT_LEN = 13;
    jint out[OUT_LEN];
    for (int i = 0; i < OUT_LEN; ++i) out[i] = 0;
    if (!bitmapPtr) {
        jintArray arr = env->NewIntArray(OUT_LEN);
        if (!arr) return NULL;
        env->SetIntArrayRegion(arr, 0, OUT_LEN, out);
        return arr;
    }
    jab_decoded_symbol sym[1];
    memset(sym, 0, sizeof(sym));
    jab_int32 status = 0;
    jab_data* data = decodeJABCodeEx_c((jab_bitmap*)bitmapPtr, (jab_int32)mode, &status, sym, 1);

    out[0] = (jint)status;
    out[1] = (jint)sym[0].metadata.Nc;
    out[2] = (jint)sym[0].side_size.x;
    out[3] = (jint)sym[0].side_size.y;
    out[4] = (jint)(sym[0].module_size + (sym[0].module_size >= 0 ? 0.5f : -0.5f));
    // pattern positions (rounded)
    for (int i = 0; i < 4; ++i) {
        int base = 5 + i*2;
        out[base + 0] = (jint)(sym[0].pattern_positions[i].x + (sym[0].pattern_positions[i].x >= 0 ? 0.5f : -0.5f));
        out[base + 1] = (jint)(sym[0].pattern_positions[i].y + (sym[0].pattern_positions[i].y >= 0 ? 0.5f : -0.5f));
    }

    jintArray arr = env->NewIntArray(OUT_LEN);
    if (!arr) return NULL;
    env->SetIntArrayRegion(arr, 0, OUT_LEN, out);
    return arr;
}

} // extern "C"


// Note: JNI_OnLoad is provided by JavaCPP's generated loader (jnijavacpp.cpp).
// Do not define it here to avoid duplicate symbol errors during linking.

// Additional pointer-based helpers (C-linkage)
extern "C" {

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNativePtr_createDataFromBytes(JNIEnv* env, jclass, jbyteArray arr) {
    if (!arr) return 0L;
    jsize len = env->GetArrayLength(arr);
    size_t total = offsetof(jab_data, data) + (size_t)len;
    jab_data* d = (jab_data*)calloc(1, total);
    if (!d) return 0L;
    d->length = (jab_int32)len;
    jbyte* bytes = env->GetByteArrayElements(arr, NULL);
    memcpy(d->data, bytes, (size_t)len);
    env->ReleaseByteArrayElements(arr, bytes, JNI_ABORT);
    return (jlong)d;
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_destroyDataPtr(JNIEnv* env, jclass, jlong dataPtr) {
    jab_data* d = (jab_data*)dataPtr;
    if (!d) return;
    free(d);
}

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNativePtr_getBitmapFromEncodePtr(JNIEnv* env, jclass, jlong encPtr) {
    jab_encode* enc = (jab_encode*)encPtr;
    if (!enc) return 0L;
    return (jlong)enc->bitmap;
}

JNIEXPORT jbyteArray JNICALL Java_com_jabcode_internal_JABCodeNativePtr_getDataBytes(JNIEnv* env, jclass, jlong dataPtr) {
    jab_data* d = (jab_data*)dataPtr;
    if (!d || d->length <= 0 || !d->data) return env->NewByteArray(0);
    jbyteArray out = env->NewByteArray((jsize)d->length);
    if (!out) return NULL;
    env->SetByteArrayRegion(out, 0, (jsize)d->length, (const jbyte*)d->data);
    return out;
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_setNcThresholds(JNIEnv* env, jclass, jint thsBlack, jdouble thsStd) {
    (void)env;
    setNcThresholds_c((jab_int32)thsBlack, (jab_double)thsStd);
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_setForceNc(JNIEnv* env, jclass, jint nc) {
    (void)env;
    setForceNc_c((jab_int32)nc);
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_setUseDefaultPaletteHighColor(JNIEnv* env, jclass, jint flag) {
    (void)env;
    setUseDefaultPaletteHighColor_c((jab_int32)flag);
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_setForceEcl(JNIEnv* env, jclass, jint wc, jint wr) {
    (void)env;
    setForceEcl_c((jab_int32)wc, (jab_int32)wr);
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_setForceMask(JNIEnv* env, jclass, jint mask) {
    (void)env;
    setForceMask_c((jab_int32)mask);
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_setClassifierDebug(JNIEnv* env, jclass, jint enable) {
    (void)env;
    setClassifierDebug_c((jab_int32)enable);
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_setClassifierMode(JNIEnv* env, jclass, jint mode) {
    (void)env;
    setClassifierMode_c((jab_int32)mode);
}

JNIEXPORT jintArray JNICALL Java_com_jabcode_internal_JABCodeNativePtr_getClassifierStats(JNIEnv* env, jclass, jint len) {
    jab_int32* out = (jab_int32*)malloc(len * sizeof(jab_int32));
    if (!out) return NULL;
    memset(out, 0, len * sizeof(jab_int32));
    getClassifierStats_c(out, (jab_int32)len);
    jintArray result = env->NewIntArray(len);
    if (result) {
        env->SetIntArrayRegion(result, 0, len, out);
    }
    free(out);
    return result;
}

JNIEXPORT jintArray JNICALL Java_com_jabcode_internal_JABCodeNativePtr_getLdpcInputDebug(JNIEnv* env, jclass, jint which, jint len) {
    jab_int32* out = (jab_int32*)malloc(len * sizeof(jab_int32));
    if (!out) return NULL;
    memset(out, 0, len * sizeof(jab_int32));
    getLdpcInputDebug_c(out, (jab_int32)len, (jab_int32)which);
    jintArray result = env->NewIntArray(len);
    if (result) {
        env->SetIntArrayRegion(result, 0, len, out);
    }
    free(out);
    return result;
}

JNIEXPORT jintArray JNICALL Java_com_jabcode_internal_JABCodeNativePtr_getDecodePipelineDebug(JNIEnv* env, jclass, jint len) {
    jab_int32* out = (jab_int32*)malloc(len * sizeof(jab_int32));
    if (!out) return NULL;
    memset(out, 0, len * sizeof(jab_int32));
    getDecodePipelineDebug_c(out, (jab_int32)len);
    jintArray result = env->NewIntArray(len);
    if (result) {
        env->SetIntArrayRegion(result, 0, len, out);
    }
    free(out);
    return result;
}

JNIEXPORT jintArray JNICALL Java_com_jabcode_internal_JABCodeNativePtr_getRawModuleSample(JNIEnv* env, jclass, jint len) {
    jab_int32* out = (jab_int32*)malloc(len * sizeof(jab_int32));
    if (!out) return NULL;
    memset(out, 0, len * sizeof(jab_int32));
    getRawModuleSample_c(out, (jab_int32)len);
    jintArray result = env->NewIntArray(len);
    if (result) {
        env->SetIntArrayRegion(result, 0, len, out);
    }
    free(out);
    return result;
}

JNIEXPORT jintArray JNICALL Java_com_jabcode_internal_JABCodeNativePtr_getPart2Debug(JNIEnv* env, jclass, jint len) {
    jab_int32* out = (jab_int32*)malloc(len * sizeof(jab_int32));
    if (!out) return NULL;
    memset(out, 0, len * sizeof(jab_int32));
    getPart2Debug_c(out, (jab_int32)len);
    jintArray result = env->NewIntArray(len);
    if (result) {
        env->SetIntArrayRegion(result, 0, len, out);
    }
    free(out);
    return result;
}

JNIEXPORT jintArray JNICALL Java_com_jabcode_internal_JABCodeNativePtr_getDecoderPaletteDebug(JNIEnv* env, jclass, jint len) {
    jab_int32* out = (jab_int32*)malloc(len * sizeof(jab_int32));
    if (!out) return NULL;
    memset(out, 0, len * sizeof(jab_int32));
    getDecoderPaletteDebug_c(out, (jab_int32)len);
    jintArray result = env->NewIntArray(len);
    if (result) {
        env->SetIntArrayRegion(result, 0, len, out);
    }
    free(out);
    return result;
}

JNIEXPORT jintArray JNICALL Java_com_jabcode_internal_JABCodeNativePtr_getEncoderDefaultPalette(JNIEnv* env, jclass, jint colorNumber, jint len) {
    jab_int32* out = (jab_int32*)malloc(len * sizeof(jab_int32));
    if (!out) return NULL;
    memset(out, 0, len * sizeof(jab_int32));
    getEncoderDefaultPalette_c((jab_int32)colorNumber, out, (jab_int32)len);
    jintArray result = env->NewIntArray(len);
    if (result) {
        env->SetIntArrayRegion(result, 0, len, out);
    }
    free(out);
    return result;
}

// Simple setters for primary encode parameters
JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_setModuleSizePtr(JNIEnv* env, jclass, jlong encPtr, jint value) {
    (void)env;
    jab_encode* enc = (jab_encode*)encPtr;
    if (!enc) return;
    enc->module_size = (jab_int32)value;
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_setMasterSymbolWidthPtr(JNIEnv* env, jclass, jlong encPtr, jint value) {
    (void)env;
    jab_encode* enc = (jab_encode*)encPtr;
    if (!enc) return;
    enc->master_symbol_width = (jab_int32)value;
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_setMasterSymbolHeightPtr(JNIEnv* env, jclass, jlong encPtr, jint value) {
    (void)env;
    jab_encode* enc = (jab_encode*)encPtr;
    if (!enc) return;
    enc->master_symbol_height = (jab_int32)value;
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_setSymbolVersionPtr(JNIEnv* env, jclass, jlong encPtr, jint index, jint vx, jint vy) {
    (void)env;
    jab_encode* enc = (jab_encode*)encPtr;
    if (!enc) return;
    if (index < 0 || index >= enc->symbol_number) return;
    enc->symbol_versions[index].x = (jab_int32)vx;
    enc->symbol_versions[index].y = (jab_int32)vy;
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_setSymbolPositionPtr(JNIEnv* env, jclass, jlong encPtr, jint index, jint pos) {
    (void)env;
    jab_encode* enc = (jab_encode*)encPtr;
    if (!enc) return;
    if (index < 0 || index >= enc->symbol_number) return;
    enc->symbol_positions[index] = (jab_int32)pos;
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_setSymbolEccLevelPtr(JNIEnv* env, jclass, jlong encPtr, jint index, jint level) {
    (void)env;
    jab_encode* enc = (jab_encode*)encPtr;
    if (!enc || !enc->symbol_ecc_levels) return;
    if (index < 0 || index >= enc->symbol_number) return;
    if (level < 0) level = 0;
    if (level > 10) level = 10;
    enc->symbol_ecc_levels[index] = (jab_byte)level;
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_setAllEccLevelsPtr(JNIEnv* env, jclass, jlong encPtr, jint level) {
    (void)env;
    jab_encode* enc = (jab_encode*)encPtr;
    if (!enc || !enc->symbol_ecc_levels) return;
    if (level < 0) level = 0;
    if (level > 10) level = 10;
    for (jab_int32 i = 0; i < enc->symbol_number; ++i) {
        enc->symbol_ecc_levels[i] = (jab_byte)level;
    }
}

JNIEXPORT jintArray JNICALL Java_com_jabcode_internal_JABCodeNativePtr_debugEncodeInfoPtr(JNIEnv* env, jclass, jlong encPtr) {
    jint out[4] = {0};
    jab_encode* enc = (jab_encode*)encPtr;
    if (enc) {
        out[0] = (jint)enc->color_number;
        out[1] = (jint)enc->symbol_versions[0].x;
        out[2] = (jint)enc->symbol_versions[0].y;
        out[3] = (jint)enc->module_size;
    }
    jintArray arr = env->NewIntArray(4);
    if (!arr) return NULL;
    env->SetIntArrayRegion(arr, 0, 4, out);
    return arr;
}

} // extern "C"

