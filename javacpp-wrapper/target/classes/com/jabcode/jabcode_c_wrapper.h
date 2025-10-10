/**
 * JABCode C Wrapper Header
 * This file provides C wrapper functions for the JABCode C++ library
 */

#ifndef JABCODE_C_WRAPPER_H
#define JABCODE_C_WRAPPER_H

#ifdef __cplusplus
extern "C" {
#endif

// Ensure jabcode.h also has C linkage when included in C++ compilation units
#include "jabcode.h"

// C wrapper functions for JABCode library
jab_encode* createEncode_c(jab_int32 color_number, jab_int32 symbol_number);
void destroyEncode_c(jab_encode* enc);
jab_int32 generateJABCode_c(jab_encode* enc, jab_data* data);
jab_data* decodeJABCode_c(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status);
jab_data* decodeJABCodeEx_c(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status, jab_decoded_symbol* symbols, jab_int32 max_symbol_number);
jab_boolean saveImage_c(jab_bitmap* bitmap, jab_char* filename);
jab_boolean saveImageCMYK_c(jab_bitmap* bitmap, jab_boolean isCMYK, jab_char* filename);
jab_bitmap* readImage_c(jab_char* filename);
void reportError_c(jab_char* message);

#ifdef __cplusplus
}
#endif

#endif // JABCODE_C_WRAPPER_H
