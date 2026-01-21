#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

int main() {
    printf("\n=== PNG Roundtrip Test (confirm PNG path works) ===\n\n");
    
    const char* test_data = "A";
    const char* temp_png = "/tmp/jabcode_test_roundtrip.png";
    
    // Encode
    jab_encode* enc = createEncode(4, 1);
    if (!enc) {
        printf("ERROR: createEncode failed\n");
        return 1;
    }
    
    enc->module_size = 12;
    enc->symbol_ecc_levels[0] = 3;
    
    jab_data* data = (jab_data*)malloc(sizeof(jab_data) + strlen(test_data));
    if (!data) {
        printf("ERROR: malloc failed\n");
        destroyEncode(enc);
        return 1;
    }
    data->length = strlen(test_data);
    memcpy(data->data, test_data, strlen(test_data));
    
    printf("Encoding...\n");
    jab_int32 result = generateJABCode(enc, data);
    free(data);
    
    if (result != 0) {
        printf("ERROR: generateJABCode failed\n");
        destroyEncode(enc);
        return 1;
    }
    
    printf("SUCCESS: Encoded %dx%d bitmap\n", enc->bitmap->width, enc->bitmap->height);
    
    // Save to PNG
    printf("Saving to PNG: %s\n", temp_png);
    if (!saveImage(enc->bitmap, (char*)temp_png)) {
        printf("ERROR: saveImage failed\n");
        destroyEncode(enc);
        return 1;
    }
    
    printf("SUCCESS: Saved PNG\n");
    destroyEncode(enc);
    
    // Load from PNG
    printf("Loading from PNG...\n");
    jab_bitmap* loaded = readImage((char*)temp_png);
    if (!loaded) {
        printf("ERROR: readImage failed\n");
        return 1;
    }
    
    printf("SUCCESS: Loaded %dx%d bitmap from PNG\n", loaded->width, loaded->height);
    
    // Decode from loaded PNG bitmap
    printf("Decoding from PNG-loaded bitmap...\n");
    int decode_status = 0;
    jab_data* decoded = decodeJABCode(loaded, NORMAL_DECODE, &decode_status);
    
    printf("Decode status: %d\n", decode_status);
    
    if (decoded && decoded->length > 0) {
        printf("SUCCESS: Decoded %d bytes\n", decoded->length);
        
        if ((size_t)decoded->length == strlen(test_data) &&
            memcmp(decoded->data, test_data, strlen(test_data)) == 0) {
            printf("✓ Data matches: '%s'\n", test_data);
            printf("\n=== PNG ROUNDTRIP WORKS ===\n");
            free(decoded);
            free(loaded);
            return 0;
        } else {
            printf("✗ Data mismatch\n");
            free(decoded);
            free(loaded);
            return 1;
        }
    } else {
        printf("ERROR: PNG roundtrip decode failed (status=%d)\n", decode_status);
        printf("\n=== PNG ROUNDTRIP ALSO BROKEN ===\n");
        if (decoded) free(decoded);
        free(loaded);
        return 1;
    }
}
