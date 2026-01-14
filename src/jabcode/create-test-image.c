/**
 * Create a test JABCode image for benchmarking
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

int main(int argc, char* argv[]) {
    char* output_file = (argc > 1) ? argv[1] : "/tmp/test-benchmark.png";
    int color_number = (argc > 2) ? atoi(argv[2]) : 64;
    
    // Create ~1KB test message
    char message[1024];
    memset(message, 0, sizeof(message));
    strcpy(message, "Test message for PNG timing benchmark. ");
    for (int i = strlen(message); i < 900; i++) {
        message[i] = 'A' + (i % 26);
    }
    
    printf("Creating JABCode image...\n");
    printf("  Output: %s\n", output_file);
    printf("  Colors: %d\n", color_number);
    printf("  Message: %zu bytes\n", strlen(message));
    
    // Create encoder
    jab_encode* enc = createEncode(color_number, 1);
    if (!enc) {
        printf("ERROR: Failed to create encoder\n");
        return 1;
    }
    
    // Set ECC level
    enc->symbol_ecc_levels[0] = 5;
    
    // Create data
    jab_data* data = (jab_data*)malloc(sizeof(jab_data) + strlen(message));
    data->length = strlen(message);
    memcpy(data->data, message, strlen(message));
    
    // Generate
    if (generateJABCode(enc, data) != 0) {
        printf("ERROR: Failed to generate JABCode\n");
        free(data);
        destroyEncode(enc);
        return 1;
    }
    
    // Save
    if (!saveImage(enc->bitmap, output_file)) {
        printf("ERROR: Failed to save image\n");
        free(data);
        destroyEncode(enc);
        return 1;
    }
    
    printf("  Image size: %dx%d\n", enc->bitmap->width, enc->bitmap->height);
    printf("SUCCESS: Image saved to %s\n", output_file);
    
    free(data);
    destroyEncode(enc);
    return 0;
}
