/**
 * libjabcode - JABCode Encoding/Decoding Library
 * Phase 1 Session 2: Image Filtering for Noise Reduction
 *
 * Copyright 2016 by Fraunhofer SIT. All rights reserved.
 * See LICENSE file for full terms of use and distribution.
 *
 * @file image_filter.c
 * @brief Image preprocessing filters for improved decoding
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"
#include "image_filter.h"

/**
 * @brief Comparison function for qsort
 */
static int compareBytes(const void* a, const void* b) {
    return (*(jab_byte*)a - *(jab_byte*)b);
}

/**
 * @brief Get median value from array of bytes
 * @param values array of values
 * @param count number of values
 * @return median value
 */
jab_byte getMedian(jab_byte* values, jab_int32 count) {
    if (count == 0) return 0;
    if (count == 1) return values[0];
    
    // Sort the array
    qsort(values, count, sizeof(jab_byte), compareBytes);
    
    // Return middle value
    if (count % 2 == 0) {
        // Even count: average of two middle values
        return (values[count/2 - 1] + values[count/2]) / 2;
    } else {
        // Odd count: middle value
        return values[count/2];
    }
}

/**
 * @brief Apply 3x3 median filter to a single pixel
 * @param bitmap the input bitmap
 * @param x x coordinate
 * @param y y coordinate
 * @param channel color channel (0=R, 1=G, 2=B)
 * @return filtered pixel value
 */
static jab_byte applyMedianFilterPixel(jab_bitmap* bitmap, jab_int32 x, jab_int32 y, jab_int32 channel) {
    jab_byte values[9];
    jab_int32 count = 0;
    jab_int32 bytes_per_pixel = bitmap->bits_per_pixel / 8;
    
    // Collect 3x3 neighborhood values
    for (jab_int32 dy = -1; dy <= 1; dy++) {
        for (jab_int32 dx = -1; dx <= 1; dx++) {
            jab_int32 nx = x + dx;
            jab_int32 ny = y + dy;
            
            // Check bounds
            if (nx >= 0 && nx < bitmap->width && ny >= 0 && ny < bitmap->height) {
                jab_int32 offset = (ny * bitmap->width + nx) * bytes_per_pixel + channel;
                values[count++] = bitmap->pixel[offset];
            }
        }
    }
    
    // Return median of collected values
    return getMedian(values, count);
}

/**
 * @brief Apply 3x3 median filter to bitmap
 * @param bitmap the input bitmap to filter
 * @return filtered bitmap | NULL on failure
 */
jab_bitmap* applyMedianFilter(jab_bitmap* bitmap) {
    if (bitmap == NULL) {
        JAB_REPORT_ERROR(("Null bitmap in applyMedianFilter"));
        return NULL;
    }
    
    // Allocate new bitmap for output
    jab_bitmap* filtered = (jab_bitmap*)malloc(sizeof(jab_bitmap) + bitmap->width * bitmap->height * bitmap->bits_per_pixel / 8);
    if (filtered == NULL) {
        JAB_REPORT_ERROR(("Memory allocation failed in applyMedianFilter"));
        return NULL;
    }
    
    // Copy bitmap properties
    filtered->width = bitmap->width;
    filtered->height = bitmap->height;
    filtered->bits_per_pixel = bitmap->bits_per_pixel;
    filtered->bits_per_channel = bitmap->bits_per_channel;
    filtered->channel_count = bitmap->channel_count;
    
    jab_int32 bytes_per_pixel = bitmap->bits_per_pixel / 8;
    
    // Apply median filter to each pixel
    for (jab_int32 y = 0; y < bitmap->height; y++) {
        for (jab_int32 x = 0; x < bitmap->width; x++) {
            jab_int32 offset = (y * bitmap->width + x) * bytes_per_pixel;
            
            // Filter each color channel (R, G, B)
            // Channel 0: R, Channel 1: G, Channel 2: B
            filtered->pixel[offset + 0] = applyMedianFilterPixel(bitmap, x, y, 0); // R
            filtered->pixel[offset + 1] = applyMedianFilterPixel(bitmap, x, y, 1); // G
            filtered->pixel[offset + 2] = applyMedianFilterPixel(bitmap, x, y, 2); // B
            
            // Copy alpha channel if present (4 bytes per pixel)
            if (bytes_per_pixel == 4) {
                filtered->pixel[offset + 3] = bitmap->pixel[offset + 3];
            }
        }
    }
    
    return filtered;
}

/**
 * @brief Apply median filter in-place to bitmap
 * @param bitmap the bitmap to filter (modified in place)
 * @return JAB_SUCCESS | JAB_FAILURE
 */
jab_int32 applyMedianFilterInPlace(jab_bitmap* bitmap) {
    if (bitmap == NULL) {
        JAB_REPORT_ERROR(("Null bitmap in applyMedianFilterInPlace"));
        return JAB_FAILURE;
    }
    
    // Create filtered copy
    jab_bitmap* filtered = applyMedianFilter(bitmap);
    if (filtered == NULL) {
        return JAB_FAILURE;
    }
    
    // Copy filtered data back to original
    jab_int32 pixel_count = bitmap->width * bitmap->height * (bitmap->bits_per_pixel / 8);
    memcpy(bitmap->pixel, filtered->pixel, pixel_count);
    
    // Free temporary filtered bitmap
    free(filtered);
    
    return JAB_SUCCESS;
}
