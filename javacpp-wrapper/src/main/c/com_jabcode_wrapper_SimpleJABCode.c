#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"
#include "com_jabcode_wrapper_SimpleJABCode.h"

/*
 * Class:     com_jabcode_wrapper_SimpleJABCode
 * Method:    generateJABCode
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_jabcode_wrapper_SimpleJABCode_generateJABCode
  (JNIEnv *env, jclass cls, jstring jtext, jstring joutputFile) {
    // Default to 8 colors
    Java_com_jabcode_wrapper_SimpleJABCode_generateJABCodeWithColorMode(env, cls, jtext, joutputFile, 8);
}

/*
 * Class:     com_jabcode_wrapper_SimpleJABCode
 * Method:    generateJABCodeWithColorMode
 * Signature: (Ljava/lang/String;Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_com_jabcode_wrapper_SimpleJABCode_generateJABCodeWithColorMode
  (JNIEnv *env, jclass cls, jstring jtext, jstring joutputFile, jint colorMode) {
    // Convert Java strings to C strings
    const char *text = (*env)->GetStringUTFChars(env, jtext, NULL);
    const char *outputFile = (*env)->GetStringUTFChars(env, joutputFile, NULL);
    
    // Create encoder with specified color mode
    jab_encode *encoder = createEncode(colorMode, 1);
    if (encoder == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"), "Failed to create encoder");
        goto cleanup;
    }
    
    // Create data
    jab_data *data = (jab_data*)malloc(sizeof(jab_data) + strlen(text) * sizeof(jab_char));
    if (data == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"), "Failed to allocate memory for data");
        goto cleanup_encoder;
    }
    
    data->length = strlen(text);
    memcpy(data->data, text, data->length);
    
    // Generate JABCode
    int result = generateJABCode(encoder, data);
    if (result != 0) {
        char error_msg[100];
        sprintf(error_msg, "Failed to generate JABCode: %d", result);
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"), error_msg);
        goto cleanup_data;
    }
    
    // Save image
    if (!saveImage(encoder->bitmap, (jab_char*)outputFile)) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"), "Failed to save image");
        goto cleanup_data;
    }
    
cleanup_data:
    free(data);
cleanup_encoder:
    destroyEncode(encoder);
cleanup:
    (*env)->ReleaseStringUTFChars(env, jtext, text);
    (*env)->ReleaseStringUTFChars(env, joutputFile, outputFile);
}

/*
 * Class:     com_jabcode_wrapper_SimpleJABCode
 * Method:    decodeJABCode
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_jabcode_wrapper_SimpleJABCode_decodeJABCode
  (JNIEnv *env, jclass cls, jstring jinputFile) {
    // Convert Java string to C string
    const char *inputFile = (*env)->GetStringUTFChars(env, jinputFile, NULL);
    jstring result = NULL;
    
    // Read image
    jab_bitmap *bitmap = readImage((jab_char*)inputFile);
    if (bitmap == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"), "Failed to read image");
        goto cleanup;
    }
    
    // Decode JABCode
    jab_int32 status = 0;
    jab_data *data = decodeJABCode(bitmap, NORMAL_DECODE, &status);
    if (data == NULL || status != 0) {
        char error_msg[100];
        sprintf(error_msg, "Failed to decode JABCode: %d", status);
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"), error_msg);
        goto cleanup_bitmap;
    }
    
    // Convert decoded data to Java string
    char *decoded_text = (char*)malloc(data->length + 1);
    if (decoded_text == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"), "Failed to allocate memory for decoded text");
        goto cleanup_data;
    }
    
    memcpy(decoded_text, data->data, data->length);
    decoded_text[data->length] = '\0';
    
    result = (*env)->NewStringUTF(env, decoded_text);
    free(decoded_text);
    
cleanup_data:
    free(data);
cleanup_bitmap:
    free(bitmap);
cleanup:
    (*env)->ReleaseStringUTFChars(env, jinputFile, inputFile);
    
    return result;
}
