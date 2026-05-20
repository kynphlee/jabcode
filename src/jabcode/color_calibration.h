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

#endif // JABCODE_COLOR_CALIBRATION_H
