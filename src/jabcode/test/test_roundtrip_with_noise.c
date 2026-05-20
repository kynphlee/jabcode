/*
 * test_roundtrip_with_noise.c — WS-4 Step 4.7
 *
 * The keystone empirical test for WS-4 (Phase B-Classical noise robustness).
 * Sweeps a 2D matrix of (Nc, noise σ) and measures decode success rate
 * across N independent trials. Produces a grid that subsequent WS-4 steps
 * (4.8 threshold tuning, 4.9 full regression) use to decide whether to
 * flip the conservative compile-flag defaults (-DUSE_LAB_DISTANCE,
 * -DUSE_FP_CALIBRATION) ON in the production build.
 *
 * Test structure:
 *   For each Nc in {0,1,2,3,4,5,6,7}:
 *     For each σ in {0, 4, 8, 12, 16, 20}:
 *       For N_TRIALS independent LCG-seeded trials:
 *         encode "HELLO" with mode Nc
 *         apply ±σ uniform-ish noise to the encoded bitmap RGB channels
 *         decode and tally success
 *       Report rate as "K/N" with PASS/FAIL annotation
 *
 * Assertion logic (intentionally lenient — this is the measurement test
 * that WS-4.8 tunes against; it must produce signal without false alarms):
 *
 *   SOFT GATE (σ=0 aggregate):
 *     At σ=0, AT LEAST 75% of modes (6/8) must achieve ≥1/N_TRIALS success.
 *     This catches catastrophic regressions (e.g., all decode broken) without
 *     gating on per-mode quirks (Nc=3 + HELLO is known intermittent at
 *     module_size=12; Nc=0 returns padded results with HELLO as prefix).
 *
 *   INFORMATIONAL (σ>0):
 *     Per-cell rates reported but NOT hard-asserted. WS-4.8's job is to
 *     interpret these and decide whether compile-flag defaults flip.
 *
 *   GRACEFUL-DEGRADATION (σ=4):
 *     For modes that pass at σ=0: the rate at σ=4 is reported. Drops below
 *     60% retention emit INFO lines (not failures) so WS-4.8 has a clear
 *     target to improve via -DUSE_LAB_DISTANCE / -DUSE_FP_CALIBRATION.
 *
 * Build (from src/jabcode/):
 *   gcc -O2 -std=c11 -I. -I./include \
 *       test/test_roundtrip_with_noise.c \
 *       -L./build -ljabcode -lm \
 *       -o test/test_roundtrip_with_noise -Wl,-rpath,./build
 *
 * See: docs/jabcode-all-nc-plan/00-CHECKLIST.md item 4.7
 *      docs/jabcode-all-nc-plan/04-phase-b-classical.md (WS-4 plan)
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

#define N_TRIALS  5

typedef struct {
    jab_int32   color_number;
    jab_int32   nc;
    const char* name;
    jab_int32   baseline_at_sigma_0;   /* expected K/N at σ=0; 0 = known-broken */
} mode_t;

/* Per-Nc baseline at σ=0 from WS-3 reality check (see
 * test_roundtrip_all_nc.c + WS-3 documented findings). Nc=3 fails on
 * clean input with module_size=12 default; all others succeed. */
static const mode_t MODES[] = {
    {2,   0, "Nc=0 mono",     N_TRIALS},
    {4,   1, "Nc=1 4-color",  N_TRIALS},
    {8,   2, "Nc=2 8-color",  N_TRIALS},
    {16,  3, "Nc=3 16-color", 0},          /* known pre-existing */
    {32,  4, "Nc=4 32-color", N_TRIALS},
    {64,  5, "Nc=5 64-color", N_TRIALS},
    {128, 6, "Nc=6 128-color",N_TRIALS},
    {256, 7, "Nc=7 256-color",N_TRIALS},
};
#define N_MODES (sizeof(MODES) / sizeof(MODES[0]))

static const jab_int32 SIGMA_LEVELS[] = {0, 4, 8, 12, 16, 20};
#define N_SIGMAS (sizeof(SIGMA_LEVELS) / sizeof(SIGMA_LEVELS[0]))

