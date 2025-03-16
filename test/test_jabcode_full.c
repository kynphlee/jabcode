#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../src/jabcode/include/jabcode.h"

int main() {
    printf("Testing JABCode library...\n");
    
    // Create an encode object
    jab_encode* enc = createEncode(8, 1);
    if (enc == NULL) {
        printf("Failed to create encode object\n");
        return 1;
    }
    
    printf("Successfully created encode object\n");
    
    // Create data to encode
    const char* message = "Hello JABCode!";
    size_t message_len = strlen(message);
    
    // Allocate memory for jab_data struct and the flexible array member in one go
    jab_data* data = (jab_data*)malloc(sizeof(jab_data) + message_len * sizeof(jab_char));
    if (data == NULL) {
        printf("Failed to allocate memory for data object\n");
        destroyEncode(enc);
        return 1;
    }
    
    data->length = message_len;
    memcpy(data->data, message, message_len);
    
    printf("Successfully created data object\n");
    
    // Generate JABCode
    int result = generateJABCode(enc, data);
    if (result != JAB_SUCCESS) {
        printf("Failed to generate JABCode: %d\n", result);
        free(data);
        destroyEncode(enc);
        return 1;
    }
    
    printf("Successfully generated JABCode\n");
    
    // Save the JABCode as an image
    if (!saveImage(enc->bitmap, "test_jabcode.png")) {
        printf("Failed to save JABCode image\n");
        free(data);
        destroyEncode(enc);
        return 1;
    }
    
    printf("Successfully saved JABCode image to test_jabcode.png\n");
    
    // Clean up
    free(data);
    destroyEncode(enc);
    
    return 0;
}
