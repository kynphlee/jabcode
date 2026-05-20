/**
 * libjabcode - JABCode Encoding/Decoding Library
 *
 * Copyright 2016 by Fraunhofer SIT. All rights reserved.
 * See LICENSE file for full terms of use and distribution.
 *
 * Contact: Huajian Liu <liu@sit.fraunhofer.de>
 *			Waldemar Berchtold <waldemar.berchtold@sit.fraunhofer.de>
 *
 * @file decoder.c
 * @brief Data decoding
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include "jabcode.h"
#include "detector.h"
#include "decoder.h"
#include "ldpc.h"
#include "encoder.h"

/* WS-4 Step 4.2: optional CIE Lab ΔE2000 color discrimination in decodeModuleHD.
 * Defined at compile time via -DUSE_LAB_DISTANCE. When defined, Nc≥3 (color
 * modes with color_number > 8) use perceptual Lab distance instead of squared
 * RGB Euclidean. Nc<3 (modes 0,1,2 using normalized-RGB path) are untouched,
 * preserving the Mode 1 regression gate by construction.
 * See: docs/jabcode-all-nc-plan/00-CHECKLIST.md item 4.2
 *      src/jabcode/test/test_lab_color_distance.c (WS-4 Step 4.1 TDD test) */
#ifdef USE_LAB_DISTANCE
#include "lab_color.h"
#endif

/* WS-4 Step 4.4: optional FP-core color calibration in decodeModuleHD.
 * Defined at compile time via -DUSE_FP_CALIBRATION. When defined AND when a
 * calibration profile is active (jabHasCalibration() returns true), each
 * sampled module RGB is normalized via jabRemapColorInverse before color
 * classification. This wiring is intentionally passive — it does NOT build
 * calibration from FP cores in this step; that integration ships in a
 * follow-on commit (4.4b) once test_roundtrip_with_noise.c (4.7) exists to
 * empirically validate the FP-core sampling path. Until then, calibration
 * is populated externally via jabLoadCalibrationFromJSON or
 * jabCalibrateFromObservedRGB.
 * See: docs/jabcode-all-nc-plan/00-CHECKLIST.md item 4.4
 *      src/jabcode/test/test_color_calibration.c phases 6–7 (TDD gate) */
#ifdef USE_FP_CALIBRATION
#include "color_calibration.h"
#endif

// Android logging support for debug output
#ifdef __ANDROID__
#include <android/log.h>
#define DEBUG_LOG(...) __android_log_print(ANDROID_LOG_DEBUG, "JABCodeDecoder", __VA_ARGS__)
#else
#define DEBUG_LOG(...) printf(__VA_ARGS__); printf("\n")
#endif

/**
 * @brief Copy 16-color sub-blocks of 64-color palette into 32-color blocks of 256-color palette and interpolate into 32 colors
 * @param palette the color palette
 * @param dst_offset the start offset in the destination palette
 * @param src_offset the start offset in the source palette
*/
void copyAndInterpolateSubblockFrom16To32(jab_byte* palette, jab_int32 dst_offset, jab_int32 src_offset)
{
	//copy
	memcpy(palette + dst_offset + 84, palette + src_offset + 36, 12);
	memcpy(palette + dst_offset + 60, palette + src_offset + 24, 12);
	memcpy(palette + dst_offset + 24, palette + src_offset + 12, 12);
	memcpy(palette + dst_offset + 0,  palette + src_offset + 0,  12);
	//interpolate
	for(jab_int32 j=0; j<12; j++)
	{
		jab_int32 sum = palette[dst_offset + 0 + j] + palette[dst_offset + 24 + j];
		palette[dst_offset + 12 + j] = (jab_byte)(sum / 2);
	}
	for(jab_int32 j=0; j<12; j++)
	{
		jab_int32 sum = palette[dst_offset + 24 + j] * 2 + palette[dst_offset + 60 + j];
		palette[dst_offset + 36 + j] = (jab_byte)(sum / 3);
		sum = palette[dst_offset + j] + palette[dst_offset + 60 + j] * 2;
		palette[dst_offset + 48 + j] = (jab_byte)(sum / 3);
	}
	for(jab_int32 j=0; j<12; j++)
	{
		jab_int32 sum = palette[dst_offset + 60 + j] + palette[dst_offset + 84 + j];
		palette[dst_offset + 72 + j] = (jab_byte)(sum / 2);
	}
}

/**
 * @brief Interpolate 64-color palette into 128-/256-color palette
 * @param palette the color palette
 * @param color_number the number of colors
*/
void interpolatePalette(jab_byte* palette, jab_int32 color_number)
{
	for(jab_int32 i=0; i<COLOR_PALETTE_NUMBER; i++)
	{
		jab_int32 offset = color_number * 3 * i;
		if(color_number == 128)											//each block includes 16 colors
		{																//block 1 remains the same
			memcpy(palette + offset + 336, palette + offset + 144, 48); //copy block 4 to block 8
			memcpy(palette + offset + 240, palette + offset + 96,  48); //copy block 3 to block 6
			memcpy(palette + offset + 96,  palette + offset + 48,  48); //copy block 2 to block 3

			//interpolate block 1 and block 3 to get block 2
			for(jab_int32 j=0; j<48; j++)
			{
				jab_int32 sum = palette[offset + 0 + j] + palette[offset + 96 + j];
				palette[offset + 48 + j] = (jab_byte)(sum / 2);
			}
			//interpolate block 3 and block 6 to get block 4 and block 5
			for(jab_int32 j=0; j<48; j++)
			{
				jab_int32 sum = palette[offset + 96 + j] * 2 + palette[offset + 240 + j];
				palette[offset + 144 + j] = (jab_byte)(sum / 3);
				sum = palette[offset + 96 + j] + palette[offset + 240 + j] * 2;
				palette[offset + 192 + j] = (jab_byte)(sum / 3);
			}
			//interpolate block 6 and block 8 to get block 7
			for(jab_int32 j=0; j<48; j++)
			{
				jab_int32 sum = palette[offset + 240 + j] + palette[offset + 336 + j];
				palette[offset + 288 + j] = (jab_byte)(sum / 2);
			}
		}
		else if(color_number == 256)									//each block includes 32 colors
		{
			//copy sub-block 4 to block 8 and interpolate 16 colors into 32 colors
			copyAndInterpolateSubblockFrom16To32(palette, offset + 672, offset + 144);
			//copy sub-block 3 to block 6 and interpolate 16 colors into 32 colors
			copyAndInterpolateSubblockFrom16To32(palette, offset + 480, offset + 96);
			//copy sub-block 2 to block 3 and interpolate 16 colors into 32 colors
			copyAndInterpolateSubblockFrom16To32(palette, offset + 192, offset + 48);
			//copy sub-block 1 to block 1 and interpolate 16 colors into 32 colors
			copyAndInterpolateSubblockFrom16To32(palette, offset + 0, offset + 0);

			//interpolate block 1 and block 3 to get block 2
			for(jab_int32 j=0; j<96; j++)
			{
				jab_int32 sum = palette[offset + 0 + j] + palette[offset + 192 + j];
				palette[offset + 96 + j] = (jab_byte)(sum / 2);
			}
			//interpolate block 3 and block 6 to get block 4 and block 5
			for(jab_int32 j=0; j<96; j++)
			{
				jab_int32 sum = palette[offset + 192 + j] * 2 + palette[offset + 480 + j];
				palette[offset + 288 + j] = (jab_byte)(sum / 3);
				sum = palette[offset + 192 + j] + palette[offset + 480 + j] * 2;
				palette[offset + 384 + j] = (jab_byte)(sum / 3);
			}
			//interpolate block 6 and block 8 to get block 7
			for(jab_int32 j=0; j<96; j++)
			{
				jab_int32 sum = palette[offset + 480 + j] + palette[offset + 672 + j];
				palette[offset + 576 + j] = (jab_byte)(sum / 2);
			}
		}
		else
			return;
	}
}

/**
 * @brief Write colors into color palettes
 * @param matrix the symbol matrix
 * @param symbol the master/slave symbol
 * @param p_index the color palette index
 * @param color_index the color index in color palette
 * @param x the x coordinate of the color module
 * @param y the y coordinate of the color module
*/
void writeColorPalette(jab_bitmap* matrix, jab_decoded_symbol* symbol, jab_int32 p_index, jab_int32 color_index, jab_int32 x, jab_int32 y)
{
	jab_int32 color_number = (jab_int32)pow(2, symbol->metadata.Nc + 1);
	jab_int32 mtx_bytes_per_pixel = matrix->bits_per_pixel / 8;
    jab_int32 mtx_bytes_per_row = matrix->width * mtx_bytes_per_pixel;

	jab_int32 palette_offset = color_number * 3 * p_index;
	jab_int32 mtx_offset = y * mtx_bytes_per_row + x * mtx_bytes_per_pixel;
	symbol->palette[palette_offset + color_index*3 + 0]	= matrix->pixel[mtx_offset + 0];
	symbol->palette[palette_offset + color_index*3 + 1] = matrix->pixel[mtx_offset + 1];
	symbol->palette[palette_offset + color_index*3 + 2] = matrix->pixel[mtx_offset + 2];
}

/**
 * @brief Get the coordinates of the modules in finder/alignment patterns used for color palette
 * @param p_index the color palette index
 * @param matrix_width the matrix width
 * @param matrix_height the matrix height
 * @param p1 the coordinate of the first module
 * @param p2 the coordinate of the second module
*/
void getColorPalettePosInFP(jab_int32 p_index, jab_int32 matrix_width, jab_int32 matrix_height, jab_vector2d* p1, jab_vector2d* p2)
{
	switch(p_index)
	{
	case 0:
		p1->x = DISTANCE_TO_BORDER - 1;
		p1->y = DISTANCE_TO_BORDER - 1;
		p2->x = p1->x + 1;
		p2->y = p1->y;
		break;
	case 1:
		p1->x = matrix_width - DISTANCE_TO_BORDER;
		p1->y = DISTANCE_TO_BORDER - 1;
		p2->x = p1->x - 1;
		p2->y = p1->y;
		break;
	case 2:
		p1->x = matrix_width - DISTANCE_TO_BORDER;
		p1->y = matrix_height - DISTANCE_TO_BORDER;
		p2->x = p1->x - 1;
		p2->y = p1->y;
		break;
	case 3:
		p1->x = DISTANCE_TO_BORDER - 1;
		p1->y = matrix_height - DISTANCE_TO_BORDER;
		p2->x = p1->x + 1;
		p2->y = p1->y;
		break;
	}
}

