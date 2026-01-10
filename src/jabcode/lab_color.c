/**
 * libjabcode - JABCode Encoding/Decoding Library
 *
 * Copyright 2016 by Fraunhofer SIT. All rights reserved.
 * See LICENSE file for full terms of use and distribution.
 *
 * Contact: Huajian Liu <liu@sit.fraunhofer.de>
 *          Waldemar Berchtold <waldemar.berchtold@sit.fraunhofer.de>
 *
 * @file lab_color.c
 * @brief CIE LAB color space conversion and perceptual distance calculation
 */

#include <math.h>
#include "lab_color.h"

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// D65 standard illuminant reference white point
#define REF_X 95.047
#define REF_Y 100.000
#define REF_Z 108.883

// LAB conversion constants
#define LAB_EPSILON 0.008856  // (6/29)^3
#define LAB_KAPPA   903.3     // (29/3)^3

/**
 * @brief Apply gamma correction to linearize RGB value
 * @param channel RGB channel value [0, 1]
 * @return Linearized value
 */
static jab_float linearize_rgb_channel(jab_float channel)
{
    if (channel > 0.04045) {
        return pow((channel + 0.055) / 1.055, 2.4);
    } else {
        return channel / 12.92;
    }
}

/**
 * @brief Reverse gamma correction for RGB conversion
 * @param linear Linearized RGB value
 * @return Gamma-corrected value [0, 1]
 */
static jab_float delinearize_rgb_channel(jab_float linear)
{
    if (linear > 0.0031308) {
        return 1.055 * pow(linear, 1.0 / 2.4) - 0.055;
    } else {
        return 12.92 * linear;
    }
}

/**
 * @brief LAB f(t) function for XYZ to LAB conversion
 * @param t Input value
 * @return f(t) result
 */
static jab_float lab_f(jab_float t)
{
    if (t > LAB_EPSILON) {
        return pow(t, 1.0 / 3.0);
    } else {
        return (LAB_KAPPA * t + 16.0) / 116.0;
    }
}

/**
 * @brief Inverse LAB f(t) function for LAB to XYZ conversion
 * @param t Input value
 * @return f^-1(t) result
 */
static jab_float lab_f_inv(jab_float t)
{
    jab_float t3 = t * t * t;
    if (t3 > LAB_EPSILON) {
        return t3;
    } else {
        return (116.0 * t - 16.0) / LAB_KAPPA;
    }
}

/**
 * @brief Convert RGB to XYZ color space
 */
jab_xyz_color rgb_to_xyz(jab_rgb_color rgb)
{
    // Normalize RGB to [0, 1]
    jab_float r = rgb.r / 255.0;
    jab_float g = rgb.g / 255.0;
    jab_float b = rgb.b / 255.0;

    // Apply gamma correction (sRGB)
    r = linearize_rgb_channel(r);
    g = linearize_rgb_channel(g);
    b = linearize_rgb_channel(b);

    // Convert to XYZ using sRGB D65 matrix
    jab_xyz_color xyz;
    xyz.X = r * 0.4124564 + g * 0.3575761 + b * 0.1804375;
    xyz.Y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750;
    xyz.Z = r * 0.0193339 + g * 0.1191920 + b * 0.9503041;

    // Scale to D65 illuminant (0-100 range)
    xyz.X *= 100.0;
    xyz.Y *= 100.0;
    xyz.Z *= 100.0;

    return xyz;
}

/**
 * @brief Convert XYZ to LAB color space
 */
jab_lab_color xyz_to_lab(jab_xyz_color xyz)
{
    // Normalize by reference white
    jab_float xr = xyz.X / REF_X;
    jab_float yr = xyz.Y / REF_Y;
    jab_float zr = xyz.Z / REF_Z;

    // Apply LAB f function
    jab_float fx = lab_f(xr);
    jab_float fy = lab_f(yr);
    jab_float fz = lab_f(zr);

    // Calculate LAB values
    jab_lab_color lab;
    lab.L = 116.0 * fy - 16.0;
    lab.a = 500.0 * (fx - fy);
    lab.b = 200.0 * (fy - fz);

    return lab;
}

/**
 * @brief Convert RGB to LAB color space (combined)
 */
jab_lab_color rgb_to_lab(jab_rgb_color rgb)
{
    jab_xyz_color xyz = rgb_to_xyz(rgb);
    return xyz_to_lab(xyz);
}

/**
 * @brief Convert LAB to XYZ color space
 */
