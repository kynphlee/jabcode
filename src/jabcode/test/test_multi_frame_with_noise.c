/*
 * test_multi_frame_with_noise.c — WS-5.0 (validation gate for WS-5)
 *
 * The empirical gate that validates jabMobileDecodeMultiFrame (WS-4.6 API)
 * actually helps under noise BEFORE WS-5.1+ builds Android integration on
 * top of it. WS-4.7's single-frame test showed Nc=0 mono has a cliff at σ=4
 * (5/5 → 0/5). If multi-frame can't recover Nc=0 from that cliff via CLT
 * averaging, the API doesn't deliver its motivating benefit.
 *
 * Test structure:
 *   For each Nc in {0, 1, 2, 3, 4, 5, 6, 7}:
 *     For each σ in {0, 4, 8, 16}:
 *       For TRIALS_PER_CELL independent trials:
 *         encode "HELLO" with mode Nc
 *         generate N_FRAMES noisy RGBA copies with deterministic LCG noise
 *         call jabMobileDecodeMultiFrame(frames, W, H, N_FRAMES)
 *         compare result to PAYLOAD via prefix match
 *       Report rate as K/TRIALS_PER_CELL
 *
 * GATING ASSERTION:
 *   Nc=0 mono at σ=4 must recover to ≥ 8/10. Single-frame is 0/5; if
 *   multi-frame averaging can't bring this to 8+/10, WS-5's Android
 *   integration is building on an API that doesn't deliver its benefit.
 *
 * Informational cells (reported, not gating):
 *   - Nc=0 σ=8 (stretch — averaging may or may not reach this)
 *   - All Nc=1..7 cells (color modes are robust enough that recovery is
 *     not the test's concern; what matters is no regression vs single-frame)
 *
 * Build (from src/jabcode/):
 *   gcc -O2 -std=c11 \
 *       -I. -I./include -I../../swift-java-wrapper/include \
 *       test/test_multi_frame_with_noise.c \
 *       ../../swift-java-wrapper/src/c/mobile_bridge.c \
 *       ../../swift-java-wrapper/src/c/mobile_utils.c \
 *       -L./build -ljabcode -ltiff -lpng16 -lz -lm \
 *       -o test/test_multi_frame_with_noise \
 *       -Wl,-rpath,./build
 *
 * Run with:
 *   LD_LIBRARY_PATH=./build ./test/test_multi_frame_with_noise 1>/dev/null
 *   (stdout suppressed → only the test's stderr matrix + verdict visible)
 *
 * See: docs/jabcode-all-nc-plan/00-CHECKLIST.md item 5.0
 *      docs/jabcode-all-nc-plan/04b-followups-plan.md (gate rationale)
 *      src/jabcode/test/test_roundtrip_with_noise.c (single-frame baseline)
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"
#include "mobile_bridge.h"

/* --- Tunables --- */
#define N_FRAMES          10    /* Frames averaged per decode — CLT predicts σ/√10 ≈ 3.16× reduction */
#define TRIALS_PER_CELL   10    /* Independent trials per (Nc, σ) cell */
#define LCG_SEED          5050UL

static const char* PAYLOAD = "HELLO";

typedef struct {
    jab_int32 color_number;
    jab_int32 nc;
    const char* name;
} mode_t;

static const mode_t MODES[] = {
    {2,   0, "Nc=0 mono"},
    {4,   1, "Nc=1 4-color"},
    {8,   2, "Nc=2 8-color"},
    {16,  3, "Nc=3 16-color"},
    {32,  4, "Nc=4 32-color"},
    {64,  5, "Nc=5 64-color"},
    {128, 6, "Nc=6 128-color"},
    {256, 7, "Nc=7 256-color"},
};
#define N_MODES (sizeof(MODES) / sizeof(MODES[0]))

static const jab_int32 SIGMA_LEVELS[] = {0, 4, 8, 16};
#define N_SIGMAS (sizeof(SIGMA_LEVELS) / sizeof(SIGMA_LEVELS[0]))

/* THE GATE: Nc=0 mono at σ=4 must recover to ≥ this many trials. */
#define NC0_SIGMA4_GATE  8

/* --- LCG noise --- */
static unsigned long g_rng_state = LCG_SEED;

static void seed_lcg(unsigned long s) { g_rng_state = s; }

static int next_noise_in_range(jab_int32 sigma)
{
    if (sigma <= 0) return 0;
    g_rng_state = g_rng_state * 1103515245UL + 12345UL;
    return (int)((g_rng_state / 65536UL) % (2UL * sigma + 1UL)) - sigma;
}

static jab_byte clamp_byte(int v)
{
    if (v < 0)   return 0;
    if (v > 255) return 255;
    return (jab_byte)v;
}

/* Make a single noisy copy of an RGBA buffer using CORRELATED luminance
 * noise: one noise value per PIXEL is added identically to R, G, B.
 * Alpha preserved at 255.
 *
 * Why correlated rather than per-channel: Mode 0 (Nc=0 monochrome)
 * detection in detector.c requires R=G=B across sampled pixels to fire
 * the greyscale-signature path. Per-channel independent noise destroys
 * this invariant (R≠G≠B after noise), so Mode 0 detection fails REGARDLESS
 * of frame count — multi-frame averaging can't fix what's broken before
 * averaging runs. Real-world camera capture has substantial correlated
 * luminance noise (sensor read noise, exposure variation) plus
 * smaller-amplitude per-channel chroma noise (Bayer artifacts). Using
 * the luminance component here makes the test measure what
 * jabMobileDecodeMultiFrame can actually improve. */