/**
 * @brief Read the color palettes in master symbol
 * @param matrix the symbol matrix
 * @param symbol the master symbol
 * @param data_map the data module positions
 * @param module_count the start module index
 * @param x the x coordinate of the start module
 * @param y the y coordinate of the start module
 * @return JAB_SUCCESS | FATAL_ERROR
*/
jab_int32 readColorPaletteInMaster(jab_bitmap* matrix, jab_decoded_symbol* symbol, jab_byte* data_map, jab_int32* module_count, jab_int32* x, jab_int32* y)
{
	//allocate buffer for palette
	jab_int32 color_number = (jab_int32)pow(2, symbol->metadata.Nc + 1);
	
	free(symbol->palette);
	/* WS-4.5.4: calloc (not malloc) is REQUIRED here. For Nc>=3 (color_number>8)
	 * the FP-color loop writes slots {0, 3, 5, 6} (per master_palette_placement_index)
	 * and the metadata loop writes slots 2..63 sequentially — leaving color
	 * index 1 of every panel unwritten. With malloc, those 3 bytes are
	 * process-state-dependent (ASLR-sensitive), causing ~22% non-deterministic
	 * decode failure. For color_number>64, interpolatePalette propagates the
	 * uninit bytes throughout the panel. calloc makes the unwritten slots
	 * deterministic (0,0,0); decodeModuleHD already short-circuits true-black
	 * inputs to index 0 via pal_ths, so slot 1==black is benign.
	 * See: docs/jabcode-all-nc-plan/04d-ws4_5_4-determinism-fix.md */
	symbol->palette = (jab_byte*)calloc(1, color_number * sizeof(jab_byte) * 3 * COLOR_PALETTE_NUMBER);
	if(symbol->palette == NULL)
	{
		reportError("Memory allocation for master palette failed");
		return FATAL_ERROR;
	}

	//read colors from finder patterns
	jab_int32 color_index;			//the color index number in color palette
	for(jab_int32 i=0; i<COLOR_PALETTE_NUMBER; i++)
	{
		jab_vector2d p1, p2;
		getColorPalettePosInFP(i, matrix->width, matrix->height, &p1, &p2);
		//color 0
		color_index = master_palette_placement_index[i][0] % color_number; //for 4-color and 8-color symbols
		writeColorPalette(matrix, symbol, i, color_index, p1.x, p1.y);
		//color 1
		color_index = master_palette_placement_index[i][1] % color_number; //for 4-color and 8-color symbols
		writeColorPalette(matrix, symbol, i, color_index, p2.x, p2.y);
	}

	//read colors from metadata
	jab_int32 color_counter = 2;	//the color counter
	while(color_counter < MIN(color_number, 64))
	{
		//color palette 0
		// FIX: For 16+ colors, use sequential indexing instead of placement mapping
		color_index = (color_number <= 8) ? (master_palette_placement_index[0][color_counter] % color_number) : color_counter;
		writeColorPalette(matrix, symbol, 0, color_index, *x, *y);
		//set data map
		data_map[(*y) * matrix->width + (*x)] = 1;
		//go to the next module
		(*module_count)++;
		getNextMetadataModuleInMaster(matrix->height, matrix->width, (*module_count), x, y);

		//color palette 1
		color_index = (color_number <= 8) ? (master_palette_placement_index[1][color_counter] % color_number) : color_counter;
		writeColorPalette(matrix, symbol, 1, color_index, *x, *y);
		//set data map
		data_map[(*y) * matrix->width + (*x)] = 1;
		//go to the next module
		(*module_count)++;
		getNextMetadataModuleInMaster(matrix->height, matrix->width, (*module_count), x, y);

		//color palette 2
		color_index = (color_number <= 8) ? (master_palette_placement_index[2][color_counter] % color_number) : color_counter;
		writeColorPalette(matrix, symbol, 2, color_index, *x, *y);
		//set data map
		data_map[(*y) * matrix->width + (*x)] = 1;
		//go to the next module
		(*module_count)++;
		getNextMetadataModuleInMaster(matrix->height, matrix->width, (*module_count), x, y);

		//color palette 3
		color_index = (color_number <= 8) ? (master_palette_placement_index[3][color_counter] % color_number) : color_counter;
		writeColorPalette(matrix, symbol, 3, color_index, *x, *y);
		//set data map
		data_map[(*y) * matrix->width + (*x)] = 1;
		//go to the next module
		(*module_count)++;
		getNextMetadataModuleInMaster(matrix->height, matrix->width, (*module_count), x, y);

		//next color
		color_counter++;
	}

	/* WS-4.5.4 Bug E fix: for color_number > 8 (Nc>=3), neither the FP loop
	 * (master_palette_placement_index[i][0..1] covers {0, 3, 6}) nor the
	 * metadata loop (starts at color_counter=2 with sequential indexing for
	 * >8) places palette index 1 anywhere in the matrix — yet the encoder
	 * still uses palette[1] for data modules. With Fix B's calloc, the
	 * decoder's symbol->palette[1] stays at (0,0,0), which never matches
	 * the encoder's actual palette[1]; closest-match in decodeModuleHD then
	 * misroutes those data modules, exhausting LDPC headroom for Nc=3.
	 *
	 * The encoder's setDefaultPalette path calls genColorPalette() for
	 * color_number > 8 — a procedural R/G/B grid sampler that produces
	 * different palette[1] values by color_number (e.g. (0,0,255) for
	 * color_number=16/32 with vb=2, but (0,0,85) for color_number=64/128/256
	 * with vb=4). Call the same function here so the decoder uses the
	 * canonical value regardless of how the formula evolves upstream.
	 * Populate before interpolatePalette runs, so Nc=6/7 interpolation
	 * sees the correct palette[1] at its source indices 0..63.
	 *
	 * Master only — slave_palette_placement_index covers index 1 via
	 * color_counter=4 within array bounds (separate concerns out of scope).
	 */
	if(color_number > 8)
	{
		jab_byte default_palette[256 * 3] = {0};
		genColorPalette(color_number, default_palette);
		for(jab_int32 panel = 0; panel < COLOR_PALETTE_NUMBER; panel++)
		{
			jab_int32 panel_offset = panel * color_number * 3;
			symbol->palette[panel_offset + 3] = default_palette[3];  // palette[1].R
			symbol->palette[panel_offset + 4] = default_palette[4];  // palette[1].G
			symbol->palette[panel_offset + 5] = default_palette[5];  // palette[1].B
		}
	}

	//interpolate the palette if there are more than 64 colors
	if(color_number > 64)
	{
		interpolatePalette(symbol->palette, color_number);
	}

	return JAB_SUCCESS;
}

/**
 * @brief Read the color palettes in master symbol
 * @param matrix the symbol matrix
 * @param symbol the slave symbol
 * @param data_map the data module positions
 * @return JAB_SUCCESS | FATAL_ERROR
*/
jab_int32 readColorPaletteInSlave(jab_bitmap* matrix, jab_decoded_symbol* symbol, jab_byte* data_map)
{
	//allocate buffer for palette
	jab_int32 color_number = (jab_int32)pow(2, symbol->metadata.Nc + 1);
	free(symbol->palette);
	/* WS-4.5.4: calloc required for the same reason as master (decoder.c:243). */
	symbol->palette = (jab_byte*)calloc(1, color_number * sizeof(jab_byte) * 3 * COLOR_PALETTE_NUMBER);
    if(symbol->palette == NULL)
    {
		reportError("Memory allocation for slave palette failed");
		return FATAL_ERROR;
    }

    //read colors from alignment patterns
    jab_int32 color_index;			//the color index number in color palette
	for(jab_int32 i=0; i<COLOR_PALETTE_NUMBER; i++)
	{
		jab_vector2d p1, p2;
		getColorPalettePosInFP(i, matrix->width, matrix->height, &p1, &p2);
		//color 0
		color_index = slave_palette_placement_index[0] % color_number;
		writeColorPalette(matrix, symbol, i, color_index, p1.x, p1.y);
		//color 1
		color_index = slave_palette_placement_index[1] % color_number;
		writeColorPalette(matrix, symbol, i, color_index, p2.x, p2.y);
	}

	//read colors from metadata
	jab_int32 color_counter = 2;	//the color counter
	while(color_counter < MIN(color_number, 64))
	{
		jab_int32 px, py;

		//color palette 0
		px = slave_palette_position[color_counter-2].x;
		py = slave_palette_position[color_counter-2].y;
		color_index = slave_palette_placement_index[color_counter] % color_number;
		writeColorPalette(matrix, symbol, 0, color_index, px, py);
		//set data map
		data_map[py * matrix->width + px] = 1;

		//color palette 1
		px = matrix->width - 1 - slave_palette_position[color_counter-2].y;
		py = slave_palette_position[color_counter-2].x;
		color_index = slave_palette_placement_index[color_counter] % color_number;
		writeColorPalette(matrix, symbol, 1, color_index, px, py);
		//set data map
		data_map[py * matrix->width + px] = 1;

		//color palette 2
		px = matrix->width - 1 - slave_palette_position[color_counter-2].x;
		py = matrix->height - 1 - slave_palette_position[color_counter-2].y;
		color_index = slave_palette_placement_index[color_counter] % color_number;
		writeColorPalette(matrix, symbol, 2, color_index, px, py);
		//set data map
		data_map[py * matrix->width + px] = 1;

		//color palette 3
		px = slave_palette_position[color_counter-2].y;
		py = matrix->height - 1 - slave_palette_position[color_counter-2].x;
		color_index = slave_palette_placement_index[color_counter] % color_number;
		writeColorPalette(matrix, symbol, 3, color_index, px, py);
		//set data map
		data_map[py * matrix->width + px] = 1;

		//next color
		color_counter++;
	}

	//interpolate the palette if there are more than 64 colors
	if(color_number > 64)
	{
		interpolatePalette(symbol->palette, color_number);
	}
	return JAB_SUCCESS;
}

/**
 * @brief Get the index of the nearest color palette
 * @param matrix the symbol matrix
 * @param x the x coordinate of the module
 * @param y the y coordinate of the module
 * @return the index of the nearest color palette
*/
jab_int32 getNearestPalette(jab_bitmap* matrix, jab_int32 x, jab_int32 y)
{
	//set the palette coordinate
	jab_int32 px[COLOR_PALETTE_NUMBER], py[COLOR_PALETTE_NUMBER];
	px[0] = DISTANCE_TO_BORDER - 1 + 3;
	py[0] = DISTANCE_TO_BORDER - 1;
	px[1] = matrix->width - DISTANCE_TO_BORDER - 3;
	py[1] = DISTANCE_TO_BORDER - 1;
	px[2] = matrix->width - DISTANCE_TO_BORDER - 3;
	py[2] = matrix->height- DISTANCE_TO_BORDER;
	px[3] = DISTANCE_TO_BORDER - 1 + 3;
	py[3] = matrix->height- DISTANCE_TO_BORDER;

	//calculate the nearest palette
	jab_float min = DIST(0, 0, matrix->width, matrix->height);
	jab_int32 p_index = 0;
	for(jab_int32 i=0; i<COLOR_PALETTE_NUMBER; i++)
	{
		jab_float dist = DIST(x, y, px[i], py[i]);
		if(dist < min)
		{
			min = dist;
			p_index = i;
		}
	}
	
	return p_index;
}

