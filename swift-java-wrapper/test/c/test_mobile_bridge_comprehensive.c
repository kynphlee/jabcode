/**
 * Comprehensive Mobile Bridge API Tests
 * Tests all API functions with edge cases and various configurations
 */

#include "unity.h"
#include "mobile_bridge.h"
#include <string.h>
#include <stdlib.h>

/* Additional test macros not in minimal Unity */
#define TEST_ASSERT_EQUAL_INT(expected, actual) TEST_ASSERT((expected) == (actual))
#define TEST_ASSERT_EQUAL_CHAR(expected, actual) TEST_ASSERT((expected) == (actual))
#define TEST_ASSERT_EQUAL_MEMORY(expected, actual, len) TEST_ASSERT(memcmp((expected), (actual), (len)) == 0)
#define TEST_ASSERT_NOT_NULL_MESSAGE(ptr, msg) do { if ((ptr) == NULL) { printf("FAIL: %s\n", msg); exit(1); } } while(0)
#define TEST_PASS() do { } while(0)

static int tests_run = 0;
static int tests_passed = 0;

#define RUN_TEST(test_func) do { \
    jabMobileClearError(); \
    tests_run++; \
    test_func(); \
    tests_passed++; \
    printf("  ✓ %s\n", #test_func); \
} while(0)

/* ========== Version Tests ========== */

void test_version_returns_valid_string(void) {
    const char* version = jabMobileGetVersion();
    TEST_ASSERT_NOT_NULL(version);
    TEST_ASSERT_TRUE(strlen(version) > 0);
}

/* ========== Encode Parameter Validation Tests ========== */

void test_encode_null_data_returns_null(void) {
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

void test_encode_null_params_returns_null(void) {
    char data[] = "test";
    jab_mobile_encode_result* result = jabMobileEncode(data, 4, NULL);
    TEST_ASSERT_NULL(result);
    TEST_ASSERT_NOT_NULL(jabMobileGetLastError());
}

void test_encode_zero_length_returns_null(void) {
    char data[] = "test";
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, 0, &params);
    TEST_ASSERT_NULL(result);
    TEST_ASSERT_NOT_NULL(jabMobileGetLastError());
}

void test_encode_invalid_color_number_7(void) {
    char data[] = "test";
    jab_mobile_encode_params params = {
        .color_number = 7,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, 4, &params);
    TEST_ASSERT_NULL(result);
}

void test_encode_invalid_color_number_3(void) {
    char data[] = "test";
    jab_mobile_encode_params params = {
        .color_number = 3,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, 4, &params);
    TEST_ASSERT_NULL(result);
}

void test_encode_256_color_rejected(void) {
    char data[] = "test";
    jab_mobile_encode_params params = {
        .color_number = 256,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, 4, &params);
    TEST_ASSERT_NULL(result);
    const char* error = jabMobileGetLastError();
    TEST_ASSERT_NOT_NULL(error);
    TEST_ASSERT_TRUE(strstr(error, "256") != NULL);
}

void test_encode_symbol_number_exceeds_limit(void) {
    char data[] = "test";
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 5,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, 4, &params);
    TEST_ASSERT_NULL(result);
}

void test_encode_symbol_number_zero(void) {
    char data[] = "test";
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 0,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, 4, &params);
    TEST_ASSERT_NULL(result);
}

void test_encode_ecc_level_exceeds_max(void) {
    char data[] = "test";
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 1,
        .ecc_level = 10,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, 4, &params);
    TEST_ASSERT_NULL(result);
}

void test_encode_negative_ecc_level(void) {
    char data[] = "test";
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 1,
        .ecc_level = -1,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, 4, &params);
    TEST_ASSERT_NULL(result);
}

/* ========== Successful Encode Tests ========== */