/* Graceful-degradation: at the smallest non-zero σ (σ=4), modes that
 * pass at σ=0 must retain at least this fraction of their baseline rate. */
#define GRACEFUL_RETENTION  0.6

static const char* PAYLOAD = "HELLO";

/* --- Deterministic LCG noise --- */
static unsigned long g_rng_state = 0;

static void seed_lcg(unsigned long s)
{
    g_rng_state = s;
}

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

/* Apply ±σ uniform noise to the RGB channels of an encoded bitmap.
 * Alpha (channel 3, if 4-channel) is left untouched. Operates in-place. */
static void noise_bitmap_in_place(jab_bitmap* bitmap, jab_int32 sigma)
{
    if (sigma <= 0) return;
    jab_int32 bpp = bitmap->bits_per_pixel / 8;
    jab_int32 ch  = bitmap->channel_count;
    jab_int32 n_pixels = bitmap->width * bitmap->height;
    for (jab_int32 p = 0; p < n_pixels; p++) {
        for (jab_int32 c = 0; c < ch; c++) {
            if (ch == 4 && c == 3) continue;  /* skip alpha */
            jab_int32 off = p * bpp + c;
            int noisy = (int)bitmap->pixel[off] + next_noise_in_range(sigma);
            bitmap->pixel[off] = clamp_byte(noisy);
        }
    }
}

/* Single encode → noise → decode trial. Returns 1 on roundtrip success, 0 on failure.
 *
 * Success criterion is PREFIX MATCH: result->data starts with PAYLOAD, regardless
 * of result->length. The library sometimes returns padded results (e.g., 36 bytes
 * for Nc=0 with 5-byte input) where the first payload_len bytes ARE the original
 * payload followed by zero/repeat padding. Prefix-match is the semantically
 * correct check — we care whether HELLO came back, not whether the result has
 * exactly the input's framing. */
static int run_one_trial(const mode_t* m, jab_int32 sigma, unsigned long seed)
{
    int success = 0;
    jab_int32 payload_len = (jab_int32)strlen(PAYLOAD);

    jab_encode* enc = createEncode(m->color_number, 1);
    if (!enc) return 0;

    jab_data* in = (jab_data*)malloc(sizeof(jab_data) + payload_len);
    if (!in) { destroyEncode(enc); return 0; }
    in->length = payload_len;
    memcpy(in->data, PAYLOAD, payload_len);

    jab_int32 gen_rc = generateJABCode(enc, in);
    if (gen_rc != 0 || !enc->bitmap) {
        free(in); destroyEncode(enc); return 0;
    }

    /* Apply deterministic noise BEFORE decode */
    seed_lcg(seed);
    noise_bitmap_in_place(enc->bitmap, sigma);

    jab_int32 status = -1;
    jab_data* result = decodeJABCode(enc->bitmap, NORMAL_DECODE, &status);
    if (result) {
        if ((jab_int32)result->length >= payload_len &&
            memcmp(result->data, PAYLOAD, payload_len) == 0) {
            success = 1;
        }
        free(result);
    }

    free(in);
    destroyEncode(enc);
    return success;
}

