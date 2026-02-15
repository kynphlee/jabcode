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

#endif // JABCODE_COLOR_CALIBRATION_H