jab_xyz_color lab_to_xyz(jab_lab_color lab)
{
    // Clamp LAB inputs to valid ranges to prevent NaN/Inf
    jab_float L_clamped = fmax(0.0, fmin(100.0, lab.L));
    jab_float a_clamped = fmax(-128.0, fmin(127.0, lab.a));
    jab_float b_clamped = fmax(-128.0, fmin(127.0, lab.b));
    
    // Calculate intermediate values
    jab_float fy = (L_clamped + 16.0) / 116.0;
    jab_float fx = a_clamped / 500.0 + fy;
    jab_float fz = fy - b_clamped / 200.0;

    // Apply inverse f function
    jab_float xr = lab_f_inv(fx);
    jab_float yr = lab_f_inv(fy);
    jab_float zr = lab_f_inv(fz);

    // Scale by reference white
    jab_xyz_color xyz;
    xyz.X = xr * REF_X;
    xyz.Y = yr * REF_Y;
    xyz.Z = zr * REF_Z;
    
    // Validate output to prevent NaN/Inf propagation
    if (isnan(xyz.X) || isinf(xyz.X)) xyz.X = 0.0;
    if (isnan(xyz.Y) || isinf(xyz.Y)) xyz.Y = 0.0;
    if (isnan(xyz.Z) || isinf(xyz.Z)) xyz.Z = 0.0;

    return xyz;
}

/**
 * @brief Convert XYZ to RGB color space
 */
jab_rgb_color xyz_to_rgb(jab_xyz_color xyz)
{
    // Normalize XYZ (0-100 range to 0-1)
    jab_float x = xyz.X / 100.0;
    jab_float y = xyz.Y / 100.0;
    jab_float z = xyz.Z / 100.0;

    // Convert XYZ to linear RGB using inverse sRGB D65 matrix
    jab_float r =  x * 3.2404542 + y * -1.5371385 + z * -0.4985314;
    jab_float g =  x * -0.9692660 + y * 1.8760108 + z * 0.0415560;
    jab_float b =  x * 0.0556434 + y * -0.2040259 + z * 1.0572252;

    // Apply gamma correction (inverse)
    r = delinearize_rgb_channel(r);
    g = delinearize_rgb_channel(g);
    b = delinearize_rgb_channel(b);

    // Clamp to [0, 1] and convert to [0, 255]
    jab_rgb_color rgb;
    rgb.r = (jab_byte)(fmax(0.0, fmin(1.0, r)) * 255.0 + 0.5);
    rgb.g = (jab_byte)(fmax(0.0, fmin(1.0, g)) * 255.0 + 0.5);
    rgb.b = (jab_byte)(fmax(0.0, fmin(1.0, b)) * 255.0 + 0.5);

    return rgb;
}

/**
 * @brief Convert LAB to RGB color space (combined)
 */
jab_rgb_color lab_to_rgb(jab_lab_color lab)
{
    jab_xyz_color xyz = lab_to_xyz(lab);
    return xyz_to_rgb(xyz);
}

/**
 * @brief Calculate CIE76 ΔE (perceptual color difference)
 */
jab_float delta_e_76(jab_lab_color lab1, jab_lab_color lab2)
{
    jab_float dL = lab1.L - lab2.L;
    jab_float da = lab1.a - lab2.a;
    jab_float db = lab1.b - lab2.b;

    return sqrt(dL * dL + da * da + db * db);
}

/**
 * @brief Calculate CIEDE2000 ΔE (improved perceptual color difference)
 * 
 * More complex but more accurate formula accounting for
 * perceptual non-uniformities in LAB space.
 */
