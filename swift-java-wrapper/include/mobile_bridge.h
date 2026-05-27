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
    jab_int32 wcwr[2];           ///< Actual LDPC parameters [wc, wr]
    jab_int32 Pg;                ///< Gross payload length (ecc_encoded_data->length)
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
 * @brief Decode JABCode from camera bitmap (uses full detection pipeline)
 *
 * @param rgba_buffer RGBA pixel data from camera
 * @param width Image width in pixels
 * @param height Image height in pixels
 * @return Decoded data or NULL on failure (check jabMobileGetLastError)
 *
 * @note This function uses the full JABCode camera detection pipeline.
 *       Slower than jabMobileDecode but works with real camera images.
 * @note Caller must free result with jabMobileDataFree()
 *
 * @deprecated WS-5 Council Session 5: permissive fall-through on metadata
 *             failure produces fabricated decodes on degraded camera input
 *             (HELLO-Nc-2 fabrications observed on Nc=3 prints — see
 *             docs/cassandra-register/H_partI_clean_data_failure.md). For
 *             strict Nc metadata reporting, use
 *             jabMobileDecodeCameraWithMeta. The legacy permissive behavior
 *             is preserved here for jabMobileDecodeMultiFrame's fast-path
 *             at frame_count=1 (test_multi_frame_decode contract). The
 *             open root-cause investigation may eventually allow this
 *             function to be removed entirely.
 */
__attribute__((deprecated(
    "permissive fall-through; for accurate Nc metadata use "
    "jabMobileDecodeCameraWithMeta — see Cassandra register entry "
    "H_partI_clean_data_failure")))
jab_data* jabMobileDecodeCamera(
    jab_byte* rgba_buffer,
    jab_int32 width,
    jab_int32 height
);

/**
 * @brief Decode JABCode from camera bitmap AND return the decoded color mode.
 *
 * Same decode path as jabMobileDecodeCamera, but additionally captures the
 * detected color count (1 << (Nc+1)) via the out_color_number output param.
 * Intended for SDK callers that need to surface the actual decoded color
 * mode to upstream consumers — the existing jabMobileDecodeCamera does not
 * expose this metadata.
 *
 * IMPLEMENTATION NOTE: this is a PARALLEL function to jabMobileDecodeCamera,
 * not a replacement. It calls decodeJABCodeEx directly with its own
 * stack-resident symbols[] array (mirroring decodeJABCode's wrapper body
 * defined in src/jabcode/detector.c:4035). The existing jabMobileDecodeCamera
 * code path is untouched and continues to use the decodeJABCode wrapper.
 * This deliberate duplication isolates a prior regression — a previous attempt
 * to switch the existing function's call site from decodeJABCode to
 * decodeJABCodeEx empirically broke decoding (claude/ws-5-color-metadata
 * bisect). Root cause for that regression is not yet known; this approach
 * sidesteps the question entirely.
 *
 * @param rgba_buffer       RGBA pixel data from camera (caller owns)
 * @param width             Image width in pixels
 * @param height            Image height in pixels
 * @param out_color_number  OUT: on success, set to one of {2,4,8,16,32,64,128,256};
 *                          on failure, set to 0. May be NULL to ignore.
 * @return Decoded data or NULL on failure (check jabMobileGetLastError)
 *
 * @note Caller must free result with jabMobileDataFree()
 */
jab_data* jabMobileDecodeCameraWithMeta(
    jab_byte* rgba_buffer,
    jab_int32 width,
    jab_int32 height,
    jab_int32* out_color_number
);

/**
 * @brief Decode JABCode from multiple camera frames via temporal averaging
 *
 * Averages pixel values across N frames (per-channel, per-position), then
 * runs the full JABCode camera detection pipeline on the averaged result.
 * Theoretical noise reduction is σ/√N for independent Gaussian noise; in
 * practice the benefit depends on noise model and palette saturation (see
 * src/jabcode/test/test_multi_frame_palette.c for the math gate).
 *
 * All frames must share the same width × height dimensions and 4-channel
 * RGBA layout. frame_count == 1 fast-paths through jabMobileDecodeCamera
 * without copy overhead.
 *
 * @param rgba_buffers Array of frame_count RGBA pixel buffers
 * @param width        Image width in pixels (all frames same)
 * @param height       Image height in pixels (all frames same)
 * @param frame_count  Number of frames in the buffers array (≥1)
 * @return Decoded data or NULL on failure (check jabMobileGetLastError)
 *
 * @note Caller retains ownership of rgba_buffers and individual buffers;
 *       this function only reads them. Caller must free result with
 *       jabMobileDataFree().
 *
 * WS-4 Step 4.6 — see docs/jabcode-all-nc-plan/00-CHECKLIST.md
 */
jab_data* jabMobileDecodeMultiFrame(
    jab_byte** rgba_buffers,
    jab_int32 width,
    jab_int32 height,
    jab_int32 frame_count
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

/**
 * @brief Load calibration profile from JSON
 * 
 * @param json_string Calibration profile in JSON format
 * @return 1 on success, 0 on failure
 * 
 * @note Calibration applies to subsequent encode operations (≤8 color modes only)
 * @note Remaps standard colors to printer-specific calibrated colors
 */
jab_int32 jabMobileLoadCalibration(const char* json_string);

/**
 * @brief Clear active calibration profile
 */
void jabMobileClearCalibration(void);

/**
 * @brief Toggle verbose diagnostic logging.
 *
 * Forwards to jabSetDiagVerbose. When set TRUE, the decoder emits
 * per-iteration markers (DIAG_PALETTE_LEARNED, DIAG_PARTII_RESULT,
 * Nc_FALLBACK retries, DIAG_MODE0_DETECT, DETECT SUCCESS, GRID_REF)
 * via __android_log_print on Android. When FALSE (default), those
 * markers are suppressed and the camera-thread decode budget is
 * preserved (~1.5-7ms per failed decode saved on Android, depending
 * on Nc_FALLBACK depth and logd contention).
 *
 * Terminal markers (FAIL_ATTR, DECODE_OK, DIAG_SYMBOL_DECODE) always
 * fire regardless of this flag — they are low-volume and diagnostic-
 * essential.
 *
 * Intended use: diagnostic apps enable this before a capture window
 * then disable it afterward. Production SDK consumers leave it at
 * the default. Thread-local; safe across concurrent decoders.
 *
 * See: docs/cassandra-register/H_partI_clean_data_failure.md
 *
 * @param verbose 1 to enable, 0 to disable
 */
void jabMobileSetDiagVerbose(jab_int32 verbose);

/**
 * @brief Check if calibration is active
 * 
 * @return TRUE if calibration loaded, FALSE otherwise
 */
jab_boolean jabMobileHasCalibration(void);

#ifdef __cplusplus
}
#endif

#endif // MOBILE_BRIDGE_H