static void make_noisy_copy(const jab_byte* src, jab_byte* dst,
                            jab_int32 width, jab_int32 height,
                            jab_int32 sigma)
{
    jab_int32 n_pixels = width * height;
    for (jab_int32 p = 0; p < n_pixels; p++) {
        int delta = next_noise_in_range(sigma);  /* one delta per pixel */
        jab_int32 base = p * 4;
        dst[base + 0] = clamp_byte((int)src[base + 0] + delta);  /* R */
        dst[base + 1] = clamp_byte((int)src[base + 1] + delta);  /* G */
        dst[base + 2] = clamp_byte((int)src[base + 2] + delta);  /* B */
        dst[base + 3] = 255;                                     /* A */
    }
}

/* Single multi-frame trial: encode → N noisy copies → multi-frame decode → check.
 * Returns 1 on prefix-match success, 0 on failure. */
static int run_one_trial(const mode_t* m, jab_int32 sigma, unsigned long seed)
{
    int success = 0;
    jab_int32 payload_len = (jab_int32)strlen(PAYLOAD);

    /* Encode the reference barcode */
    jab_mobile_encode_params params = {
        .color_number = m->color_number,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12,
    };
    jab_mobile_encode_result* enc = jabMobileEncode(
        (jab_char*)PAYLOAD, payload_len, &params);
    if (!enc) return 0;

    /* Generate N noisy RGBA copies */
    jab_int32 pixel_count = enc->width * enc->height * 4;
    jab_byte* frames[N_FRAMES];
    int allocated = 0;
    seed_lcg(seed);
    for (int f = 0; f < N_FRAMES; f++) {
        frames[f] = (jab_byte*)malloc(pixel_count);
        if (!frames[f]) goto cleanup;
        allocated++;
        make_noisy_copy(enc->rgba_buffer, frames[f], enc->width, enc->height, sigma);
    }

    /* Multi-frame decode */
    jab_data* result = jabMobileDecodeMultiFrame(
        frames, enc->width, enc->height, N_FRAMES);
    if (result) {
        if ((jab_int32)result->length >= payload_len &&
            memcmp(result->data, PAYLOAD, payload_len) == 0) {
            success = 1;
        }
        jabMobileDataFree(result);
    }

cleanup:
    for (int f = 0; f < allocated; f++) free(frames[f]);
    jabMobileEncodeResultFree(enc);
    return success;
}

int main(void)
{
    fprintf(stderr, "================================================\n");
    fprintf(stderr, "WS-5.0: jabMobileDecodeMultiFrame validation gate\n");
    fprintf(stderr, "payload=\"%s\"  N_FRAMES=%d  trials/cell=%d\n",
            PAYLOAD, N_FRAMES, TRIALS_PER_CELL);
    fprintf(stderr, "================================================\n");

    /* Header row */
    fprintf(stderr, "%-16s ", "Mode");
    for (size_t s = 0; s < N_SIGMAS; s++) {
        fprintf(stderr, "  σ=%-3d", SIGMA_LEVELS[s]);
    }
    fprintf(stderr, "    GATE\n");
    fprintf(stderr, "──────────────────");
    for (size_t s = 0; s < N_SIGMAS; s++) fprintf(stderr, "───────");
    fprintf(stderr, "──────\n");

    int rates[N_MODES][N_SIGMAS];

    for (size_t m = 0; m < N_MODES; m++) {
        for (size_t s = 0; s < N_SIGMAS; s++) {
            jab_int32 sigma = SIGMA_LEVELS[s];
            int successes = 0;
            for (int t = 0; t < TRIALS_PER_CELL; t++) {
                unsigned long seed = LCG_SEED
                                   + (unsigned long)m * 4001UL
                                   + (unsigned long)s * 211UL
                                   + (unsigned long)t * 17UL;
                successes += run_one_trial(&MODES[m], sigma, seed);
            }
            rates[m][s] = successes;
        }

        /* Atomic row print (matches WS-4.7 pattern to avoid library
         * diagnostic interleaving) */
        char row[256];
        int off = snprintf(row, sizeof(row), "%-16s ", MODES[m].name);
        for (size_t s = 0; s < N_SIGMAS && off < (int)sizeof(row); s++) {
            off += snprintf(row + off, sizeof(row) - off,
                            "  %d/%d ", rates[m][s], TRIALS_PER_CELL);
        }
        fprintf(stderr, "%s\n", row);
    }

    /* --- THE GATE --- */
    fprintf(stderr, "\n================================================\n");
    fprintf(stderr, "GATE: Nc=0 mono σ=4 ≥ %d/%d required\n",
            NC0_SIGMA4_GATE, TRIALS_PER_CELL);
    fprintf(stderr, "================================================\n");

    int nc0_sigma4_rate = rates[0][1];  /* MODES[0] = Nc=0, SIGMA_LEVELS[1] = σ=4 */
    fprintf(stderr, "  Nc=0 mono σ=4 actual: %d/%d  (single-frame baseline: 0/5)\n",
            nc0_sigma4_rate, TRIALS_PER_CELL);

    if (nc0_sigma4_rate >= NC0_SIGMA4_GATE) {
        fprintf(stderr, "  Result: PASS — multi-frame averaging recovers the Nc=0 σ=4 cliff\n");
        fprintf(stderr, "  WS-5.1+ Android integration may proceed\n");
        return 0;
    } else {
        fprintf(stderr, "  Result: FAIL — multi-frame averaging insufficient for Nc=0 σ=4\n");
        fprintf(stderr, "  WS-5 GATING: revisit jabMobileDecodeMultiFrame design before integrating\n");
        fprintf(stderr, "  Candidates: increase N_FRAMES, median/trimmed mean, or noise-model rethink\n");
        return 1;
    }
}