/**
 * @brief Decode a module using hard decision
 * @param matrix the symbol matrix
 * @param palette the color palettes
 * @param color_number the number of module colors
 * @param norm_palette the normalized color palettes
 * @param pal_ths the palette RGB value thresholds
 * @param x the x coordinate of the module
 * @param y the y coordinate of the module
 * @return the decoded value
*/
jab_byte decodeModuleHD(jab_bitmap* matrix, jab_byte* palette, jab_int32 color_number, jab_float* norm_palette, jab_float* pal_ths, jab_int32 x, jab_int32 y)
{
	//get the nearest palette
	jab_int32 p_index = getNearestPalette(matrix, x, y);
	

	//read the RGB values
	jab_byte rgb[3];
	jab_int32 mtx_bytes_per_pixel = matrix->bits_per_pixel / 8;
    jab_int32 mtx_bytes_per_row = matrix->width * mtx_bytes_per_pixel;
    jab_int32 mtx_offset = y * mtx_bytes_per_row + x * mtx_bytes_per_pixel;
	rgb[0] = matrix->pixel[mtx_offset + 0];
	rgb[1] = matrix->pixel[mtx_offset + 1];
	rgb[2] = matrix->pixel[mtx_offset + 2];

#ifdef USE_FP_CALIBRATION
	/* WS-4 Step 4.4: normalize observed module RGB against the active
	 * calibration profile (observed → standard). No-op when calibration
	 * inactive — preserves Mode 1 Cassandra gate behavior by default. */
	if (jabHasCalibration()) {
		jab_byte normalized[3];
		jabRemapColorInverse(rgb, normalized);
		rgb[0] = normalized[0];
		rgb[1] = normalized[1];
		rgb[2] = normalized[2];
	}
#endif

	jab_byte index1 = 0, index2 = 0;

	//check black module
	if(rgb[0] < pal_ths[p_index*3 + 0] && rgb[1] < pal_ths[p_index*3 + 1] && rgb[2] < pal_ths[p_index*3 + 2])
	{
		index1 = 0;
		return index1;
	}
	if(palette)
	{
		// For 16+ color modes, use direct RGB comparison (normalized comparison fails for same-hue colors)
		// For 4/8 color modes, normalized comparison works fine
		jab_boolean use_direct_rgb = (color_number > 8);

#ifdef USE_LAB_DISTANCE
		/* WS-4 Step 4.2: CIE Lab ΔE2000 perceptual discrimination path.
		 * Activates only for Nc≥3 (color_number > 8); Nc<3 remains on the
		 * normalized-RGB path below (Mode 1 regression gate untouched).
		 * Sample is converted to Lab ONCE outside the loop; palette colors
		 * are converted lazily inside the loop. The min1/min2/index1/index2
		 * outputs preserve the same semantics as the RGB path so all
		 * downstream logic (B/W disambiguation, second-best match handling)
		 * works identically. */
		if(use_direct_rgb)
		{
			jab_rgb_color sample_rgb_struct = { rgb[0], rgb[1], rgb[2] };
			jab_lab_color sample_lab = rgb_to_lab(sample_rgb_struct);

			jab_float min1 = 1e30f, min2 = 1e30f;
			for(jab_int32 i=0; i<color_number; i++)
			{
				jab_rgb_color pal_rgb_struct;
				pal_rgb_struct.r = palette[color_number*3*p_index + i*3 + 0];
				pal_rgb_struct.g = palette[color_number*3*p_index + i*3 + 1];
				pal_rgb_struct.b = palette[color_number*3*p_index + i*3 + 2];
				jab_lab_color pal_lab = rgb_to_lab(pal_rgb_struct);

				jab_float diff = delta_e_2000(sample_lab, pal_lab);

				if(diff < min1)
				{
					min2 = min1;
					index2 = index1;
					min1 = diff;
					index1 = (jab_byte)i;
				}
				else if(diff < min2)
				{
					min2 = diff;
					index2 = (jab_byte)i;
				}
			}
		}
		else
#endif /* USE_LAB_DISTANCE */
		{
	    //normalize the RGB values
        jab_float rgb_max = MAX(rgb[0], MAX(rgb[1], rgb[2]));
        jab_float r = use_direct_rgb ? (jab_float)rgb[0] : ((jab_float)rgb[0] / rgb_max);
        jab_float g = use_direct_rgb ? (jab_float)rgb[1] : ((jab_float)rgb[1] / rgb_max);
        jab_float b = use_direct_rgb ? (jab_float)rgb[2] : ((jab_float)rgb[2] / rgb_max);

		jab_float min1 = 255*255*3, min2 = 255*255*3;
		for(jab_int32 i=0; i<color_number; i++)
		{
			jab_float pr, pg, pb;
			if(use_direct_rgb) {
				// Use actual palette RGB values for direct comparison
				// CRITICAL FIX: Must include palette slot offset (p_index) for multi-palette support
				pr = (jab_float)palette[color_number*3*p_index + i*3 + 0];
				pg = (jab_float)palette[color_number*3*p_index + i*3 + 1];
				pb = (jab_float)palette[color_number*3*p_index + i*3 + 2];
			} else {
				// Use normalized palette values
				pr = norm_palette[color_number*4*p_index + i*4 + 0];
				pg = norm_palette[color_number*4*p_index + i*4 + 1];
				pb = norm_palette[color_number*4*p_index + i*4 + 2];
			}

			//compare the module color with palette
			jab_float diff = (pr - r) * (pr - r) + (pg - g) * (pg - g) + (pb - b) * (pb - b);

			if(diff < min1)
			{
				//copy min1 to min2
				min2 = min1;
				index2 = index1;
				//update min1
				min1 = diff;
				index1 = (jab_byte)i;
			}
			else if(diff < min2)
			{
				min2 = diff;
				index2 = (jab_byte)i;
			}
		}
		} /* end of #ifdef USE_LAB_DISTANCE else-block */

		// Black/white disambiguation: use actual last color index (color_number-1) instead of hardcoded 7
		jab_int32 white_index = color_number - 1;
		if(index1 == 0 || index1 == white_index)
		{
			jab_int32 rgb_sum = rgb[0] + rgb[1] + rgb[2];
			jab_int32 p0_sum = palette[color_number*3*p_index + 0*3 + 0] + palette[color_number*3*p_index + 0*3 + 1] + palette[color_number*3*p_index + 0*3 + 2];
			jab_int32 pw_sum = palette[color_number*3*p_index + white_index*3 + 0] + palette[color_number*3*p_index + white_index*3 + 1] + palette[color_number*3*p_index + white_index*3 + 2];

			if(rgb_sum < ((p0_sum + pw_sum) / 2))
			{
				index1 = 0;
			}
			else
			{
				index1 = (jab_byte)white_index;
			}
		}
		
		//if the minimum is close to the second minimum, do further match
/*		if(min1 * 1.5 > min2)
		{
			//printf("min1(%d) * 1.5 > min2(%d), %d %d %d", index1, index2, rgb[0], rgb[1], rgb[2]);
			jab_int32 rg = abs(rgb[0] - rgb[1]);
			jab_int32 rb = abs(rgb[0] - rgb[2]);
			jab_int32 gb = abs(rgb[1] - rgb[2]);

			jab_int32 c1rg = abs(palette[color_number*3*p_index + index1*3 + 0] - palette[color_number*3*p_index + index1*3 + 1]);
			jab_int32 c1rb = abs(palette[color_number*3*p_index + index1*3 + 0] - palette[color_number*3*p_index + index1*3 + 2]);
			jab_int32 c1gb = abs(palette[color_number*3*p_index + index1*3 + 1] - palette[color_number*3*p_index + index1*3 + 2]);
			jab_int32 diff1 = abs(rg - c1rg) + abs(rb - c1rb) + abs(gb - c1gb);

			jab_int32 c2rg = abs(palette[color_number*3*p_index + index2*3 + 0] - palette[color_number*3*p_index + index2*3 + 1]);
			jab_int32 c2rb = abs(palette[color_number*3*p_index + index2*3 + 0] - palette[color_number*3*p_index + index2*3 + 2]);
			jab_int32 c2gb = abs(palette[color_number*3*p_index + index2*3 + 1] - palette[color_number*3*p_index + index2*3 + 2]);
			jab_int32 diff2 = abs(rg - c2rg) + abs(rb - c2rb) + abs(gb - c2gb);

			if(diff2 < diff1)
				index1 = index2;
			//printf("final: %d\n", index1);
		}
*/
	}
	else	//if no palette is available, decode the module as black/white
	{
		index1 = ((rgb[0] > 100 ? 1 : 0) + (rgb[1] > 100 ? 1 : 0) + (rgb[2] > 100 ? 1 : 0)) > 1 ? 1 : 0;
	}
	return index1;
}

/**
 * @brief Decode a module for PartI (Nc) of the metadata of master symbol
 * @param rgb the pixel value in RGB format
 * @return the decoded value
*/
jab_byte decodeModuleNc(jab_byte* rgb)
{
	// FIX: For 16+ color modes, check for exact matches to base palette colors first
	// Part I always uses black(0,0,0), cyan(0,255,255), yellow(255,255,0)
	jab_int32 tolerance = 80; // Camera-captured blacks read up to ~(60,40,50) due to screen glow + ambient light

	// Check for black (index 0)
	if(rgb[0] < tolerance && rgb[1] < tolerance && rgb[2] < tolerance)
		return 0;
	if(rgb[0] < tolerance && rgb[1] > (255-tolerance) && rgb[2] > (255-tolerance))
		return 3;
	if(rgb[0] > (255-tolerance) && rgb[1] > (255-tolerance) && rgb[2] < tolerance)
		return 6;
	
	// Fallback to original algorithm for 4/8-color modes or imperfect colors
	jab_double ths_std = 0.08;
	jab_double ave, var;
	getAveVar(rgb, &ave, &var);
	jab_double std = sqrt(var);	//standard deviation
	jab_byte min, mid, max;
	jab_int32 index_min, index_mid, index_max;
	getMinMax(rgb, &min, &mid, &max, &index_min, &index_mid, &index_max);
	std /= (jab_double)max;	//normalize std
	jab_byte bits[3];
	if(std > ths_std)
	{
		bits[index_max] = 1;
		bits[index_min] = 0;
		// Handle pure color cases where min==mid (e.g., RGB(0,0,255))
		if(rgb[index_mid] == 0 || rgb[index_min] == 0)
		{
			// Pure color: only one channel is non-zero
			bits[index_mid] = (rgb[index_mid] > rgb[index_min]) ? 1 : 0;
		}
		else
		{
			jab_double r1 = (jab_double)rgb[index_mid] / (jab_double)rgb[index_min];
			jab_double r2 = (jab_double)rgb[index_max] / (jab_double)rgb[index_mid];
			if(r1 > r2)
				bits[index_mid] = 1;
			else
				bits[index_mid] = 0;
		}
	}
	else
	{
		return 7;//111
	}
	jab_byte result = ((bits[0] << 2) + (bits[1] << 1) + bits[2]);
	return result;
}

