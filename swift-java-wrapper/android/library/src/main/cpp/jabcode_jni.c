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
    jobject bitmap,
    jlong timeout
) {
    // Get Bitmap class and methods
    jclass bitmapClass = (*env)->GetObjectClass(env, bitmap);
    jmethodID getWidthMethod = (*env)->GetMethodID(env, bitmapClass, "getWidth", "()I");
    jmethodID getHeightMethod = (*env)->GetMethodID(env, bitmapClass, "getHeight", "()I");
    jmethodID getPixelsMethod = (*env)->GetMethodID(env, bitmapClass, "getPixels", "([IIIIIII)V");
    
    jint width = (*env)->CallIntMethod(env, bitmap, getWidthMethod);
    jint height = (*env)->CallIntMethod(env, bitmap, getHeightMethod);
    
    // Get pixel data
    jintArray pixelArray = (*env)->NewIntArray(env, width * height);
    (*env)->CallVoidMethod(env, bitmap, getPixelsMethod, pixelArray, 0, width, 0, 0, width, height);
    
    jint* pixels = (*env)->GetIntArrayElements(env, pixelArray, NULL);
    if (!pixels) {
        return NULL;
    }
    
    // Convert ARGB to RGBA
    jab_byte* rgbaBuffer = (jab_byte*)malloc(width * height * 4);
    if (!rgbaBuffer) {
        (*env)->ReleaseIntArrayElements(env, pixelArray, pixels, JNI_ABORT);
        return NULL;
    }
    
    for (int i = 0; i < width * height; i++) {
        jint pixel = pixels[i];
        rgbaBuffer[i * 4 + 0] = (pixel >> 16) & 0xFF; // R
        rgbaBuffer[i * 4 + 1] = (pixel >> 8) & 0xFF;  // G
        rgbaBuffer[i * 4 + 2] = pixel & 0xFF;          // B
        rgbaBuffer[i * 4 + 3] = (pixel >> 24) & 0xFF; // A
    }
    
    (*env)->ReleaseIntArrayElements(env, pixelArray, pixels, JNI_ABORT);
    
    // Decode
    jab_data* decoded = jabMobileDecodeFromBitmap(rgbaBuffer, width, height);
    
    free(rgbaBuffer);
    
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
