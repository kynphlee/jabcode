/**
 * JABCode JNI Interface Implementation
 * This file provides the JNI interface for the JABCode library
 */

#include <jni.h>
#include "jabcode.h"
#include "jabcode_c_wrapper.h"

// JNI method implementations
extern "C" {

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNative_createEncode(JNIEnv *env, jclass cls, jint colorNumber, jint symbolNumber) {
    return (jlong)createEncode_c(colorNumber, symbolNumber);
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNative_destroyEncode(JNIEnv *env, jclass cls, jlong encPtr) {
    destroyEncode_c((jab_encode*)encPtr);
}

JNIEXPORT jint JNICALL Java_com_jabcode_internal_JABCodeNative_generateJABCode(JNIEnv *env, jclass cls, jlong encPtr, jlong dataPtr) {
    return generateJABCode_c((jab_encode*)encPtr, (jab_data*)dataPtr);
}

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNative_decodeJABCode(JNIEnv *env, jclass cls, jlong bitmapPtr, jint mode, jintArray statusArray) {
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

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNative_decodeJABCodeEx(JNIEnv *env, jclass cls, jlong bitmapPtr, jint mode, jintArray statusArray, jlong symbolsPtr, jint maxSymbolNumber) {
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

JNIEXPORT jboolean JNICALL Java_com_jabcode_internal_JABCodeNative_saveImage(JNIEnv *env, jclass cls, jlong bitmapPtr, jstring filename) {
    const char* filenameChars = env->GetStringUTFChars(filename, NULL);
    jboolean result = saveImage_c((jab_bitmap*)bitmapPtr, (jab_char*)filenameChars);
    env->ReleaseStringUTFChars(filename, filenameChars);
    return result;
}

JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNative_readImage(JNIEnv *env, jclass cls, jstring filename) {
    const char* filenameChars = env->GetStringUTFChars(filename, NULL);
    jab_bitmap* result = readImage_c((jab_char*)filenameChars);
    env->ReleaseStringUTFChars(filename, filenameChars);
    return (jlong)result;
}

} // extern "C"