/**
 * @brief Get the pixel value thresholds for each channel of the colors in the palette
 * @param palette the color palette
 * @param color_number the number of colors
 * @param palette_ths the palette RGB value thresholds
*/
void getPaletteThreshold(jab_byte* palette, jab_int32 color_number, jab_float* palette_ths)
{
	if(color_number == 4)
	{
		jab_int32 cpr0 = MAX(palette[0], palette[3]);
		jab_int32 cpr1 = MIN(palette[6], palette[9]);
		jab_int32 cpg0 = MAX(palette[1], palette[7]);
		jab_int32 cpg1 = MIN(palette[4], palette[10]);
		jab_int32 cpb0 = MAX(palette[8], palette[11]);
		jab_int32 cpb1 = MIN(palette[2], palette[5]);

		palette_ths[0] = (cpr0 + cpr1) / 2.0f;
		palette_ths[1] = (cpg0 + cpg1) / 2.0f;
		palette_ths[2] = (cpb0 + cpb1) / 2.0f;
	}
	else if(color_number == 8)
	{
		jab_int32 cpr0 = MAX(MAX(MAX(palette[0], palette[3]), palette[6]), palette[9]);
		jab_int32 cpr1 = MIN(MIN(MIN(palette[12], palette[15]), palette[18]), palette[21]);
		jab_int32 cpg0 = MAX(MAX(MAX(palette[1], palette[4]), palette[13]), palette[16]);
		jab_int32 cpg1 = MIN(MIN(MIN(palette[7], palette[10]), palette[19]), palette[22]);
		jab_int32 cpb0 = MAX(MAX(MAX(palette[2], palette[8]), palette[14]), palette[20]);
		jab_int32 cpb1 = MIN(MIN(MIN(palette[5], palette[11]), palette[17]), palette[23]);

		palette_ths[0] = (cpr0 + cpr1) / 2.0f;
		palette_ths[1] = (cpg0 + cpg1) / 2.0f;
		palette_ths[2] = (cpb0 + cpb1) / 2.0f;
	}
}

/**
 * @brief Get the coordinate of the next metadata module in master symbol
 * @param matrix_height the height of the matrix
 * @param matrix_width the width of the matrix
 * @param next_module_count the index number of the next module
 * @param x the x coordinate of the current and the next module
 * @param y the y coordinate of the current and the next module
*/
void getNextMetadataModuleInMaster(jab_int32 matrix_height, jab_int32 matrix_width, jab_int32 next_module_count, jab_int32* x, jab_int32* y)
{
	// Flip coordinates based on modulo pattern
	if(next_module_count % 4 == 0 || next_module_count % 4 == 2)
	{
		(*y) = matrix_height - 1 - (*y);
	}
	if(next_module_count % 4 == 1 || next_module_count % 4 == 3)
	{
		(*x) = matrix_width -1 - (*x);
	}
	
	// Advance coordinates
	jab_int32 mod4 = next_module_count % 4;
	
	if(mod4 == 0)
	{
		// Y-increment ranges: lengths 21, 25, 29, 33, 37, 41, 45, 49 (+4 per cycle)
        if( next_module_count <= 20 ||
           (next_module_count >= 44  && next_module_count <= 68)  ||
           (next_module_count >= 96  && next_module_count <= 124) ||
           (next_module_count >= 156 && next_module_count <= 188) ||
           (next_module_count >= 224 && next_module_count <= 260) ||
           (next_module_count >= 300 && next_module_count <= 340) ||
           (next_module_count >= 384 && next_module_count <= 428) ||
           (next_module_count >= 476 && next_module_count <= 524))
		{
			(*y) += 1;
		}
		// X-decrement ranges: lengths 23, 27, 31, 35, 39, 43, 47 (+4 per cycle)
        else if((next_module_count > 20  && next_module_count < 44)  ||
                (next_module_count > 68  && next_module_count < 96)  ||
                (next_module_count > 124 && next_module_count < 156) ||
                (next_module_count > 188 && next_module_count < 224) ||
                (next_module_count > 260 && next_module_count < 300) ||
                (next_module_count > 340 && next_module_count < 384) ||
                (next_module_count > 428 && next_module_count < 476))
		{
			(*x) -= 1;
		}
	}
	else if(mod4 == 1 || mod4 == 2 || mod4 == 3)
	{
		// For very high module counts (>260, needed for 128-color), also advance on mod1/2/3
		// Note: 64-color needs up to module 259, so we delay this until >260 to avoid cycles
		if(next_module_count > 260)
		{
			if((next_module_count >= 300 && next_module_count <= 340) ||
			   (next_module_count >= 384 && next_module_count <= 428) ||
			   (next_module_count >= 476 && next_module_count <= 524))
			{
				(*y) += 1;
			}
			else if((next_module_count > 260 && next_module_count < 300) ||
			        (next_module_count > 340 && next_module_count < 384) ||
			        (next_module_count > 428 && next_module_count < 476))
			{
				(*x) -= 1;
			}
		}
	}
	
	// Coordinate swap points: Occur at transition between cycles
    if(next_module_count == 44  || next_module_count == 96  || next_module_count == 156 ||
       next_module_count == 224 || next_module_count == 300 || next_module_count == 384 ||
       next_module_count == 476)
    {
        jab_int32 tmp = (*x);
        (*x) = (*y);
        (*y) = tmp;
    }
}

/**
 * @brief Decode slave symbol metadata
 * @param host_symbol the host symbol
 * @param docked_position the docked position
 * @param data the data stream of the host symbol
 * @param offset the metadata start offset in the data stream
 * @return the read metadata bit length | DECODE_METADATA_FAILED
*/
jab_int32 decodeSlaveMetadata(jab_decoded_symbol* host_symbol, jab_int32 docked_position, jab_data* data, jab_int32 offset)
{
	//set metadata from host symbol
	host_symbol->slave_metadata[docked_position].Nc = host_symbol->metadata.Nc;
	host_symbol->slave_metadata[docked_position].mask_type = host_symbol->metadata.mask_type;
	host_symbol->slave_metadata[docked_position].docked_position = 0;

	//decode metadata
	jab_int32 index = offset;
	jab_uint32 SS, SE, V, E;

	//parse part1
	if(index < 0) return DECODE_METADATA_FAILED;
	SS = data->data[index--];//SS
	if(SS == 0)
	{
		host_symbol->slave_metadata[docked_position].side_version = host_symbol->metadata.side_version;
	}
	if(index < 0) return DECODE_METADATA_FAILED;
	SE = data->data[index--];//SE
	if(SE == 0)
	{
		host_symbol->slave_metadata[docked_position].ecl = host_symbol->metadata.ecl;
	}
	//decode part2 if it exists
	if(SS == 1)
	{
		if((index-4) < 0) return DECODE_METADATA_FAILED;
		V = 0;
		for(jab_int32 i=0; i<5; i++)
		{
			V += data->data[index--] << (4 - i);
		}
		jab_int32 side_version = V + 1;
		if(docked_position == 2 || docked_position == 3)
		{
			host_symbol->slave_metadata[docked_position].side_version.y = host_symbol->metadata.side_version.y;
			host_symbol->slave_metadata[docked_position].side_version.x = side_version;
		}
		else
		{
			host_symbol->slave_metadata[docked_position].side_version.x = host_symbol->metadata.side_version.x;
			host_symbol->slave_metadata[docked_position].side_version.y = side_version;
		}
	}
	if(SE == 1)
	{
		if((index-5) < 0) return DECODE_METADATA_FAILED;
		//get wc (the first half of E)
		E = 0;
		for(jab_int32 i=0; i<3; i++)
		{
			E += data->data[index--] << (2 - i);
		}
		host_symbol->slave_metadata[docked_position].ecl.x = E + 3;	//wc = E_part1 + 3
		//get wr (the second half of E)
		E = 0;
		for(jab_int32 i=0; i<3; i++)
		{
			E += data->data[index--] << (2 - i);
		}
		host_symbol->slave_metadata[docked_position].ecl.y = E + 4;	//wr = E_part2 + 4

		//check wc and wr
		jab_int32 wc = host_symbol->slave_metadata[docked_position].ecl.x;
		jab_int32 wr = host_symbol->slave_metadata[docked_position].ecl.y;
		if(wc >= wr)
		{
			reportError("Incorrect error correction parameter in slave metadata");
			return DECODE_METADATA_FAILED;
		}
	}
	return (offset - index);
}

/**
 * @brief Decode the encoded bits of Nc from the module color
 * @param module1_color the color of the first module
 * @param module2_color the color of the second module
 * @return the decoded bits
*/
jab_byte decodeNcModuleColor(jab_byte module1_color, jab_byte module2_color)
{
	for(jab_int32 i=0; i<8; i++)
	{
		if(module1_color == nc_color_encode_table[i][0] && module2_color == nc_color_encode_table[i][1])
			return i;
	}
	return 8; //if no match, return an invalid value
}

/**
 * @brief Decode the PartI of master symbol metadata
 * @param matrix the symbol matrix
 * @param symbol the master symbol
 * @param data_map the data module positions
 * @param module_count the index number of the next module
 * @param x the x coordinate of the current and the next module
 * @param y the y coordinate of the current and the next module
 * @return JAB_SUCCESS | JAB_FAILURE | DECODE_METADATA_FAILED
*/
jab_int32 decodeMasterMetadataPartI(jab_bitmap* matrix, jab_decoded_symbol* symbol, jab_byte* data_map, jab_int32* module_count, jab_int32* x, jab_int32* y)
{
	//decode Nc module color
	jab_byte module_color[MASTER_METADATA_PART1_MODULE_NUMBER];
	jab_int32 mtx_bytes_per_pixel = matrix->bits_per_pixel / 8;
    jab_int32 mtx_bytes_per_row = matrix->width * mtx_bytes_per_pixel;
    jab_int32 mtx_offset;
	while((*module_count) < MASTER_METADATA_PART1_MODULE_NUMBER)
	{
		//decode bit out of the module at (x,y)
		mtx_offset = (*y) * mtx_bytes_per_row + (*x) * mtx_bytes_per_pixel;
		jab_byte rgb =  decodeModuleNc(&matrix->pixel[mtx_offset]);
		if(rgb != 0 && rgb != 3 && rgb != 6)
		{
#if TEST_MODE
		reportError("Invalid module color in primary metadata part 1 found");
#endif
			return DECODE_METADATA_FAILED;
		}
		module_color[*module_count] = rgb;
		//set data map
		data_map[(*y) * matrix->width + (*x)] = 1;
		//go to the next module
		(*module_count)++;
		getNextMetadataModuleInMaster(matrix->height, matrix->width, (*module_count), x, y);
	}

	//decode encoded Nc
	jab_byte bits[2];
	bits[0] = decodeNcModuleColor(module_color[0], module_color[1]);	//the first 3 bits
	bits[1] = decodeNcModuleColor(module_color[2], module_color[3]);	//the last 3 bits
	if(bits[0] > 7 || bits[1] > 7)
	{
#if TEST_MODE
		reportError("Invalid color combination in primary metadata part 1 found");
#endif
		return DECODE_METADATA_FAILED;
	}
	//set bits in part1
	jab_byte part1[MASTER_METADATA_PART1_LENGTH] = {0};			//6 encoded bits
	jab_int32 bit_count = 0;
	for(jab_int32 n=0; n<2; n++)
	{
		for(jab_int32 i=0; i<3; i++)
		{
			jab_byte bit = (bits[n] >> (2 - i)) & 0x01;
			part1[bit_count] = bit;
			bit_count++;
		}
	}

	//decode ldpc for part1
	if( !decodeLDPChd(part1, MASTER_METADATA_PART1_LENGTH, 2, 0) )
	{
#if TEST_MODE
		reportError("LDPC decoding for master metadata part 1 failed");
#endif
		return JAB_FAILURE;
	}
	DEBUG_LOG("[PartI] LDPC decode SUCCESS, Nc=%d", (part1[0] << 2) + (part1[1] << 1) + part1[2]);
	//parse part1
	symbol->metadata.Nc = (part1[0] << 2) + (part1[1] << 1) + part1[2];

	return JAB_SUCCESS;
}

