/**
 * JABCode JNI Bridge - Android Native Interface
 * 
 * Bridges Java/Kotlin API to the JABCode mobile C library.
 */

#include <jni.h>
#include <string.h>
#include <android/log.h>
#include <android/bitmap.h>
#include "mobile_bridge.h"

#define LOG_TAG "JABCodeJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * Native encode implementation
 * 
 * Java signature: private static native EncodeResult nativeEncode(byte[] data, int length,
 *     int colorNumber, int symbolNumber, int eccLevel, int moduleSize);
 */
JNIEXPORT jobject JNICALL
Java_com_jabcode_JABCodeMobile_nativeEncode(
    JNIEnv *env,
    jclass clazz,
    jbyteArray data,
    jint length,
    jint colorNumber,
    jint symbolNumber,
    jint eccLevel,
    jint moduleSize)
{
    // Get data bytes
    jbyte *dataBytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (dataBytes == NULL) {
        LOGE("Failed to get data bytes");
        return NULL;
    }
    
    // Setup encode params
    jab_mobile_encode_params params = {
        .color_number = colorNumber,
        .symbol_number = symbolNumber,
        .ecc_level = eccLevel,
        .module_size = moduleSize
    };
    
    // Encode
    jab_mobile_encode_result *result = jabMobileEncode((jab_char *)dataBytes, length, &params);
    
    // Release data bytes
    (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
    
    if (result == NULL) {
        const char *error = jabMobileGetLastError();
        LOGE("Encode failed: %s", error ? error : "unknown error");
        return NULL;
    }
    
    LOGI("Encoded %dx%d bitmap", result->width, result->height);
    
    // Create RGBA byte array
    jint bufferSize = result->width * result->height * 4;
    jbyteArray rgbaArray = (*env)->NewByteArray(env, bufferSize);
    if (rgbaArray == NULL) {
        LOGE("Failed to allocate RGBA array");
        jabMobileEncodeResultFree(result);
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, rgbaArray, 0, bufferSize, (jbyte *)result->rgba_buffer);
    
    // Find EncodeResult class and constructor
    jclass encodeResultClass = (*env)->FindClass(env, "com/jabcode/JABCodeMobile$EncodeResult");
    if (encodeResultClass == NULL) {
        LOGE("Failed to find EncodeResult class");
        jabMobileEncodeResultFree(result);
        return NULL;
    }
    
    // Get constructor: private EncodeResult(long nativePtr, int width, int height, byte[] rgbaBuffer)
    jmethodID constructor = (*env)->GetMethodID(env, encodeResultClass, "<init>", "(JII[B)V");
    if (constructor == NULL) {
        LOGE("Failed to find EncodeResult constructor");
        jabMobileEncodeResultFree(result);
        return NULL;
    }
    
    // Create EncodeResult object - store native pointer for later decode
    jobject encodeResultObj = (*env)->NewObject(env, encodeResultClass, constructor,
        (jlong)(uintptr_t)result,
        result->width,
        result->height,
        rgbaArray);
    
    if (encodeResultObj == NULL) {
        LOGE("Failed to create EncodeResult object");
        jabMobileEncodeResultFree(result);
        return NULL;
    }
    
    // Note: Don't free result here - it's stored in the Java object and freed via nativeFreeEncodeResult
    return encodeResultObj;
}

/**
 * Native decode implementation
 * 
 * Java signature: private static native byte[] nativeDecode(long encodeResultPtr, int colorNumber, int eccLevel);
 */
JNIEXPORT jbyteArray JNICALL
Java_com_jabcode_JABCodeMobile_nativeDecode(
    JNIEnv *env,
    jclass clazz,
    jlong encodeResultPtr,
    jint colorNumber,
    jint eccLevel)
{
    jab_mobile_encode_result *encodeResult = (jab_mobile_encode_result *)(uintptr_t)encodeResultPtr;
    if (encodeResult == NULL) {
        LOGE("Null encode result pointer");
        return NULL;
    }
    
    // Decode using the mobile bridge
    jab_data *decoded = jabMobileDecode(encodeResult, colorNumber, eccLevel);
    if (decoded == NULL) {
        const char *error = jabMobileGetLastError();
        LOGE("Decode failed: %s", error ? error : "unknown error");
        return NULL;
    }
    
    LOGI("Decoded %d bytes", decoded->length);
    
    // Create result byte array
    jbyteArray resultArray = (*env)->NewByteArray(env, decoded->length);
    if (resultArray == NULL) {
        LOGE("Failed to allocate result array");
        jabMobileDataFree(decoded);
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, resultArray, 0, decoded->length, (jbyte *)decoded->data);
    
    jabMobileDataFree(decoded);
    return resultArray;
}

/**
 * Decode from bitmap implementation
 * 
 * Java signature: external fun nativeDecodeFromBitmap(bitmap: Bitmap, timeout: Long): ByteArray?
 */
JNIEXPORT jbyteArray JNICALL
Java_com_jabcode_JABCodeMobile_nativeDecodeFromBitmap(
    JNIEnv *env,
    jobject thiz,
    jobject bitmap,
    jlong timeout)
{
    AndroidBitmapInfo info;
    void* pixels;
    int ret;
    
    // Get bitmap info
    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("AndroidBitmap_getInfo failed, error=%d", ret);
        return NULL;
    }
    
    // Verify format is RGBA_8888
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888, format=%d", info.format);
        return NULL;
    }
    
    // Lock pixels for reading
    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        LOGE("AndroidBitmap_lockPixels failed, error=%d", ret);
        return NULL;
    }
    
    // Decode using camera pipeline (full detection)
    jab_data *decoded = jabMobileDecodeCamera((jab_byte *)pixels, info.width, info.height);
    
    // Unlock pixels
    AndroidBitmap_unlockPixels(env, bitmap);
    
    if (decoded == NULL) {
        const char *error = jabMobileGetLastError();
        LOGE("Camera decode failed: %s", error ? error : "unknown error");
        return NULL;
    }
    
    LOGI("Decoded %d bytes from %dx%d bitmap", decoded->length, info.width, info.height);
    
    // Create result byte array
    jbyteArray resultArray = (*env)->NewByteArray(env, decoded->length);
    if (resultArray == NULL) {
        LOGE("Failed to allocate result array");
        jabMobileDataFree(decoded);
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, resultArray, 0, decoded->length, (jbyte *)decoded->data);
    
    jabMobileDataFree(decoded);
    return resultArray;
}

