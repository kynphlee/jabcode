/**
 * JABCode JNI Bridge - Android Native Interface
 */

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "mobile_bridge.h"

JNIEXPORT jobject JNICALL
Java_com_jabcode_JABCodeMobile_nativeEncode(
    JNIEnv* env,
    jclass clazz,
    jbyteArray data,
    jint length,
    jint colorNumber,
    jint symbolNumber,
    jint eccLevel,
    jint moduleSize
) {
    jab_mobile_encode_params params = {
        .color_number = colorNumber,
        .symbol_number = symbolNumber,
        .ecc_level = eccLevel,
        .module_size = moduleSize
    };
    
    jbyte* dataBytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (!dataBytes) {
        return NULL;
    }
    
    jab_mobile_encode_result* result = jabMobileEncode(
        (jab_char*)dataBytes,
        length,
        &params
    );
    
    (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
    
    if (!result) {
        return NULL;
    }
    
    jclass resultClass = (*env)->FindClass(env, "com/jabcode/JABCodeMobile$EncodeResult");
    jmethodID constructor = (*env)->GetMethodID(env, resultClass, "<init>", "(JII[B)V");
    
    jbyteArray rgbaArray = (*env)->NewByteArray(env, result->width * result->height * 4);
    (*env)->SetByteArrayRegion(env, rgbaArray, 0, result->width * result->height * 4,
        (jbyte*)result->rgba_buffer);
    
    jobject javaResult = (*env)->NewObject(env, resultClass, constructor,
        (jlong)result, result->width, result->height, rgbaArray);
    
    return javaResult;
}

JNIEXPORT jbyteArray JNICALL
Java_com_jabcode_JABCodeMobile_nativeDecode(
    JNIEnv* env,
    jclass clazz,
    jlong encodeResultPtr,
    jint colorNumber,
    jint eccLevel
) {
    jab_mobile_encode_result* encodeResult = (jab_mobile_encode_result*)encodeResultPtr;
    if (!encodeResult) {
        return NULL;
    }
    
    jab_data* decoded = jabMobileDecode(encodeResult, colorNumber, eccLevel);
    if (!decoded) {
        return NULL;
    }
    
    jbyteArray result = (*env)->NewByteArray(env, decoded->length);
    (*env)->SetByteArrayRegion(env, result, 0, decoded->length, (jbyte*)decoded->data);
    
    jabMobileDataFree(decoded);
    
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_jabcode_JABCodeMobile_nativeDecodeFromBitmap(
    JNIEnv* env,
    jclass clazz,
    jbyteArray rgbaBuffer,
    jint width,
    jint height
) {
    jbyte* buffer = (*env)->GetByteArrayElements(env, rgbaBuffer, NULL);
    if (!buffer) {
        return NULL;
    }
    
    jab_data* decoded = jabMobileDecodeFromBitmap((jab_byte*)buffer, width, height);
    
    (*env)->ReleaseByteArrayElements(env, rgbaBuffer, buffer, JNI_ABORT);
    
    if (!decoded) {
        return NULL;
    }
    
    jbyteArray result = (*env)->NewByteArray(env, decoded->length);
    (*env)->SetByteArrayRegion(env, result, 0, decoded->length, (jbyte*)decoded->data);
    
    jabMobileDataFree(decoded);
    
    return result;
}

JNIEXPORT void JNICALL
Java_com_jabcode_JABCodeMobile_nativeFreeEncodeResult(
    JNIEnv* env,
    jclass clazz,
    jlong ptr
) {
    jab_mobile_encode_result* result = (jab_mobile_encode_result*)ptr;
    jabMobileEncodeResultFree(result);
}

JNIEXPORT jstring JNICALL
Java_com_jabcode_JABCodeMobile_nativeGetLastError(
    JNIEnv* env,
    jclass clazz
) {
    const char* error = jabMobileGetLastError();
    if (!error) {
        return NULL;
    }
    return (*env)->NewStringUTF(env, error);
}

JNIEXPORT void JNICALL
Java_com_jabcode_JABCodeMobile_nativeClearError(
    JNIEnv* env,
    jclass clazz
) {
    jabMobileClearError();
}

JNIEXPORT jstring JNICALL
Java_com_jabcode_JABCodeMobile_nativeGetVersion(
    JNIEnv* env,
    jclass clazz
) {
    return (*env)->NewStringUTF(env, jabMobileGetVersion());
}

JNIEXPORT jboolean JNICALL
Java_com_jabcode_JABCodeMobile_nativeLoadCalibration(
    JNIEnv* env,
    jclass clazz,
    jstring jsonProfile
) {
    const char* jsonStr = (*env)->GetStringUTFChars(env, jsonProfile, NULL);
    if (!jsonStr) {
        return JNI_FALSE;
    }
    
    jab_int32 result = jabMobileLoadCalibration(jsonStr);
    
    (*env)->ReleaseStringUTFChars(env, jsonProfile, jsonStr);
    
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_jabcode_JABCodeMobile_nativeClearCalibration(
    JNIEnv* env,
    jclass clazz
) {
    jabMobileClearCalibration();
}

JNIEXPORT jboolean JNICALL
Java_com_jabcode_JABCodeMobile_nativeHasCalibration(
    JNIEnv* env,
    jclass clazz
) {
    return jabMobileHasCalibration() ? JNI_TRUE : JNI_FALSE;
}
