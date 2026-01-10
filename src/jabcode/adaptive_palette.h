/**
 * libjabcode - JABCode Encoding/Decoding Library
 *
 * Copyright 2016 by Fraunhofer SIT. All rights reserved.
 * See LICENSE file for full terms of use and distribution.
 *
 * Contact: Huajian Liu <liu@sit.fraunhofer.de>
 *          Waldemar Berchtold <waldemar.berchtold@sit.fraunhofer.de>
 *
 * @file adaptive_palette.h
 * @brief Adaptive palette calibration for digital decoding optimization
 */

#ifndef ADAPTIVE_PALETTE_H
#define ADAPTIVE_PALETTE_H

#include "jabcode.h"
#include "lab_color.h"

/**
 * @brief Color observation for distribution analysis
 */
typedef struct {
    jab_rgb_color observed;     // Observed RGB color in image
    jab_byte palette_index;      // Decoded palette index
    jab_float confidence;        // Confidence of classification (inverse of second-best distance)
} jab_color_observation;

/**
 * @brief Palette correction data
 */
typedef struct {
    jab_lab_color shift;         // LAB color shift to apply
    jab_float confidence;        // Confidence in correction
    jab_int32 sample_count;      // Number of observations
} jab_palette_correction;

/**
 * @brief Analyze color distribution and compute palette corrections
 * 
 * Collects high-confidence module observations and computes systematic
 * color shifts for improved discrimination in digital images.
 *
 * @param observations Array of color observations
 * @param observation_count Number of observations
 * @param expected_palette Expected palette RGB colors
 * @param palette_size Number of colors in palette
 * @param corrections Output array of corrections per palette color
 * @return JAB_SUCCESS | JAB_FAILURE
 */
jab_int32 analyzePaletteDistribution(
    jab_color_observation* observations,
    jab_int32 observation_count,
    jab_byte* expected_palette,
    jab_int32 palette_size,
    jab_palette_correction* corrections
);

/**
 * @brief Apply palette corrections to improve color discrimination
 * 
 * Adjusts palette colors in LAB space based on observed distribution
 * to compensate for systematic encode/decode shifts.
 *
 * @param original_palette Original palette RGB colors (input)
 * @param corrections Computed corrections per color
 * @param palette_size Number of colors
 * @param corrected_palette Output corrected palette RGB colors
 */
void applyPaletteCorrections(
    jab_byte* original_palette,
    jab_palette_correction* corrections,
    jab_int32 palette_size,
    jab_byte* corrected_palette
);

/**
 * @brief Compute palette correction confidence threshold
 * 
 * Determines minimum confidence and sample count required for
 * applying corrections to avoid over-fitting to noise.
 *
 * @param corrections Array of corrections
 * @param palette_size Number of colors
 * @return Minimum confidence threshold (0.0-1.0)
 */
jab_float computeCorrectionThreshold(
    jab_palette_correction* corrections,
    jab_int32 palette_size
);

/**
 * @brief Collect color observations during decoding for analysis
 * 
 * Records observed colors with their decoded indices and confidence
 * for later distribution analysis. Should be called during first decode pass.
 *
 * @param observed_rgb Observed module RGB color
 * @param palette_index Decoded palette index
 * @param confidence Classification confidence (0.0-1.0)
 * @param observations Output observation array
 * @param observation_count Current count (updated)
 * @param max_observations Maximum capacity of array
 */
void collectColorObservation(
    jab_rgb_color observed_rgb,
    jab_byte palette_index,
    jab_float confidence,
    jab_color_observation* observations,
    jab_int32* observation_count,
    jab_int32 max_observations
);

#endif // ADAPTIVE_PALETTE_H