/**
 * Decode from bitmap implementation WITH color-mode metadata.
 *
 * Same logic as nativeDecodeFromBitmap, but also writes the detected color
 * count (1 << (Nc+1)) into element [0] of the outColorNumber IntArray.
 *
 * Java signature: external fun nativeDecodeFromBitmapWithMeta(
 *     bitmap: Bitmap, timeout: Long, outColorNumber: IntArray
 * ): ByteArray?
 */
JNIEXPORT jbyteArray JNICALL
Java_com_jabcode_JABCodeMobile_nativeDecodeFromBitmapWithMeta(
    JNIEnv *env,
    jobject thiz,
    jobject bitmap,
    jlong timeout,
    jintArray outColorNumber)
{
    AndroidBitmapInfo info;
    void* pixels;
    int ret;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("AndroidBitmap_getInfo failed, error=%d", ret);
        return NULL;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888, format=%d", info.format);
        return NULL;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        LOGE("AndroidBitmap_lockPixels failed, error=%d", ret);
        return NULL;
    }

    // Decode via the meta-aware native function. The local color_number is
    // populated on success; failure leaves it at the initial 0.
    jab_int32 color_number = 0;
    jab_data *decoded = jabMobileDecodeCameraWithMeta(
        (jab_byte *)pixels, info.width, info.height, &color_number);

    AndroidBitmap_unlockPixels(env, bitmap);

    if (decoded == NULL) {
        const char *error = jabMobileGetLastError();
        LOGE("Camera decode (with meta) failed: %s", error ? error : "unknown error");
        return NULL;
    }

    LOGI("Decoded %d bytes from %dx%d bitmap, color_number=%d",
         decoded->length, info.width, info.height, color_number);

    // Write color_number into element [0] of outColorNumber if caller provided it.
    if (outColorNumber != NULL) {
        jsize outLen = (*env)->GetArrayLength(env, outColorNumber);
        if (outLen >= 1) {
            jint colorNumberJ = (jint)color_number;
            (*env)->SetIntArrayRegion(env, outColorNumber, 0, 1, &colorNumberJ);
        } else {
            LOGE("outColorNumber array too small (len=%d, need >=1)", outLen);
        }
    }

    // Create result byte array
    jbyteArray resultArray = (*env)->NewByteArray(env, decoded->length);
    if (resultArray == NULL) {
        LOGE("Failed to allocate result array");
        jabMobileDataFree(decoded);
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, resultArray, 0, decoded->length, (jbyte *)decoded->data);

    jabMobileDataFree(decoded);
    return resultArray;
}

/**
 * Free encode result native memory
 *
 * Java signature: private static native void nativeFreeEncodeResult(long ptr);
 */
JNIEXPORT void JNICALL
Java_com_jabcode_JABCodeMobile_nativeFreeEncodeResult(
    JNIEnv *env,
    jclass clazz,
    jlong ptr)
{
    jab_mobile_encode_result *result = (jab_mobile_encode_result *)(uintptr_t)ptr;
    if (result != NULL) {
        jabMobileEncodeResultFree(result);
    }
}

/**
 * Get last error message
 * 
 * Java signature: private static native String nativeGetLastError();
 */
JNIEXPORT jstring JNICALL
Java_com_jabcode_JABCodeMobile_nativeGetLastError(
    JNIEnv *env,
    jclass clazz)
{
    const char *error = jabMobileGetLastError();
    if (error == NULL) {
        return NULL;
    }
    return (*env)->NewStringUTF(env, error);
}

/**
 * Clear last error
 * 
 * Java signature: private static native void nativeClearError();
 */
JNIEXPORT void JNICALL
Java_com_jabcode_JABCodeMobile_nativeClearError(
    JNIEnv *env,
    jclass clazz)
{
    jabMobileClearError();
}

/**
 * Get version string
 * 
 * Java signature: private static native String nativeGetVersion();
 */
JNIEXPORT jstring JNICALL
Java_com_jabcode_JABCodeMobile_nativeGetVersion(
    JNIEnv *env,
    jclass clazz)
{
    const char *version = jabMobileGetVersion();
    return (*env)->NewStringUTF(env, version);
}
