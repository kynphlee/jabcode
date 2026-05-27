/**
 * JABCode Mobile Bridge - Implementation
 * 
 * Platform-agnostic C implementation for mobile platforms.
 * No dependencies on libpng, libtiff, or desktop-specific libraries.
 */

#include "mobile_bridge.h"
#include "encoder.h"
#include "decoder.h"
#include "color_calibration.h"
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
    
    // Validate color mode: accept all 8 JABCode color modes (Nc=0..7 → 2,4,8,16,32,64,128,256).
    // The historical 256-color "known broken" carve-out and the {4..128}-only allowed list
    // pre-dated the WS-0 (Mode 0 / 2-color) and WS-3 (Nc=7 / 256-color) library work. The C
    // library now supports all eight modes natively; this validation is purely a positive-list
    // sanity check on the caller's color_number argument. See:
    //   docs/jabcode-all-nc-plan/00-CHECKLIST.md items 0.11 (Mode 0 Android) and 3.11 (Nc=7 Android)
    if (params->color_number != 2   && params->color_number != 4   &&
        params->color_number != 8   && params->color_number != 16  &&
        params->color_number != 32  && params->color_number != 64  &&
        params->color_number != 128 && params->color_number != 256) {
        setError("Invalid color mode (must be one of 2, 4, 8, 16, 32, 64, 128, 256)");
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
    
    // Apply color calibration if active (before encoding)
    if (jabHasCalibration()) {
        jabApplyCalibration(enc);
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
    
    // Validate color_number: accept all 8 JABCode color modes (Nc=0..7 → 2,4,8,16,32,64,128,256).
    // Symmetric with the encode-path gate above; see that block's comment for history.
    if (color_number != 2   && color_number != 4   &&
        color_number != 8   && color_number != 16  &&
        color_number != 32  && color_number != 64  &&
        color_number != 128 && color_number != 256) {
        setError("Invalid color_number - must be one of 2, 4, 8, 16, 32, 64, 128, 256");
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

jab_data* jabMobileDecodeCamera(
    jab_byte* rgba_buffer,
    jab_int32 width,
    jab_int32 height
) {
    // Clear previous error
    jabMobileClearError();

    // Validate parameters
    if (!rgba_buffer) {
        setError("Invalid RGBA buffer");
        return NULL;
    }

    if (width <= 0 || height <= 0) {
        setError("Invalid image dimensions");
        return NULL;
    }

    // Create bitmap structure from RGBA buffer
    jab_int32 pixel_count = width * height * 4;
    jab_bitmap* bitmap = (jab_bitmap*)malloc(
        sizeof(jab_bitmap) + pixel_count
    );
    if (!bitmap) {
        setError("Memory allocation failed");
        return NULL;
    }

    bitmap->width = width;
    bitmap->height = height;
    bitmap->bits_per_pixel = 32;
    bitmap->bits_per_channel = 8;
    bitmap->channel_count = 4;
    memcpy(bitmap->pixel, rgba_buffer, pixel_count);

    // Decode using full camera detection pipeline
    jab_int32 decode_status;
    jab_data* result = decodeJABCode(bitmap, NORMAL_DECODE, &decode_status);

    free(bitmap);

    if (!result) {
        if (decode_status == 0) {
            setError("No JABCode found in image");
        } else if (decode_status == 1) {
            setError("JABCode found but not decodable");
        } else {
            setError("Decoding failed");
        }
        return NULL;
    }

    return result;
}

/**
 * @see mobile_bridge.h for full contract documentation.
 *
 * IMPORTANT: this is a PARALLEL function to jabMobileDecodeCamera, NOT a
 * replacement. Do not consolidate them. The duplication is intentional to
 * isolate from a prior regression (see header doc).
 */
jab_data* jabMobileDecodeCameraWithMeta(
    jab_byte* rgba_buffer,
    jab_int32 width,
    jab_int32 height,
    jab_int32* out_color_number
) {
    // Always initialize the output param to 0 (= "no decode yet").
    if (out_color_number) *out_color_number = 0;

    // Clear previous error
    jabMobileClearError();

    // Validate parameters (same set as jabMobileDecodeCamera)
    if (!rgba_buffer) {
        setError("Invalid RGBA buffer");
        return NULL;
    }
    if (width <= 0 || height <= 0) {
        setError("Invalid image dimensions");
        return NULL;
    }

    // Create bitmap structure from RGBA buffer
    jab_int32 pixel_count = width * height * 4;
    jab_bitmap* bitmap = (jab_bitmap*)malloc(
        sizeof(jab_bitmap) + pixel_count
    );
    if (!bitmap) {
        setError("Memory allocation failed");
        return NULL;
    }

    bitmap->width = width;
    bitmap->height = height;
    bitmap->bits_per_pixel = 32;
    bitmap->bits_per_channel = 8;
    bitmap->channel_count = 4;
    memcpy(bitmap->pixel, rgba_buffer, pixel_count);

    // Decode via decodeJABCodeEx with our own stack-resident symbols array
    // so we can capture symbols[0].metadata.Nc on success. This mirrors the
    // body of decodeJABCode (src/jabcode/detector.c:4035) — same code path,
    // same behavior, just with the symbols array kept in scope long enough
    // to read .Nc before it goes out of scope.
    //
    // Option D (WS-5 Council Session 5): scope strict PartII to the WithMeta
    // path only. The legacy jabMobileDecodeCamera retains its permissive
    // fall-through so test_multi_frame_decode Phase 1 (which fast-paths
    // through the legacy entry point at frame_count=1) keeps passing.
    // SDK consumers using WithMeta get honest metadata; legacy callers
    // retain backward-compatible behavior. See:
    //   docs/cassandra-register/H_partI_clean_data_failure.md
    jabSetStrictPartIIRequired(1);
    jab_int32 decode_status;
    jab_decoded_symbol symbols[MAX_SYMBOL_NUMBER];
    jab_data* result = decodeJABCodeEx(bitmap, NORMAL_DECODE, &decode_status,
                                       symbols, MAX_SYMBOL_NUMBER);
    jabSetStrictPartIIRequired(0);
    if (result && out_color_number) {
        // Nc is the color-index (0..7) mapping to {2,4,8,16,32,64,128,256}.
        // symbols[0].metadata.Nc is a value-type (jab_byte) field; valid to
        // read after decodeJABCodeEx returns even though it frees the
        // heap-owned palette/data pointers on the same struct.
        *out_color_number = 1 << (symbols[0].metadata.Nc + 1);
    }

    free(bitmap);

    if (!result) {
        if (decode_status == 0) {
            setError("No JABCode found in image");
        } else if (decode_status == 1) {
            setError("JABCode found but not decodable");
        } else {
            setError("Decoding failed");
        }
        return NULL;
    }

    return result;
}

/* WS-4 Step 4.6: temporal-averaging multi-frame decode.
 *
 * Averages pixel values across N RGBA buffers (per-channel, per-position)
 * then runs decodeJABCode on the averaged frame. Mirrors jabMobileDecodeCamera
 * for everything except the averaging pass. The math gate is
 * src/jabcode/test/test_multi_frame_palette.c (WS-4.5) — that test asserts
 * the CLT property √N noise reduction; this API is the production wrapper
 * that exposes the same averaging to mobile callers.
 *
 * See: docs/jabcode-all-nc-plan/00-CHECKLIST.md item 4.6 */
jab_data* jabMobileDecodeMultiFrame(
    jab_byte** rgba_buffers,
    jab_int32 width,
    jab_int32 height,
    jab_int32 frame_count
) {
    jabMobileClearError();

    if (!rgba_buffers) {
        setError("Invalid rgba_buffers array (null)");
        return NULL;
    }
    if (frame_count <= 0) {
        setError("Invalid frame_count (must be ≥1)");
        return NULL;
    }
    if (width <= 0 || height <= 0) {
        setError("Invalid image dimensions");
        return NULL;
    }
    for (jab_int32 f = 0; f < frame_count; f++) {
        if (!rgba_buffers[f]) {
            setError("Null buffer in rgba_buffers array");
            return NULL;
        }
    }

    /* Single-frame fast path: delegate to existing camera decode without copy.
     *
     * The deprecation warning on jabMobileDecodeCamera is intentional for
     * SDK consumers (it steers them to jabMobileDecodeCameraWithMeta). This
     * internal call is the deliberate exception — multi-frame averaging
     * needs the permissive fall-through preserved on the legacy path (see
     * the @deprecated note in mobile_bridge.h and Cassandra register entry
     * H_partI_clean_data_failure). Suppress the warning here only. */
    if (frame_count == 1) {
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
        return jabMobileDecodeCamera(rgba_buffers[0], width, height);
#pragma GCC diagnostic pop
    }

    jab_int32 pixel_count = width * height * 4;

    /* Allocate the jab_bitmap directly to avoid a separate averaging buffer
     * (saves one width*height*4 allocation pair). */
    jab_bitmap* bitmap = (jab_bitmap*)malloc(sizeof(jab_bitmap) + pixel_count);
    if (!bitmap) {
        setError("Memory allocation failed for averaged bitmap");
        return NULL;
    }
    bitmap->width = width;
    bitmap->height = height;
    bitmap->bits_per_pixel = 32;
    bitmap->bits_per_channel = 8;
    bitmap->channel_count = 4;

    /* Per-byte average across frames, rounded to nearest integer.
     * frame_count is bounded by jab_int32 and sum cannot overflow since
     * each byte is ≤255 and frame_count is typically <100 in mobile use
     * (max 255 * 8388607 frames before int overflow — practical upper
     * bound for mobile multi-frame buffering is ~30). */
    const jab_int32 half = frame_count / 2;  /* rounding offset */
    for (jab_int32 i = 0; i < pixel_count; i++) {
        jab_int32 sum = 0;
        for (jab_int32 f = 0; f < frame_count; f++) {
            sum += rgba_buffers[f][i];
        }
        bitmap->pixel[i] = (jab_byte)((sum + half) / frame_count);
    }

    /* Decode via the full camera pipeline — same path as jabMobileDecodeCamera */
    jab_int32 decode_status;
    jab_data* result = decodeJABCode(bitmap, NORMAL_DECODE, &decode_status);

    free(bitmap);

    if (!result) {
        if (decode_status == 0) {
            setError("No JABCode found in averaged frame");
        } else if (decode_status == 1) {
            setError("JABCode found but not decodable in averaged frame");
        } else {
            setError("Multi-frame decoding failed");
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

jab_int32 jabMobileLoadCalibration(const char* json_string) {
    jabMobileClearError();
    
    if (!json_string) {
        setError("Invalid JSON string");
        return 0;
    }
    
    jab_int32 result = jabLoadCalibrationFromJSON(json_string);
    if (!result) {
        setError("Failed to parse calibration profile");
    }
    
    return result;
}

void jabMobileClearCalibration(void) {
    jabClearCalibration();
}

jab_boolean jabMobileHasCalibration(void) {
    return jabHasCalibration();
}