/**
 * @brief Decode the PartII of master symbol metadata
 * @param matrix the symbol matrix
 * @param symbol the master symbol
 * @param data_map the data module positions
 * @param norm_palette the normalized color palettes
 * @param pal_ths the palette RGB value thresholds
 * @param module_count the index number of the next module
 * @param x the x coordinate of the current and the next module
 * @param y the y coordinate of the current and the next module
 * @return JAB_SUCCESS | JAB_FAILURE | DECODE_METADATA_FAILED | FATAL_ERROR
*/
jab_int32 decodeMasterMetadataPartII(jab_bitmap* matrix, jab_decoded_symbol* symbol, jab_byte* data_map, jab_float* norm_palette, jab_float* pal_ths, jab_int32* module_count, jab_int32* x, jab_int32* y)
{
	jab_uint32 V, E;
	jab_uint32 V_length = 10, E_length = 6;

	jab_int32 color_number = (jab_int32)pow(2, symbol->metadata.Nc + 1);
	jab_int32 bits_per_module = (jab_int32)(log(color_number) / log(2));

    // Calculate how many modules are needed for Part II metadata
    jab_int32 modules_needed = (MASTER_METADATA_PART2_LENGTH + bits_per_module - 1) / bits_per_module;
    jab_int32 total_bits = modules_needed * bits_per_module;  // Includes padding bits
    
    // Allocate array for all bits including padding
    jab_byte* part2 = (jab_byte*)calloc(total_bits, sizeof(jab_byte));
    if(part2 == NULL) {
        reportError("Memory allocation for Part II metadata failed");
        return FATAL_ERROR;
    }
	jab_int32 part2_bit_count = 0;

    //read part2 - read ALL modules completely including padding bits
    for(jab_int32 mod=0; mod<modules_needed; mod++)
    {
		//decode bits out of the module at (x,y)
		jab_byte bits = decodeModuleHD(matrix, symbol->palette, color_number, norm_palette, pal_ths, *x, *y);
		//write ALL bits from this module into part2
		for(jab_int32 i=0; i<bits_per_module; i++)
		{
			jab_byte bit = (bits >> (bits_per_module - 1 - i)) & 0x01;
			part2[part2_bit_count] = bit;
			part2_bit_count++;
		}
		//set data map
		data_map[(*y) * matrix->width + (*x)] = 1;
		//go to the next module
		(*module_count)++;
		getNextMetadataModuleInMaster(matrix->height, matrix->width, (*module_count), x, y);
    }

	//decode ldpc for part2 using EXACTLY 38 bits (encoder outputs 38, padding is only for module alignment)
	if( !decodeLDPChd(part2, MASTER_METADATA_PART2_LENGTH, 2, 0) )
	{
		free(part2);
#if TEST_MODE
		reportError("LDPC decoding for master metadata part 2 failed");
#endif
		return DECODE_METADATA_FAILED;
	}

    //parse part2
	//read V
	//get horizontal side version
	V = 0;
	for(jab_int32 i=0; i<V_length/2; i++)
	{
		V += part2[i] << (V_length/2 - 1 - i);
	}
	symbol->metadata.side_version.x = V + 1;
	//get vertical side version
	V = 0;
	for(jab_int32 i=0; i<V_length/2; i++)
	{
		V += part2[i+V_length/2] << (V_length/2 - 1 - i);
	}
	symbol->metadata.side_version.y = V + 1;

	//read E
	jab_int32 bit_index = V_length;
	//get wc (the first half of E)
	E = 0;
	for(jab_int32 i=bit_index; i<(bit_index+E_length/2); i++)
	{
		E += part2[i] << (E_length/2 - 1 - (i - bit_index));
	}
	symbol->metadata.ecl.x = E + 3;		//wc = E_part1 + 3
	//get wr (the second half of E)
	E = 0;
	for(jab_int32 i=bit_index; i<(bit_index+E_length/2); i++)
	{
		E += part2[i+E_length/2] << (E_length/2 - 1 - (i - bit_index));
	}
	symbol->metadata.ecl.y = E + 4;		//wr = E_part2 + 4

	//read MSK
	bit_index = V_length + E_length;
	symbol->metadata.mask_type = (part2[bit_index+0] << 2) + (part2[bit_index+1] << 1) + part2[bit_index+2];

	symbol->metadata.docked_position = 0;

	//check side version
	symbol->side_size.x = VERSION2SIZE(symbol->metadata.side_version.x);
	symbol->side_size.y = VERSION2SIZE(symbol->metadata.side_version.y);
	if(matrix->width != symbol->side_size.x || matrix->height != symbol->side_size.y)
	{
		reportError("Primary symbol matrix size does not match the metadata");
		free(part2);
		return JAB_FAILURE;
	}
	//check wc and wr
	jab_int32 wc = symbol->metadata.ecl.x;
	jab_int32 wr = symbol->metadata.ecl.y;
	if(wc >= wr)
	{
		reportError("Incorrect error correction parameter in primary symbol metadata");
		free(part2);
		return DECODE_METADATA_FAILED;
	}
	free(part2);
	return JAB_SUCCESS;
}

/**
 * @brief Decode data modules
 * @param matrix the symbol matrix
 * @param symbol the symbol to be decoded
 * @param data_map the data module positions
 * @param norm_palette the normalized color palettes
 * @param pal_ths the palette RGB value thresholds
 * @return the decoded data | NULL if failed
*/
jab_data* readRawModuleData(jab_bitmap* matrix, jab_decoded_symbol* symbol, jab_byte* data_map, jab_float* norm_palette, jab_float* pal_ths)
{
    jab_int32 color_number = (jab_int32)pow(2, symbol->metadata.Nc + 1);
	jab_int32 module_count = 0;
    jab_data* data = (jab_data*)malloc(sizeof(jab_data) + matrix->width * matrix->height * sizeof(jab_char));
    if(data == NULL)
	{
		reportError("Memory allocation for raw module data failed");
		return NULL;
	}

#if TEST_MODE
	jab_byte decoded_module_color_index[matrix->height * matrix->width];
#endif

	for(jab_int32 j=0; j<matrix->width; j++)
	{
		for(jab_int32 i=0; i<matrix->height; i++)
		{
			if(data_map[i*matrix->width + j] == 0)
			{
				//decode bits out of the module at (x,y)
				jab_byte bits = decodeModuleHD(matrix, symbol->palette, color_number, norm_palette, pal_ths, j, i);
				//write the bits into data
				data->data[module_count] = (jab_char)bits;
				module_count++;
#if TEST_MODE
				decoded_module_color_index[i*matrix->width + j] = bits;
#endif
			}
			else
			{
#if TEST_MODE
				decoded_module_color_index[i*matrix->width + j] = 255;
#endif
			}
		}
	}
	data->length = module_count;

#if TEST_MODE
	FILE* fp1 = fopen("jab_dec_module_sampled_rgb.raw", "wb");
	FILE* fp2 = fopen("jab_dec_module_decoded_rgb.raw", "wb");
	for(jab_int32 i=0; i<matrix->height; i++)
	{
		for(jab_int32 j=0; j<matrix->width; j++)
		{
			jab_byte rgb1[3], rgb2[3];
			jab_int32 mtx_bytes_per_pixel = matrix->bits_per_pixel / 8;
			jab_int32 mtx_bytes_per_row = matrix->width * mtx_bytes_per_pixel;
			jab_int32 mtx_offset = i * mtx_bytes_per_row + j * mtx_bytes_per_pixel;
			rgb1[0] = matrix->pixel[mtx_offset + 0];
			rgb1[1] = matrix->pixel[mtx_offset + 1];
			rgb1[2] = matrix->pixel[mtx_offset + 2];

			if(data_map[i*matrix->width + j] == 0)
			{
				jab_int32 index = decoded_module_color_index[i*matrix->width + j];
				rgb2[0] = jab_default_palette[index*3 + 0];
				rgb2[1] = jab_default_palette[index*3 + 1];
				rgb2[2] = jab_default_palette[index*3 + 2];
			}
			else
			{
				rgb2[0] = rgb1[0];
				rgb2[1] = rgb1[1];
				rgb2[2] = rgb1[2];
				//rgb1[0] = rgb2[0] = 128;
				//rgb1[1] = rgb2[1] = 128;
				//rgb1[2] = rgb2[2] = 128;
			}
			fwrite(rgb1, 3, 1, fp1);
			fwrite(rgb2, 3, 1, fp2);
		}
	}
	fclose(fp1);
	fclose(fp2);
#endif // TEST_MODE

	return data;
}

/**
 * @brief Convert multi-bit-per-byte raw module data to one-bit-per-byte raw data
 * @param raw_module_data the input raw module data
 * @param bits_per_module the number of bits per module
 * @return the converted data | NULL if failed
*/
jab_data* rawModuleData2RawData(jab_data* raw_module_data, jab_int32 bits_per_module)
{
	//
	jab_data* raw_data = (jab_data *)malloc(sizeof(jab_data) + raw_module_data->length * bits_per_module * sizeof(jab_char));
    if(raw_data == NULL)
	{
		reportError("Memory allocation for raw data failed");
		return NULL;
	}
	for(jab_int32 i=0; i<raw_module_data->length; i++)
	{
		for(jab_int32 j=0; j<bits_per_module; j++)
		{
			raw_data->data[i * bits_per_module + j] = (raw_module_data->data[i] >> (bits_per_module - 1 - j)) & 0x01;
		}
	}
	raw_data->length = raw_module_data->length * bits_per_module;
	return raw_data;
}

