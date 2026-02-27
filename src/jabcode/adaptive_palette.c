/**
 * Adaptive Palette System Implementation
 * 
 * Enables camera-based decoding for 16+ color modes by learning
 * color transforms from reference samples (finder patterns).
 */

#include "adaptive_palette.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

/**
 * Generate default palette for given color count (from JABCode spec)
 * Matches encoder.c genColorPalette() exactly
 */
static void generate_default_palette(jab_rgb_color* palette, jab_int32 color_count)
{
    if (color_count == 4) {
        // 4-color: Black, Magenta, Yellow, Cyan
        palette[0] = (jab_rgb_color){0, 0, 0};       // Black
        palette[1] = (jab_rgb_color){255, 0, 255};   // Magenta
        palette[2] = (jab_rgb_color){255, 255, 0};   // Yellow
        palette[3] = (jab_rgb_color){0, 255, 255};   // Cyan
    } else if (color_count == 8) {
        // 8-color: Black, Blue, Green, Cyan, Red, Magenta, Yellow, White
        jab_rgb_color defaults[] = {
            {0, 0, 0}, {0, 0, 255}, {0, 255, 0}, {0, 255, 255},
            {255, 0, 0}, {255, 0, 255}, {255, 255, 0}, {255, 255, 255}
        };
        for (int i = 0; i < 8; i++) {
            palette[i] = defaults[i];
        }
    } else {
        // Algorithmic palette generation matching encoder.c genColorPalette()
        jab_int32 vr, vg, vb;
        switch(color_count) {
            case 16:  vr = 4; vg = 2; vb = 2; break;
            case 32:  vr = 4; vg = 4; vb = 2; break;
            case 64:  vr = 4; vg = 4; vb = 4; break;
            case 128: vr = 8; vg = 4; vb = 4; break;
            case 256: vr = 8; vg = 8; vb = 4; break;
            default: return;
        }
        
        // Calculate intervals (special case for 3 intervals = 85)
        jab_float dr = (vr - 1) == 3 ? 85.0f : 256.0f / (jab_float)(vr - 1);
        jab_float dg = (vg - 1) == 3 ? 85.0f : 256.0f / (jab_float)(vg - 1);
        jab_float db = (vb - 1) == 3 ? 85.0f : 256.0f / (jab_float)(vb - 1);
        
        jab_int32 idx = 0;
        for (jab_int32 i = 0; i < vr; i++) {
            jab_int32 r = (jab_int32)(dr * i);
            if (r > 255) r = 255;
            for (jab_int32 j = 0; j < vg; j++) {
                jab_int32 g = (jab_int32)(dg * j);
                if (g > 255) g = 255;
                for (jab_int32 k = 0; k < vb; k++) {
                    jab_int32 b = (jab_int32)(db * k);
                    if (b > 255) b = 255;
                    palette[idx].r = (jab_byte)r;
                    palette[idx].g = (jab_byte)g;
                    palette[idx].b = (jab_byte)b;
                    idx++;
                }
            }
        }
    }
}

/**
 * Convert entire palette to LAB space
 */
static void convert_palette_to_lab(jab_rgb_color* rgb, jab_lab_color* lab, jab_int32 count)
{
    for (jab_int32 i = 0; i < count; i++) {
        lab[i] = rgb_to_lab(rgb[i]);
    }
}

/**
 * Build K-d tree from adapted palette
 */
static kdtree_color* build_palette_tree(jab_rgb_color* palette, jab_int32 color_count)
{
    // Convert to flat byte array for kdtree_build
    jab_byte* flat_palette = (jab_byte*)malloc(color_count * 3);
    if (!flat_palette) return NULL;
    
    for (jab_int32 i = 0; i < color_count; i++) {
        flat_palette[i * 3 + 0] = palette[i].r;
        flat_palette[i * 3 + 1] = palette[i].g;
        flat_palette[i * 3 + 2] = palette[i].b;
    }
    
    kdtree_color* tree = kdtree_build(flat_palette, color_count, 0);
    free(flat_palette);
    
    return tree;
}

