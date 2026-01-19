#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "mobile_bridge.h"

int main() {
    printf("\n=== Synthetic Decode Diagnostic ===\n\n");
    
    // Encode
    jab_mobile_encode_params params = {
        .color_number = 4,
        .symbol_number = 1,
        .ecc_level = 0,  // Use lowest ECC to minimize LDPC complexity
        .module_size = 12
    };
    
    const char* test_data = "A";  // Minimal data to test basic roundtrip
    jab_mobile_encode_result* encoded = jabMobileEncode(
        (jab_char*)test_data,
        strlen(test_data),
        &params
    );
    
    if (!encoded) {
        printf("ERROR: Encoding failed\n");
        return 1;
    }
    
    printf("Encoded: %dx%d bitmap\n", encoded->width, encoded->height);
    
    // Sample a few pixels to verify colors
    int bpp = 4;
    int bpr = encoded->width * bpp;
    
    printf("\nSample pixels from encoded bitmap:\n");
    
    // Corner (should be white quiet zone)
    printf("  (0,0): R=%02x G=%02x B=%02x\n",
        encoded->rgba_buffer[0],
        encoded->rgba_buffer[1],
        encoded->rgba_buffer[2]);
    
    // Just inside quiet zone (should still be white or start of symbol)
    int qz = 48; // 4 modules * 12 px
    int offset = qz * bpr + qz * bpp;
    printf("  (%d,%d): R=%02x G=%02x B=%02x\n",
        qz, qz,
        encoded->rgba_buffer[offset],
        encoded->rgba_buffer[offset+1],
        encoded->rgba_buffer[offset+2]);
    
    // Center (should be colored)
    int cx = encoded->width / 2;
    int cy = encoded->height / 2;
    offset = cy * bpr + cx * bpp;
    printf("  (center %d,%d): R=%02x G=%02x B=%02x\n",
        cx, cy,
        encoded->rgba_buffer[offset],
        encoded->rgba_buffer[offset+1],
        encoded->rgba_buffer[offset+2]);
    
    printf("\nAttempting decode...\n");
    
    // Decode with known encoding parameters and spatial metadata
    printf("Decoding with known parameters:\n");
    printf("  color_number=%d, ecc_level=%d\n", params.color_number, params.ecc_level);
    printf("  module_size=%d, symbol=%dx%d modules\n", 
           encoded->module_size, encoded->symbol_width, encoded->symbol_height);
    printf("  mask_type=%d\n", encoded->mask_type);
    
    printf("\nStarting decode...\n");
    jab_data* decoded = jabMobileDecode(
        encoded,
        params.color_number,
        params.ecc_level
    );
    printf("Decode call returned\n");
    
    if (!decoded) {
        printf("ERROR: Decoding failed\n");
        printf("\nThis indicates the synthetic decoder still needs work to bypass detection logic.\n");
        jabMobileEncodeResultFree(encoded);
        return 1;
    }
    
    printf("SUCCESS: Decoded %d bytes\n", decoded->length);
    printf("Data: %.*s\n", decoded->length, decoded->data);
    
    // Verify
    if (decoded->length == strlen(test_data) && 
        memcmp(decoded->data, test_data, decoded->length) == 0) {
        printf("\n✓ Round-trip successful!\n");
    } else {
        printf("\n✗ Data mismatch!\n");
    }
    
    jabMobileDataFree(decoded);
    jabMobileEncodeResultFree(encoded);
    
    return 0;
}