/**
 * @brief Mark the positions of finder patterns and alignment patterns in the data map
 * @param data_map the data module positions
 * @param width the width of the data map
 * @param height the height of the data map
 * @param type the symbol type, 0: master, 1: slave
*/
void fillDataMap(jab_byte* data_map, jab_int32 width, jab_int32 height, jab_int32 type)
{
	jab_int32 side_ver_x_index = SIZE2VERSION(width) - 1;
	jab_int32 side_ver_y_index = SIZE2VERSION(height) - 1;
    jab_int32 number_of_ap_x = jab_ap_num[side_ver_x_index];
    jab_int32 number_of_ap_y = jab_ap_num[side_ver_y_index];
    for(jab_int32 i=0; i<number_of_ap_y; i++)
    {
		for(jab_int32 j=0; j<number_of_ap_x; j++)
		{
			//the center coordinate
			jab_int32 x_offset = jab_ap_pos[side_ver_x_index][j] - 1;
            jab_int32 y_offset = jab_ap_pos[side_ver_y_index][i] - 1;
			//the cross
			data_map[y_offset 		* width + x_offset]		  =
			data_map[y_offset		* width + (x_offset - 1)] =
			data_map[y_offset		* width + (x_offset + 1)] =
			data_map[(y_offset - 1) * width + x_offset] 	  =
			data_map[(y_offset + 1) * width + x_offset] 	  = 1;

			//the diagonal modules
			if(i == 0 && (j == 0 || j == number_of_ap_x - 1))	//at finder pattern 0 and 1 positions
			{
				data_map[(y_offset - 1) * width + (x_offset - 1)] =
				data_map[(y_offset + 1) * width + (x_offset + 1)] = 1;
				if(type == 0)	//master symbol
				{
					data_map[(y_offset - 2) * width + (x_offset - 2)] =
					data_map[(y_offset - 2) * width + (x_offset - 1)] =
					data_map[(y_offset - 2) * width +  x_offset] 	  =
					data_map[(y_offset - 1) * width + (x_offset - 2)] =
					data_map[ y_offset		* width + (x_offset - 2)] = 1;

					data_map[(y_offset + 2) * width + (x_offset + 2)] =
					data_map[(y_offset + 2) * width + (x_offset + 1)] =
					data_map[(y_offset + 2) * width +  x_offset] 	  =
					data_map[(y_offset + 1) * width + (x_offset + 2)] =
					data_map[ y_offset		* width + (x_offset + 2)] = 1;
				}
			}
			else if(i == number_of_ap_y - 1 && (j == 0 || j == number_of_ap_x - 1))	//at finder pattern 2 and 3 positions
			{
				data_map[(y_offset - 1) * width + (x_offset + 1)] =
				data_map[(y_offset + 1) * width + (x_offset - 1)] = 1;
				if(type == 0) 	//master symbol
				{
					data_map[(y_offset - 2) * width + (x_offset + 2)] =
					data_map[(y_offset - 2) * width + (x_offset + 1)] =
					data_map[(y_offset - 2) * width +  x_offset] 	  =
					data_map[(y_offset - 1) * width + (x_offset + 2)] =
					data_map[ y_offset		* width + (x_offset + 2)] = 1;

					data_map[(y_offset + 2) * width + (x_offset - 2)] =
					data_map[(y_offset + 2) * width + (x_offset - 1)] =
					data_map[(y_offset + 2) * width +  x_offset] 	  =
					data_map[(y_offset + 1) * width + (x_offset - 2)] =
					data_map[ y_offset		* width + (x_offset - 2)] = 1;
				}
			}
			else	//at other positions
			{
				//even row, even column / odd row, odd column
				if( (i % 2 == 0 && j % 2 == 0) || (i % 2 == 1 && j % 2 == 1))
				{
					data_map[(y_offset - 1) * width + (x_offset - 1)] =
					data_map[(y_offset + 1) * width + (x_offset + 1)] = 1;
				}
				//odd row, even column / even row, old column
				else
				{
					data_map[(y_offset - 1) * width + (x_offset + 1)] =
					data_map[(y_offset + 1) * width + (x_offset - 1)] = 1;
				}
			}
		}
    }
}

/**
 * @brief Load default metadata values and color palettes for master symbol
 * @param matrix the symbol matrix
 * @param symbol the master symbol
*/
void loadDefaultMasterMetadata(jab_bitmap* matrix, jab_decoded_symbol* symbol)
{
#if TEST_MODE
	JAB_REPORT_INFO(("Loading default master metadata"))
#endif
	//set default metadata values
	symbol->metadata.default_mode = 1;
	symbol->metadata.Nc = DEFAULT_MODULE_COLOR_MODE;
	symbol->metadata.ecl.x = ecclevel2wcwr[DEFAULT_ECC_LEVEL][0];
	symbol->metadata.ecl.y = ecclevel2wcwr[DEFAULT_ECC_LEVEL][1];
	symbol->metadata.mask_type = DEFAULT_MASKING_REFERENCE;
	symbol->metadata.docked_position = 0;							//no default value
	symbol->metadata.side_version.x = SIZE2VERSION(matrix->width);	//no default value
	symbol->metadata.side_version.y = SIZE2VERSION(matrix->height);	//no default value
}

/**
 * @brief Decode symbol
 * @param matrix the symbol matrix
 * @param symbol the symbol to be decoded
 * @param data_map the data module positions
 * @param norm_palette the normalized color palettes
 * @param pal_ths the palette RGB value thresholds
 * @param type the symbol type, 0: master, 1: slave
 * @return JAB_SUCCESS | JAB_FAILURE | DECODE_METADATA_FAILED | FATAL_ERROR
*/
jab_int32 decodeSymbol(jab_bitmap* matrix, jab_decoded_symbol* symbol, jab_byte* data_map, jab_float* norm_palette, jab_float* pal_ths, jab_int32 type)
{
#ifdef USE_FP_CALIBRATION
	/* WS-4 Step 4.4b: build calibration from FP-core observations before
	 * any module sampling. Samples matrix at module (3,3) for K and, for
	 * Nc≥2, modules (W-4,H-4) for Y and (3,H-4) for C. On CLEAN encoded
	 * input observations equal canonical → calibration is identity →
	 * jabRemapColorInverse in decodeModuleHD is a literal no-op. Under
	 * camera noise the observations diverge and the inverse remap
	 * normalizes module samples back toward the palette. */
	{
		jab_int32 fp_cal_color_number = (jab_int32)pow(2, symbol->metadata.Nc + 1);
		jabBuildCalibrationFromFPCores(matrix, fp_cal_color_number);
	}
#endif

#if TEST_MODE
	/* Fix for stale debug code: compute color_number from Nc instead of using
	 * an undefined variable. Preserves the diagnostic intent. */
	jab_int32 color_number = (jab_int32)pow(2, symbol->metadata.Nc + 1);
	printf("p1:\n");
	for(int i=0;i<color_number;i++)
	{
		printf("%d\t%d\t%d\n", symbol->palette[i*3], symbol->palette[i*3+1], symbol->palette[i*3+2]);
	}
	printf("p2:\n");
	for(int i=0;i<color_number;i++)
	{
		printf("%d\t%d\t%d\n", symbol->palette[3*color_number+i*3], symbol->palette[3*color_number+i*3+1], symbol->palette[3*color_number+i*3+2]);
	}
	printf("p3:\n");
	for(int i=0;i<color_number;i++)
	{
		printf("%d\t%d\t%d\n", symbol->palette[3*color_number*2+i*3], symbol->palette[3*color_number*2+i*3+1], symbol->palette[3*color_number*2+i*3+2]);
	}
	printf("p4:\n");
	for(int i=0;i<color_number;i++)
	{
		printf("%d\t%d\t%d\n", symbol->palette[3*color_number*3+i*3], symbol->palette[3*color_number*3+i*3+1], symbol->palette[3*color_number*3+i*3+2]);
	}
#endif // TEST_MODE

	//fill data map - skip if synthetic decoder already set it (Pg > 0 indicates synthetic mode)
	if(symbol->metadata.Pg == 0) {
		fillDataMap(data_map, matrix->width, matrix->height, type);
	}

	//read raw data
	jab_data* raw_module_data = readRawModuleData(matrix, symbol, data_map, norm_palette, pal_ths);
	if(raw_module_data == NULL)
	{
		JAB_REPORT_ERROR(("Reading raw module data in symbol %d failed", symbol->index))
		free(data_map);
		return FATAL_ERROR;
	}
#if TEST_MODE
	FILE* fp = fopen("jab_dec_module_data.bin", "wb");
	fwrite(raw_module_data->data, raw_module_data->length, 1, fp);
	fclose(fp);
#endif // TEST_MODE

	//demask
	demaskSymbol(raw_module_data, data_map, symbol->side_size, symbol->metadata.mask_type, (jab_int32)pow(2, symbol->metadata.Nc + 1));
	
	free(data_map);
#if TEST_MODE
	fp = fopen("jab_demasked_module_data.bin", "wb");
	fwrite(raw_module_data->data, raw_module_data->length, 1, fp);
	fclose(fp);
#endif // TEST_MODE

	//change to one-bit-per-byte representation
	jab_data* raw_data = rawModuleData2RawData(raw_module_data, symbol->metadata.Nc + 1);
	free(raw_module_data);
	if(raw_data == NULL)
	{
		JAB_REPORT_ERROR(("Reading raw data in symbol %d failed", symbol->index))
		return FATAL_ERROR;
	}

	//calculate Pn and Pg
	jab_int32 wc = symbol->metadata.ecl.x;
	jab_int32 wr = symbol->metadata.ecl.y;
	// Use encoder's Pg if available (synthetic decoder), otherwise calculate from raw_data->length
	jab_int32 Pg;
	if(symbol->metadata.Pg > 0) {
		Pg = symbol->metadata.Pg;  // Use encoder's exact Pg value
	} else {
		Pg = (raw_data->length / wr) * wr;  // Calculate: max_gross_payload = floor(capacity / wr) * wr
	}
    jab_int32 Pn = Pg * (wr - wc) / wr;				//code_rate = 1 - wc/wr = (wr - wc)/wr, max_net_payload = max_gross_payload * code_rate

	//deinterleave data
	raw_data->length = Pg;	//drop the padding bits
    deinterleaveData(raw_data);

#if TEST_MODE
	JAB_REPORT_INFO(("wc:%d, wr:%d, Pg:%d, Pn: %d", wc, wr, Pg, Pn))
	fp = fopen("jab_dec_bit_data.bin", "wb");
	fwrite(raw_data->data, raw_data->length, 1, fp);
	fclose(fp);
#endif // TEST_MODE

	//decode ldpc
    if(decodeLDPChd((jab_byte*)raw_data->data, Pg, symbol->metadata.ecl.x, symbol->metadata.ecl.y) != Pn)
    {
		/* WS-2 Step 2.2: symbol decode failed in LDPC stage. */
		JAB_REPORT_INFO(("DIAG_SYMBOL_DECODE result=ldpc_fail Nc=%d Pg=%d Pn=%d",
		                 symbol->metadata.Nc, Pg, Pn));
		JAB_REPORT_ERROR(("LDPC decoding for data in symbol %d failed", symbol->index))
		free(raw_data);
		return JAB_FAILURE;
	}
	DEBUG_LOG("[DECODE] SUCCESS Nc=%d (%d bytes)", symbol->metadata.Nc, Pn);
	/* WS-2 Step 2.2: per-stage diagnostic marker — symbol decode complete.
	 * Pg = gross payload (raw module bits / bits-per-module), Pn = net payload
	 * (after LDPC). Includes a checksum of the decoded raw data for fast
	 * cross-trace integrity comparison. */
	{
		jab_int32 _data_checksum = 0;
		for(jab_int32 _i = 0; _i < Pn; _i++) {
			_data_checksum = (_data_checksum * 31) + ((jab_byte*)raw_data->data)[_i];
		}
		JAB_REPORT_INFO(("DIAG_SYMBOL_DECODE result=ok Nc=%d Pg=%d Pn=%d checksum=0x%08x",
		                 symbol->metadata.Nc, Pg, Pn, _data_checksum));
	}

	//find the start flag of metadata
	jab_int32 metadata_offset = Pn - 1;
	while(raw_data->data[metadata_offset] == 0)
	{
		metadata_offset--;
	}
	//skip the flag bit
	metadata_offset--;
	//set docked positions in host metadata
	symbol->metadata.docked_position = 0;
	for(jab_int32 i=0; i<4; i++)
	{
		if(type == 1)	//if host is a slave symbol
		{
			if(i == symbol->host_position) continue; //skip host position
		}
		symbol->metadata.docked_position += raw_data->data[metadata_offset--] << (3 - i);
	}
	//decode metadata for docked slave symbols
	for(jab_int32 i=0; i<4; i++)
	{
		if(symbol->metadata.docked_position & (0x08 >> i))
		{
			jab_int32 read_bit_length = decodeSlaveMetadata(symbol, i, raw_data, metadata_offset);
			if(read_bit_length == DECODE_METADATA_FAILED)
			{
				free(raw_data);
				return DECODE_METADATA_FAILED;
			}
			metadata_offset -= read_bit_length;
		}
	}

	//copy the decoded data to symbol
	jab_int32 net_data_length = metadata_offset + 1;
	symbol->data = (jab_data *)malloc(sizeof(jab_data) + net_data_length * sizeof(jab_char));
	if(symbol->data == NULL)
	{
		reportError("Memory allocation for symbol data failed");
		free(raw_data);
		return FATAL_ERROR;
	}
	symbol->data->length = net_data_length;
	memcpy(symbol->data->data, raw_data->data, net_data_length);

	//clean memory
	free(raw_data);
	return JAB_SUCCESS;
}

