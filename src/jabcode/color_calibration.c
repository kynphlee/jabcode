#include "color_calibration.h"
#include <string.h>
#include <stdlib.h>
#include <stdio.h>

static jab_color_calibration g_calibration = {0};

static void parseColorMapping(const char* json_section, jab_byte* rgb_out);

jab_int32 jabLoadCalibrationFromJSON(const char* json_string) {
    if (!json_string) {
        return 0;
    }
    
    memset(&g_calibration, 0, sizeof(jab_color_calibration));
    
    g_calibration.standard_colors[0][0] = 0;   g_calibration.standard_colors[0][1] = 0;   g_calibration.standard_colors[0][2] = 0;     // Black
    g_calibration.standard_colors[1][0] = 255; g_calibration.standard_colors[1][1] = 255; g_calibration.standard_colors[1][2] = 255;   // White
    g_calibration.standard_colors[2][0] = 255; g_calibration.standard_colors[2][1] = 0;   g_calibration.standard_colors[2][2] = 0;     // Red
    g_calibration.standard_colors[3][0] = 0;   g_calibration.standard_colors[3][1] = 255; g_calibration.standard_colors[3][2] = 0;     // Green
    g_calibration.standard_colors[4][0] = 0;   g_calibration.standard_colors[4][1] = 0;   g_calibration.standard_colors[4][2] = 255;   // Blue
    g_calibration.standard_colors[5][0] = 255; g_calibration.standard_colors[5][1] = 255; g_calibration.standard_colors[5][2] = 0;     // Yellow
    g_calibration.standard_colors[6][0] = 0;   g_calibration.standard_colors[6][1] = 255; g_calibration.standard_colors[6][2] = 255;   // Cyan
    g_calibration.standard_colors[7][0] = 255; g_calibration.standard_colors[7][1] = 0;   g_calibration.standard_colors[7][2] = 255;   // Magenta
    
    const char* red_cal = strstr(json_string, "\"red\"");
    const char* green_cal = strstr(json_string, "\"green\"");
    const char* blue_cal = strstr(json_string, "\"blue\"");
    const char* white_cal = strstr(json_string, "\"white\"");
    const char* magenta_cal = strstr(json_string, "\"magenta\"");
    
    if (red_cal && green_cal && blue_cal && white_cal && magenta_cal) {
        parseColorMapping(red_cal, g_calibration.calibrated_colors[2]);
        parseColorMapping(green_cal, g_calibration.calibrated_colors[3]);
        parseColorMapping(blue_cal, g_calibration.calibrated_colors[4]);
        parseColorMapping(white_cal, g_calibration.calibrated_colors[1]);
        parseColorMapping(magenta_cal, g_calibration.calibrated_colors[7]);
        
        g_calibration.calibrated_colors[0][0] = 0;   
        g_calibration.calibrated_colors[0][1] = 0;   
        g_calibration.calibrated_colors[0][2] = 0;
        g_calibration.calibrated_colors[5][0] = 255; 
        g_calibration.calibrated_colors[5][1] = 255; 
        g_calibration.calibrated_colors[5][2] = 0;
        g_calibration.calibrated_colors[6][0] = 0;   
        g_calibration.calibrated_colors[6][1] = 255; 
        g_calibration.calibrated_colors[6][2] = 255;
        
        g_calibration.is_active = 1;
        return 1;
    }
    
    return 0;
}

static void parseColorMapping(const char* json_section, jab_byte* rgb_out) {
    const char* calibrated = strstr(json_section, "\"calibrated\"");
    if (calibrated) {
        calibrated = strchr(calibrated, '[');
        if (calibrated) {
            int r, g, b;
            if (sscanf(calibrated, "[%d,%d,%d]", &r, &g, &b) == 3) {
                rgb_out[0] = (jab_byte)r;
                rgb_out[1] = (jab_byte)g;
                rgb_out[2] = (jab_byte)b;
            }
        }
    }
}

