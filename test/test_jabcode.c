#include <stdio.h>
#include <stdlib.h>
#include "../src/jabcode/include/jabcode.h"

int main() {
    printf("Testing JABCode library...\n");
    
    // Create an encode object
    jab_encode* enc = (jab_encode*)malloc(sizeof(jab_encode));
    if (enc == NULL) {
        printf("Failed to allocate memory for encode object\n");
        return 1;
    }
    
    // Initialize the encode object
    enc->color_number = 8;
    enc->symbol_number = 1;
    
    printf("Successfully created encode object\n");
    
    // Clean up
    free(enc);
    
    return 0;
}