void normalizeColorPalette(jab_decoded_symbol* symbol, jab_float* norm_palette, jab_int32 color_number)
{
	for(jab_int32 i=0; i<(color_number * COLOR_PALETTE_NUMBER); i++)
	{
		jab_float rgb_max = MAX(symbol->palette[i*3 + 0], MAX(symbol->palette[i*3 + 1], symbol->palette[i*3 + 2]));
		norm_palette[i*4 + 0] = (jab_float)symbol->palette[i*3 + 0] / rgb_max;
		norm_palette[i*4 + 1] = (jab_float)symbol->palette[i*3 + 1] / rgb_max;
		norm_palette[i*4 + 2] = (jab_float)symbol->palette[i*3 + 2] / rgb_max;
		norm_palette[i*4 + 3] = ((symbol->palette[i*3 + 0] + symbol->palette[i*3 + 1] + symbol->palette[i*3 + 2]) / 3.0f) / 255.0f; ;
	}
}

/**
 * @brief Decode master symbol
 * @param matrix the symbol matrix
 * @param symbol the master symbol
 * @return JAB_SUCCESS | JAB_FAILURE | FATAL_ERROR
*/
jab_int32 decodeMaster(jab_bitmap* matrix, jab_decoded_symbol* symbol)
{
	if(matrix == NULL)
	{
		reportError("Invalid master symbol matrix");
		return FATAL_ERROR;
	}

	//create data map
	jab_byte* data_map = (jab_byte*)calloc(1, matrix->width*matrix->height*sizeof(jab_byte));
	if(data_map == NULL)
	{
		reportError("Memory allocation for data map in master failed");
		return FATAL_ERROR;
	}

	//decode metadata and color palette
	jab_int32 x = MASTER_METADATA_X;
	jab_int32 y = MASTER_METADATA_Y;
	jab_int32 module_count = 0;

	//decode metadata PartI (Nc)
	jab_int32 decode_partI_ret = decodeMasterMetadataPartI(matrix, symbol, data_map, &module_count, &x, &y);
	if(decode_partI_ret == JAB_FAILURE)
	{
		free(data_map);
		return JAB_FAILURE;
	}
	if(decode_partI_ret == DECODE_METADATA_FAILED)
	{
		//reset variables
		x = MASTER_METADATA_X;
		y = MASTER_METADATA_Y;
		module_count = 0;
		//clear data_map
		memset(data_map, 0, matrix->width*matrix->height*sizeof(jab_byte));
		//load default metadata and color palette
		loadDefaultMasterMetadata(matrix, symbol);
	}

	//save state after PartI for potential Nc retry
	jab_int32 x_postP1 = x, y_postP1 = y, mc_postP1 = module_count;
	size_t dm_size = (size_t)matrix->width * matrix->height * sizeof(jab_byte);
	jab_byte* dm_postP1 = (jab_byte*)malloc(dm_size);
	if(dm_postP1) memcpy(dm_postP1, data_map, dm_size);

	jab_byte original_Nc = symbol->metadata.Nc;
	jab_byte nc_order[] = {original_Nc, 1, 0, 2, 3, 4, 5, 6};
	jab_int32 nc_tries = 8;

	for(jab_int32 nc_idx = 0; nc_idx < nc_tries; nc_idx++)
	{
		if(nc_idx > 0 && nc_order[nc_idx] == original_Nc) continue;

		if(nc_idx > 0)
		{
			if(!dm_postP1) break;
			x = x_postP1; y = y_postP1; module_count = mc_postP1;
			memcpy(data_map, dm_postP1, dm_size);
			symbol->metadata.Nc = nc_order[nc_idx];
			JAB_REPORT_INFO(("Nc_FALLBACK: Retrying with Nc=%d (try %d/%d, original=%d)",
				nc_order[nc_idx], nc_idx+1, nc_tries, original_Nc));
		}

		//read color palettes
		if(readColorPaletteInMaster(matrix, symbol, data_map, &module_count, &x, &y) < 0)
		{
			continue;
		}

		/* WS-2 Step 2.2: per-stage diagnostic marker — palette learning complete.
		 * Captures Nc, color count, and a deterministic hash of the four palette
		 * slots so trace comparison across decode attempts can detect divergence.
		 * See: docs/jabcode-all-nc-plan/02-diagnostic-instrumentation.md */
		{
			jab_int32 _palette_color_n = (jab_int32)pow(2, symbol->metadata.Nc + 1);
			jab_int32 _palette_hash = 0;
			jab_int32 _palette_bytes = _palette_color_n * 3 * COLOR_PALETTE_NUMBER;
			for(jab_int32 _i = 0; _i < _palette_bytes && _i < 768; _i++) {
				_palette_hash = (_palette_hash * 31) + symbol->palette[_i];
			}
			JAB_REPORT_INFO(("DIAG_PALETTE_LEARNED Nc=%d colors=%d hash=0x%08x",
			                 symbol->metadata.Nc, _palette_color_n, _palette_hash));
		}

		//normalize the RGB values in color palettes
		jab_int32 color_number = (jab_int32)pow(2, symbol->metadata.Nc + 1);
		jab_float norm_palette[color_number * 4 * COLOR_PALETTE_NUMBER];
		normalizeColorPalette(symbol, norm_palette, color_number);

		//get the palette RGB thresholds
		/* WS-4.5.4: zero-initialize pal_ths because getPaletteThreshold writes
		 * outputs only when color_number == 4 or 8 (see decoder.c:698). For
		 * Nc=0 (color_number=2) and Nc>=3 (color_number>=16), it returns
		 * without writing — leaving the VLA as uninitialized stack memory,
		 * which the "early black module" check at decodeModuleHD reads as
		 * `rgb[k] < pal_ths[...]`. Zero-init makes that comparison evaluate
		 * to false for any non-negative rgb byte, preserving "no early
		 * threshold" semantics for unhandled color modes. */
		jab_float pal_ths[3 * COLOR_PALETTE_NUMBER] = {0};
		for(jab_int32 i=0; i<COLOR_PALETTE_NUMBER; i++)
		{
			getPaletteThreshold(symbol->palette + (color_number*3)*i, color_number, &pal_ths[i*3]);
		}

		//decode metadata PartII
		jab_boolean partII_ok = 0;
		if(decode_partI_ret == JAB_SUCCESS)
		{
			jab_int32 part2_result = decodeMasterMetadataPartII(matrix, symbol, data_map, norm_palette, pal_ths, &module_count, &x, &y);
			if(part2_result > 0) partII_ok = 1;
			/* WS-2 Step 2.2: per-stage diagnostic marker — PartII metadata decode result. */
			JAB_REPORT_INFO(("DIAG_PARTII_RESULT result=%d Nc=%d ok=%d",
			                 part2_result, symbol->metadata.Nc, (int)partII_ok));
		}
		else
		{
			partII_ok = 1;
			/* WS-2 Step 2.2: PartI failed; PartII skipped. Still emit marker for
			 * consistent trace structure. */
			JAB_REPORT_INFO(("DIAG_PARTII_RESULT result=skipped Nc=%d ok=1",
			                 symbol->metadata.Nc));
		}

		if(!partII_ok) continue;

		//decode master symbol — give decodeSymbol its own copy because it frees data_map internally
		jab_byte* dm_copy = (jab_byte*)malloc(dm_size);
		if(!dm_copy) break;
		memcpy(dm_copy, data_map, dm_size);
		jab_int32 sym_ret = decodeSymbol(matrix, symbol, dm_copy, norm_palette, pal_ths, 0);
		if(sym_ret == JAB_SUCCESS)
		{
			if(nc_idx > 0) JAB_REPORT_INFO(("Nc_FALLBACK: SUCCESS with Nc=%d (original was %d)", symbol->metadata.Nc, original_Nc));
			free(dm_postP1);
			free(data_map);
			return JAB_SUCCESS;
		}
	}

	free(dm_postP1);
	free(data_map);
	return JAB_FAILURE;
}

