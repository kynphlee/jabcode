/**
 * Adaptive Palette System for JABCode High-Color Mode Camera Decoding
 * 
 * Enables runtime palette learning from captured images to handle
 * camera color shift in 16+ color modes.
 * 
 * Features:
 * - LAB color space for perceptual matching
 * - K-d tree for O(log n) color lookup
 * - Color transform learning from finder patterns
 * - Integration with static calibration profiles
 */

#ifndef ADAPTIVE_PALETTE_H
#define ADAPTIVE_PALETTE_H

#include "jabcode.h"
#include "lab_color.h"
#include "kdtree_color.h"

#define MAX_PALETTE_SIZE 256
#define MAX_REFERENCE_SAMPLES 8

/**
 * Color transform for mapping observed colors to expected colors
 */
typedef struct {
    jab_float brightness_offset;    // L* channel offset
    jab_float saturation_scale;     // Chroma scaling factor
    jab_float hue_rotation;         // Hue angle rotation (degrees)
    jab_float gamma_correction;     // Gamma correction factor
    // Per-channel calibration (RGB space)
    jab_float channel_scale[3];     // Per-channel brightness scale (R, G, B)
    jab_float channel_gamma[3];     // Per-channel gamma correction
    jab_boolean use_per_channel;    // Whether to use per-channel calibration
    jab_boolean is_valid;           // Whether transform has been computed
} jab_color_transform;

/**
 * Reference color sample (from finder patterns)
 */
typedef struct {
    jab_rgb_color expected;         // Expected color from spec
    jab_rgb_color observed;         // Observed color from capture
    jab_lab_color expected_lab;     // Expected in LAB
    jab_lab_color observed_lab;     // Observed in LAB
    jab_float confidence;           // Sample confidence (0-1)
} jab_color_sample;

/**
 * Adaptive palette state
 */
typedef struct {
    jab_int32 color_count;                           // Number of colors in palette
    jab_rgb_color expected_palette[MAX_PALETTE_SIZE]; // Expected RGB palette
    jab_lab_color expected_lab[MAX_PALETTE_SIZE];    // Expected palette in LAB
    jab_rgb_color adapted_palette[MAX_PALETTE_SIZE]; // Adapted palette for matching
    jab_lab_color adapted_lab[MAX_PALETTE_SIZE];     // Adapted palette in LAB
    kdtree_color* lookup_tree;                       // K-d tree for fast lookup
    jab_color_transform transform;                   // Learned color transform
    jab_color_sample reference_samples[MAX_REFERENCE_SAMPLES]; // Reference samples
    jab_int32 sample_count;                          // Number of reference samples
    jab_boolean is_initialized;                      // Whether palette is ready
} jab_adaptive_palette;

/**
 * Initialize adaptive palette from encoder default palette
 * 
 * @param palette Adaptive palette to initialize
 * @param color_count Number of colors (4, 8, 16, 32, 64, 128, 256)
 * @return JAB_SUCCESS or JAB_FAILURE
 */
jab_boolean adaptive_palette_init(jab_adaptive_palette* palette, jab_int32 color_count);

/**
 * Add reference color sample from finder pattern
 * 
 * @param palette Adaptive palette
 * @param expected Expected RGB color
 * @param observed Observed RGB color from capture
 * @param confidence Sample confidence (0-1)
 */
void adaptive_palette_add_sample(jab_adaptive_palette* palette, 
                                  jab_rgb_color expected,
                                  jab_rgb_color observed,
                                  jab_float confidence);

/**
 * Learn color transform from collected samples
 * 
 * @param palette Adaptive palette
 * @return JAB_SUCCESS if transform computed, JAB_FAILURE if not enough samples
 */
jab_boolean adaptive_palette_learn_transform(jab_adaptive_palette* palette);

/**
 * Apply learned transform to adapt palette for matching
 * 
 * @param palette Adaptive palette
 */
void adaptive_palette_apply_transform(jab_adaptive_palette* palette);

/**
 * Find nearest color index for observed RGB color
 * 
 * Uses K-d tree in LAB space for perceptual matching
 * 
 * @param palette Adaptive palette
 * @param observed Observed RGB color
 * @return Palette index (0 to color_count-1)
 */
jab_byte adaptive_palette_match(jab_adaptive_palette* palette, jab_rgb_color observed);

/**
 * Find nearest color with confidence score
 * 
 * @param palette Adaptive palette
 * @param observed Observed RGB color
 * @param confidence Output: confidence of match (0-1)
 * @return Palette index (0 to color_count-1)
 */
jab_byte adaptive_palette_match_with_confidence(jab_adaptive_palette* palette,
                                                  jab_rgb_color observed,
                                                  jab_float* confidence);

/**
 * Reset adaptive palette (clear samples and transform)
 * 
 * @param palette Adaptive palette
 */
void adaptive_palette_reset(jab_adaptive_palette* palette);

/**
 * Free adaptive palette resources
 * 
 * @param palette Adaptive palette
 */
void adaptive_palette_free(jab_adaptive_palette* palette);

/**
 * Get expected palette RGB values
 * 
 * @param palette Adaptive palette
 * @return Pointer to expected palette array
 */
jab_rgb_color* adaptive_palette_get_expected(jab_adaptive_palette* palette);

/**
 * Get adapted palette RGB values
 * 
 * @param palette Adaptive palette
 * @return Pointer to adapted palette array
 */
jab_rgb_color* adaptive_palette_get_adapted(jab_adaptive_palette* palette);

#endif // ADAPTIVE_PALETTE_H
