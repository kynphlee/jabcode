#include "../../include/mobile_bridge.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

int main(void) {
    printf("\n=== Testing Various Configurations ===\n\n");
    
    int passed = 0, total = 0;
    
    // Test 1: Very short messages
    const char* short_msgs[] = {"A", "12", "Hi!", "Test"};
    printf("1. Testing short messages (1-4 bytes)...\n");
    for(int i = 0; i < 4; i++) {
        total++;
        jab_mobile_encode_params params = {4, 1, 3, 12};
        jab_mobile_encode_result* enc = jabMobileEncode(
            (jab_char*)short_msgs[i], strlen(short_msgs[i]), &params);
        
        if(enc) {
            jab_data* dec = jabMobileDecode(enc, 4, 3);
            if(dec && dec->length == strlen(short_msgs[i]) && 
               memcmp(dec->data, short_msgs[i], dec->length) == 0) {
                printf("   ✓ '%s' OK\n", short_msgs[i]);
                passed++;
                free(dec);
            } else {
                printf("   ✗ '%s' FAILED\n", short_msgs[i]);
            }
            jabMobileEncodeResultFree(enc);
        } else {
            printf("   ✗ '%s' encode failed\n", short_msgs[i]);
        }
    }
    
    // Test 2: Different ECC levels
    printf("\n2. Testing ECC levels (0, 3, 5, 7, 10)...\n");
    const char* msg = "ECC test message";
    int ecc_levels[] = {0, 3, 5, 7, 10};
    for(int i = 0; i < 5; i++) {
        total++;
        jab_mobile_encode_params params = {4, 1, ecc_levels[i], 12};
        jab_mobile_encode_result* enc = jabMobileEncode(
            (jab_char*)msg, strlen(msg), &params);
        
        if(enc) {
            jab_data* dec = jabMobileDecode(enc, 4, ecc_levels[i]);
            if(dec && dec->length == strlen(msg)) {
                printf("   ✓ ECC level %d OK\n", ecc_levels[i]);
                passed++;
                free(dec);
            } else {
                printf("   ✗ ECC level %d decode failed\n", ecc_levels[i]);
            }
            jabMobileEncodeResultFree(enc);
        } else {
            printf("   ✗ ECC level %d encode failed\n", ecc_levels[i]);
        }
    }
    
    // Test 3: Medium message
    printf("\n3. Testing medium message (85 bytes)...\n");
    const char* medium = "This is a longer test message to verify that JABCode can handle medium-sized data.";
    total++;
    jab_mobile_encode_params params = {4, 1, 5, 12};
    jab_mobile_encode_result* enc = jabMobileEncode(
        (jab_char*)medium, strlen(medium), &params);
    
    if(enc) {
        jab_data* dec = jabMobileDecode(enc, 4, 5);
        if(dec && dec->length == strlen(medium) && 
           memcmp(dec->data, medium, dec->length) == 0) {
            printf("   ✓ Medium message OK (%zu bytes)\n", strlen(medium));
            passed++;
            free(dec);
        } else {
            printf("   ✗ Medium message decode failed\n");
        }
        jabMobileEncodeResultFree(enc);
    } else {
        printf("   ✗ Medium message encode failed\n");
    }
    
    // Test 4: 8-color mode
    printf("\n4. Testing 8-color mode...\n");
    total++;
    jab_mobile_encode_params params8 = {8, 1, 3, 12};
    jab_mobile_encode_result* enc8 = jabMobileEncode(
        (jab_char*)"8-color test", 12, &params8);
    
    if(enc8) {
        printf("   ✓ 8-color encode OK (%dx%d)\n", enc8->width, enc8->height);
        passed++;
        jabMobileEncodeResultFree(enc8);
    } else {
        printf("   ✗ 8-color encode failed\n");
    }
    
    // Summary
    printf("\n=================================\n");
    printf("Results: %d/%d tests passed (%.1f%%)\n", 
           passed, total, (100.0 * passed) / total);
    printf("=================================\n\n");
    
    return (passed == total) ? 0 : 1;
}
