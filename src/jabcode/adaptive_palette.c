/**
 * libjabcode - JABCode Encoding/Decoding Library
 *
 * Copyright 2016 by Fraunhofer SIT. All rights reserved.
 * See LICENSE file for full terms of use and distribution.
 *
 * Contact: Huajian Liu <liu@sit.fraunhofer.de>
 *          Waldemar Berchtold <waldemar.berchtold@sit.fraunhofer.de>
 *
 * @file adaptive_palette.c
 * @brief Adaptive palette calibration for digital decoding optimization
 */

#include <stdlib.h>
#include <string.h>
#include <math.h>
#include "adaptive_palette.h"

#define MIN_SAMPLES_FOR_CORRECTION 5    // Minimum observations per color for correction
#define MIN_CONFIDENCE_THRESHOLD 0.6    // Minimum confidence to use observation
#define MAX_CORRECTION_DELTA_E 10.0     // Maximum ΔE correction to apply (prevent over-correction)
#define MAX_DIFF_CAPACITY 1000          // Maximum diff array capacity per palette color (prevents unbounded growth)

/**
 * @brief Collect color observations during decoding
 */
void collectColorObservation(
    jab_rgb_color observed_rgb,
    jab_byte palette_index,
    jab_float confidence,
    jab_color_observation* observations,
    jab_int32* observation_count,
    jab_int32 max_observations
)
{
    // Only collect high-confidence observations to avoid noise
    if (confidence < MIN_CONFIDENCE_THRESHOLD) {
        return;
    }

    // Check bounds BEFORE accessing array
    if (!observations || !observation_count || *observation_count >= max_observations) {
        return;
    }

    jab_color_observation* obs = &observations[*observation_count];
    obs->observed = observed_rgb;
    obs->palette_index = palette_index;
    obs->confidence = confidence;

    (*observation_count)++;
}

/**
 * @brief Compute median of LAB color samples for robust shift estimation
 */
static jab_lab_color computeMedianLAB(jab_lab_color* samples, jab_int32 count)
{
    if (count == 0) {
        jab_lab_color zero = {0.0, 0.0, 0.0};
        return zero;
    }

    // Sort L, a, b separately and take median
    jab_float* L_vals = (jab_float*)malloc(count * sizeof(jab_float));
    jab_float* a_vals = (jab_float*)malloc(count * sizeof(jab_float));
    jab_float* b_vals = (jab_float*)malloc(count * sizeof(jab_float));

    if (!L_vals || !a_vals || !b_vals) {
        free(L_vals);
        free(a_vals);
        free(b_vals);
        jab_lab_color zero = {0.0, 0.0, 0.0};
        return zero;
    }

    for (jab_int32 i = 0; i < count; i++) {
        L_vals[i] = samples[i].L;
        a_vals[i] = samples[i].a;
        b_vals[i] = samples[i].b;
    }

    // Simple bubble sort (sufficient for small counts)
    for (jab_int32 i = 0; i < count - 1; i++) {
        for (jab_int32 j = 0; j < count - i - 1; j++) {
            if (L_vals[j] > L_vals[j + 1]) {
                jab_float temp = L_vals[j];
                L_vals[j] = L_vals[j + 1];
                L_vals[j + 1] = temp;
            }
            if (a_vals[j] > a_vals[j + 1]) {
                jab_float temp = a_vals[j];
                a_vals[j] = a_vals[j + 1];
                a_vals[j + 1] = temp;
            }
            if (b_vals[j] > b_vals[j + 1]) {
                jab_float temp = b_vals[j];
                b_vals[j] = b_vals[j + 1];
                b_vals[j + 1] = temp;
            }
        }
    }

    jab_lab_color median;
    jab_int32 mid = count / 2;
    if (count % 2 == 0) {
        median.L = (L_vals[mid - 1] + L_vals[mid]) / 2.0;
        median.a = (a_vals[mid - 1] + a_vals[mid]) / 2.0;
        median.b = (b_vals[mid - 1] + b_vals[mid]) / 2.0;
    } else {
        median.L = L_vals[mid];
        median.a = a_vals[mid];
        median.b = b_vals[mid];
    }

    free(L_vals);
    free(a_vals);
    free(b_vals);

    return median;
}

/**
 * @brief Analyze palette distribution and compute corrections
 */
