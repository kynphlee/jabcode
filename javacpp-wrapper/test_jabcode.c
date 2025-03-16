#include <stdio.h>
#include "src/main/c/jabcode_c_wrapper.h"

int main() {
    printf("Testing JABCode library...\n");
    
    // Create a JABCode encode object
    printf("Creating JABCode encode object...\n");
    jab_encode* enc = createEncode_c(8, 1);
    if (enc != NULL) {
        printf("JABCode encode object created successfully!\n");
        
        // Destroy the JABCode encode object
        printf("Destroying JABCode encode object...\n");
        destroyEncode_c(enc);
        printf("JABCode encode object destroyed successfully!\n");
    } else {
        printf("Failed to create JABCode encode object!\n");
    }
    
    return 0;
}
