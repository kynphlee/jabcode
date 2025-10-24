/**
 * JABCode C Wrapper Implementation (C language)
 * Provides C wrapper functions over the JABCode C library APIs.
 */

#include "jabcode_c_wrapper.h"
#include <stdio.h>
#include <string.h>

// Forward declaration to avoid including encoder.h here
#ifdef __cplusplus
extern "C" {
#endif
extern void setDefaultPalette(jab_int32 color_number, jab_byte* palette);
#ifdef __cplusplus
}
#endif

jab_encode* createEncode_c(jab_int32 color_number, jab_int32 symbol_number) {
    return createEncode(color_number, symbol_number);
}

void destroyEncode_c(jab_encode* enc) {
    destroyEncode(enc);
}

jab_int32 generateJABCode_c(jab_encode* enc, jab_data* data) {
    return generateJABCode(enc, data);
}

jab_data* decodeJABCode_c(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status) {
    return decodeJABCode(bitmap, mode, status);
}

jab_data* decodeJABCodeEx_c(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status, jab_decoded_symbol* symbols, jab_int32 max_symbol_number) {
    return decodeJABCodeEx(bitmap, mode, status, symbols, max_symbol_number);
}

jab_boolean saveImage_c(jab_bitmap* bitmap, jab_char* filename) {
    return saveImage(bitmap, filename);
}

jab_boolean saveImageCMYK_c(jab_bitmap* bitmap, jab_boolean isCMYK, jab_char* filename) {
    return saveImageCMYK(bitmap, isCMYK, filename);
}

jab_bitmap* readImage_c(jab_char* filename) {
    return readImage(filename);
}

void reportError_c(jab_char* message) {
    reportError(message);
}

void getEncoderDefaultPalette_c(jab_int32 color_number, jab_int32* out, jab_int32 len) {
    if (!out || len <= 0) return;
    if (color_number != 16 && color_number != 32 && color_number != 64 && color_number != 128 && color_number != 256 && color_number != 8 && color_number != 4) {
        // Default to 8 if invalid
        color_number = 8;
    }
    jab_int32 bytes = color_number * 3;
    jab_byte buf[256 * 3];
    memset(buf, 0, sizeof(buf));
    setDefaultPalette(color_number, buf);
    jab_int32 n = bytes;
    if (n > len) n = len;
    for (jab_int32 i = 0; i < n; ++i) out[i] = (jab_int32)buf[i];
    for (jab_int32 i = n; i < len; ++i) out[i] = 0;
}