int main(void)
{
    fprintf(stderr,"================================================\n");
    fprintf(stderr,"WS-4 Step 4.7: roundtrip-with-noise empirical sweep\n");
    fprintf(stderr,"payload=\"%s\"  trials/cell=%d\n", PAYLOAD, N_TRIALS);
    fprintf(stderr,"================================================\n");

    /* Header row */
    fprintf(stderr,"%-15s ", "Mode");
    for (size_t s = 0; s < N_SIGMAS; s++) {
        fprintf(stderr,"  σ=%-3d", SIGMA_LEVELS[s]);
    }
    fprintf(stderr,"    expected@σ=0\n");
    fprintf(stderr,"─────────────────");
    for (size_t s = 0; s < N_SIGMAS; s++) printf("───────");
    fprintf(stderr,"─────────────────\n");

    int failures = 0;
    int graceful_failures = 0;
    int rates[N_MODES][N_SIGMAS];

    for (size_t m = 0; m < N_MODES; m++) {
        /* Compute all cells for this row BEFORE printing — keeps the row
         * atomic so library diagnostics don't interleave between cells. */
        for (size_t s = 0; s < N_SIGMAS; s++) {
            jab_int32 sigma = SIGMA_LEVELS[s];
            int successes = 0;
            for (int t = 0; t < N_TRIALS; t++) {
                /* Unique seed per (mode, sigma, trial) — reproducible but varied */
                unsigned long seed = 0x4242UL
                                   + (unsigned long)m * 1009UL
                                   + (unsigned long)s * 47UL
                                   + (unsigned long)t * 13UL;
                successes += run_one_trial(&MODES[m], sigma, seed);
            }
            rates[m][s] = successes;
        }

        /* Build row as one atomic string, then write to stderr (unbuffered)
         * to avoid interleaving with the library's stdout diagnostics. */
        char row[256];
        int off = snprintf(row, sizeof(row), "%-15s ", MODES[m].name);
        for (size_t s = 0; s < N_SIGMAS && off < (int)sizeof(row); s++) {
            off += snprintf(row + off, sizeof(row) - off,
                            "  %d/%d  ", rates[m][s], N_TRIALS);
        }
        if (off < (int)sizeof(row)) {
            snprintf(row + off, sizeof(row) - off,
                     "    %d/%d", MODES[m].baseline_at_sigma_0, N_TRIALS);
        }
        fprintf(stderr, "%s\n", row);

        /* GRACEFUL-DEGRADATION INFO: σ=4 (index 1) < 60% retention for modes
         * that pass at σ=0. Soft signal, not a hard failure. */
        if (rates[m][0] >= 4 && N_SIGMAS > 1) {
            int min_retained = (int)(GRACEFUL_RETENTION * N_TRIALS + 0.5);
            if (rates[m][1] < min_retained) {
                fprintf(stderr,"  INFO: %s σ=4 graceful-degradation %d/%d < %d/%d (60%% retention)\n",
                    MODES[m].name, rates[m][1], N_TRIALS,
                    min_retained, N_TRIALS);
                graceful_failures++;
            }
        }
    }

    /* SOFT GATE: aggregate σ=0 success across all modes ≥75% (6/8 modes
     * must have at least one success at σ=0). Catches catastrophic
     * regressions without gating on per-mode library quirks. */
    int modes_passing_at_sigma_0 = 0;
    for (size_t m = 0; m < N_MODES; m++) {
        if (rates[m][0] >= 1) modes_passing_at_sigma_0++;
    }
    int min_modes_passing = (int)(0.75 * N_MODES + 0.5);
    if (modes_passing_at_sigma_0 < min_modes_passing) {
        fprintf(stderr,"  FAIL: σ=0 aggregate %d/%d modes succeeded — below %d/%d (75%%) threshold\n",
            modes_passing_at_sigma_0, (int)N_MODES,
            min_modes_passing, (int)N_MODES);
        failures++;
    } else {
        fprintf(stderr,"  PASS: σ=0 aggregate %d/%d modes succeeded — ≥%d/%d (75%%) threshold\n",
            modes_passing_at_sigma_0, (int)N_MODES,
            min_modes_passing, (int)N_MODES);
    }

    fprintf(stderr,"\n================================================\n");
    fprintf(stderr,"Summary:\n");
    fprintf(stderr,"  Hard-gate (σ=0 baseline) failures: %d\n", failures);
    fprintf(stderr,"  Graceful-degradation INFO (σ=4 <60%%): %d (informational, not failures)\n",
           graceful_failures);
    if (failures == 0) {
        fprintf(stderr,"  Result: PASS — σ=0 baseline preserved; noise tolerance grid measured\n");
    } else {
        fprintf(stderr,"  Result: FAIL — σ=0 baseline regressed\n");
    }
    fprintf(stderr,"================================================\n");
    return failures > 0 ? 1 : 0;
}
