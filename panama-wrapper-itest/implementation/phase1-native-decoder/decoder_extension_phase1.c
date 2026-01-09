/**
 * PHASE 1: Native Decoder Extension for Color Modes 3-5
 * 
 * This file contains the extended getPaletteThreshold() function
 * to support 16, 32, and 64 color modes.
 * 
 * Original file: src/jabcode/decoder.c
 * Function: getPaletteThreshold() (lines 561-589)
 * 
 * Palette structures (from encoder.c genColorPalette):
 * - 16 colors: R=4, G=2, B=2 levels → R: {0,85,170,255}, G: {0,255}, B: {0,255}
 * - 32 colors: R=4, G=4, B=2 levels → R: {0,85,170,255}, G: {0,85,170,255}, B: {0,255}
 * - 64 colors: R=4, G=4, B=4 levels → R: {0,85,170,255}, G: {0,85,170,255}, B: {0,85,170,255}
 * 
 * Palette layout: nested loops R→G→B
 * Index = (r_level * vg * vb + g_level * vb + b_level) * 3
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
	else if(color_number == 16)
	{
		/**
		 * 16 colors: 4×2×2 (R×G×B)
		 * R: 4 levels {0, 85, 170, 255} → 3 thresholds
		 * G: 2 levels {0, 255} → 1 threshold
		 * B: 2 levels {0, 255} → 1 threshold
		 * 
		 * Fixed thresholds based on known palette values:
		 * - R: 42.5, 127.5, 212.5 (midpoints between 0-85, 85-170, 170-255)
		 * - G: 127.5 (midpoint between 0-255)
		 * - B: 127.5 (midpoint between 0-255)
		 */
		
		palette_ths[0] = 42.5f;   // R: between 0 and 85
		palette_ths[1] = 127.5f;  // R: between 85 and 170
		palette_ths[2] = 212.5f;  // R: between 170 and 255
		palette_ths[3] = 127.5f;  // G: between 0 and 255
		palette_ths[4] = 127.5f;  // B: between 0 and 255
	}
	else if(color_number == 32)
	{
		/**
		 * 32 colors: 4×4×2 (R×G×B)
		 * R: 4 levels {0, 85, 170, 255} → 3 thresholds
		 * G: 4 levels {0, 85, 170, 255} → 3 thresholds
		 * B: 2 levels {0, 255} → 1 threshold
		 * 
		 * Fixed thresholds:
		 * - R: 42.5, 127.5, 212.5
		 * - G: 42.5, 127.5, 212.5
		 * - B: 127.5
		 */
		
		palette_ths[0] = 42.5f;   // R: between 0 and 85
		palette_ths[1] = 127.5f;  // R: between 85 and 170
		palette_ths[2] = 212.5f;  // R: between 170 and 255
		palette_ths[3] = 42.5f;   // G: between 0 and 85
		palette_ths[4] = 127.5f;  // G: between 85 and 170
		palette_ths[5] = 212.5f;  // G: between 170 and 255
		palette_ths[6] = 127.5f;  // B: between 0 and 255
	}
	else if(color_number == 64)
	{
		/**
		 * 64 colors: 4×4×4 (R×G×B)
		 * R: 4 levels {0, 85, 170, 255} → 3 thresholds
		 * G: 4 levels {0, 85, 170, 255} → 3 thresholds
		 * B: 4 levels {0, 85, 170, 255} → 3 thresholds
		 * 
		 * Fixed thresholds (all channels same):
		 * - 42.5, 127.5, 212.5
		 */
		
		palette_ths[0] = 42.5f;   // R: between 0 and 85
		palette_ths[1] = 127.5f;  // R: between 85 and 170
		palette_ths[2] = 212.5f;  // R: between 170 and 255
		palette_ths[3] = 42.5f;   // G: between 0 and 85
		palette_ths[4] = 127.5f;  // G: between 85 and 170
		palette_ths[5] = 212.5f;  // G: between 170 and 255
		palette_ths[6] = 42.5f;   // B: between 0 and 85
		palette_ths[7] = 127.5f;  // B: between 85 and 170
		palette_ths[8] = 212.5f;  // B: between 170 and 255
	}
}
