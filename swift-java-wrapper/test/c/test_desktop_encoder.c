#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

int main() {
    printf("\n=== Desktop Encoder Test (baseline) ===\n\n");
    
    const char* test_data = "A";
    
    // Use desktop encoder API directly - try 8 colors (known working from memories)
    jab_encode* enc = createEncode(8, 1);  // 8 colors, 1 symbol
    if (!enc) {
        printf("ERROR: createEncode failed\n");
        return 1;
    }
    
    enc->module_size = 12;
    enc->symbol_ecc_levels[0] = 5;  // Mid-range ECC
    
    // Create data structure
    jab_data* data = (jab_data*)malloc(sizeof(jab_data) + strlen(test_data));
    if (!data) {
        printf("ERROR: malloc failed\n");
        destroyEncode(enc);
        return 1;
    }
    data->length = strlen(test_data);
    memcpy(data->data, test_data, strlen(test_data));
    
    printf("Encoding with desktop encoder...\n");
    printf("  color_number=4, ecc_level=3, module_size=12\n");
    
    // Generate JABCode
    jab_int32 result = generateJABCode(enc, data);
    free(data);
    
    if (result != 0) {
        printf("ERROR: generateJABCode failed with code %d\n", result);
        destroyEncode(enc);
        return 1;
    }
    
    if (!enc->bitmap) {
        printf("ERROR: No bitmap generated\n");
        destroyEncode(enc);
        return 1;
    }
    
    printf("SUCCESS: Encoded %dx%d bitmap\n", enc->bitmap->width, enc->bitmap->height);
    printf("  mask_type=%d\n", enc->mask_type);
    printf("  symbol size=%dx%d modules\n", 
           enc->symbols[0].side_size.x, 
           enc->symbols[0].side_size.y);
    
    // Now try to decode it
    printf("\nAttempting camera-based decode...\n");
    
    int decode_status = 0;
    jab_data* decoded = decodeJABCode(enc->bitmap, NORMAL_DECODE, &decode_status);
    
    printf("Decode status: %d\n", decode_status);
    
    if (decoded && decoded->length > 0) {
        printf("SUCCESS: Decoded %d bytes\n", decoded->length);
        
        if ((size_t)decoded->length == strlen(test_data) &&
            memcmp(decoded->data, test_data, strlen(test_data)) == 0) {
            printf("✓ Data matches: '%s'\n", test_data);
            printf("\n=== DESKTOP ENCODER WORKS ===\n");
            free(decoded);
            destroyEncode(enc);
            return 0;
        } else {
            printf("✗ Data mismatch\n");
            free(decoded);
            destroyEncode(enc);
            return 1;
        }
    } else {
        printf("ERROR: Desktop encode->decode failed (status=%d)\n", decode_status);
        printf("\n=== DESKTOP ENCODER ALSO BROKEN ===\n");
        if (decoded) free(decoded);
        destroyEncode(enc);
        return 1;
    }
}
