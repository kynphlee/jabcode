/**
 * Debug test to examine bitmap data
 */

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "mobile_bridge.h"
#include "jabcode.h"

int main(void) {
    const char* message = "Test";
    jab_char* data = (jab_char*)message;
    jab_int32 data_length = strlen(message);
    
    jab_mobile_encode_params params = {
        .color_number = 4,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12
    };
    
    printf("Encoding '%s'...\n", message);
    jab_mobile_encode_result* encode_result = jabMobileEncode(data, data_length, &params);
    
    if (!encode_result) {
        printf("ERROR: Encoding failed: %s\n", jabMobileGetLastError());
        return 1;
    }
    
    printf("Encoded: %dx%d bitmap\n", encode_result->width, encode_result->height);
    printf("Total buffer size: %d bytes\n", encode_result->width * encode_result->height * 4);
    printf("First 64 bytes (should have white if quiet zone exists):\n");
    for (int i = 0; i < 64 && i < encode_result->width * encode_result->height * 4; i++) {
        printf("%02x ", encode_result->rgba_buffer[i]);
        if ((i + 1) % 16 == 0) printf("\n");
    }
    printf("\n");
    
    // Check middle of first row (should be white or part of symbol)
    printf("Middle of first row (pixel at x=%d):\n", encode_result->width / 2);
    int mid_offset = (encode_result->width / 2) * 4;
    printf("R=%02x G=%02x B=%02x A=%02x\n",
        encode_result->rgba_buffer[mid_offset],
        encode_result->rgba_buffer[mid_offset + 1],
        encode_result->rgba_buffer[mid_offset + 2],
        encode_result->rgba_buffer[mid_offset + 3]);
    
    // Now test desktop decode directly with the encoder's bitmap
    // Create a test using createEncode + generateJABCode + decodeJABCode
    printf("\nTesting desktop encode->decode cycle...\n");
    
    jab_encode* enc2 = createEncode(params.color_number, params.symbol_number);
    enc2->module_size = params.module_size;
    for (jab_int32 i = 0; i < enc2->symbol_number; i++) {
        enc2->symbol_ecc_levels[i] = params.ecc_level;
    }
    
    jab_data* data_struct = (jab_data*)malloc(sizeof(jab_data) + data_length);
    data_struct->length = data_length;
    memcpy(data_struct->data, data, data_length);
    
    if (generateJABCode(enc2, data_struct) == 0) {
        printf("Desktop encode succeeded\n");
        
        // Try to decode the bitmap directly
        jab_int32 decode_status;
        jab_data* decoded = decodeJABCode(enc2->bitmap, NORMAL_DECODE, &decode_status);
        
        if (decoded) {
            printf("Desktop decode succeeded: '%.*s'\n", decoded->length, decoded->data);
            free(decoded);
        } else {
            printf("Desktop decode failed: status=%d\n", decode_status);
        }
    }
    
    free(data_struct);
    destroyEncode(enc2);
    
    // Now try mobile decode
    printf("\nTrying mobile decode...\n");
    jab_data* mobile_decoded = jabMobileDecode(
        encode_result->rgba_buffer,
        encode_result->width,
        encode_result->height
    );
    
    if (mobile_decoded) {
        printf("Mobile decode succeeded: '%.*s'\n", mobile_decoded->length, mobile_decoded->data);
        free(mobile_decoded);
    } else {
        printf("Mobile decode failed: %s\n", jabMobileGetLastError());
    }
    
    jabMobileEncodeResultFree(encode_result);
    return 0;
}
