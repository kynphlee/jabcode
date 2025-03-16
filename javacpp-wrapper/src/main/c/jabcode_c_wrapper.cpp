/**
 * JABCode C Wrapper Implementation
 * This file provides C wrapper functions for the JABCode C++ library
 */

#include "jabcode_c_wrapper.h"
#include <stdio.h>

// C wrapper functions for JABCode library
extern "C" {

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
    // This is a simple wrapper for the reportError function
    printf("JABCode Error: %s\n", message);
}

} // extern "C"