jab_int32 analyzePaletteDistribution(
    jab_color_observation* observations,
    jab_int32 observation_count,
    jab_byte* expected_palette,
    jab_int32 palette_size,
    jab_palette_correction* corrections
)
{
    if (!observations || !expected_palette || !corrections) {
        return JAB_FAILURE;
    }

    // Initialize corrections
    for (jab_int32 i = 0; i < palette_size; i++) {
        corrections[i].shift.L = 0.0;
        corrections[i].shift.a = 0.0;
        corrections[i].shift.b = 0.0;
        corrections[i].confidence = 0.0;
        corrections[i].sample_count = 0;
    }

    // Allocate temporary storage for LAB differences per palette color
    jab_lab_color** lab_diffs = (jab_lab_color**)malloc(palette_size * sizeof(jab_lab_color*));
    jab_int32* diff_counts = (jab_int32*)calloc(palette_size, sizeof(jab_int32));
    jab_int32* diff_capacities = (jab_int32*)malloc(palette_size * sizeof(jab_int32));

    if (!lab_diffs || !diff_counts || !diff_capacities) {
        free(lab_diffs);
        free(diff_counts);
        free(diff_capacities);
        return JAB_FAILURE;
    }

    for (jab_int32 i = 0; i < palette_size; i++) {
        diff_capacities[i] = 100;
        lab_diffs[i] = (jab_lab_color*)malloc(diff_capacities[i] * sizeof(jab_lab_color));
        if (!lab_diffs[i]) {
            // Cleanup already allocated arrays
            for (jab_int32 j = 0; j < i; j++) {
                free(lab_diffs[j]);
            }
            free(lab_diffs);
            free(diff_counts);
            free(diff_capacities);
            return JAB_FAILURE;
        }
    }

    // Convert expected palette to LAB
    jab_lab_color* expected_lab = (jab_lab_color*)malloc(palette_size * sizeof(jab_lab_color));
    if (!expected_lab) {
        for (jab_int32 i = 0; i < palette_size; i++) {
            free(lab_diffs[i]);
        }
        free(lab_diffs);
        free(diff_counts);
        free(diff_capacities);
        return JAB_FAILURE;
    }
    for (jab_int32 i = 0; i < palette_size; i++) {
        jab_rgb_color rgb = {
            expected_palette[i * 3 + 0],
            expected_palette[i * 3 + 1],
            expected_palette[i * 3 + 2]
        };
        expected_lab[i] = rgb_to_lab(rgb);
    }

    // Collect LAB differences for each palette color
    for (jab_int32 i = 0; i < observation_count; i++) {
        jab_color_observation* obs = &observations[i];
        jab_byte idx = obs->palette_index;

        if (idx >= palette_size) {
            continue;
        }

        // Convert observed color to LAB
        jab_lab_color observed_lab = rgb_to_lab(obs->observed);

        // Compute difference from expected
        jab_lab_color diff;
        diff.L = observed_lab.L - expected_lab[idx].L;
        diff.a = observed_lab.a - expected_lab[idx].a;
        diff.b = observed_lab.b - expected_lab[idx].b;

        // Store difference if not too extreme (outlier rejection)
        jab_float delta_e = sqrt(diff.L * diff.L + diff.a * diff.a + diff.b * diff.b);
        if (delta_e < MAX_CORRECTION_DELTA_E) {
            if (diff_counts[idx] >= diff_capacities[idx]) {
                // Cap capacity to prevent unbounded exponential growth
                if (diff_capacities[idx] >= MAX_DIFF_CAPACITY) {
                    // Already at max capacity - skip this observation
                    continue;
                }
                
                // Double capacity but cap at MAX_DIFF_CAPACITY
                jab_int32 new_capacity = diff_capacities[idx] * 2;
                if (new_capacity > MAX_DIFF_CAPACITY) {
                    new_capacity = MAX_DIFF_CAPACITY;
                }
                
                // Use temporary pointer to avoid losing original memory on realloc failure
                jab_lab_color* temp = (jab_lab_color*)realloc(
                    lab_diffs[idx],
                    new_capacity * sizeof(jab_lab_color)
                );
                if (!temp) {
                    // Realloc failed - skip this observation but keep existing data
                    continue;
                }
                lab_diffs[idx] = temp;
                diff_capacities[idx] = new_capacity;
            }
            lab_diffs[idx][diff_counts[idx]++] = diff;
        }
    }

    // Compute corrections using median of differences (robust to outliers)
    for (jab_int32 i = 0; i < palette_size; i++) {
        if (diff_counts[i] >= MIN_SAMPLES_FOR_CORRECTION) {
            corrections[i].shift = computeMedianLAB(lab_diffs[i], diff_counts[i]);
            corrections[i].sample_count = diff_counts[i];
            // Confidence based on sample count and consistency
            corrections[i].confidence = fmin(1.0, diff_counts[i] / 20.0);
        }
    }

    // Cleanup
    for (jab_int32 i = 0; i < palette_size; i++) {
        free(lab_diffs[i]);
    }
    free(lab_diffs);
    free(diff_counts);
    free(diff_capacities);
    free(expected_lab);

    return JAB_SUCCESS;
}

