#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "mobile_bridge.h"

int main() {
    printf("\n=== Camera Decoder Test (baseline) ===\n\n");
    
    // Encode
    jab_mobile_encode_params params = {
        .color_number = 4,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    const char* test_data = "A";
    jab_mobile_encode_result* encoded = jabMobileEncode(
        (jab_char*)test_data,
        strlen(test_data),
        &params
    );
    
    if (!encoded) {
        printf("ERROR: Encoding failed: %s\n", jabMobileGetLastError());
        return 1;
    }
    
    printf("Encoded: %dx%d bitmap\n", encoded->width, encoded->height);
    
    // Create jab_bitmap from encoded result
    jab_int32 bitmap_size = encoded->width * encoded->height * 4;
    jab_bitmap* bitmap = (jab_bitmap*)malloc(sizeof(jab_bitmap) + bitmap_size);
    if (!bitmap) {
        printf("ERROR: Failed to allocate bitmap\n");
        jabMobileEncodeResultFree(encoded);
        return 1;
    }
    
    bitmap->width = encoded->width;
    bitmap->height = encoded->height;
    bitmap->bits_per_pixel = 32;
    bitmap->bits_per_channel = 8;
    bitmap->channel_count = 4;
    memcpy(bitmap->pixel, encoded->rgba_buffer, bitmap_size);
    
    printf("Attempting camera-based decode (full detection pipeline)...\n");
    
    // Decode using NORMAL camera decoder (not synthetic)
    int decode_status = 0;
    jab_data* decoded = decodeJABCode(bitmap, NORMAL_DECODE, &decode_status);
    
    printf("Decode status: %d\n", decode_status);
    
    if (decoded && decoded->length > 0) {
        printf("SUCCESS: Decoded %d bytes\n", decoded->length);
        
        // Verify data
        if ((size_t)decoded->length == strlen(test_data) &&
            memcmp(decoded->data, test_data, strlen(test_data)) == 0) {
            printf("✓ Data matches: '%s'\n", test_data);
            free(decoded);
            free(bitmap);
            jabMobileEncodeResultFree(encoded);
            return 0;
        } else {
            printf("✗ Data mismatch\n");
            free(decoded);
            free(bitmap);
            jabMobileEncodeResultFree(encoded);
            return 1;
        }
    } else {
        printf("ERROR: Camera decode failed (status=%d)\n", decode_status);
        if (decoded) free(decoded);
        free(bitmap);
        jabMobileEncodeResultFree(encoded);
        return 1;
    }
}
