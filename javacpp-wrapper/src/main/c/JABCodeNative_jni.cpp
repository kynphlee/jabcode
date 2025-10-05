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

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNative_createEncodePtr(JNIEnv *env, jclass cls, jint colorNumber, jint symbolNumber) {
    return (jlong)createEncode_c(colorNumber, symbolNumber);
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNative_destroyEncodePtr(JNIEnv *env, jclass cls, jlong encPtr) {
    destroyEncode_c((jab_encode*)encPtr);
}

JNIEXPORT jint JNICALL Java_com_jabcode_internal_JABCodeNative_generateJABCodePtr(JNIEnv *env, jclass cls, jlong encPtr, jlong dataPtr) {
    return generateJABCode_c((jab_encode*)encPtr, (jab_data*)dataPtr);
}

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNative_decodeJABCodePtr(JNIEnv *env, jclass cls, jlong bitmapPtr, jint mode, jintArray statusArray) {
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

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNative_decodeJABCodeExPtr(JNIEnv *env, jclass cls, jlong bitmapPtr, jint mode, jintArray statusArray, jlong symbolsPtr, jint maxSymbolNumber) {
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

JNIEXPORT jboolean JNICALL Java_com_jabcode_internal_JABCodeNative_saveImagePtr(JNIEnv *env, jclass cls, jlong bitmapPtr, jstring filename) {
    const char* filenameChars = env->GetStringUTFChars(filename, NULL);
    jboolean result = saveImage_c((jab_bitmap*)bitmapPtr, (jab_char*)filenameChars);
    env->ReleaseStringUTFChars(filename, filenameChars);
    return result;
}

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNative_readImagePtr(JNIEnv *env, jclass cls, jstring filename) {
    const char* filenameChars = env->GetStringUTFChars(filename, NULL);
    jab_bitmap* result = readImage_c((jab_char*)filenameChars);
    env->ReleaseStringUTFChars(filename, filenameChars);
    return (jlong)result;
}

} // extern "C"


// Export a minimal JNI_OnLoad to satisfy JVM expectations and set JNI version.
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK || env == nullptr) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}

// Additional pointer-based helpers (C-linkage)
extern "C" {

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNative_createDataFromBytes(JNIEnv* env, jclass, jbyteArray arr) {
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

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNative_destroyDataPtr(JNIEnv* env, jclass, jlong dataPtr) {
    jab_data* d = (jab_data*)dataPtr;
    if (!d) return;
    free(d);
}

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNative_getBitmapFromEncodePtr(JNIEnv* env, jclass, jlong encPtr) {
    jab_encode* enc = (jab_encode*)encPtr;
    if (!enc) return 0L;
    return (jlong)enc->bitmap;
}

JNIEXPORT jbyteArray JNICALL Java_com_jabcode_internal_JABCodeNative_getDataBytes(JNIEnv* env, jclass, jlong dataPtr) {
    jab_data* d = (jab_data*)dataPtr;
    if (!d || d->length <= 0 || !d->data) return env->NewByteArray(0);
    jbyteArray out = env->NewByteArray((jsize)d->length);
    if (!out) return NULL;
    env->SetByteArrayRegion(out, 0, (jsize)d->length, (const jbyte*)d->data);
    return out;
}

// Simple setters for primary encode parameters
JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNative_setModuleSizePtr(JNIEnv* env, jclass, jlong encPtr, jint value) {
    (void)env;
    jab_encode* enc = (jab_encode*)encPtr;
    if (!enc) return;
    enc->module_size = (jab_int32)value;
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNative_setMasterSymbolWidthPtr(JNIEnv* env, jclass, jlong encPtr, jint value) {
    (void)env;
    jab_encode* enc = (jab_encode*)encPtr;
    if (!enc) return;
    enc->master_symbol_width = (jab_int32)value;
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNative_setMasterSymbolHeightPtr(JNIEnv* env, jclass, jlong encPtr, jint value) {
    (void)env;
    jab_encode* enc = (jab_encode*)encPtr;
    if (!enc) return;
    enc->master_symbol_height = (jab_int32)value;
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNative_setSymbolVersionPtr(JNIEnv* env, jclass, jlong encPtr, jint index, jint vx, jint vy) {
    (void)env;
    jab_encode* enc = (jab_encode*)encPtr;
    if (!enc) return;
    if (index < 0 || index >= enc->symbol_number) return;
    enc->symbol_versions[index].x = (jab_int32)vx;
    enc->symbol_versions[index].y = (jab_int32)vy;
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNative_setSymbolPositionPtr(JNIEnv* env, jclass, jlong encPtr, jint index, jint pos) {
    (void)env;
    jab_encode* enc = (jab_encode*)encPtr;
    if (!enc) return;
    if (index < 0 || index >= enc->symbol_number) return;
    enc->symbol_positions[index] = (jab_int32)pos;
}

} // extern "C"