/**
 * @brief Decode slave symbol
 * @param matrix the symbol matrix
 * @param symbol the slave symbol
 * @return JAB_SUCCESS | JAB_FAILURE | FATAL_ERROR
*/
jab_int32 decodeSlave(jab_bitmap* matrix, jab_decoded_symbol* symbol)
{
	if(matrix == NULL)
	{
		reportError("Invalid slave symbol matrix");
		return FATAL_ERROR;
	}

	//create data map
	jab_byte* data_map = (jab_byte*)calloc(1, matrix->width*matrix->height*sizeof(jab_byte));
	if(data_map == NULL)
	{
		reportError("Memory allocation for data map in slave failed");
		return FATAL_ERROR;
	}

	//read color palettes
	if(readColorPaletteInSlave(matrix, symbol, data_map) < 0)
	{
		reportError("Reading color palettes in slave symbol failed");
		free(data_map);
		return FATAL_ERROR;
	}

	//normalize the RGB values in color palettes
	jab_int32 color_number = (jab_int32)pow(2, symbol->metadata.Nc + 1);
	jab_float norm_palette[color_number * 4 * COLOR_PALETTE_NUMBER];	//each color contains 4 normalized values, i.e. R, G, B and Luminance
	normalizeColorPalette(symbol, norm_palette, color_number);

	//get the palette RGB thresholds
	/* WS-4.5.4: zero-init pal_ths (see master version above for rationale). */
	jab_float pal_ths[3 * COLOR_PALETTE_NUMBER] = {0};
	for(jab_int32 i=0; i<COLOR_PALETTE_NUMBER; i++)
	{
		getPaletteThreshold(symbol->palette + i*3, color_number, &pal_ths[i*3]);
	}

	//decode slave symbol
	return decodeSymbol(matrix, symbol, data_map, norm_palette, pal_ths, 1);
}

/**
 * @brief Read bit data
 * @param data the data buffer
 * @param start the start reading offset
 * @param length the length of the data to be read
 * @param value the read data
 * @return the length of the read data
*/
jab_int32 readData(jab_data* data, jab_int32 start, jab_int32 length, jab_int32* value)
{
	jab_int32 i;
	jab_int32 val = 0;
	for(i=start; i<(start + length) && i<data->length; i++)
	{
		val += data->data[i] << (length - 1 - (i - start));
	}
	*value = val;
	return (i - start);
}

/**
 * @brief Interpret decoded bits
 * @param bits the input bits
 * @return the data message
*/
jab_data* decodeData(jab_data* bits)
{
	jab_byte* decoded_bytes = (jab_byte *)malloc(bits->length * sizeof(jab_byte));
	if(decoded_bytes == NULL)
	{
		reportError("Memory allocation for decoded bytes failed");
		return NULL;
	}

	jab_encode_mode mode = Upper;
	jab_encode_mode pre_mode = None;
	jab_int32 index = 0;	//index of input bits
	jab_int32 count = 0;	//index of decoded bytes

	while(index < bits->length)
	{
		//read the encoded value
		jab_boolean flag = 0;
		jab_int32 value = 0;
        jab_int32 n;
        if(mode != Byte)
        {
            n = readData(bits, index, character_size[mode], &value);
            if(n < character_size[mode])	//did not read enough bits
                break;
            //update index
            index += character_size[mode];
        }

		//decode value
		switch(mode)
		{
			case Upper:
				if(value <= 26)
				{
					decoded_bytes[count++] = jab_decoding_table_upper[value];
					if(pre_mode != None)
						mode = pre_mode;
				}
				else
				{
					switch(value)
					{
						case 27:
							mode = Punct;
							pre_mode = Upper;
							break;
						case 28:
							mode = Lower;
							pre_mode = None;
							break;
						case 29:
							mode = Numeric;
							pre_mode = None;
							break;
						case 30:
							mode = Alphanumeric;
							pre_mode = None;
							break;
						case 31:
							//read 2 bits more
							n = readData(bits, index, 2, &value);
							if(n < 2)	//did not read enough bits
							{
								flag = 1;
								break;
							}
							//update index
							index += 2;
							switch(value)
							{
								case 0:
									mode = Byte;
									pre_mode = Upper;
									break;
								case 1:
									mode = Mixed;
									pre_mode = Upper;
									break;
								case 2:
									mode = ECI;
									pre_mode = None;
									break;
								case 3:
									flag = 1;		//end of message (EOM)
									break;
							}
							break;
						default:
							reportError("Invalid value decoded");
							free(decoded_bytes);
							return NULL;
					}
				}
				break;
			case Lower:
				if(value <= 26)
				{
					decoded_bytes[count++] = jab_decoding_table_lower[value];
					if(pre_mode != None)
						mode = pre_mode;
				}
				else
				{
					switch(value)
					{
						case 27:
							mode = Punct;
							pre_mode = Lower;
							break;
						case 28:
							mode = Upper;
							pre_mode = Lower;
							break;
						case 29:
							mode = Numeric;
							pre_mode = None;
							break;
						case 30:
							mode = Alphanumeric;
							pre_mode = None;
							break;
						case 31:
							//read 2 bits more
							n = readData(bits, index, 2, &value);
							if(n < 2)	//did not read enough bits
							{
								flag = 1;
								break;
							}
							//update index
							index += 2;
							switch(value)
							{
								case 0:
									mode = Byte;
									pre_mode = Lower;
									break;
								case 1:
									mode = Mixed;
									pre_mode = Lower;
									break;
								case 2:
									mode = Upper;
									pre_mode = None;
									break;
								case 3:
									mode = FNC1;
									pre_mode = None;
									break;
							}
							break;
						default:
							reportError("Invalid value decoded");
							free(decoded_bytes);
							return NULL;
					}
				}
				break;
			case Numeric:
				if(value <= 12)
				{
					decoded_bytes[count++] = jab_decoding_table_numeric[value];
					if(pre_mode != None)
						mode = pre_mode;
				}
				else
				{
					switch(value)
					{
						case 13:
							mode = Punct;
							pre_mode = Numeric;
							break;
						case 14:
							mode = Upper;
							pre_mode = None;
							break;
						case 15:
							//read 2 bits more
							n = readData(bits, index, 2, &value);
							if(n < 2)	//did not read enough bits
							{
								flag = 1;
								break;
							}
							//update index
							index += 2;
							switch(value)
							{
								case 0:
									mode = Byte;
									pre_mode = Numeric;
									break;
								case 1:
									mode = Mixed;
									pre_mode = Numeric;
									break;
								case 2:
									mode = Upper;
									pre_mode = Numeric;
									break;
								case 3:
									mode = Lower;
									pre_mode = None;
									break;
							}
							break;
						default:
							reportError("Invalid value decoded");
							free(decoded_bytes);
							return NULL;
					}
				}
				break;
			case Punct:
				if(value >=0 && value <= 15)
				{
					decoded_bytes[count++] = jab_decoding_table_punct[value];
					mode = pre_mode;
				}
				else
				{
					reportError("Invalid value decoded");
					free(decoded_bytes);
					return NULL;
				}
				break;
			case Mixed:
				if(value >=0 && value <= 31)
				{
					if(value == 19)
					{
						decoded_bytes[count++] = 10;
						decoded_bytes[count++] = 13;
					}
					else if(value == 20)
					{
						decoded_bytes[count++] = 44;
						decoded_bytes[count++] = 32;
					}
					else if(value == 21)
					{
						decoded_bytes[count++] = 46;
						decoded_bytes[count++] = 32;
					}
					else if(value == 22)
					{
						decoded_bytes[count++] = 58;
						decoded_bytes[count++] = 32;
					}
					else
					{
						decoded_bytes[count++] = jab_decoding_table_mixed[value];
					}
					mode = pre_mode;
				}
				else
				{
					reportError("Invalid value decoded");
					free(decoded_bytes);
					return NULL;
				}
				break;
			case Alphanumeric:
				if(value <= 62)
				{
					decoded_bytes[count++] = jab_decoding_table_alphanumeric[value];
					if(pre_mode != None)
						mode = pre_mode;
				}
				else if(value == 63)
				{
					//read 2 bits more
					n = readData(bits, index, 2, &value);
					if(n < 2)	//did not read enough bits
					{
						flag = 1;
						break;
					}
					//update index
					index += 2;
					switch(value)
					{
						case 0:
							mode = Byte;
							pre_mode = Alphanumeric;
							break;
						case 1:
							mode = Mixed;
							pre_mode = Alphanumeric;
							break;
						case 2:
							mode = Punct;
							pre_mode = Alphanumeric;
							break;
						case 3:
							mode = Upper;
							pre_mode = None;
							break;
					}
				}
				else
				{
					reportError("Invalid value decoded");
					free(decoded_bytes);
					return NULL;
				}
				break;
			case Byte:
			{
				//read 4 bits more
				n = readData(bits, index, 4, &value);
				if(n < 4)	//did not read enough bits
				{
                    reportError("Not enough bits to decode");
					free(decoded_bytes);
					return NULL;
				}
				//update index
				index += 4;
				if(value == 0)		//read the next 13 bits
				{
					//read 13 bits more
					n = readData(bits, index, 13, &value);
					if(n < 13)	//did not read enough bits
					{
                        reportError("Not enough bits to decode");
						free(decoded_bytes);
						return NULL;
					}
                    value += 15+1;	//the number of encoded bytes = value + 15
					//update index
					index += 13;
				}
				jab_int32 byte_length = value;
				//read the next (byte_length * 8) bits
				for(jab_int32 i=0; i<byte_length; i++)
				{
					n = readData(bits, index, 8, &value);
					if(n < 8)	//did not read enough bits
					{
                        reportError("Not enough bits to decode");
						free(decoded_bytes);
						return NULL;
					}
					//update index
					index += 8;
					decoded_bytes[count++] = (jab_byte)value;
				}
				mode = pre_mode;
				break;
			}
			case ECI:
				//TODO: not implemented
				index += bits->length;
				break;
			case FNC1:
				//TODO: not implemented
				index += bits->length;
				break;
			case None:
				reportError("Decoding mode is None.");
				index += bits->length;
				break;
		}
		if(flag) break;
	}

	//copy decoded data
	jab_data* decoded_data = (jab_data *)malloc(sizeof(jab_data) + count * sizeof(jab_byte));
	if(decoded_data == NULL){
        reportError("Memory allocation for decoded data failed");
        return NULL;
    }
    decoded_data->length = count;
    memcpy(decoded_data->data, decoded_bytes, count);

	free(decoded_bytes);
	return decoded_data;
}

/*
 * WS-6 Option F ABI-compatibility stub.
 *
 * The Panama JAR shipped with jab-auth-jabcode was generated from the panama-poc
 * fork, which exports `resetDecoderState` for thread-local observation-context
 * cleanup as part of its adaptive-palette subsystem. The Panama wrapper's
 * generated nested class `jabcode_h$resetDecoderState` performs a
 * `SymbolLookup.findOrThrow("resetDecoderState")` at <clinit>; if the symbol is
 * absent, the JAR raises `NoSuchElementException` at first reference site.
 *
 * The swift-java-poc branch carries the WS-0/2/3 work (Mode 0 monochrome,
 * diagnostic markers, Nc=7) but does NOT include the adaptive observation
 * context — there is genuinely no decoder state to reset. Per the council's
 * Option F decision, we export this symbol as a documented no-op so the Panama
 * JAR's symbol lookup succeeds without requiring either JAR regeneration or
 * Java-side reflection-call modification.
 *
 * If/when the panama-poc adaptive system is merged onto swift-java-poc, this
 * stub is removed and the full implementation from panama-poc:decoder.c
 * replaces it.
 *
 * See: docs/jabcode-all-nc-plan/mode0-investigation/09-ws6-option-f-resolution.md
 */
void resetDecoderState(void)
{
	/* No-op: swift-java-poc has no observation context to reset. */
}
