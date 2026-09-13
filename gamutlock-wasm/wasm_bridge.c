/*
 * gamutlock-wasm bridge — bridge #3 (Emscripten/browser) beside the JNI and
 * Swift bridges, over the SAME portable codec source (src/jabcode).
 *
 * Decode protocol: byte-identical to the on-device camera path and to the R0
 * robustness rig probe (robustness/r0/rig/r0_decode.c):
 *
 *     jabSetStrictPartIIRequired(1);
 *     decodeJABCodeEx(bitmap, NORMAL_DECODE, &status, symbols, MAX_SYMBOL_NUMBER);
 *     jabSetStrictPartIIRequired(0);
 *
 * Strict Part-II is load-bearing: without it, degraded frames can produce
 * fabricated decodes (see docs/cassandra-register/H_partI_clean_data_failure.md).
 * A web reader that fabricates on bad camera frames would hand the verify
 * endpoint garbage blobs — the strict gate refuses them at the codec layer,
 * exactly as the Android SDK does.
 *
 * Pixel contract: the frame buffer is R,G,B,A byte order, 8 bits/channel —
 * the SAME no-shuffle contract the JNI bridge documents for Android's
 * ANDROID_BITMAP_FORMAT_RGBA_8888, and the layout the HTML canvas
 * getImageData() is specified to return. JS writes ImageData.data straight
 * into gw_frame_buffer(); no per-pixel channel work anywhere.
 *
 * Single-threaded by design (the browser main thread or one worker). Result
 * state is plain static and valid until the next gw_decode_frame() call.
 */

#include <stdlib.h>
#include <string.h>
#include <stdio.h>

#include "jabcode.h"

#include <emscripten/emscripten.h>

/* Persistent frame bitmap, grown on demand. jab_bitmap carries its pixel
 * data in a flexible array member, so the whole thing is one allocation. */
static jab_bitmap *frame = NULL;
static jab_int32 frame_capacity = 0; /* bytes available in frame->pixel */

/* Last decode result, freed on the next decode. */
static jab_data *result = NULL;
static jab_decoded_symbol symbols[MAX_SYMBOL_NUMBER];
static jab_int32 last_status = -1;
static jab_int32 last_color_count = 0;
static char last_error[256] = "";

static void set_error(const char *msg) {
    strncpy(last_error, msg ? msg : "", sizeof(last_error) - 1);
    last_error[sizeof(last_error) - 1] = '\0';
}

/*
 * (Re)size the persistent frame bitmap and return the pixel pointer.
 * JS fills it with ImageData.data bytes (RGBA), then calls gw_decode_frame().
 * Returns NULL on allocation failure or absurd dimensions.
 */
EMSCRIPTEN_KEEPALIVE
unsigned char *gw_frame_buffer(int width, int height) {
    if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
        set_error("gw_frame_buffer: dimensions out of range");
        return NULL;
    }
    jab_int32 needed = width * height * BITMAP_CHANNEL_COUNT;
    if (!frame || frame_capacity < needed) {
        free(frame);
        frame = (jab_bitmap *)malloc(sizeof(jab_bitmap) + (size_t)needed);
        if (!frame) {
            frame_capacity = 0;
            set_error("gw_frame_buffer: out of memory");
            return NULL;
        }
        frame_capacity = needed;
    }
    frame->width = width;
    frame->height = height;
    frame->bits_per_pixel = BITMAP_BITS_PER_PIXEL;
    frame->bits_per_channel = BITMAP_BITS_PER_CHANNEL;
    frame->channel_count = BITMAP_CHANNEL_COUNT;
    return frame->pixel;
}

/*
 * Decode the current frame. Returns 1 on success (payload available via the
 * gw_result_* accessors), 0 on failure (gw_result_status still reports the
 * detector's verdict: 0=not detectable, 1=not decodable, 2=partly, 3=fully).
 */
EMSCRIPTEN_KEEPALIVE
int gw_decode_frame(void) {
    set_error("");
    if (result) {
        free(result);
        result = NULL;
    }
    last_status = -1;
    last_color_count = 0;
    if (!frame) {
        set_error("gw_decode_frame: no frame buffer (call gw_frame_buffer first)");
        return 0;
    }
    memset(symbols, 0, sizeof(symbols));

    jabSetStrictPartIIRequired(1);
    result = decodeJABCodeEx(frame, NORMAL_DECODE, &last_status,
                             symbols, MAX_SYMBOL_NUMBER);
    jabSetStrictPartIIRequired(0);

    if (!result) {
        set_error("decode failed (no symbol or payload unrecoverable)");
        return 0;
    }
    last_color_count = 1 << (symbols[0].metadata.Nc + 1);
    return 1;
}

EMSCRIPTEN_KEEPALIVE
const unsigned char *gw_result_data(void) {
    return result ? (const unsigned char *)result->data : NULL;
}

EMSCRIPTEN_KEEPALIVE
int gw_result_length(void) {
    return result ? result->length : 0;
}

EMSCRIPTEN_KEEPALIVE
int gw_result_color_count(void) {
    return last_color_count;
}

EMSCRIPTEN_KEEPALIVE
int gw_result_status(void) {
    return last_status;
}

/* Finder-pattern corner positions of the master symbol (pixel coordinates in
 * the decoded frame), index 0..3. Only meaningful after a successful decode. */
EMSCRIPTEN_KEEPALIVE
float gw_result_corner_x(int i) {
    if (!result || i < 0 || i > 3) return -1.0f;
    return symbols[0].pattern_positions[i].x;
}

EMSCRIPTEN_KEEPALIVE
float gw_result_corner_y(int i) {
    if (!result || i < 0 || i > 3) return -1.0f;
    return symbols[0].pattern_positions[i].y;
}

EMSCRIPTEN_KEEPALIVE
const char *gw_last_error(void) {
    return last_error;
}

EMSCRIPTEN_KEEPALIVE
const char *gw_version(void) {
    return "gamutlock-wasm 0.1.0 (libjabcode " VERSION ")";
}

/* Fork decode-rate toggles, surfaced so the web lane can be driven exactly
 * like the Android SDK (pinned-Nc scans, diagnostic capture windows). */
EMSCRIPTEN_KEEPALIVE
void gw_set_preferred_color_count(int count) {
    jabSetPreferredColorCount(count);
}

EMSCRIPTEN_KEEPALIVE
void gw_set_diag_verbose(int verbose) {
    jabSetDiagVerbose((jab_boolean)(verbose ? 1 : 0));
}