jab_boolean adaptive_palette_init(jab_adaptive_palette* palette, jab_int32 color_count)
{
    if (!palette || color_count < 2 || color_count > MAX_PALETTE_SIZE) {
        return JAB_FAILURE;
    }
    
    memset(palette, 0, sizeof(jab_adaptive_palette));
    palette->color_count = color_count;
    
    // Generate expected palette
    generate_default_palette(palette->expected_palette, color_count);
    
    // Convert to LAB
    convert_palette_to_lab(palette->expected_palette, palette->expected_lab, color_count);
    
    // Initially, adapted palette equals expected palette
    memcpy(palette->adapted_palette, palette->expected_palette, 
           color_count * sizeof(jab_rgb_color));
    memcpy(palette->adapted_lab, palette->expected_lab,
           color_count * sizeof(jab_lab_color));
    
    // Build K-d tree for fast lookup
    palette->lookup_tree = build_palette_tree(palette->adapted_palette, color_count);
    if (!palette->lookup_tree) {
        return JAB_FAILURE;
    }
    
    palette->is_initialized = JAB_SUCCESS;
    return JAB_SUCCESS;
}

void adaptive_palette_add_sample(jab_adaptive_palette* palette,
                                  jab_rgb_color expected,
                                  jab_rgb_color observed,
                                  jab_float confidence)
{
    if (!palette || palette->sample_count >= MAX_REFERENCE_SAMPLES) {
        return;
    }
    
    jab_color_sample* sample = &palette->reference_samples[palette->sample_count];
    sample->expected = expected;
    sample->observed = observed;
    sample->expected_lab = rgb_to_lab(expected);
    sample->observed_lab = rgb_to_lab(observed);
    sample->confidence = confidence;
    
    palette->sample_count++;
}

