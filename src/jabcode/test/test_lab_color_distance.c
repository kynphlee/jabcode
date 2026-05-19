/*
 * test_lab_color_distance.c — WS-4 Step 4.1
 *
 * Phase B-Classical noise robustness: TDD test comparing CIE Lab ΔE2000
 * against RGB Euclidean distance for color classification under synthetic
 * Gaussian-ish RGB noise.
 *
 * Method:
 *   1. Reference palette of 6 distinct colors (K, W, R, G, B, Y)
 *   2. For each palette entry, generate N_SAMPLES noisy variants by adding
 *      deterministic ±NOISE_SIGMA per-channel RGB perturbation
 *   3. Classify each noisy variant with two methods:
 *        a) RGB Euclidean distance (baseline)
 *        b) Lab ΔE2000 via delta_e_2000() in lab_color.h
 *   4. Count correct classifications per method
 *   5. PASS criterion: Lab classifier ≥ RGB classifier (no regression)
 *
 * This is TDD authoring (Step 4.1). When WS-4 Step 4.2 wires lab_color
 * into decodeModuleHD, this test guards the assumption that Lab
 * discrimination is at least as good as RGB on synthetic-uniform RGB noise.
 *
 * Build (from src/jabcode/):
 *   gcc -O2 -std=c11 -I. -I./include \
 *       test/test_lab_color_distance.c lab_color.c \
 *       -o test/test_lab_color_distance -lm
 *
 * See: docs/jabcode-all-nc-plan/00-CHECKLIST.md item 4.1
 *      docs/jabcode-all-nc-plan/04-phase-b-classical.md (WS-4 plan)
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"
#include "lab_color.h"

#define N_PALETTE     6
#define N_SAMPLES    20
#define NOISE_SIGMA  25
#define LCG_SEED     42UL

/* Reference palette: primary + secondary distinct colors */
static const jab_rgb_color REFERENCE_PALETTE[N_PALETTE] = {
    {  0,   0,   0},   /* 0: Black  */
    {255, 255, 255},   /* 1: White  */
    {255,   0,   0},   /* 2: Red    */
    {  0, 255,   0},   /* 3: Green  */
    {  0,   0, 255},   /* 4: Blue   */
    {255, 255,   0},   /* 5: Yellow */
};

/* Deterministic linear congruential generator — reproducible across runs */
static unsigned long g_rng_state = LCG_SEED;

static int next_noise_step(void)
{
    g_rng_state = g_rng_state * 1103515245UL + 12345UL;
    /* Map to [-NOISE_SIGMA, +NOISE_SIGMA] */
    return (int)((g_rng_state / 65536UL) % (2UL * NOISE_SIGMA + 1UL)) - NOISE_SIGMA;
}

static jab_byte clamp_byte(int v)
{
    if (v < 0)   return 0;
    if (v > 255) return 255;
    return (jab_byte)v;
}

/* Baseline: RGB Euclidean nearest-neighbor classifier */
static int classify_rgb(jab_rgb_color sample)
{
    int best = -1;
    double best_d2 = 1e30;
    for (int i = 0; i < N_PALETTE; i++) {
        double dr = (double)sample.r - (double)REFERENCE_PALETTE[i].r;
        double dg = (double)sample.g - (double)REFERENCE_PALETTE[i].g;
        double db = (double)sample.b - (double)REFERENCE_PALETTE[i].b;
        double d2 = dr*dr + dg*dg + db*db;
        if (d2 < best_d2) {
            best_d2 = d2;
            best = i;
        }
    }
    return best;
}

/* Candidate: Lab ΔE2000 nearest-neighbor classifier */
static int classify_lab(jab_rgb_color sample)
{
    jab_lab_color sample_lab = rgb_to_lab(sample);
    int best = -1;
    jab_float best_d = 1e30f;
    for (int i = 0; i < N_PALETTE; i++) {
        jab_lab_color ref_lab = rgb_to_lab(REFERENCE_PALETTE[i]);
        jab_float d = delta_e_2000(sample_lab, ref_lab);
        if (d < best_d) {
            best_d = d;
            best = i;
        }
    }
    return best;
}

int main(void)
{
    printf("================================================\n");
    printf("WS-4 Step 4.1: Lab ΔE2000 vs RGB Euclidean\n");
    printf("Palette=%d  Samples/color=%d  Noise σ=±%d\n",
           N_PALETTE, N_SAMPLES, NOISE_SIGMA);
    printf("================================================\n");

    int rgb_correct = 0;
    int lab_correct = 0;
    int total = 0;

    for (int p = 0; p < N_PALETTE; p++) {
        int rgb_ok = 0;
        int lab_ok = 0;
        for (int s = 0; s < N_SAMPLES; s++) {
            jab_rgb_color noisy;
            noisy.r = clamp_byte((int)REFERENCE_PALETTE[p].r + next_noise_step());
            noisy.g = clamp_byte((int)REFERENCE_PALETTE[p].g + next_noise_step());
            noisy.b = clamp_byte((int)REFERENCE_PALETTE[p].b + next_noise_step());

            int rgb_pred = classify_rgb(noisy);
            int lab_pred = classify_lab(noisy);

            if (rgb_pred == p) rgb_ok++;
            if (lab_pred == p) lab_ok++;
            total++;
        }
        rgb_correct += rgb_ok;
        lab_correct += lab_ok;

        printf("  Palette[%d] (%3d,%3d,%3d)  RGB=%2d/%d  LAB=%2d/%d\n",
               p,
               REFERENCE_PALETTE[p].r,
               REFERENCE_PALETTE[p].g,
               REFERENCE_PALETTE[p].b,
               rgb_ok, N_SAMPLES,
               lab_ok, N_SAMPLES);
    }

    double rgb_acc = 100.0 * (double)rgb_correct / (double)total;
    double lab_acc = 100.0 * (double)lab_correct / (double)total;

    printf("\n");
    printf("RGB Euclidean accuracy: %d/%d (%.1f%%)\n", rgb_correct, total, rgb_acc);
    printf("Lab ΔE2000  accuracy:   %d/%d (%.1f%%)\n", lab_correct, total, lab_acc);
    printf("================================================\n");

    /* TDD assertion: Lab classifier must be at least as good as RGB
     * on this synthetic uniform-RGB-noise model. A strict inequality
     * (Lab > RGB) is the expected theoretical outcome; we accept ties
     * to avoid false negatives on small sample sizes. What we REJECT
     * is a regression where Lab silently degrades below RGB. */
    if (lab_correct < rgb_correct) {
        printf("FAIL: Lab classifier WORSE than RGB "
               "(regression in lab_color.c — investigate delta_e_2000 or rgb_to_lab)\n");
        return 1;
    }
    printf("PASS: Lab classifier ≥ RGB classifier "
           "(lab_color.c discrimination preserved)\n");
    return 0;
}
