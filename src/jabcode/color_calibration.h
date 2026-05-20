#ifndef JABCODE_COLOR_CALIBRATION_H
#define JABCODE_COLOR_CALIBRATION_H

#include "jabcode.h"

typedef struct {
    jab_byte standard_colors[8][3];
    jab_byte calibrated_colors[8][3];
    jab_boolean is_active;
} jab_color_calibration;

jab_int32 jabLoadCalibrationFromJSON(const char* json_string);
void jabApplyCalibration(jab_encode* enc);
void jabRemapColor(const jab_byte* rgb_in, jab_byte* rgb_out);
void jabClearCalibration();
jab_boolean jabHasCalibration();

/* WS-4 Step 4.4: decode-direction calibration primitives.
 *
 * jabCalibrateFromObservedRGB — populate the calibration's calibrated_colors[]
 *   from an array of 8 observed RGB triples indexed by standard color slot:
 *     [0]=K, [1]=W, [2]=R, [3]=G, [4]=B, [5]=Y, [6]=C, [7]=M
 *   For slots the caller did not observe (e.g., the camera only sees K, Y, C
 *   from FP cores), pass the corresponding standard_colors[i] entry so that
 *   jabRemapColorInverse leaves those colors unmapped. Sets is_active=1.
 *
 * jabRemapColorInverse — inverse of jabRemapColor: take an OBSERVED (camera)
 *   RGB and return the STANDARD RGB whose calibrated entry matches. Used by
 *   the decoder's pre-sample pass to normalize observed pixels against the
 *   palette before classification. Exact-match lookup with pass-through for
 *   non-matching inputs (symmetric to jabRemapColor).
 *
 * See: docs/jabcode-all-nc-plan/00-CHECKLIST.md item 4.4
 *      src/jabcode/test/test_color_calibration.c (regression gate)
 */
void jabCalibrateFromObservedRGB(const jab_byte observed[8][3]);
void jabRemapColorInverse(const jab_byte* rgb_in, jab_byte* rgb_out);

/* WS-4 Step 4.4b: build calibration from FP-core observations.
 *
 * Samples the (module-coordinate) `matrix` bitmap at the canonical FP center
 * positions to derive the camera's observed RGB for known-truth colors:
 *
 *   FP0 (TL) module (3, 3)                       → observed K (all Nc)
 *   FP2 (BR) module (width-4, height-4) [Nc≥2]   → observed Y
 *   FP3 (BL) module (3, height-4)        [Nc≥2]  → observed C
 *
 * Other palette slots (W, R, G, B, M) default to canonical (no shift), so
 * jabRemapColorInverse leaves those colors unmapped. On CLEAN encoded input
 * the observations equal canonical → calibration is identity → no behavioral
 * change (Mode 1 Cassandra gate held by construction).
 *
 * `color_number` parameter gates whether Y/C are sampled: pass the symbol's
 * encoded color_number (= 2^(Nc+1)). Pass 0 to disable Y/C sampling (K-only).
 *
 * No-op if matrix is NULL, has zero dimensions, or color_number is invalid.
 *
 * See: docs/jabcode-all-nc-plan/00-CHECKLIST.md item 4.4b
 */
void jabBuildCalibrationFromFPCores(const jab_bitmap* matrix, jab_int32 color_number);

#endif // JABCODE_COLOR_CALIBRATION_H
