/**
 * End-to-end encoding test for mobile bridge
 * Tests actual encoding functionality (not just validation)
 */

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "unity.h"
#include "mobile_bridge.h"

void test_encode_simple_message(void) {
    const char* message = "Hello Mobile!";
    jab_char* data = (jab_char*)message;
    jab_int32 data_length = strlen(message);
    
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, data_length, &params);
    
    TEST_ASSERT_NOT_NULL(result);
    TEST_ASSERT_NULL(jabMobileGetLastError());
    TEST_ASSERT_NOT_NULL(result->rgba_buffer);
    TEST_ASSERT_TRUE(result->width > 0);
    TEST_ASSERT_TRUE(result->height > 0);
    
    printf("\n  Encoded '%s' -> %dx%d bitmap\n", 
           message, result->width, result->height);
    
    jabMobileEncodeResultFree(result);
}

void test_encode_decode_roundtrip(void) {
    const char* message = "Mobile roundtrip test!";
    jab_char* data = (jab_char*)message;
    jab_int32 data_length = strlen(message);
    
    jab_mobile_encode_params params = {
        .color_number = 4,  // Use 4-color mode for reliability
        .symbol_number = 1,
        .ecc_level = 5,     // Higher ECC for mobile
        .module_size = 12
    };
    
    // Encode
    jab_mobile_encode_result* encode_result = jabMobileEncode(data, data_length, &params);
    TEST_ASSERT_NOT_NULL(encode_result);
    TEST_ASSERT_NULL(jabMobileGetLastError());
    
    // Decode
    jab_data* decoded = jabMobileDecode(
        encode_result->rgba_buffer, 
        encode_result->width, 
        encode_result->height
    );
    
    TEST_ASSERT_NOT_NULL(decoded);
    TEST_ASSERT_NULL(jabMobileGetLastError());
    TEST_ASSERT_EQUAL(data_length, decoded->length);
    
    // Compare decoded data
    int match = (memcmp(message, decoded->data, data_length) == 0);
    TEST_ASSERT_TRUE(match);
    
    printf("\n  Round-trip: '%s' -> encoded -> decoded -> '%.*s' ✓\n",
           message, decoded->length, decoded->data);
    
    free(decoded);
    jabMobileEncodeResultFree(encode_result);
}

void test_encode_multiple_symbols(void) {
    const char* message = "This is a longer message that might need multiple symbols "
                          "to encode properly in JABCode format.";
    jab_char* data = (jab_char*)message;
    jab_int32 data_length = strlen(message);
    
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 2,  // Use 2 symbols
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, data_length, &params);
    
    TEST_ASSERT_NOT_NULL(result);
    TEST_ASSERT_NULL(jabMobileGetLastError());
    
    printf("\n  Multi-symbol: %d chars -> %dx%d\n", 
           data_length, result->width, result->height);
    
    jabMobileEncodeResultFree(result);
}

int main(void) {
    printf("\nRunning Mobile Bridge Encoding Tests...\n");
    
    jabMobileClearError();
    test_encode_simple_message();
    
    jabMobileClearError();
    test_encode_decode_roundtrip();
    
    jabMobileClearError();
    test_encode_multiple_symbols();
    
    printf("\n✅ All Encoding Tests Passed!\n");
    return 0;
}