jab_boolean adaptive_palette_learn_transform(jab_adaptive_palette* palette)
{
    if (!palette || palette->sample_count < 2) {
        return JAB_FAILURE;
    }
    
    // Calculate color transform from samples
    // Simple linear model: observed = transform(expected)
    
    jab_float L_offset_sum = 0.0f;
    jab_float total_weight = 0.0f;
    jab_float chroma_ratio_sum = 0.0f;
    jab_int32 chroma_samples = 0;
    
    for (jab_int32 i = 0; i < palette->sample_count; i++) {
        jab_color_sample* s = &palette->reference_samples[i];
        jab_float weight = s->confidence;
        
        // Brightness offset (L* channel)
        L_offset_sum += (s->observed_lab.L - s->expected_lab.L) * weight;
        total_weight += weight;
        
        // Chroma (saturation) ratio
        jab_float expected_chroma = sqrt(s->expected_lab.a * s->expected_lab.a +
                                         s->expected_lab.b * s->expected_lab.b);
        jab_float observed_chroma = sqrt(s->observed_lab.a * s->observed_lab.a +
                                         s->observed_lab.b * s->observed_lab.b);
        
        if (expected_chroma > 5.0f) {  // Avoid division by small values
            chroma_ratio_sum += (observed_chroma / expected_chroma) * weight;
            chroma_samples++;
        }
    }
    
    if (total_weight > 0) {
        palette->transform.brightness_offset = L_offset_sum / total_weight;
    } else {
        palette->transform.brightness_offset = 0.0f;
    }
    
    if (chroma_samples > 0 && total_weight > 0) {
        palette->transform.saturation_scale = chroma_ratio_sum / total_weight;
    } else {
        palette->transform.saturation_scale = 1.0f;
    }
    
    // Hue rotation (simplified: average hue shift for chromatic colors)
    jab_float hue_shift_sum = 0.0f;
    jab_int32 hue_samples = 0;
    
    for (jab_int32 i = 0; i < palette->sample_count; i++) {
        jab_color_sample* s = &palette->reference_samples[i];
        
        jab_float expected_chroma = sqrt(s->expected_lab.a * s->expected_lab.a +
                                         s->expected_lab.b * s->expected_lab.b);
        jab_float observed_chroma = sqrt(s->observed_lab.a * s->observed_lab.a +
                                         s->observed_lab.b * s->observed_lab.b);
        
        if (expected_chroma > 10.0f && observed_chroma > 10.0f) {
            jab_float expected_hue = atan2(s->expected_lab.b, s->expected_lab.a);
            jab_float observed_hue = atan2(s->observed_lab.b, s->observed_lab.a);
            jab_float shift = (observed_hue - expected_hue) * 180.0f / M_PI;
            
            // Normalize to [-180, 180]
            while (shift > 180.0f) shift -= 360.0f;
            while (shift < -180.0f) shift += 360.0f;
            
            hue_shift_sum += shift * s->confidence;
            hue_samples++;
        }
    }
    
    if (hue_samples > 0) {
        palette->transform.hue_rotation = hue_shift_sum / hue_samples;
    } else {
        palette->transform.hue_rotation = 0.0f;
    }
    
    // Gamma correction (estimate from black/white samples)
    palette->transform.gamma_correction = 1.0f;  // Default, could be refined
    
    // Per-channel calibration (RGB space)
    // Calculate separate scale factors for R, G, B channels
    jab_float channel_sum_expected[3] = {0, 0, 0};
    jab_float channel_sum_observed[3] = {0, 0, 0};
    jab_int32 channel_samples[3] = {0, 0, 0};
    
    for (jab_int32 i = 0; i < palette->sample_count; i++) {
        jab_color_sample* s = &palette->reference_samples[i];
        
        // Only use samples where channel has significant value
        if (s->expected.r > 20) {
            channel_sum_expected[0] += s->expected.r * s->confidence;
            channel_sum_observed[0] += s->observed.r * s->confidence;
            channel_samples[0]++;
        }
        if (s->expected.g > 20) {
            channel_sum_expected[1] += s->expected.g * s->confidence;
            channel_sum_observed[1] += s->observed.g * s->confidence;
            channel_samples[1]++;
        }
        if (s->expected.b > 20) {
            channel_sum_expected[2] += s->expected.b * s->confidence;
            channel_sum_observed[2] += s->observed.b * s->confidence;
            channel_samples[2]++;
        }
    }
    
    // Calculate per-channel scale factors
    for (int c = 0; c < 3; c++) {
        if (channel_samples[c] > 0 && channel_sum_expected[c] > 0) {
            palette->transform.channel_scale[c] = channel_sum_observed[c] / channel_sum_expected[c];
        } else {
            palette->transform.channel_scale[c] = 1.0f;
        }
        palette->transform.channel_gamma[c] = 1.0f;  // Could be refined with more samples
    }
    
    // Enable per-channel calibration if we have enough channel diversity
    palette->transform.use_per_channel = (channel_samples[0] > 0 && 
                                           channel_samples[1] > 0 && 
                                           channel_samples[2] > 0);
    
    palette->transform.is_valid = JAB_SUCCESS;
    return JAB_SUCCESS;
}

void adaptive_palette_apply_transform(jab_adaptive_palette* palette)
{
    if (!palette || !palette->transform.is_valid) {
        return;
    }
    
    // Apply inverse transform to expected palette to get adapted palette
    // adapted = expected adjusted for camera color shift
    
    for (jab_int32 i = 0; i < palette->color_count; i++) {
        jab_lab_color expected = palette->expected_lab[i];
        jab_lab_color adapted;
        
        // Apply brightness offset
        adapted.L = expected.L + palette->transform.brightness_offset;
        adapted.L = fmax(0.0f, fmin(100.0f, adapted.L));
        
        // Apply saturation scaling
        jab_float chroma = sqrt(expected.a * expected.a + expected.b * expected.b);
        if (chroma > 0.001f) {
            jab_float scaled_chroma = chroma * palette->transform.saturation_scale;
            jab_float hue = atan2(expected.b, expected.a);
            
            // Apply hue rotation
            hue += palette->transform.hue_rotation * M_PI / 180.0f;
            
            adapted.a = scaled_chroma * cos(hue);
            adapted.b = scaled_chroma * sin(hue);
        } else {
            adapted.a = expected.a;
            adapted.b = expected.b;
        }
        
        palette->adapted_lab[i] = adapted;
        palette->adapted_palette[i] = lab_to_rgb(adapted);
    }
    
    // Rebuild K-d tree with adapted palette
    if (palette->lookup_tree) {
        kdtree_free(palette->lookup_tree);
    }
    palette->lookup_tree = build_palette_tree(palette->adapted_palette, palette->color_count);
}

