#include <jni.h>
#include "../../../src/jabcode/include/jabcode.h"

JNIEXPORT jstring JNICALL Java_com_jabcode_wrapper_JABCodeNative_VERSION(JNIEnv *env, jclass cls) {
    return (*env)->NewStringUTF(env, VERSION);
}

JNIEXPORT jstring JNICALL Java_com_jabcode_wrapper_JABCodeNative_BUILD_1DATE(JNIEnv *env, jclass cls) {
    return (*env)->NewStringUTF(env, BUILD_DATE);
}

JNIEXPORT jstring JNICALL Java_com_jabcode_internal_JABCodeNative_VERSION(JNIEnv *env, jclass cls) {
    return (*env)->NewStringUTF(env, VERSION);
}

JNIEXPORT jstring JNICALL Java_com_jabcode_internal_JABCodeNative_BUILD_1DATE(JNIEnv *env, jclass cls) {
    return (*env)->NewStringUTF(env, BUILD_DATE);
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_MAX_1SYMBOL_1NUMBER(JNIEnv *env, jclass cls) {
    return MAX_SYMBOL_NUMBER;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_MAX_1COLOR_1NUMBER(JNIEnv *env, jclass cls) {
    return MAX_COLOR_NUMBER;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_MAX_1SIZE_1ENCODING_1MODE(JNIEnv *env, jclass cls) {
    return MAX_SIZE_ENCODING_MODE;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_JAB_1ENCODING_1MODES(JNIEnv *env, jclass cls) {
    return JAB_ENCODING_MODES;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_ENC_1MAX(JNIEnv *env, jclass cls) {
    return ENC_MAX;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_NUMBER_1OF_1MASK_1PATTERNS(JNIEnv *env, jclass cls) {
    return NUMBER_OF_MASK_PATTERNS;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_DEFAULT_1SYMBOL_1NUMBER(JNIEnv *env, jclass cls) {
    return DEFAULT_SYMBOL_NUMBER;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_DEFAULT_1MODULE_1SIZE(JNIEnv *env, jclass cls) {
    return DEFAULT_MODULE_SIZE;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_DEFAULT_1COLOR_1NUMBER(JNIEnv *env, jclass cls) {
    return DEFAULT_COLOR_NUMBER;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_DEFAULT_1MODULE_1COLOR_1MODE(JNIEnv *env, jclass cls) {
    return DEFAULT_MODULE_COLOR_MODE;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_DEFAULT_1ECC_1LEVEL(JNIEnv *env, jclass cls) {
    return DEFAULT_ECC_LEVEL;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_DEFAULT_1MASKING_1REFERENCE(JNIEnv *env, jclass cls) {
    return DEFAULT_MASKING_REFERENCE;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_DISTANCE_1TO_1BORDER(JNIEnv *env, jclass cls) {
    return DISTANCE_TO_BORDER;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_MAX_1ALIGNMENT_1NUMBER(JNIEnv *env, jclass cls) {
    return MAX_ALIGNMENT_NUMBER;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_COLOR_1PALETTE_1NUMBER(JNIEnv *env, jclass cls) {
    return COLOR_PALETTE_NUMBER;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_BITMAP_1BITS_1PER_1PIXEL(JNIEnv *env, jclass cls) {
    return BITMAP_BITS_PER_PIXEL;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_BITMAP_1BITS_1PER_1CHANNEL(JNIEnv *env, jclass cls) {
    return BITMAP_BITS_PER_CHANNEL;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_BITMAP_1CHANNEL_1COUNT(JNIEnv *env, jclass cls) {
    return BITMAP_CHANNEL_COUNT;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_JAB_1SUCCESS(JNIEnv *env, jclass cls) {
    return JAB_SUCCESS;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_JAB_1FAILURE(JNIEnv *env, jclass cls) {
    return JAB_FAILURE;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_NORMAL_1DECODE(JNIEnv *env, jclass cls) {
    return NORMAL_DECODE;
}

JNIEXPORT jint JNICALL Java_com_jabcode_wrapper_JABCodeNative_COMPATIBLE_1DECODE(JNIEnv *env, jclass cls) {
    return COMPATIBLE_DECODE;
}
