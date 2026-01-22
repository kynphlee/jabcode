/**
 * JABCode Mobile Bridge - Platform-Agnostic C API
 * 
 * This header defines the interface between mobile platforms (Android/iOS)
 * and the JABCode native library. It eliminates desktop dependencies
 * (libpng, libtiff) and provides a simple RGBA buffer-based API.
 * 
 * Copyright 2026
 * License: Same as JABCode (see LICENSE)
 */

#ifndef MOBILE_BRIDGE_H
#define MOBILE_BRIDGE_H

#ifdef __cplusplus
extern "C" {
#endif

#include "jabcode.h"

/**
 * @brief Mobile-specific encode parameters
 */
typedef struct {
    jab_int32 color_number;      ///< 4, 8, 16, 32, 64, 128 (NOT 256 - broken)
    jab_int32 symbol_number;     ///< Default: 1, Max: 4 (mobile limit)
    jab_int32 ecc_level;         ///< Error correction level 0-7 (default: 3)
    jab_int32 module_size;       ///< Pixels per module (default: 12)
} jab_mobile_encode_params;

/**
 * @brief Mobile encode result with spatial metadata for synthetic roundtrip decode
 */
typedef struct {
    jab_byte* rgba_buffer;       ///< Output RGBA pixel data (width × height × 4)
    jab_int32 width;             ///< Image width in pixels
    jab_int32 height;            ///< Image height in pixels
    // Spatial metadata for decoder bypass (avoids camera-tuned pattern detection)
    jab_int32 module_size;       ///< Pixels per module
    jab_int32 symbol_width;      ///< Symbol width in modules
    jab_int32 symbol_height;     ///< Symbol height in modules
    jab_int32 mask_type;         ///< Mask pattern type used by encoder
    jab_byte* data_map;          ///< Encoder's data_map (0=metadata/pattern, 1=data module)
} jab_mobile_encode_result;

/**
 * @brief Create encoder with mobile parameters
 * 
 * @param data Data to encode
 * @param data_length Length of data in bytes
 * @param params Encoding parameters
 * @return Encoder instance or NULL on failure (check jabMobileGetLastError)
 * 
 * @note Caller must free result with jabMobileEncodeResultFree()
 */
jab_mobile_encode_result* jabMobileEncode(
    jab_char* data,
    jab_int32 data_length,
    jab_mobile_encode_params* params
);

/**
 * @brief Free encode result
 * 
 * @param result Encode result to free
 */
void jabMobileEncodeResultFree(jab_mobile_encode_result* result);

/**
 * @brief Decode JABCode from encode result (optimal for mobile roundtrip)
 * 
 * @param encode_result The encode result containing bitmap and spatial metadata
 * @param color_number Color count used during encoding (4, 8, 16, 32, 64, 128)
 * @param ecc_level Error correction level used during encoding
 * @return Decoded data or NULL on failure (check jabMobileGetLastError)
 * 
 * @note This function uses spatial metadata from encoding to bypass camera-specific
 *       pattern detection, which fails on perfect synthetic bitmaps.
 * @note Caller must free result with jabMobileDataFree()
 */
jab_data* jabMobileDecode(
    jab_mobile_encode_result* encode_result,
    jab_int32 color_number,
    jab_int32 ecc_level
);

/**
 * @brief Free decoded data
 * 
 * @param data Data to free
 */
void jabMobileDataFree(jab_data* data);

/**
 * @brief Get last error message (thread-local)
 * 
 * @return Error string or NULL if no error
 * 
 * @note Error message is stored in thread-local storage
 */
const char* jabMobileGetLastError(void);

/**
 * @brief Clear last error message (thread-local)
 */
void jabMobileClearError(void);

/**
 * @brief Get mobile bridge version string
 * 
 * @return Version string (e.g., "1.0.0")
 */
const char* jabMobileGetVersion(void);

#ifdef __cplusplus
}
#endif

#endif // MOBILE_BRIDGE_H
