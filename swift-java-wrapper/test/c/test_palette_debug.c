/**
 * Debug test to examine palette values
 */

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "jabcode.h"

int main(void) {
    // Create encoder with 4-color mode
    jab_encode* enc = createEncode(4, 1);
    
    printf("4-color palette:\n");
    for (int i = 0; i < 4; i++) {
        printf("  Color %d: R=%3d G=%3d B=%3d\n", 
            i,
            enc->palette[i * 3 + 0],
            enc->palette[i * 3 + 1],
            enc->palette[i * 3 + 2]);
    }
    
    // Encode a simple message
    enc->module_size = 12;
    enc->symbol_ecc_levels[0] = 3;
    
    const char* message = "Test";
    jab_data* data_struct = (jab_data*)malloc(sizeof(jab_data) + strlen(message));
    data_struct->length = strlen(message);
    memcpy(data_struct->data, message, strlen(message));
    
    if (generateJABCode(enc, data_struct) == 0) {
        printf("\nSymbol info:\n");
        printf("  Symbol size: %dx%d modules\n", 
            enc->symbols[0].side_size.x,
            enc->symbols[0].side_size.y);
        printf("  Bitmap size: %dx%d pixels\n", 
            enc->bitmap->width,
            enc->bitmap->height);
        
        // Check first few matrix values
        printf("\nFirst row of symbol matrix (first 20 modules):\n  ");
        for (int i = 0; i < 20 && i < enc->symbols[0].side_size.x; i++) {
            printf("%d ", enc->symbols[0].matrix[i]);
        }
        printf("\n");
        
        // Check if matrix edges are all palette index 0
        int edge_count = 0;
        int white_count = 0;
        for (int i = 0; i < enc->symbols[0].side_size.x; i++) {
            if (enc->symbols[0].matrix[i] == 0) edge_count++;
        }
        printf("\nTop row: %d/%d modules use palette index 0\n", 
            edge_count, enc->symbols[0].side_size.x);
        
        // Check actual bitmap pixels at edges and center
        printf("\nBitmap corner pixels:\n");
        int bpp = 4; // RGBA
        int bpr = enc->bitmap->width * bpp;
        printf("  Top-left (0,0): R=%02x G=%02x B=%02x A=%02x\n",
            enc->bitmap->pixel[0],
            enc->bitmap->pixel[1],
            enc->bitmap->pixel[2],
            enc->bitmap->pixel[3]);
            
        int mid = enc->bitmap->width / 2 * bpp;
        printf("  Top-mid (%d,0): R=%02x G=%02x B=%02x A=%02x\n",
            enc->bitmap->width / 2,
            enc->bitmap->pixel[mid],
            enc->bitmap->pixel[mid + 1],
            enc->bitmap->pixel[mid + 2],
            enc->bitmap->pixel[mid + 3]);
            
        // Check center pixel (should be colored)
        int center_offset = (enc->bitmap->height / 2) * bpr + (enc->bitmap->width / 2) * bpp;
        printf("  Center (%d,%d): R=%02x G=%02x B=%02x A=%02x\n",
            enc->bitmap->width / 2, enc->bitmap->height / 2,
            enc->bitmap->pixel[center_offset],
            enc->bitmap->pixel[center_offset + 1],
            enc->bitmap->pixel[center_offset + 2],
            enc->bitmap->pixel[center_offset + 3]);
            
        // Check where symbol should start (after quiet zone)
        int quiet_px = 12 * 4; // 4 modules × 12 pixels
        int symbol_start_offset = quiet_px * bpr + quiet_px * bpp;
        printf("  Symbol-start (%d,%d): R=%02x G=%02x B=%02x A=%02x\n",
            quiet_px, quiet_px,
            enc->bitmap->pixel[symbol_start_offset],
            enc->bitmap->pixel[symbol_start_offset + 1],
            enc->bitmap->pixel[symbol_start_offset + 2],
            enc->bitmap->pixel[symbol_start_offset + 3]);
    }
    
    free(data_struct);
    destroyEncode(enc);
    return 0;
}
