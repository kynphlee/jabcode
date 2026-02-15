#include "color_calibration.h"
#include <string.h>
#include <stdlib.h>
#include <stdio.h>

static jab_color_calibration g_calibration = {0};

static void parseColorMapping(const char* json_section, jab_byte* rgb_out);

jab_int32 jabLoadCalibrationFromJSON(const char* json_string) {
    if (!json_string) {
        return 0;
    }
    
    memset(&g_calibration, 0, sizeof(jab_color_calibration));
    
    g_calibration.standard_colors[0][0] = 0;   g_calibration.standard_colors[0][1] = 0;   g_calibration.standard_colors[0][2] = 0;     // Black
    g_calibration.standard_colors[1][0] = 255; g_calibration.standard_colors[1][1] = 255; g_calibration.standard_colors[1][2] = 255;   // White
    g_calibration.standard_colors[2][0] = 255; g_calibration.standard_colors[2][1] = 0;   g_calibration.standard_colors[2][2] = 0;     // Red
    g_calibration.standard_colors[3][0] = 0;   g_calibration.standard_colors[3][1] = 255; g_calibration.standard_colors[3][2] = 0;     // Green
    g_calibration.standard_colors[4][0] = 0;   g_calibration.standard_colors[4][1] = 0;   g_calibration.standard_colors[4][2] = 255;   // Blue
    g_calibration.standard_colors[5][0] = 255; g_calibration.standard_colors[5][1] = 255; g_calibration.standard_colors[5][2] = 0;     // Yellow
    g_calibration.standard_colors[6][0] = 0;   g_calibration.standard_colors[6][1] = 255; g_calibration.standard_colors[6][2] = 255;   // Cyan
    g_calibration.standard_colors[7][0] = 255; g_calibration.standard_colors[7][1] = 0;   g_calibration.standard_colors[7][2] = 255;   // Magenta
    
    const char* red_cal = strstr(json_string, "\"red\"");
    const char* green_cal = strstr(json_string, "\"green\"");
    const char* blue_cal = strstr(json_string, "\"blue\"");
    const char* white_cal = strstr(json_string, "\"white\"");
    const char* magenta_cal = strstr(json_string, "\"magenta\"");
    
    if (red_cal && green_cal && blue_cal && white_cal && magenta_cal) {
        parseColorMapping(red_cal, g_calibration.calibrated_colors[2]);
        parseColorMapping(green_cal, g_calibration.calibrated_colors[3]);
        parseColorMapping(blue_cal, g_calibration.calibrated_colors[4]);
        parseColorMapping(white_cal, g_calibration.calibrated_colors[1]);
        parseColorMapping(magenta_cal, g_calibration.calibrated_colors[7]);
        
        g_calibration.calibrated_colors[0][0] = 0;   
        g_calibration.calibrated_colors[0][1] = 0;   
        g_calibration.calibrated_colors[0][2] = 0;
        g_calibration.calibrated_colors[5][0] = 255; 
        g_calibration.calibrated_colors[5][1] = 255; 
        g_calibration.calibrated_colors[5][2] = 0;
        g_calibration.calibrated_colors[6][0] = 0;   
        g_calibration.calibrated_colors[6][1] = 255; 
        g_calibration.calibrated_colors[6][2] = 255;
        
        g_calibration.is_active = 1;
        return 1;
    }
    
    return 0;
}

static void parseColorMapping(const char* json_section, jab_byte* rgb_out) {
    const char* calibrated = strstr(json_section, "\"calibrated\"");
    if (calibrated) {
        calibrated = strchr(calibrated, '[');
        if (calibrated) {
            int r, g, b;
            if (sscanf(calibrated, "[%d,%d,%d]", &r, &g, &b) == 3) {
                rgb_out[0] = (jab_byte)r;
                rgb_out[1] = (jab_byte)g;
                rgb_out[2] = (jab_byte)b;
            }
        }
    }
}

void jabApplyCalibration(jab_encode* enc) {
    if (!enc || !g_calibration.is_active || enc->color_number > 8) {
        return;
    }
    
    for (jab_int32 i = 0; i < enc->color_number * 3; i += 3) {
        jab_byte r = enc->palette[i];
        jab_byte g = enc->palette[i + 1];
        jab_byte b = enc->palette[i + 2];
        
        for (jab_int32 j = 0; j < 8; j++) {
            if (r == g_calibration.standard_colors[j][0] &&
                g == g_calibration.standard_colors[j][1] &&
                b == g_calibration.standard_colors[j][2]) {
                
                enc->palette[i]     = g_calibration.calibrated_colors[j][0];
                enc->palette[i + 1] = g_calibration.calibrated_colors[j][1];
                enc->palette[i + 2] = g_calibration.calibrated_colors[j][2];
                break;
            }
        }
    }
}

void jabRemapColor(const jab_byte* rgb_in, jab_byte* rgb_out) {
    if (!g_calibration.is_active) {
        rgb_out[0] = rgb_in[0];
        rgb_out[1] = rgb_in[1];
        rgb_out[2] = rgb_in[2];
        return;
    }
    
    for (jab_int32 i = 0; i < 8; i++) {
        if (rgb_in[0] == g_calibration.standard_colors[i][0] &&
            rgb_in[1] == g_calibration.standard_colors[i][1] &&
            rgb_in[2] == g_calibration.standard_colors[i][2]) {
            
            rgb_out[0] = g_calibration.calibrated_colors[i][0];
            rgb_out[1] = g_calibration.calibrated_colors[i][1];
            rgb_out[2] = g_calibration.calibrated_colors[i][2];
            return;
        }
    }
    
    rgb_out[0] = rgb_in[0];
    rgb_out[1] = rgb_in[1];
    rgb_out[2] = rgb_in[2];
}

void jabClearCalibration() {
    memset(&g_calibration, 0, sizeof(jab_color_calibration));
}

jab_boolean jabHasCalibration() {
    return g_calibration.is_active;
}
