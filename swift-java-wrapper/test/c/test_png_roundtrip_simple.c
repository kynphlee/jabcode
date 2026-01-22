#include "../../include/mobile_bridge.h"
#include "../../vendor/unity/unity.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// External PNG functions from image.c (if we link full library)
extern int saveImage(unsigned char* bitmap, int width, int height, const char* filename);
extern unsigned char* readImage(const char* filename, int* width, int* height, int* bits_per_pixel, int* bits_per_channel, int* color_space);

void setUp(void) {}
void tearDown(void) {}

void test_png_file_roundtrip(void) {
    const char* message = "A";
    int color_number = 4;
    int ecc_level = 0;
    int module_size = 12;
    
    printf("\n=== PNG File Roundtrip Test ===\n");
    
    // Step 1: Encode to bitmap
    jab_mobile_encode_result* enc_result = jabMobileEncode(
        (const unsigned char*)message, strlen(message),
        color_number, ecc_level, module_size
    );
    
    TEST_ASSERT_NOT_NULL_MESSAGE(enc_result, "Encoding failed");
    printf("Encoded: %dx%d bitmap\n", enc_result->width, enc_result->height);
    
    // Step 2: Save bitmap to PNG file
    int save_result = saveImage(
        enc_result->bitmap_data,
        enc_result->width,
        enc_result->height,
        "/tmp/test_roundtrip.png"
    );
    TEST_ASSERT_EQUAL_MESSAGE(0, save_result, "PNG save failed");
    printf("Saved to /tmp/test_roundtrip.png\n");
    
    // Step 3: Load PNG file back
    int loaded_width, loaded_height, bits_per_pixel, bits_per_channel, color_space;
    unsigned char* loaded_bitmap = readImage(
        "/tmp/test_roundtrip.png",
        &loaded_width, &loaded_height,
        &bits_per_pixel, &bits_per_channel, &color_space
    );
    TEST_ASSERT_NOT_NULL_MESSAGE(loaded_bitmap, "PNG load failed");
    printf("Loaded from PNG: %dx%d\n", loaded_width, loaded_height);
    
    // Step 4: Decode from loaded PNG bitmap
    jab_mobile_decode_result* dec_result = jabMobileDecode(
        loaded_bitmap, loaded_width, loaded_height,
        color_number, ecc_level, enc_result->module_size,
        enc_result->symbol_width, enc_result->symbol_height,
        enc_result->mask_type
    );
    
    TEST_ASSERT_NOT_NULL_MESSAGE(dec_result, "Decoding from PNG failed");
    TEST_ASSERT_NOT_NULL_MESSAGE(dec_result->decoded_data, "Decoded data is NULL");
    
    printf("Decoded: '%s'\n", dec_result->decoded_data);
    TEST_ASSERT_EQUAL_STRING_MESSAGE(message, (const char*)dec_result->decoded_data, "Round-trip data mismatch");
    
    // Cleanup
    jabMobileFreeEncodeResult(enc_result);
    jabMobileFreeDecodeResult(dec_result);
    free(loaded_bitmap);
    
    printf("PNG roundtrip: SUCCESS!\n");
}

int main(void) {
    UNITY_BEGIN();
    RUN_TEST(test_png_file_roundtrip);
    return UNITY_END();
}