void jabApplyCalibration(jab_encode* enc) {
    if (!enc || !g_calibration.is_active || enc->color_number > 8) {
        return;
    }
    
    for (jab_int32 i = 0; i < enc->color_number * 3; i += 3) {
        jab_byte r = enc->palette[i];
        jab_byte g = enc->palette[i + 1];
        jab_byte b = enc->palette[i + 2];
        
        for (jab_int32 j = 0; j < 8; j++) {
            if (r == g_calibration.standard_colors[j][0] &&
                g == g_calibration.standard_colors[j][1] &&
                b == g_calibration.standard_colors[j][2]) {
                
                enc->palette[i]     = g_calibration.calibrated_colors[j][0];
                enc->palette[i + 1] = g_calibration.calibrated_colors[j][1];
                enc->palette[i + 2] = g_calibration.calibrated_colors[j][2];
                break;
            }
        }
    }
}

void jabRemapColor(const jab_byte* rgb_in, jab_byte* rgb_out) {
    if (!g_calibration.is_active) {
        rgb_out[0] = rgb_in[0];
        rgb_out[1] = rgb_in[1];
        rgb_out[2] = rgb_in[2];
        return;
    }
    
    for (jab_int32 i = 0; i < 8; i++) {
        if (rgb_in[0] == g_calibration.standard_colors[i][0] &&
            rgb_in[1] == g_calibration.standard_colors[i][1] &&
            rgb_in[2] == g_calibration.standard_colors[i][2]) {
            
            rgb_out[0] = g_calibration.calibrated_colors[i][0];
            rgb_out[1] = g_calibration.calibrated_colors[i][1];
            rgb_out[2] = g_calibration.calibrated_colors[i][2];
            return;
        }
    }
    
    rgb_out[0] = rgb_in[0];
    rgb_out[1] = rgb_in[1];
    rgb_out[2] = rgb_in[2];
}

void jabClearCalibration() {
    memset(&g_calibration, 0, sizeof(jab_color_calibration));
}

jab_boolean jabHasCalibration() {
    return g_calibration.is_active;
}

/* WS-4 Step 4.4: populate calibration from observed RGB array.
 * The standard_colors[] table is fixed (K, W, R, G, B, Y, C, M canonical
 * RGB values). The calibrated_colors[] table is filled from the caller's
 * observations; for slots the caller did not observe, pass the standard
 * color so jabRemapColorInverse leaves that color unmapped. */
void jabCalibrateFromObservedRGB(const jab_byte observed[8][3]) {
    if (!observed) {
        return;
    }

    /* Standard colors are the canonical truth (same set jabLoadCalibrationFromJSON
     * installs). Set them unconditionally so callers don't need to populate them. */
    g_calibration.standard_colors[0][0] = 0;   g_calibration.standard_colors[0][1] = 0;   g_calibration.standard_colors[0][2] = 0;     /* Black */
    g_calibration.standard_colors[1][0] = 255; g_calibration.standard_colors[1][1] = 255; g_calibration.standard_colors[1][2] = 255;   /* White */
    g_calibration.standard_colors[2][0] = 255; g_calibration.standard_colors[2][1] = 0;   g_calibration.standard_colors[2][2] = 0;     /* Red */
    g_calibration.standard_colors[3][0] = 0;   g_calibration.standard_colors[3][1] = 255; g_calibration.standard_colors[3][2] = 0;     /* Green */
    g_calibration.standard_colors[4][0] = 0;   g_calibration.standard_colors[4][1] = 0;   g_calibration.standard_colors[4][2] = 255;   /* Blue */
    g_calibration.standard_colors[5][0] = 255; g_calibration.standard_colors[5][1] = 255; g_calibration.standard_colors[5][2] = 0;     /* Yellow */
    g_calibration.standard_colors[6][0] = 0;   g_calibration.standard_colors[6][1] = 255; g_calibration.standard_colors[6][2] = 255;   /* Cyan */
    g_calibration.standard_colors[7][0] = 255; g_calibration.standard_colors[7][1] = 0;   g_calibration.standard_colors[7][2] = 255;   /* Magenta */

    for (jab_int32 i = 0; i < 8; i++) {
        g_calibration.calibrated_colors[i][0] = observed[i][0];
        g_calibration.calibrated_colors[i][1] = observed[i][1];
        g_calibration.calibrated_colors[i][2] = observed[i][2];
    }

    g_calibration.is_active = 1;
}

