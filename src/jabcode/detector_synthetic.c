/**
 * libjabcode - JABCode Encoding/Decoding Library
 *
 * Copyright 2016 by Fraunhofer SIT. All rights reserved.
 * See LICENSE file for full terms of use and distribution.
 *
 * Contact: Huajian Liu <liu@sit.fraunhofer.de>
 *          Waldemar Berchtold <waldemar.berchtold@sit.fraunhofer.de>
 *
 * @file detector_synthetic.c
 * @brief Synthetic bitmap decoder (for encoder-generated perfect images)
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include "jabcode.h"
#include "detector.h"
#include "decoder.h"

// Forward declarations from encoder.c and decoder.c
extern void setDefaultPalette(jab_int32 color_number, jab_byte* palette);
extern void normalizeColorPalette(jab_decoded_symbol* symbol, jab_float* norm_palette, jab_int32 color_number);
extern void getPaletteThreshold(jab_byte* palette, jab_int32 color_number, jab_float* palette_ths);
extern void fillDataMap(jab_byte* data_map, jab_int32 width, jab_int32 height, jab_int32 type);
extern jab_int32 decodeSymbol(jab_bitmap* matrix, jab_decoded_symbol* symbol, jab_byte* data_map, jab_float* norm_palette, jab_float* pal_ths, jab_int32 type);

// ECC level to wc/wr mapping (from decoder.c)
static const jab_int32 ecclevel2wcwr[10][2] = {
    {3, 5},   // Level 0
    {7, 9},   // Level 1
    {3, 4},   // Level 2
    {5, 6},   // Level 3
    {7, 8},   // Level 4
    {4, 5},   // Level 5
    {5, 7},   // Level 6
    {6, 7},   // Level 7
    {8, 9},   // Level 8
    {9, 10}   // Level 9
};

/**
 * @brief Extract RGB channels directly from synthetic RGBA bitmap without binarization
 * @param bitmap the input RGBA bitmap with perfect palette colors
 * @param rgb output array of 3 single-channel bitmaps (R, G, B)
 * @return JAB_SUCCESS | JAB_FAILURE
 */
jab_boolean extractRGBChannelsSynthetic(jab_bitmap* bitmap, jab_bitmap* rgb[3])
{
    // Allocate 3 single-channel bitmaps
    for(jab_int32 i=0; i<3; i++)
    {
        rgb[i] = (jab_bitmap*)calloc(1, sizeof(jab_bitmap) + bitmap->width*bitmap->height*sizeof(jab_byte));
        if(rgb[i] == NULL)
        {
            JAB_REPORT_ERROR(("Memory allocation for RGB channel %d failed", i))
            // Free previously allocated channels
            for(jab_int32 j=0; j<i; j++)
                free(rgb[j]);
            return JAB_FAILURE;
        }
        rgb[i]->width = bitmap->width;
        rgb[i]->height = bitmap->height;
        rgb[i]->bits_per_channel = 8;
        rgb[i]->bits_per_pixel = 8;
        rgb[i]->channel_count = 1;
    }

    jab_int32 bytes_per_pixel = bitmap->bits_per_pixel / 8;
    jab_int32 bytes_per_row = bitmap->width * bytes_per_pixel;

    // For synthetic images with perfect palette colors, use simple per-channel thresholding
    // Palette colors are either 0 or 255 per channel, so threshold at 128
    // Examples:
    //   Black (0,0,0) → R=0, G=0, B=0
    //   White (255,255,255) → R=255, G=255, B=255
    //   Magenta (255,0,255) → R=255, G=0, B=255
    //   Yellow (255,255,0) → R=255, G=255, B=0
    //   Cyan (0,255,255) → R=0, G=255, B=255
    
    for(jab_int32 y=0; y<bitmap->height; y++)
    {
        for(jab_int32 x=0; x<bitmap->width; x++)
        {
            jab_int32 src_offset = y * bytes_per_row + x * bytes_per_pixel;
            jab_int32 dst_offset = y * bitmap->width + x;

            // Get RGB values
            jab_byte r = bitmap->pixel[src_offset + 0];
            jab_byte g = bitmap->pixel[src_offset + 1];
            jab_byte b = bitmap->pixel[src_offset + 2];
            
            // Simple per-channel binarization
            rgb[0]->pixel[dst_offset] = (r >= 128) ? 255 : 0;
            rgb[1]->pixel[dst_offset] = (g >= 128) ? 255 : 0;
            rgb[2]->pixel[dst_offset] = (b >= 128) ? 255 : 0;
        }
    }

    return JAB_SUCCESS;
}

