/**
 * JABCode Mobile Bridge - Implementation
 * 
 * Platform-agnostic C implementation for mobile platforms.
 * No dependencies on libpng, libtiff, or desktop-specific libraries.
 */

#include "mobile_bridge.h"
#include "encoder.h"
#include "decoder.h"
#include <string.h>
#include <stdlib.h>

#define MOBILE_BRIDGE_VERSION "1.0.0"
#define MAX_ERROR_LENGTH 256

// Thread-local error storage
static __thread char last_error[MAX_ERROR_LENGTH] = {0};

/**
 * @brief Set error message (thread-local)
 */
static void setError(const char* msg) {
    if (msg) {
        strncpy(last_error, msg, MAX_ERROR_LENGTH - 1);
        last_error[MAX_ERROR_LENGTH - 1] = '\0';
    } else {
        last_error[0] = '\0';
    }
}

const char* jabMobileGetLastError(void) {
    return last_error[0] ? last_error : NULL;
}

void jabMobileClearError(void) {
    last_error[0] = '\0';
}

const char* jabMobileGetVersion(void) {
    return MOBILE_BRIDGE_VERSION;
}

jab_mobile_encode_result* jabMobileEncode(
    jab_char* data,
    jab_int32 data_length,
    jab_mobile_encode_params* params
) {
    // Clear previous error
    jabMobileClearError();
    
    // Validate parameters
    if (!data || data_length <= 0) {
        setError("Invalid input data");
        return NULL;
    }
    
    if (!params) {
        setError("Invalid parameters");
        return NULL;
    }
    
    // Validate color mode (exclude 256-color mode - known broken)
    if (params->color_number == 256) {
        setError("256-color mode not supported (known issue - use 4, 8, 16, 32, 64, or 128)");
        return NULL;
    }
    
    if (params->color_number != 4 && params->color_number != 8 && 
        params->color_number != 16 && params->color_number != 32 && 
        params->color_number != 64 && params->color_number != 128) {
        setError("Invalid color mode (must be 4, 8, 16, 32, 64, or 128)");
        return NULL;
    }
    
    // Validate symbol number (mobile limit: 4 symbols max)
    if (params->symbol_number < 1 || params->symbol_number > 4) {
        setError("Symbol number must be 1-4 (mobile limit)");
        return NULL;
    }
    
    // Validate ECC level
    if (params->ecc_level < 0 || params->ecc_level > 7) {
        setError("ECC level must be 0-7");
        return NULL;
    }
    
    // Create encoder (only takes color_number and symbol_number)
    jab_encode* enc = createEncode(
        params->color_number,
        params->symbol_number
    );
    
    if (!enc) {
        setError("Failed to create encoder");
        return NULL;
    }
    
    // Set ECC level and module size (these are set after creation)
    enc->module_size = params->module_size;
    for (jab_int32 i = 0; i < enc->symbol_number; i++) {
        enc->symbol_ecc_levels[i] = params->ecc_level;
        // Initialize symbol positions (default: sequential grid layout)
        enc->symbol_positions[i] = i;
    }
    
    // For multi-symbol: set reasonable default versions (encoder requires 1-32 range)
    // For single-symbol: version will be auto-calculated by setMasterSymbolVersion
    if (enc->symbol_number > 1) {
        for (jab_int32 i = 0; i < enc->symbol_number; i++) {
            // Use medium size as default (version 10 = ~57x57 modules)
            // Encoder will optimize these in fitDataIntoSymbols if needed
            enc->symbol_versions[i].x = 10;
            enc->symbol_versions[i].y = 10;
        }
    }
    
    // Create jab_data structure from input
    jab_data* data_struct = (jab_data*)malloc(sizeof(jab_data) + data_length);
    if (!data_struct) {
        destroyEncode(enc);
        setError("Memory allocation failed for input data");
        return NULL;
    }
    data_struct->length = data_length;
    memcpy(data_struct->data, data, data_length);
    
    // Encode data using full pipeline (now available in library)
    jab_int32 encode_result = generateJABCode(enc, data_struct);
    free(data_struct);
    
    if (encode_result != 0) {
        destroyEncode(enc);
        setError("Encoding failed");
        return NULL;
    }
    
    // Extract bitmap
    if (!enc->bitmap) {
        destroyEncode(enc);
        setError("No bitmap generated");
        return NULL;
    }
    
    jab_bitmap* bitmap = enc->bitmap;
    jab_int32 width = bitmap->width;
    jab_int32 height = bitmap->height;
    jab_int32 pixel_count = width * height * 4; // RGBA
    
    // Allocate result structure
    jab_mobile_encode_result* result = (jab_mobile_encode_result*)malloc(
        sizeof(jab_mobile_encode_result)
    );
    if (!result) {
        destroyEncode(enc);
        setError("Memory allocation failed");
        return NULL;
    }
    
    // Allocate output buffer
    result->rgba_buffer = (jab_byte*)malloc(pixel_count);
    if (!result->rgba_buffer) {
        free(result);
        destroyEncode(enc);
        setError("Memory allocation failed for output buffer");
        return NULL;
    }
    
    // Copy bitmap data to output buffer
    memcpy(result->rgba_buffer, bitmap->pixel, pixel_count);
    result->width = width;
    result->height = height;
    
    // Capture spatial metadata for synthetic decoder bypass
    result->module_size = enc->module_size;
    result->symbol_width = enc->symbols[0].side_size.x;
    result->symbol_height = enc->symbols[0].side_size.y;
    result->mask_type = enc->mask_type;
    
    // Copy encoder's actual LDPC parameters
    result->wcwr[0] = enc->symbols[0].wcwr[0];
    result->wcwr[1] = enc->symbols[0].wcwr[1];
    result->Pg = enc->symbols[0].Pg;
    
    // Copy encoder's data_map so decoder knows exact metadata/data positions
    jab_int32 map_size = enc->symbols[0].side_size.x * enc->symbols[0].side_size.y;
    result->data_map = (jab_byte*)malloc(map_size * sizeof(jab_byte));
    if(result->data_map) {
        memcpy(result->data_map, enc->symbols[0].data_map, map_size * sizeof(jab_byte));
    }
    
    // Cleanup encoder
    destroyEncode(enc);
    
    return result;
}

