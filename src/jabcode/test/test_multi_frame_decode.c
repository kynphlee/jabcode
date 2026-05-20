/*
 * test_multi_frame_decode.c — WS-4 Step 4.6
 *
 * End-to-end integration test for jabMobileDecodeMultiFrame. Encodes a
 * known payload, produces N noisy copies of the encoded RGBA buffer with
 * deterministic LCG noise, then verifies:
 *
 *   Phase 1  Single-frame fast path: frame_count=1 delegates correctly to
 *            jabMobileDecodeCamera and roundtrips on the clean buffer.
 *   Phase 2  Multi-frame averaging: frame_count=N decodes correctly when
 *            given N copies of the clean buffer (identity averaging case).
 *   Phase 3  Multi-frame on noisy buffers: N noisy copies decode to the
 *            original payload, demonstrating noise robustness improvement.
 *            We assert *roundtrip success* rather than per-pixel error —
 *            the math gate for the averaging principle lives in
 *            test_multi_frame_palette.c (WS-4.5).
 *
 * This test exercises the production wiring (jabMobileDecodeMultiFrame in
 * mobile_bridge.c) end-to-end through the encoder, multi-frame averager,
 * and decoder. WS-4.5's pure-math test guards the averaging algorithm
 * itself; this test guards the integration of that algorithm into the
 * mobile API.
 *
 * Build (from src/jabcode/, with swift-java-wrapper sources):
 *   gcc -O2 -std=c11 \\
 *       -I. -I./include \\
 *       -I../../swift-java-wrapper/include \\
 *       test/test_multi_frame_decode.c \\
 *       ../../swift-java-wrapper/src/c/mobile_bridge.c \\
 *       ../../swift-java-wrapper/src/c/mobile_utils.c \\
 *       -L./build -ljabcode -lm \\
 *       -o test/test_multi_frame_decode -Wl,-rpath,./build
 *
 * See: docs/jabcode-all-nc-plan/00-CHECKLIST.md item 4.6
 *      src/jabcode/test/test_multi_frame_palette.c (WS-4.5 math gate)
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"
#include "mobile_bridge.h"

#define N_FRAMES        10
#define NOISE_SIGMA      6   /* Lower than WS-4.5 — clean encoded bitmap is
                                less forgiving than mid-tone palette */
#define LCG_SEED        4242UL

/* Deterministic LCG noise */
static unsigned long g_rng_state = LCG_SEED;
static int next_noise_step(void)
{
    g_rng_state = g_rng_state * 1103515245UL + 12345UL;
    return (int)((g_rng_state / 65536UL) % (2UL * NOISE_SIGMA + 1UL)) - NOISE_SIGMA;
}
static jab_byte clamp_byte(int v)
{
    if (v < 0)   return 0;
    if (v > 255) return 255;
    return (jab_byte)v;
}

/* Produce a noisy copy of the source RGBA buffer. Alpha (channel 3) is
 * left untouched at 255 to avoid accidental transparency. */
static void make_noisy_copy(const jab_byte* src, jab_byte* dst,
                            jab_int32 width, jab_int32 height)
{
    jab_int32 pixel_count = width * height * 4;
    for (jab_int32 i = 0; i < pixel_count; i++) {
        if ((i % 4) == 3) {
            dst[i] = 255;  /* Preserve full alpha */
        } else {
            dst[i] = clamp_byte((int)src[i] + next_noise_step());
        }
    }
}

