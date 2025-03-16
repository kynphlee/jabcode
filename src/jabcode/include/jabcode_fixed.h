/**
 * libjabcode - JABCode Encoding/Decoding Library
 *
 * Copyright 2016 by Fraunhofer SIT. All rights reserved.
 * See LICENSE file for full terms of use and distribution.
 *
 * Contact: Huajian Liu <liu@sit.fraunhofer.de>
 *Waldemar Berchtold <waldemar.berchtold@sit.fraunhofer.de>
 *
 * @file jabcode.h
 * @brief Main libjabcode header
 */

#ifndef JABCODE_H
#define JABCODE_H

#define VERSION "2.0.0"
#define BUILD_DATE __DATE__

#define MAX_SYMBOL_NUMBER       61
#define MAX_COLOR_NUMBER        256
#define MAX_SIZE_ENCODING_MODE  256
#define JAB_ENCODING_MODES      6
#define ENC_MAX                 1000000
#define NUMBER_OF_MASK_PATTERNS8

#define DEFAULT_SYMBOL_NUMBER 1
#define DEFAULT_MODULE_SIZE12
#define DEFAULT_COLOR_NUMBER 8
#define DEFAULT_MODULE_COLOR_MODE 2
#define DEFAULT_ECC_LEVEL3
#define DEFAULT_MASKING_REFERENCE 7


#define DISTANCE_TO_BORDER      4
#define MAX_ALIGNMENT_NUMBER    9
#define COLOR_PALETTE_NUMBER4

#define BITMAP_BITS_PER_PIXEL32
#define BITMAP_BITS_PER_CHANNEL8
#define BITMAP_CHANNEL_COUNT4

#defineJAB_SUCCESS1
#defineJAB_FAILURE0

#define NORMAL_DECODE0
#define COMPATIBLE_DECODE1

#define VERSION2SIZE(x)(x * 4 + 17)
#define SIZE2VERSION(x)((x - 17) / 4)
#define MAX(a,b) ({__typeof__ (a) _a = (a); __typeof__ (b) _b = (b); _a > _b ? _a : _b;})
#define MIN(a,b) ({__typeof__ (a) _a = (a); __typeof__ (b) _b = (b); _a < _b ? _a : _b;})

#define JAB_REPORT_ERROR(x){ printf("JABCode Error: "); printf x; printf("\n"); }
#define JAB_REPORT_INFO(x){ printf("JABCode Info: "); printf x; printf("\n"); }

typedef unsigned char jab_byte;
typedef char jab_char;
typedef unsigned char jab_boolean;
typedef int jab_int32;
typedef unsigned int jab_uint32;
typedef short jab_int16;
typedef unsigned short jab_uint16;
typedef long long jab_int64;
typedef unsigned long longjab_uint64;
typedef floatjab_float;
typedef double              jab_double;

/**
 * @brief 2-dimensional integer vector
*/
typedef struct {
jab_int32x;
jab_int32y;
}jab_vector2d;

/**
 * @brief 2-dimensional float vector
*/
typedef struct {
jab_floatx;
jab_floaty;
}jab_point;

/**
 * @brief Data structure
*/
typedef struct {
jab_int32length;
jab_chardata[];
}jab_data;

/**
 * @brief Code bitmap
*/
typedef struct {
   jab_int32width;
   jab_int32height;
   jab_int32bits_per_pixel;
   jab_int32bits_per_channel;
   jab_int32channel_count;
   jab_bytepixel[];
}jab_bitmap;

/**
 * @brief Symbol parameters
*/
typedef struct {
jab_int32index;
jab_vector2dside_size;
jab_int32host;
jab_int32slaves[4];
jab_int32 wcwr[2];
jab_data*data;
jab_byte*data_map;
jab_data*metadata;
jab_byte*matrix;
}jab_symbol;

/**
 * @brief Encode parameters
*/
typedef struct {
jab_int32color_number;
jab_int32symbol_number;
jab_int32module_size;
jab_int32master_symbol_width;
jab_int32master_symbol_height;
jab_byte*palette;g used module colors in format RGB
jab_vector2d*symbol_versions;
jab_byte* symbol_ecc_levels;
jab_int32*symbol_positions;
jab_symbol*symbols;ter to internal representation of JAB Code symbols
jab_bitmap*bitmap;
}jab_encode;

/**
 * @brief Decoded metadata
*/
typedef struct {
jab_boolean default_mode;
jab_byte Nc;
jab_byte mask_type;
jab_byte docked_position;
jab_vector2d side_version;
jab_vector2d ecl;
}jab_metadata;

/**
 * @brief Decoded symbol
*/
typedef struct {
jab_int32 index;
jab_int32 host_index;
jab_int32 host_position;
jab_vector2d side_size;
jab_float module_size;
jab_point pattern_positions[4];
jab_metadata metadata;
jab_metadata slave_metadata[4];
jab_byte* palette;
jab_data* data;
}jab_decoded_symbol;


extern jab_encode* createEncode(jab_int32 color_number, jab_int32 symbol_number);
extern void destroyEncode(jab_encode* enc);
extern jab_int32 generateJABCode(jab_encode* enc, jab_data* data);
extern jab_data* decodeJABCode(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status);
extern jab_data* decodeJABCodeEx(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status, jab_decoded_symbol* symbols, jab_int32 max_symbol_number);
extern jab_boolean saveImage(jab_bitmap* bitmap, jab_char* filename);
extern jab_boolean saveImageCMYK(jab_bitmap* bitmap, jab_boolean isCMYK, jab_char* filename);
extern jab_bitmap* readImage(jab_char* filename);
extern void reportError(jab_char* message);

#endif
