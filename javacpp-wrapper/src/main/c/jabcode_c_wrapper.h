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
// Experimental: adjust Nc detection thresholds (for tests)
void setNcThresholds_c(jab_int32 ths_black, jab_double ths_std);
// Experimental: force Nc value in decoder (for tests)
void setForceNc_c(jab_int32 nc);
// Experimental: fetch last Nc RGB samples (4x RGB), 4 module values, and final Nc
void getLastNcDebug_c(jab_int32* out, jab_int32 len);
// Experimental: use default palette grid for >=16 colors during decode (for tests)
void setUseDefaultPaletteHighColor_c(jab_int32 flag);
// Experimental: force ECL (wc, wr) during decode (for tests)
void setForceEcl_c(jab_int32 wc, jab_int32 wr);

#ifdef __cplusplus
}
#endif

#endif // JABCODE_C_WRAPPER_H