jab_float delta_e_2000(jab_lab_color lab1, jab_lab_color lab2)
{
    // Weight factors (default: 1.0 for graphic arts)
    const jab_float kL = 1.0;
    const jab_float kC = 1.0;
    const jab_float kH = 1.0;

    // Calculate C (chroma) and h (hue angle)
    jab_float C1 = sqrt(lab1.a * lab1.a + lab1.b * lab1.b);
    jab_float C2 = sqrt(lab2.a * lab2.a + lab2.b * lab2.b);
    jab_float C_avg = (C1 + C2) / 2.0;

    // Calculate G factor for a' adjustment
    jab_float C_avg_7 = pow(C_avg, 7.0);
    jab_float G = 0.5 * (1.0 - sqrt(C_avg_7 / (C_avg_7 + pow(25.0, 7.0))));

    // Calculate a' (adjusted a)
    jab_float a1_prime = lab1.a * (1.0 + G);
    jab_float a2_prime = lab2.a * (1.0 + G);

    // Calculate C' (adjusted chroma)
    jab_float C1_prime = sqrt(a1_prime * a1_prime + lab1.b * lab1.b);
    jab_float C2_prime = sqrt(a2_prime * a2_prime + lab2.b * lab2.b);

    // Calculate h' (adjusted hue angle)
    jab_float h1_prime = atan2(lab1.b, a1_prime) * 180.0 / M_PI;
    jab_float h2_prime = atan2(lab2.b, a2_prime) * 180.0 / M_PI;
    if (h1_prime < 0.0) h1_prime += 360.0;
    if (h2_prime < 0.0) h2_prime += 360.0;

    // Calculate ΔL', ΔC', ΔH'
    jab_float dL_prime = lab2.L - lab1.L;
    jab_float dC_prime = C2_prime - C1_prime;

    jab_float dh_prime;
    if (C1_prime * C2_prime == 0.0) {
        dh_prime = 0.0;
    } else {
        jab_float dh = h2_prime - h1_prime;
        if (fabs(dh) <= 180.0) {
            dh_prime = dh;
        } else if (dh > 180.0) {
            dh_prime = dh - 360.0;
        } else {
            dh_prime = dh + 360.0;
        }
    }

    jab_float dH_prime = 2.0 * sqrt(C1_prime * C2_prime) * sin(dh_prime * M_PI / 360.0);

    // Calculate average values
    jab_float L_avg_prime = (lab1.L + lab2.L) / 2.0;
    jab_float C_avg_prime = (C1_prime + C2_prime) / 2.0;

    jab_float h_avg_prime;
    if (C1_prime * C2_prime == 0.0) {
        h_avg_prime = h1_prime + h2_prime;
    } else {
        jab_float sum_h = h1_prime + h2_prime;
        jab_float diff_h = fabs(h1_prime - h2_prime);
        if (diff_h <= 180.0) {
            h_avg_prime = sum_h / 2.0;
        } else if (sum_h < 360.0) {
            h_avg_prime = (sum_h + 360.0) / 2.0;
        } else {
            h_avg_prime = (sum_h - 360.0) / 2.0;
        }
    }

    // Calculate T
    jab_float T = 1.0
        - 0.17 * cos((h_avg_prime - 30.0) * M_PI / 180.0)
        + 0.24 * cos(2.0 * h_avg_prime * M_PI / 180.0)
        + 0.32 * cos((3.0 * h_avg_prime + 6.0) * M_PI / 180.0)
        - 0.20 * cos((4.0 * h_avg_prime - 63.0) * M_PI / 180.0);

    // Calculate SL, SC, SH
    jab_float L_avg_minus_50_sq = (L_avg_prime - 50.0) * (L_avg_prime - 50.0);
    jab_float SL = 1.0 + (0.015 * L_avg_minus_50_sq) / sqrt(20.0 + L_avg_minus_50_sq);
    jab_float SC = 1.0 + 0.045 * C_avg_prime;
    jab_float SH = 1.0 + 0.015 * C_avg_prime * T;

    // Calculate RT (rotation function)
    jab_float dTheta = 30.0 * exp(-pow((h_avg_prime - 275.0) / 25.0, 2.0));
    jab_float C_avg_prime_7 = pow(C_avg_prime, 7.0);
    jab_float RC = 2.0 * sqrt(C_avg_prime_7 / (C_avg_prime_7 + pow(25.0, 7.0)));
    jab_float RT = -RC * sin(2.0 * dTheta * M_PI / 180.0);

    // Calculate final ΔE2000
    jab_float term1 = dL_prime / (kL * SL);
    jab_float term2 = dC_prime / (kC * SC);
    jab_float term3 = dH_prime / (kH * SH);
    jab_float term4 = RT * term2 * term3;

    return sqrt(term1 * term1 + term2 * term2 + term3 * term3 + term4);
}

/**
 * @brief Find nearest palette color using LAB perceptual distance
 */
jab_int32 find_nearest_color_lab(jab_rgb_color observed_rgb, jab_rgb_color* palette_rgb, jab_int32 palette_size)
{
    jab_lab_color observed_lab = rgb_to_lab(observed_rgb);
    
    jab_int32 nearest_index = 0;
    jab_float min_distance = INFINITY;

    for (jab_int32 i = 0; i < palette_size; i++) {
        jab_lab_color palette_lab = rgb_to_lab(palette_rgb[i]);
        jab_float distance = delta_e_76(observed_lab, palette_lab);

        if (distance < min_distance) {
            min_distance = distance;
            nearest_index = i;
        }
    }

    return nearest_index;
}