void jabMobileEncodeResultFree(jab_mobile_encode_result* result) {
    if (result) {
        if (result->rgba_buffer) {
            free(result->rgba_buffer);
        }
        if (result->data_map) {
            free(result->data_map);
        }
        free(result);
    }
}

jab_data* jabMobileDecode(
    jab_mobile_encode_result* encode_result,
    jab_int32 color_number,
    jab_int32 ecc_level
) {
    // Clear previous error
    jabMobileClearError();
    
    // Validate parameters
    if (!encode_result || !encode_result->rgba_buffer) {
        setError("Invalid encode result");
        return NULL;
    }
    
    if (encode_result->width <= 0 || encode_result->height <= 0) {
        setError("Invalid image dimensions");
        return NULL;
    }
    
    // Validate color_number (must be power of 2 from 4 to 128)
    if (color_number != 4 && color_number != 8 && color_number != 16 && 
        color_number != 32 && color_number != 64 && color_number != 128) {
        setError("Invalid color_number - must be 4, 8, 16, 32, 64, or 128");
        return NULL;
    }
    
    // Validate spatial metadata
    if (encode_result->module_size <= 0 || 
        encode_result->symbol_width <= 0 || 
        encode_result->symbol_height <= 0 ||
        encode_result->mask_type < 0 || encode_result->mask_type > 7) {
        setError("Invalid spatial metadata in encode result");
        return NULL;
    }
    
    // Create bitmap structure from RGBA buffer
    jab_int32 pixel_count = encode_result->width * encode_result->height * 4;
    jab_bitmap* bitmap = (jab_bitmap*)malloc(
        sizeof(jab_bitmap) + pixel_count
    );
    if (!bitmap) {
        setError("Memory allocation failed");
        return NULL;
    }
    
    bitmap->width = encode_result->width;
    bitmap->height = encode_result->height;
    bitmap->bits_per_pixel = 32;
    bitmap->bits_per_channel = 8;
    bitmap->channel_count = 4;
    memcpy(bitmap->pixel, encode_result->rgba_buffer, pixel_count);
    
    // Decode using synthetic bitmap decoder with known encoding parameters AND spatial metadata
    // This completely bypasses camera-specific pattern detection
    jab_int32 decode_status;
    jab_data* result = decodeJABCodeSynthetic(
        bitmap,
        color_number,
        ecc_level,
        encode_result->module_size,
        encode_result->symbol_width,
        encode_result->symbol_height,
        encode_result->mask_type,
        encode_result->data_map,
        encode_result->wcwr,
        encode_result->Pg,
        NORMAL_DECODE, 
        &decode_status
    );
    
    free(bitmap);
    
    if (!result) {
        if (decode_status == 0) {
            setError("Decoding failed - no symbols found");
        } else if (decode_status == 1) {
            setError("Decoding failed - symbol not decodable");
        } else {
            setError("Decoding failed");
        }
        return NULL;
    }
    
    return result;
}

void jabMobileDataFree(jab_data* data) {
    if (data) {
        free(data);
    }
}
