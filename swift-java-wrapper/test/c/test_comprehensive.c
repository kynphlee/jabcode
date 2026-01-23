#include "../../include/mobile_bridge.h"
#include "../unity/unity.h"
#include <stdio.h>
#include <string.h>

void setUp(void) {}
void tearDown(void) {}

// Test short messages
void test_short_messages(void) {
    const char* messages[] = {"A", "AB", "ABC", "Test", "12345"};
    
    for(int i = 0; i < 5; i++) {
        jab_mobile_encode_params params = {
            .color_number = 4,
            .symbol_number = 1,
            .ecc_level = 3,
            .module_size = 12
        };
        
        jab_mobile_encode_result* encode_result = jabMobileEncode(
            (jab_char*)messages[i], 
            strlen(messages[i]), 
            &params
        );
        
        TEST_ASSERT_NOT_NULL_MESSAGE(encode_result, messages[i]);
        
        if(encode_result) {
            jab_data* decoded = jabMobileDecode(encode_result, 4, 3);
            TEST_ASSERT_NOT_NULL_MESSAGE(decoded, messages[i]);
            
            if(decoded) {
                TEST_ASSERT_EQUAL_INT(strlen(messages[i]), decoded->length);
                TEST_ASSERT_EQUAL_MEMORY(messages[i], decoded->data, decoded->length);
                printf("  ✓ '%s' roundtrip OK\n", messages[i]);
                free(decoded);
            }
            
            jabMobileEncodeResultFree(encode_result);
        }
    }
}

// Test different ECC levels
void test_ecc_levels(void) {
    const char* message = "Testing ECC levels";
    
    for(int ecc = 0; ecc <= 10; ecc++) {
        jab_mobile_encode_params params = {
            .color_number = 4,
            .symbol_number = 1,
            .ecc_level = ecc,
            .module_size = 12
        };
        
        jab_mobile_encode_result* encode_result = jabMobileEncode(
            (jab_char*)message, 
            strlen(message), 
            &params
        );
        
        if(encode_result) {
            jab_data* decoded = jabMobileDecode(encode_result, 4, ecc);
            if(decoded) {
                TEST_ASSERT_EQUAL_INT(strlen(message), decoded->length);
                printf("  ✓ ECC level %d OK\n", ecc);
                free(decoded);
            } else {
                printf("  ✗ ECC level %d decode failed\n", ecc);
            }
            jabMobileEncodeResultFree(encode_result);
        } else {
            printf("  ✗ ECC level %d encode failed\n", ecc);
        }
    }
}

// Test medium messages
void test_medium_messages(void) {
    const char* message = "This is a medium length message to test JABCode encoding and decoding capabilities.";
    
    jab_mobile_encode_params params = {
        .color_number = 4,
        .symbol_number = 1,
        .ecc_level = 5,
        .module_size = 12
    };
    
    jab_mobile_encode_result* encode_result = jabMobileEncode(
        (jab_char*)message, 
        strlen(message), 
        &params
    );
    
    TEST_ASSERT_NOT_NULL(encode_result);
    
    if(encode_result) {
        jab_data* decoded = jabMobileDecode(encode_result, 4, 5);
        TEST_ASSERT_NOT_NULL(decoded);
        
        if(decoded) {
            TEST_ASSERT_EQUAL_INT(strlen(message), decoded->length);
            TEST_ASSERT_EQUAL_MEMORY(message, decoded->data, decoded->length);
            printf("  ✓ Medium message (%zu bytes) OK\n", strlen(message));
            free(decoded);
        }
        
        jabMobileEncodeResultFree(encode_result);
    }
}

int main(void) {
    printf("\n=== Comprehensive JABCode Mobile Tests ===\n\n");
    
    printf("Testing short messages...\n");
    jabMobileClearError();
    test_short_messages();
    
    printf("\nTesting ECC levels 0-10...\n");
    jabMobileClearError();
    test_ecc_levels();
    
    printf("\nTesting medium messages...\n");
    jabMobileClearError();
    test_medium_messages();
    
    printf("\n✅ Comprehensive tests complete!\n");
    return 0;
}
