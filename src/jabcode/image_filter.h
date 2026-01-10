/**
 * libjabcode - JABCode Encoding/Decoding Library
 * Phase 1 Session 2: Image Filtering for Noise Reduction
 *
 * Copyright 2016 by Fraunhofer SIT. All rights reserved.
 * See LICENSE file for full terms of use and distribution.
 *
 * @file image_filter.h
 * @brief Image preprocessing filters for improved decoding
 */

#ifndef IMAGE_FILTER_H
#define IMAGE_FILTER_H

#include "jabcode.h"

/**
 * @brief Apply 3x3 median filter to bitmap
 * @param bitmap the input bitmap to filter
 * @return filtered bitmap | NULL on failure
 */
jab_bitmap* applyMedianFilter(jab_bitmap* bitmap);

/**
 * @brief Apply median filter in-place to bitmap
 * @param bitmap the bitmap to filter (modified in place)
 * @return JAB_SUCCESS | JAB_FAILURE
 */
jab_int32 applyMedianFilterInPlace(jab_bitmap* bitmap);

/**
 * @brief Get median value from array of bytes
 * @param values array of values
 * @param count number of values
 * @return median value
 */
jab_byte getMedian(jab_byte* values, jab_int32 count);

#endif // IMAGE_FILTER_H