/**
 * @brief Decode a JABCode from synthetic (encoder-generated) bitmap with known parameters
 * @param bitmap the synthetic RGBA bitmap with perfect palette colors
 * @param color_number the color count used during encoding (known ground truth)
 * @param ecc_level the error correction level used during encoding (known ground truth)
 * @param module_size pixels per module (from encoder)
 * @param symbol_width symbol width in modules (from encoder)
 * @param symbol_height symbol height in modules (from encoder)
 * @param mask_type the mask pattern type used by encoder
 * @param mode the decoding mode (NORMAL_DECODE or COMPATIBLE_DECODE)
 * @param status the decoding status code (0: not detectable, 1: not decodable, 2: partly decoded, 3: fully decoded)
 * @return the decoded data | NULL if failed
 *
 * This function bypasses camera-specific detection logic (Nc detection, palette learning, 
 * pattern detection) by using known encoding parameters and spatial metadata.
 * This solves the "too perfect" problem where camera-tuned detectors fail on synthetic images.
 */
jab_data* decodeJABCodeSynthetic(jab_bitmap* bitmap, jab_int32 color_number, jab_int32 ecc_level, jab_int32 module_size, jab_int32 symbol_width, jab_int32 symbol_height, jab_int32 mask_type, jab_int32 mode, jab_int32* status)
{
    jab_decoded_symbol symbols[MAX_SYMBOL_NUMBER];
    
    if(status) *status = 0;
    
    // Extract RGB channels directly (no binarization for synthetic images)
    jab_bitmap* ch[3];
    if(!extractRGBChannelsSynthetic(bitmap, ch))
    {
        reportError("Failed to extract RGB channels from synthetic bitmap");
        return NULL;
    }

    // Initialize symbols buffer
    memset(symbols, 0, MAX_SYMBOL_NUMBER * sizeof(jab_decoded_symbol));
    jab_int32 total = 0;
    jab_boolean res = 1;
    // Use known parameters to completely bypass camera-specific detection
    // Calculate Nc from color_number: Nc = log2(color_number) - 1
    jab_byte Nc;
    switch(color_number) {
        case 4:   Nc = 1; break;  // log2(4) - 1 = 2 - 1 = 1
        case 8:   Nc = 2; break;  // log2(8) - 1 = 3 - 1 = 2
        case 16:  Nc = 3; break;  // log2(16) - 1 = 4 - 1 = 3
        case 32:  Nc = 4; break;  // log2(32) - 1 = 5 - 1 = 4
        case 64:  Nc = 5; break;  // log2(64) - 1 = 6 - 1 = 5
        case 128: Nc = 6; break;  // log2(128) - 1 = 7 - 1 = 6
        default:
            reportError("Invalid color_number for synthetic decode");
            for(jab_int32 i=0; i<3; free(ch[i++]));
            return NULL;
    }
    
    // Construct master symbol structure directly from known spatial metadata
    symbols[0].index = 0;
    symbols[0].host_index = -1;  // master has no host
    symbols[0].module_size = (jab_float)module_size;
    symbols[0].side_size.x = symbol_width;
    symbols[0].side_size.y = symbol_height;
    
    // Set known metadata
    symbols[0].metadata.Nc = Nc;
    // Convert ECC level to wc/wr using lookup table (decoder reads from metadata.ecl)
    symbols[0].metadata.ecl.x = ecclevel2wcwr[ecc_level][0];  // wc
    symbols[0].metadata.ecl.y = ecclevel2wcwr[ecc_level][1];  // wr
    // side_version is VERSION not SIZE: VERSION = (SIZE - 17) / 4
    symbols[0].metadata.side_version.x = SIZE2VERSION(symbol_width);
    symbols[0].metadata.side_version.y = SIZE2VERSION(symbol_height);
    symbols[0].metadata.mask_type = mask_type;  // Use encoder's mask_type
    symbols[0].metadata.docked_position = 0;  // Not docked
    symbols[0].metadata.default_mode = 1;  // Using default parameters
    
    // Calculate finder pattern positions from spatial metadata
    // Assuming encoder added 4-module quiet zone on all sides
    jab_int32 quiet_zone = 4;
    jab_float pattern_offset = quiet_zone * module_size + 3.5f * module_size;  // 3.5 = half of 7x7 finder pattern
    jab_float symbol_pixel_width = symbol_width * module_size;
    jab_float symbol_pixel_height = symbol_height * module_size;
    
    // Top-left (FP0)
    symbols[0].pattern_positions[0].x = pattern_offset;
    symbols[0].pattern_positions[0].y = pattern_offset;
    
    // Top-right (FP1)
    symbols[0].pattern_positions[1].x = pattern_offset + symbol_pixel_width - 7.0f * module_size;
    symbols[0].pattern_positions[1].y = pattern_offset;
    
    // Bottom-left (FP2)
    symbols[0].pattern_positions[2].x = pattern_offset;
    symbols[0].pattern_positions[2].y = pattern_offset + symbol_pixel_height - 7.0f * module_size;
    
    // Bottom-right (FP3)
    symbols[0].pattern_positions[3].x = pattern_offset + symbol_pixel_width - 7.0f * module_size;
    symbols[0].pattern_positions[3].y = pattern_offset + symbol_pixel_height - 7.0f * module_size;
    
    // Allocate single palette - decoder will only use first palette for master symbol
    symbols[0].palette = (jab_byte*)calloc(color_number * 3, sizeof(jab_byte));
    if(!symbols[0].palette) {
        reportError("Failed to allocate palette for synthetic decode");
        for(jab_int32 i=0; i<3; free(ch[i++]));
        return NULL;
    }
    // Set default palette colors
    setDefaultPalette(color_number, symbols[0].palette);
    
    // For perfect synthetic images, sample modules directly without perspective transform
    // Start position: quiet_zone offset + half module for center sampling
    jab_float start_x = quiet_zone * module_size + 0.5f * module_size;
    jab_float start_y = quiet_zone * module_size + 0.5f * module_size;
    
    // Allocate module matrix - MUST be 3-channel RGB for decodeModuleHD
    // decodeModuleHD reads rgb[0], rgb[1], rgb[2] from matrix->pixel[offset+0/1/2]
    jab_int32 mtx_bytes_per_pixel = 3;  // RGB = 3 bytes per pixel
    jab_int32 mtx_bytes_per_row = symbol_width * mtx_bytes_per_pixel;
    jab_int32 matrix_size = symbol_width * symbol_height * mtx_bytes_per_pixel;
    jab_bitmap* matrix = (jab_bitmap*)malloc(sizeof(jab_bitmap) + matrix_size);
    if(!matrix) {
        reportError("Failed to allocate module matrix");
        free(symbols[0].palette);
        for(jab_int32 i=0; i<3; free(ch[i++]));
        if(status) *status = 1;
        return NULL;
    }
    
    matrix->width = symbol_width;
    matrix->height = symbol_height;
    matrix->channel_count = 3;
    matrix->bits_per_channel = 8;
    matrix->bits_per_pixel = 24;
    
    // Sample each module by reading center pixel and reconstructing RGB from separate channels
    for(jab_int32 y = 0; y < symbol_height; y++) {
        for(jab_int32 x = 0; x < symbol_width; x++) {
            // Calculate pixel position for this module's center
            jab_int32 pixel_x = (jab_int32)(start_x + x * module_size);
            jab_int32 pixel_y = (jab_int32)(start_y + y * module_size);
            
            // Read from each separate channel and interleave into RGB
            jab_int32 ch_offset = pixel_y * ch[0]->width + pixel_x;
            jab_int32 mtx_offset = y * mtx_bytes_per_row + x * mtx_bytes_per_pixel;
            
            matrix->pixel[mtx_offset + 0] = ch[0]->pixel[ch_offset];  // R
            matrix->pixel[mtx_offset + 1] = ch[1]->pixel[ch_offset];  // G
            matrix->pixel[mtx_offset + 2] = ch[2]->pixel[ch_offset];  // B
        }
    }
    
    // Skip decodeMaster() since we've already set metadata and palette
    // decodeMaster() expects to READ metadata from matrix, but we're bypassing that
    // Call decodeSymbol() directly with our pre-configured setup
    
    // Create data map for the symbol
    jab_byte* data_map = (jab_byte*)calloc(1, matrix->width * matrix->height * sizeof(jab_byte));
    if(!data_map) {
        reportError("Failed to allocate data map");
        free(matrix);
        free(symbols[0].palette);
        for(jab_int32 i=0; i<3; free(ch[i++]));
        if(status) *status = 1;
        return NULL;
    }
    
    // Mark finder and alignment patterns in data map (type=0 for master symbol)
    fillDataMap(data_map, matrix->width, matrix->height, 0);
    
    // Normalize palette for all 4 palette slots (decodeModuleHD expects COLOR_PALETTE_NUMBER palettes)
    jab_float norm_palette[color_number * 4 * COLOR_PALETTE_NUMBER];
    for(jab_int32 p=0; p<COLOR_PALETTE_NUMBER; p++) {
        for(jab_int32 i=0; i<color_number; i++) {
            jab_byte* color = symbols[0].palette + (i * 3);
            jab_float max_val = (jab_float)MAX(color[0], MAX(color[1], color[2]));
            jab_int32 offset = color_number*4*p + i*4;
            if(max_val > 0) {
                norm_palette[offset + 0] = (jab_float)color[0] / max_val;  // R
                norm_palette[offset + 1] = (jab_float)color[1] / max_val;  // G
                norm_palette[offset + 2] = (jab_float)color[2] / max_val;  // B
                norm_palette[offset + 3] = ((jab_float)(color[0] + color[1] + color[2]) / 3.0f) / 255.0f;  // L
            } else {
                norm_palette[offset + 0] = 0;
                norm_palette[offset + 1] = 0;
                norm_palette[offset + 2] = 0;
                norm_palette[offset + 3] = 0;
            }
        }
    }
    
    // Get palette thresholds for all 4 palettes (decodeModuleHD uses pal_ths[p_index*3+...])
    jab_float pal_ths[3 * COLOR_PALETTE_NUMBER];
    getPaletteThreshold(symbols[0].palette, color_number, &pal_ths[0]);
    // Replicate thresholds to all palette slots
    for(jab_int32 p=1; p<COLOR_PALETTE_NUMBER; p++) {
        pal_ths[p*3 + 0] = pal_ths[0];
        pal_ths[p*3 + 1] = pal_ths[1];
        pal_ths[p*3 + 2] = pal_ths[2];
    }
    
    // Decode the symbol data directly
    total = 1;
    if(decodeSymbol(matrix, &symbols[0], data_map, norm_palette, pal_ths, 0) < 0)
    {
        reportError("Failed to decode symbol data");
        // Note: data_map already freed by decodeSymbol() even on error
        free(matrix);
        free(symbols[0].palette);
        for(jab_int32 i=0; i<3; free(ch[i++]));
        if(status) *status = 1;
        return NULL;
    }
    
    free(matrix);

    // Check result
    if(total == 0 || (mode == NORMAL_DECODE && res == 0))
    {
        if(symbols[0].module_size > 0 && status)
            *status = 1;
        
        // Clean memory
        for(jab_int32 i=0; i<3; free(ch[i++]));
        for(jab_int32 i=0; i<=MIN(total, MAX_SYMBOL_NUMBER-1); i++)
        {
            free(symbols[i].palette);
            free(symbols[i].data);
        }
        return NULL;
    }
    
    if(mode == COMPATIBLE_DECODE && res == 0)
    {
        if(status) *status = 2;
        res = 1;
    }

    // Concatenate the decoded data
    jab_int32 total_data_length = 0;
    for(jab_int32 i=0; i<total; i++)
    {
        if(symbols[i].data == NULL)
        {
            reportError("Symbol data is NULL after decode");
            // Note: data_map already freed by decodeSymbol()
            for(jab_int32 j=0; j<3; free(ch[j++]));
            for(jab_int32 j=0; j<=i; j++)
            {
                free(symbols[j].palette);
                free(symbols[j].data);
            }
            if(status) *status = 1;
            return NULL;
        }
        total_data_length += symbols[i].data->length;
    }
    
    jab_data* decoded_bits = (jab_data*)malloc(sizeof(jab_data) + total_data_length * sizeof(jab_char));
    if(decoded_bits == NULL)
    {
        reportError("Memory allocation for decoded bits failed");
        if(status) *status = 1;
        
        // Clean memory
        for(jab_int32 i=0; i<3; free(ch[i++]));
        for(jab_int32 i=0; i<total; i++)
        {
            free(symbols[i].palette);
            free(symbols[i].data);
        }
        return NULL;
    }
    
    jab_int32 offset = 0;
    for(jab_int32 i=0; i<total; i++)
    {
        jab_char* src = symbols[i].data->data;
        jab_char* dst = decoded_bits->data;
        dst += offset;
        memcpy(dst, src, symbols[i].data->length);
        offset += symbols[i].data->length;
    }
    decoded_bits->length = total_data_length;
    
    // Decode data
    jab_data* decoded_data = decodeData(decoded_bits);
    
    // Clean memory
    free(decoded_bits);
    for(jab_int32 i=0; i<3; free(ch[i++]));
    for(jab_int32 i=0; i<total; i++)
    {
        free(symbols[i].palette);
        free(symbols[i].data);
    }
    
    if(decoded_data == NULL)
    {
        reportError("Decoding data failed");
        if(status) *status = 1;
        return NULL;
    }
    
    if(status) *status = 3;
    return decoded_data;
}
