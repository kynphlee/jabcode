#ifndef JABCODE_WRAPPER_H
#define JABCODE_WRAPPER_H

#include "jabcode.h"

#ifdef __cplusplus
extern "C" {
#endif

jab_encode* createEncodeWrapper(jab_int32 color_number, jab_int32 symbol_number);
void destroyEncodeWrapper(jab_encode* enc);
jab_int32 generateJABCodeWrapper(jab_encode* enc, jab_data* data);
jab_data* decodeJABCodeWrapper(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status);
jab_data* decodeJABCodeExWrapper(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status, jab_decoded_symbol* symbols, jab_int32 max_symbol_number);
jab_boolean saveImageWrapper(jab_bitmap* bitmap, jab_char* filename);

#ifdef __cplusplus
}
#endif

#endif // JABCODE_WRAPPER_H