int main(void)
{
    const char* payload = "WS-4.6 multi-frame test";
    const jab_int32 payload_len = (jab_int32)strlen(payload);

    printf("================================================\n");
    printf("WS-4 Step 4.6: jabMobileDecodeMultiFrame integration\n");
    printf("payload=\"%s\" (%d bytes) N=%d σ=±%d\n",
           payload, payload_len, N_FRAMES, NOISE_SIGMA);
    printf("================================================\n");

    int failures = 0;

    /* ---- Encode the reference barcode ---- */
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12,
    };
    jab_mobile_encode_result* enc = jabMobileEncode(
        (jab_char*)payload, payload_len, &params);
    if (!enc) {
        printf("FAIL: jabMobileEncode returned NULL (%s)\n", jabMobileGetLastError());
        return 1;
    }
    printf("Encoded %dx%d bitmap (%d-color, ECC=%d)\n",
           enc->width, enc->height, params.color_number, params.ecc_level);

    /* ---- Phase 1: Single-frame fast path ---- */
    printf("--- Phase 1: single-frame fast path ---\n");
    {
        jab_byte* one_frame[1];
        one_frame[0] = enc->rgba_buffer;
        jab_data* decoded = jabMobileDecodeMultiFrame(
            one_frame, enc->width, enc->height, 1);
        if (!decoded) {
            printf("  FAIL: decode returned NULL (%s)\n", jabMobileGetLastError());
            failures++;
        } else if ((jab_int32)decoded->length != payload_len ||
                   memcmp(decoded->data, payload, payload_len) != 0) {
            printf("  FAIL: roundtrip mismatch (got %d bytes)\n", decoded->length);
            failures++;
            jabMobileDataFree(decoded);
        } else {
            printf("  PASS: single-frame decoded %d bytes correctly\n", decoded->length);
            jabMobileDataFree(decoded);
        }
    }

    /* ---- Phase 2: N copies of clean buffer (identity averaging) ---- */
    printf("--- Phase 2: %d clean copies (identity average) ---\n", N_FRAMES);
    {
        jab_byte* frames[N_FRAMES];
        for (int f = 0; f < N_FRAMES; f++) {
            frames[f] = enc->rgba_buffer;  /* All point at same buffer — clean */
        }
        jab_data* decoded = jabMobileDecodeMultiFrame(
            frames, enc->width, enc->height, N_FRAMES);
        if (!decoded) {
            printf("  FAIL: decode returned NULL (%s)\n", jabMobileGetLastError());
            failures++;
        } else if ((jab_int32)decoded->length != payload_len ||
                   memcmp(decoded->data, payload, payload_len) != 0) {
            printf("  FAIL: roundtrip mismatch (got %d bytes)\n", decoded->length);
            failures++;
            jabMobileDataFree(decoded);
        } else {
            printf("  PASS: identity-averaged %d frames decoded correctly\n", decoded->length);
            jabMobileDataFree(decoded);
        }
    }

    /* ---- Phase 3: N noisy copies — real multi-frame benefit ---- */
    printf("--- Phase 3: %d noisy copies (σ=±%d) ---\n", N_FRAMES, NOISE_SIGMA);
    {
        jab_int32 pixel_count = enc->width * enc->height * 4;
        jab_byte* frames[N_FRAMES];
        for (int f = 0; f < N_FRAMES; f++) {
            frames[f] = (jab_byte*)malloc(pixel_count);
            if (!frames[f]) {
                printf("  FAIL: malloc failed for frame %d\n", f);
                failures++;
                for (int k = 0; k < f; k++) free(frames[k]);
                goto cleanup;
            }
            make_noisy_copy(enc->rgba_buffer, frames[f], enc->width, enc->height);
        }

        jab_data* decoded = jabMobileDecodeMultiFrame(
            frames, enc->width, enc->height, N_FRAMES);
        if (!decoded) {
            printf("  FAIL: multi-frame noisy decode returned NULL (%s)\n",
                   jabMobileGetLastError());
            failures++;
        } else if ((jab_int32)decoded->length != payload_len ||
                   memcmp(decoded->data, payload, payload_len) != 0) {
            printf("  FAIL: noisy-roundtrip mismatch (got %d bytes)\n", decoded->length);
            failures++;
            jabMobileDataFree(decoded);
        } else {
            printf("  PASS: %d noisy frames averaged + decoded correctly\n", decoded->length);
            jabMobileDataFree(decoded);
        }

        for (int f = 0; f < N_FRAMES; f++) free(frames[f]);
    }

cleanup:
    jabMobileEncodeResultFree(enc);

    printf("\n================================================\n");
    if (failures == 0) {
        printf("Summary: PASS — multi-frame API integrated end-to-end\n");
    } else {
        printf("Summary: FAIL (%d phase(s) failed)\n", failures);
    }
    printf("================================================\n");
    return failures > 0 ? 1 : 0;
}
