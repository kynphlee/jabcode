#include "../../include/mobile_bridge.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

int test_color_mode(int color_number, const char* message) {
    jab_mobile_encode_params params = {
        .color_number = color_number,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* enc = jabMobileEncode(
        (jab_char*)message, strlen(message), &params);
    
    if (!enc) {
        printf("   ✗ %d-color: ENCODE FAILED\n", color_number);
        return 0;
    }
    
    printf("   %d-color: encoded %dx%d bitmap... ", 
           color_number, enc->width, enc->height);
    
    // Debug: Show first few pixels of bitmap for 8-color mode
    if (color_number == 8 && enc->rgba_buffer) {
        printf("\n   [DEBUG 8-color] First 3 module pixels (at center):\n");
        int module_size = 12;
        int bytes_per_pixel = 4; // RGBA
        int bytes_per_row = enc->width * bytes_per_pixel;
        for (int m = 0; m < 3; m++) {
            int px = module_size/2 + m * module_size;
            int py = module_size/2;
            int offset = py * bytes_per_row + px * bytes_per_pixel;
            printf("   Module %d at (%d,%d): RGB(%d,%d,%d)\n", 
                   m, px, py,
                   enc->rgba_buffer[offset+0], enc->rgba_buffer[offset+1], enc->rgba_buffer[offset+2]);
        }
    }
    
    jab_data* dec = jabMobileDecode(enc, color_number, 3);
    
    if (!dec) {
        printf("DECODE FAILED\n");
        jabMobileEncodeResultFree(enc);
        return 0;
    }
    
    int match = (dec->length == strlen(message) && 
                 memcmp(dec->data, message, dec->length) == 0);
    
    if (match) {
        printf("✓ roundtrip OK\n");
    } else {
        printf("✗ DATA MISMATCH (got %d bytes, expected %zu)\n", 
               dec->length, strlen(message));
    }
    
    free(dec);
    jabMobileEncodeResultFree(enc);
    return match;
}

int main(void) {
    printf("\n=== JABCode Color Mode Tests ===\n\n");
    
    int color_modes[] = {4, 8, 16, 32, 64, 128, 256};
    int num_modes = sizeof(color_modes) / sizeof(color_modes[0]);
    
    const char* test_message = "Color mode test!";
    
    int passed = 0;
    int total = num_modes;
    
    printf("Testing message: \"%s\" (%zu bytes)\n\n", 
           test_message, strlen(test_message));
    
    for (int i = 0; i < num_modes; i++) {
        jabMobileClearError();
        if (test_color_mode(color_modes[i], test_message)) {
            passed++;
        }
    }
    
    printf("\n=================================\n");
    printf("Results: %d/%d color modes passed\n", passed, total);
    printf("=================================\n\n");
    
    return (passed == total) ? 0 : 1;
}
