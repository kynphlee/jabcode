/**
 * Mobile Bridge API Tests
 * Tests basic functionality, validation, and error handling
 */

#include "unity.h"
#include "mobile_bridge.h"
#include <string.h>

void test_version_string(void) {
    const char* version = jabMobileGetVersion();
    TEST_ASSERT_NOT_NULL(version);
    TEST_ASSERT_EQUAL_STRING("1.0.0", version);
}

void test_encode_rejects_null_data(void) {
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(NULL, 10, &params);
    
    TEST_ASSERT_NULL(result);
    TEST_ASSERT_NOT_NULL(jabMobileGetLastError());
}

void test_encode_rejects_null_params(void) {
    char data[] = "test";
    
    jab_mobile_encode_result* result = jabMobileEncode(data, 4, NULL);
    
    TEST_ASSERT_NULL(result);
    TEST_ASSERT_NOT_NULL(jabMobileGetLastError());
}

void test_encode_rejects_256_color(void) {
    char data[] = "test";
    jab_mobile_encode_params params = {
        .color_number = 256,  // Known broken mode
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, 4, &params);
    
    TEST_ASSERT_NULL(result);
    const char* error = jabMobileGetLastError();
    TEST_ASSERT_NOT_NULL(error);
    // Error message should mention 256-color
    TEST_ASSERT_TRUE(strstr(error, "256") != NULL);
}

void test_encode_rejects_invalid_color_mode(void) {
    char data[] = "test";
    jab_mobile_encode_params params = {
        .color_number = 7,  // Invalid (not 4, 8, 16, 32, 64, 128)
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, 4, &params);
    
    TEST_ASSERT_NULL(result);
    TEST_ASSERT_NOT_NULL(jabMobileGetLastError());
}

void test_encode_limits_symbols_to_4(void) {
    char data[] = "test";
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 5,  // Exceeds mobile limit
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, 4, &params);
    
    TEST_ASSERT_NULL(result);
    const char* error = jabMobileGetLastError();
    TEST_ASSERT_NOT_NULL(error);
    TEST_ASSERT_TRUE(strstr(error, "1-4") != NULL);
}

void test_encode_rejects_invalid_ecc_level(void) {
    char data[] = "test";
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 1,
        .ecc_level = 10,  // Invalid (max is 7)
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, 4, &params);
    
    TEST_ASSERT_NULL(result);
    TEST_ASSERT_NOT_NULL(jabMobileGetLastError());
}

void test_decode_rejects_null_buffer(void) {
    jab_data* result = jabMobileDecode(NULL, 100, 100);
    
    TEST_ASSERT_NULL(result);
    TEST_ASSERT_NOT_NULL(jabMobileGetLastError());
}

void test_decode_rejects_invalid_dimensions(void) {
    jab_byte buffer[100];
    
    jab_data* result = jabMobileDecode(buffer, 0, 100);
    TEST_ASSERT_NULL(result);
    TEST_ASSERT_NOT_NULL(jabMobileGetLastError());
    
    jabMobileClearError();
    
    result = jabMobileDecode(buffer, 100, -1);
    TEST_ASSERT_NULL(result);
    TEST_ASSERT_NOT_NULL(jabMobileGetLastError());
    
    jabMobileClearError();  // Clean up for next test
}

void test_error_handling_thread_local(void) {
    // Initially no error
    TEST_ASSERT_NULL(jabMobileGetLastError());
    
    // Trigger error
    jabMobileEncode(NULL, 0, NULL);
    TEST_ASSERT_NOT_NULL(jabMobileGetLastError());
    
    // Clear error
    jabMobileClearError();
    TEST_ASSERT_NULL(jabMobileGetLastError());
}

int main(void) {
    printf("Running Mobile Bridge Tests...\n\n");
    
    test_version_string();
    printf("✓ test_version_string\n");
    
    test_encode_rejects_null_data();
    printf("✓ test_encode_rejects_null_data\n");
    
    test_encode_rejects_null_params();
    printf("✓ test_encode_rejects_null_params\n");
    
    test_encode_rejects_256_color();
    printf("✓ test_encode_rejects_256_color\n");
    
    test_encode_rejects_invalid_color_mode();
    printf("✓ test_encode_rejects_invalid_color_mode\n");
    
    test_encode_limits_symbols_to_4();
    printf("✓ test_encode_limits_symbols_to_4\n");
    
    test_encode_rejects_invalid_ecc_level();
    printf("✓ test_encode_rejects_invalid_ecc_level\n");
    
    test_decode_rejects_null_buffer();
    printf("✓ test_decode_rejects_null_buffer\n");
    
    test_decode_rejects_invalid_dimensions();
    printf("✓ test_decode_rejects_invalid_dimensions\n");
    
    test_error_handling_thread_local();
    printf("✓ test_error_handling_thread_local\n");
    
    printf("\n✅ All Mobile Bridge Tests Passed!\n");
    return 0;
}