/**
 * @brief Compute correction threshold
 */
jab_float computeCorrectionThreshold(
    jab_palette_correction* corrections,
    jab_int32 palette_size
)
{
    // Compute median confidence
    jab_float* confidences = (jab_float*)malloc(palette_size * sizeof(jab_float));
    if (!confidences) {
        return 0.5; // Default threshold on allocation failure
    }
    for (jab_int32 i = 0; i < palette_size; i++) {
        confidences[i] = corrections[i].confidence;
    }

    // Sort
    for (jab_int32 i = 0; i < palette_size - 1; i++) {
        for (jab_int32 j = 0; j < palette_size - i - 1; j++) {
            if (confidences[j] > confidences[j + 1]) {
                jab_float temp = confidences[j];
                confidences[j] = confidences[j + 1];
                confidences[j + 1] = temp;
            }
        }
    }

    jab_float threshold = confidences[palette_size / 2];
    free(confidences);

    // Minimum threshold
    return fmax(threshold, 0.3);
}

/**
 * @brief Apply palette corrections
 */
void applyPaletteCorrections(
    jab_byte* original_palette,
    jab_palette_correction* corrections,
    jab_int32 palette_size,
    jab_byte* corrected_palette
)
{
    if (!original_palette || !corrections || !corrected_palette || palette_size <= 0 || palette_size > 256) {
        return;
    }
    
    jab_float threshold = computeCorrectionThreshold(corrections, palette_size);

    for (jab_int32 i = 0; i < palette_size; i++) {
        jab_rgb_color original = {
            original_palette[i * 3 + 0],
            original_palette[i * 3 + 1],
            original_palette[i * 3 + 2]
        };

        jab_rgb_color corrected = original;

        // Apply correction if confidence exceeds threshold
        if (corrections[i].confidence >= threshold && corrections[i].sample_count >= MIN_SAMPLES_FOR_CORRECTION) {
            // Validate shift magnitudes to prevent extreme corrections
            jab_float shift_magnitude = sqrt(
                corrections[i].shift.L * corrections[i].shift.L +
                corrections[i].shift.a * corrections[i].shift.a +
                corrections[i].shift.b * corrections[i].shift.b
            );
            
            if (shift_magnitude > 50.0 || isnan(shift_magnitude) || isinf(shift_magnitude)) {
                // Skip this correction - shift is too extreme or invalid
                corrected_palette[i * 3 + 0] = original.r;
                corrected_palette[i * 3 + 1] = original.g;
                corrected_palette[i * 3 + 2] = original.b;
                continue;
            }
            
            // Convert to LAB
            jab_lab_color lab = rgb_to_lab(original);

            // Apply shift with bounds checking
            lab.L += corrections[i].shift.L;
            lab.a += corrections[i].shift.a;
            lab.b += corrections[i].shift.b;
            
            // Clamp after shift application
            lab.L = fmax(0.0, fmin(100.0, lab.L));
            lab.a = fmax(-128.0, fmin(127.0, lab.a));
            lab.b = fmax(-128.0, fmin(127.0, lab.b));

            // Convert back to RGB
            corrected = lab_to_rgb(lab);
        }

        corrected_palette[i * 3 + 0] = corrected.r;
        corrected_palette[i * 3 + 1] = corrected.g;
        corrected_palette[i * 3 + 2] = corrected.b;
    }
}
