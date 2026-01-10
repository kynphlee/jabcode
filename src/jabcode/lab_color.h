/**
 * libjabcode - JABCode Encoding/Decoding Library
 *
 * Copyright 2016 by Fraunhofer SIT. All rights reserved.
 * See LICENSE file for full terms of use and distribution.
 *
 * Contact: Huajian Liu <liu@sit.fraunhofer.de>
 *          Waldemar Berchtold <waldemar.berchtold@sit.fraunhofer.de>
 *
 * @file lab_color.h
 * @brief CIE LAB color space conversion and perceptual distance calculation
 */

#ifndef LAB_COLOR_H
#define LAB_COLOR_H

#include "jabcode.h"

/**
 * @brief CIE LAB color representation
 * 
 * L* (Lightness): 0-100
 * a* (Green-Red axis): -128 to +127
 * b* (Blue-Yellow axis): -128 to +127
 */
typedef struct {
    jab_float L;  // Lightness [0, 100]
    jab_float a;  // Green-Red axis [-128, +127]
    jab_float b;  // Blue-Yellow axis [-128, +127]
} jab_lab_color;

/**
 * @brief CIE XYZ color representation (intermediate for RGB↔LAB conversion)
 */
typedef struct {
    jab_float X;
    jab_float Y;
    jab_float Z;
} jab_xyz_color;

/**
 * @brief RGB color representation (8-bit per channel)
 */
typedef struct {
    jab_byte r;
    jab_byte g;
    jab_byte b;
} jab_rgb_color;

/**
 * @brief Convert RGB to LAB color space
 * @param rgb RGB color (0-255 per channel)
 * @return LAB color
 */
jab_lab_color rgb_to_lab(jab_rgb_color rgb);

/**
 * @brief Convert LAB to RGB color space
 * @param lab LAB color
 * @return RGB color (0-255 per channel)
 */
jab_rgb_color lab_to_rgb(jab_lab_color lab);

/**
 * @brief Calculate perceptual color difference using CIE76 ΔE formula
 * 
 * ΔE values:
 *   < 1.0 = Not perceptible by human eyes
 *   1-2   = Perceptible through close observation
 *   2-10  = Perceptible at a glance
 *   > 10  = Colors are more different than similar
 *
 * @param lab1 First LAB color
 * @param lab2 Second LAB color
 * @return ΔE (perceptual distance)
 */
jab_float delta_e_76(jab_lab_color lab1, jab_lab_color lab2);

/**
 * @brief Calculate perceptual color difference using CIEDE2000 formula (more accurate)
 * 
 * More accurate than CIE76, accounts for perceptual non-uniformities
 * in LAB space, particularly for blue colors.
 *
 * @param lab1 First LAB color
 * @param lab2 Second LAB color
 * @return ΔE2000 (perceptual distance)
 */
jab_float delta_e_2000(jab_lab_color lab1, jab_lab_color lab2);

/**
 * @brief Find nearest palette color using LAB perceptual distance
 * @param observed_rgb Observed RGB color to classify
 * @param palette_rgb Array of palette RGB colors
 * @param palette_size Number of colors in palette
 * @return Index of nearest color in palette
 */
jab_int32 find_nearest_color_lab(jab_rgb_color observed_rgb, jab_rgb_color* palette_rgb, jab_int32 palette_size);

/**
 * @brief Convert RGB to XYZ color space (intermediate step)
 * @param rgb RGB color
 * @return XYZ color (D65 illuminant reference white)
 */
jab_xyz_color rgb_to_xyz(jab_rgb_color rgb);

/**
 * @brief Convert XYZ to LAB color space
 * @param xyz XYZ color
 * @return LAB color
 */
jab_lab_color xyz_to_lab(jab_xyz_color xyz);

/**
 * @brief Convert LAB to XYZ color space
 * @param lab LAB color
 * @return XYZ color
 */
jab_xyz_color lab_to_xyz(jab_lab_color lab);

/**
 * @brief Convert XYZ to RGB color space
 * @param xyz XYZ color
 * @return RGB color
 */
jab_rgb_color xyz_to_rgb(jab_xyz_color xyz);

#endif // LAB_COLOR_H