jab_byte adaptive_palette_match(jab_adaptive_palette* palette, jab_rgb_color observed)
{
    if (!palette || !palette->lookup_tree) {
        return 0;
    }
    
    // Apply per-channel inverse correction to observed color
    jab_rgb_color corrected = observed;
    if (palette->transform.use_per_channel && palette->transform.is_valid) {
        // Inverse transform: divide by scale to undo camera color shift
        if (palette->transform.channel_scale[0] > 0.01f) {
            jab_float r = observed.r / palette->transform.channel_scale[0];
            corrected.r = (jab_byte)fmax(0, fmin(255, r));
        }
        if (palette->transform.channel_scale[1] > 0.01f) {
            jab_float g = observed.g / palette->transform.channel_scale[1];
            corrected.g = (jab_byte)fmax(0, fmin(255, g));
        }
        if (palette->transform.channel_scale[2] > 0.01f) {
            jab_float b = observed.b / palette->transform.channel_scale[2];
            corrected.b = (jab_byte)fmax(0, fmin(255, b));
        }
    }
    
    jab_lab_color observed_lab = rgb_to_lab(corrected);
    return kdtree_nearest(palette->lookup_tree, observed_lab);
}

jab_byte adaptive_palette_match_with_confidence(jab_adaptive_palette* palette,
                                                  jab_rgb_color observed,
                                                  jab_float* confidence)
{
    if (!palette || !palette->lookup_tree) {
        if (confidence) *confidence = 0.0f;
        return 0;
    }
    
    jab_lab_color observed_lab = rgb_to_lab(observed);
    jab_byte best_index = kdtree_nearest(palette->lookup_tree, observed_lab);
    
    if (confidence) {
        // Calculate confidence based on distance to matched color
        jab_float dist = delta_e_76(observed_lab, palette->adapted_lab[best_index]);
        
        // Find distance to second-best match
        jab_float second_best_dist = 1e10f;
        for (jab_int32 i = 0; i < palette->color_count; i++) {
            if (i != best_index) {
                jab_float d = delta_e_76(observed_lab, palette->adapted_lab[i]);
                if (d < second_best_dist) {
                    second_best_dist = d;
                }
            }
        }
        
        // Confidence based on margin between best and second-best
        jab_float margin = second_best_dist - dist;
        if (margin > 20.0f) {
            *confidence = 1.0f;
        } else if (margin > 0.0f) {
            *confidence = margin / 20.0f;
        } else {
            *confidence = 0.0f;
        }
        
        // Also factor in absolute distance (closer = more confident)
        if (dist > 30.0f) {
            *confidence *= 0.5f;
        } else if (dist > 15.0f) {
            *confidence *= (45.0f - dist) / 30.0f;
        }
    }
    
    return best_index;
}

void adaptive_palette_reset(jab_adaptive_palette* palette)
{
    if (!palette) return;
    
    palette->sample_count = 0;
    palette->transform.is_valid = JAB_FAILURE;
    
    // Reset adapted palette to expected
    memcpy(palette->adapted_palette, palette->expected_palette,
           palette->color_count * sizeof(jab_rgb_color));
    memcpy(palette->adapted_lab, palette->expected_lab,
           palette->color_count * sizeof(jab_lab_color));
    
    // Rebuild K-d tree
    if (palette->lookup_tree) {
        kdtree_free(palette->lookup_tree);
    }
    palette->lookup_tree = build_palette_tree(palette->adapted_palette, palette->color_count);
}

void adaptive_palette_free(jab_adaptive_palette* palette)
{
    if (!palette) return;
    
    if (palette->lookup_tree) {
        kdtree_free(palette->lookup_tree);
        palette->lookup_tree = NULL;
    }
    
    palette->is_initialized = JAB_FAILURE;
}

jab_rgb_color* adaptive_palette_get_expected(jab_adaptive_palette* palette)
{
    return palette ? palette->expected_palette : NULL;
}

jab_rgb_color* adaptive_palette_get_adapted(jab_adaptive_palette* palette)
{
    return palette ? palette->adapted_palette : NULL;
}