void test_encode_4_color_success(void) {
    char data[] = "Hello";
    jab_mobile_encode_params params = {
        .color_number = 4,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, 5, &params);
    TEST_ASSERT_NOT_NULL(result);
    TEST_ASSERT_NOT_NULL(result->rgba_buffer);
    TEST_ASSERT_TRUE(result->width > 0);
    TEST_ASSERT_TRUE(result->height > 0);
    TEST_ASSERT_TRUE(result->module_size > 0);
    TEST_ASSERT_TRUE(result->symbol_width > 0);
    TEST_ASSERT_TRUE(result->symbol_height > 0);
    
    jabMobileEncodeResultFree(result);
}

void test_encode_8_color_success(void) {
    char data[] = "Test 8-color";
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, strlen(data), &params);
    TEST_ASSERT_NOT_NULL(result);
    TEST_ASSERT_NOT_NULL(result->rgba_buffer);
    
    jabMobileEncodeResultFree(result);
}

void test_encode_128_color_success(void) {
    char data[] = "Test 128-color mode";
    jab_mobile_encode_params params = {
        .color_number = 128,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* result = jabMobileEncode(data, strlen(data), &params);
    TEST_ASSERT_NOT_NULL(result);
    TEST_ASSERT_NOT_NULL(result->rgba_buffer);
    
    jabMobileEncodeResultFree(result);
}

void test_encode_various_ecc_levels(void) {
    char data[] = "ECC test";
    
    for (int ecc = 0; ecc <= 7; ecc++) {
        jab_mobile_encode_params params = {
            .color_number = 8,
            .symbol_number = 1,
            .ecc_level = ecc,
            .module_size = 12
        };
        
        jab_mobile_encode_result* result = jabMobileEncode(data, strlen(data), &params);
        TEST_ASSERT_NOT_NULL_MESSAGE(result, "Encode failed for ECC level");
        jabMobileEncodeResultFree(result);
        jabMobileClearError();
    }
}

/* ========== Decode Tests ========== */

void test_decode_null_encode_result(void) {
    jab_data* result = jabMobileDecode(NULL, 8, 3);
    TEST_ASSERT_NULL(result);
    TEST_ASSERT_NOT_NULL(jabMobileGetLastError());
}

void test_decode_invalid_color_number(void) {
    char data[] = "test";
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* enc = jabMobileEncode(data, 4, &params);
    TEST_ASSERT_NOT_NULL(enc);
    
    // Try to decode with wrong color number
    jab_data* dec = jabMobileDecode(enc, 7, 3);  // 7 is invalid
    TEST_ASSERT_NULL(dec);
    
    jabMobileEncodeResultFree(enc);
}

/* ========== Roundtrip Tests ========== */

void test_roundtrip_4_color(void) {
    char data[] = "Roundtrip 4-color test";
    jab_mobile_encode_params params = {
        .color_number = 4,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* enc = jabMobileEncode(data, strlen(data), &params);
    TEST_ASSERT_NOT_NULL(enc);
    
    jab_data* dec = jabMobileDecode(enc, 4, 3);
    TEST_ASSERT_NOT_NULL(dec);
    TEST_ASSERT_EQUAL_INT(strlen(data), dec->length);
    TEST_ASSERT_EQUAL_MEMORY(data, dec->data, dec->length);
    
    free(dec);
    jabMobileEncodeResultFree(enc);
}

void test_roundtrip_8_color(void) {
    char data[] = "Roundtrip 8-color test";
    jab_mobile_encode_params params = {
        .color_number = 8,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    jab_mobile_encode_result* enc = jabMobileEncode(data, strlen(data), &params);
    TEST_ASSERT_NOT_NULL(enc);
    
    jab_data* dec = jabMobileDecode(enc, 8, 3);
    TEST_ASSERT_NOT_NULL(dec);
    TEST_ASSERT_EQUAL_INT(strlen(data), dec->length);
    TEST_ASSERT_EQUAL_MEMORY(data, dec->data, dec->length);
    
    free(dec);
    jabMobileEncodeResultFree(enc);
}

void test_roundtrip_single_byte(void) {
    char data[] = "A";
    jab_mobile_encode_params params = {
        .color_number = 4,
        .symbol_number = 1,
        .ecc_level = 0,
        .module_size = 12
    };
    
    jab_mobile_encode_result* enc = jabMobileEncode(data, 1, &params);
    TEST_ASSERT_NOT_NULL(enc);
    
    jab_data* dec = jabMobileDecode(enc, 4, 0);
    TEST_ASSERT_NOT_NULL(dec);
    TEST_ASSERT_EQUAL_INT(1, dec->length);
    TEST_ASSERT_EQUAL_CHAR('A', dec->data[0]);
    
    free(dec);
    jabMobileEncodeResultFree(enc);
}

/* ========== Error Handling Tests ========== */

void test_error_initially_null(void) {
    jabMobileClearError();
    TEST_ASSERT_NULL(jabMobileGetLastError());
}

void test_error_set_after_failure(void) {
    jabMobileClearError();
    jabMobileEncode(NULL, 0, NULL);
    TEST_ASSERT_NOT_NULL(jabMobileGetLastError());
}

void test_error_cleared_properly(void) {
    jabMobileEncode(NULL, 0, NULL);
    TEST_ASSERT_NOT_NULL(jabMobileGetLastError());
    
    jabMobileClearError();
    TEST_ASSERT_NULL(jabMobileGetLastError());
}

/* ========== Memory Management Tests ========== */

void test_encode_result_free_null_safe(void) {
    // Should not crash
    jabMobileEncodeResultFree(NULL);
    TEST_PASS();
}

void test_data_free_null_safe(void) {
    // Should not crash
    jabMobileDataFree(NULL);
    TEST_PASS();
}

/* ========== Main ========== */

int main(void) {
    printf("\n=== Comprehensive Mobile Bridge Tests ===\n\n");
    
    printf("Version tests:\n");
    RUN_TEST(test_version_returns_valid_string);
    
    printf("\nEncode parameter validation:\n");
    RUN_TEST(test_encode_null_data_returns_null);
    RUN_TEST(test_encode_null_params_returns_null);
    RUN_TEST(test_encode_zero_length_returns_null);
    RUN_TEST(test_encode_invalid_color_number_7);
    RUN_TEST(test_encode_invalid_color_number_3);
    RUN_TEST(test_encode_256_color_rejected);
    RUN_TEST(test_encode_symbol_number_exceeds_limit);
    RUN_TEST(test_encode_symbol_number_zero);
    RUN_TEST(test_encode_ecc_level_exceeds_max);
    RUN_TEST(test_encode_negative_ecc_level);
    
    printf("\nSuccessful encode tests:\n");
    RUN_TEST(test_encode_4_color_success);
    RUN_TEST(test_encode_8_color_success);
    RUN_TEST(test_encode_128_color_success);
    RUN_TEST(test_encode_various_ecc_levels);
    
    printf("\nDecode tests:\n");
    RUN_TEST(test_decode_null_encode_result);
    RUN_TEST(test_decode_invalid_color_number);
    
    printf("\nRoundtrip tests:\n");
    RUN_TEST(test_roundtrip_4_color);
    RUN_TEST(test_roundtrip_8_color);
    RUN_TEST(test_roundtrip_single_byte);
    
    printf("\nError handling tests:\n");
    RUN_TEST(test_error_initially_null);
    RUN_TEST(test_error_set_after_failure);
    RUN_TEST(test_error_cleared_properly);
    
    printf("\nMemory management tests:\n");
    RUN_TEST(test_encode_result_free_null_safe);
    RUN_TEST(test_data_free_null_safe);
    
    printf("\n=================================\n");
    printf("Results: %d/%d tests passed\n", tests_passed, tests_run);
    printf("=================================\n\n");
    
    return (tests_passed == tests_run) ? 0 : 1;
}