/* WS-4 Step 4.4b: sample a single matrix pixel safely with bounds-check.
 * Writes (0,0,0) to rgb_out when out of bounds — caller can detect this
 * and fall back to canonical defaults. */
static jab_int32 sample_matrix_pixel(const jab_bitmap* matrix,
                                     jab_int32 x, jab_int32 y,
                                     jab_byte rgb_out[3])
{
    if (!matrix || !matrix->pixel) {
        rgb_out[0] = rgb_out[1] = rgb_out[2] = 0;
        return 0;
    }
    if (x < 0 || y < 0 || x >= matrix->width || y >= matrix->height) {
        rgb_out[0] = rgb_out[1] = rgb_out[2] = 0;
        return 0;
    }
    jab_int32 bpp = matrix->bits_per_pixel / 8;
    if (bpp < 3) {
        rgb_out[0] = rgb_out[1] = rgb_out[2] = 0;
        return 0;
    }
    jab_int32 offset = y * matrix->width * bpp + x * bpp;
    rgb_out[0] = matrix->pixel[offset + 0];
    rgb_out[1] = matrix->pixel[offset + 1];
    rgb_out[2] = matrix->pixel[offset + 2];
    return 1;
}

void jabBuildCalibrationFromFPCores(const jab_bitmap* matrix, jab_int32 color_number) {
    if (!matrix || matrix->width <= 0 || matrix->height <= 0) {
        return;
    }

    /* Start with canonical (no shift) for all 8 standard color slots.
     * This guarantees that any slot we don't observe stays identity. */
    jab_byte observed[8][3] = {
        {  0,   0,   0},   /* [0] K — Black */
        {255, 255, 255},   /* [1] W — White */
        {255,   0,   0},   /* [2] R — Red */
        {  0, 255,   0},   /* [3] G — Green */
        {  0,   0, 255},   /* [4] B — Blue */
        {255, 255,   0},   /* [5] Y — Yellow */
        {  0, 255, 255},   /* [6] C — Cyan */
        {255,   0, 255},   /* [7] M — Magenta */
    };

    /* FP0 center module is at (3, 3) — K-cored across all Nc.
     * The matrix is in module-coordinate space (one pixel per module after
     * perspective transform / synthetic setup), so direct (3,3) sample is
     * the FP0 core. */
    sample_matrix_pixel(matrix, 3, 3, observed[0]);

    /* FP2 (BR) Y-cored and FP3 (BL) C-cored for Nc≥2 (color_number ≥ 8). */
    if (color_number >= 8) {
        jab_int32 fp2_x = matrix->width  - 4;
        jab_int32 fp2_y = matrix->height - 4;
        jab_int32 fp3_x = 3;
        jab_int32 fp3_y = matrix->height - 4;
        sample_matrix_pixel(matrix, fp2_x, fp2_y, observed[5]); /* Y */
        sample_matrix_pixel(matrix, fp3_x, fp3_y, observed[6]); /* C */
    }

    jabCalibrateFromObservedRGB(observed);
}

/* WS-4 Step 4.4: inverse-direction remap (observed → standard).
 * Symmetric to jabRemapColor: exact-match lookup with pass-through for
 * unmatched inputs. Used by the decoder's pre-sample pass to normalize
 * camera observations against the encoder's palette geometry. */
void jabRemapColorInverse(const jab_byte* rgb_in, jab_byte* rgb_out) {
    if (!g_calibration.is_active) {
        rgb_out[0] = rgb_in[0];
        rgb_out[1] = rgb_in[1];
        rgb_out[2] = rgb_in[2];
        return;
    }

    for (jab_int32 i = 0; i < 8; i++) {
        if (rgb_in[0] == g_calibration.calibrated_colors[i][0] &&
            rgb_in[1] == g_calibration.calibrated_colors[i][1] &&
            rgb_in[2] == g_calibration.calibrated_colors[i][2]) {

            rgb_out[0] = g_calibration.standard_colors[i][0];
            rgb_out[1] = g_calibration.standard_colors[i][1];
            rgb_out[2] = g_calibration.standard_colors[i][2];
            return;
        }
    }

    /* No match — pass through unchanged */
    rgb_out[0] = rgb_in[0];
    rgb_out[1] = rgb_in[1];
    rgb_out[2] = rgb_in[2];
}
