#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

int main() {
    printf("\n=== Desktop Decoder Test on Synthetic Bitmap ===\n\n");
    printf("Goal: Verify if desktop decoder (with camera binarization) can decode\n");
    printf("      encoder-generated synthetic bitmaps with perfect palette colors.\n\n");
    
    // Encode with desktop encoder
    const char* test_data = "Test";
    jab_int32 color_number = 4;
    jab_int32 symbol_number = 1;
    
    jab_encode* enc = createEncode(color_number, symbol_number);
    if (!enc) {
        printf("ERROR: Failed to create encoder\n");
        return 1;
    }
    
    enc->module_size = 12;
    enc->master_symbol_width = 0;  // auto
    enc->master_symbol_height = 0; // auto
    
    // Allocate jab_data properly (flexible array member)
    jab_int32 data_len = strlen(test_data);
    jab_data* data = (jab_data*)malloc(sizeof(jab_data) + data_len);
    if (!data) {
        printf("ERROR: Memory allocation failed\n");
        destroyEncode(enc);
        return 1;
    }
    data->length = data_len;
    memcpy(data->data, test_data, data_len);
    
    printf("Encoding with desktop encoder: color=%d, ecc=default, module_size=12\n", color_number);
    if (generateJABCode(enc, data) != 0) {
        printf("ERROR: Encoding failed\n");
        free(data);
        destroyEncode(enc);
        return 1;
    }
    free(data);
    
    // Get the encoded bitmap
    jab_bitmap* bitmap = enc->bitmap;
    printf("Encoded: %dx%d bitmap\n", bitmap->width, bitmap->height);
    
    // Sample some pixels to verify palette colors
    printf("\nSample pixels from encoded bitmap:\n");
    jab_int32 bpp = bitmap->bits_per_pixel / 8;
    jab_int32 bpr = bitmap->width * bpp;
    
    jab_int32 offset = 0;
    printf("  (0,0): R=%02x G=%02x B=%02x A=%02x\n",
        bitmap->pixel[offset], bitmap->pixel[offset+1],
        bitmap->pixel[offset+2], bitmap->pixel[offset+3]);
    
    offset = 48 * bpr + 48 * bpp;
    printf("  (48,48): R=%02x G=%02x B=%02x A=%02x\n",
        bitmap->pixel[offset], bitmap->pixel[offset+1],
        bitmap->pixel[offset+2], bitmap->pixel[offset+3]);
    
    printf("\nTesting DESKTOP DECODER (uses camera binarization + Nc detection)...\n");
    
    // Decode with desktop decoder - uses full camera pipeline
    jab_int32 decode_status;
    jab_data* decoded = decodeJABCode(bitmap, NORMAL_DECODE, &decode_status);
    
    if (!decoded) {
        printf("\n❌ Desktop decoder FAILED (status=%d)\n", decode_status);
        printf("\nConclusion: Desktop decoder with camera binarization CANNOT decode\n");
        printf("            encoder-generated synthetic bitmaps. This confirms:\n");
        printf("            1. Simple threshold binarization is insufficient\n");
        printf("            2. Nc detection from alignment patterns fails on synthetic images\n");
        printf("            3. We need Option B: clean synthetic-specific decode path\n");
        destroyEncode(enc);
        return 1;
    }
    
    printf("\n✅ Desktop decoder SUCCESS!\n");
    printf("Decoded %d bytes: %.*s\n", decoded->length, decoded->length, decoded->data);
    
    // Verify
    if (decoded->length == strlen(test_data) && 
        memcmp(decoded->data, test_data, decoded->length) == 0) {
        printf("\n✓ Data matches!\n");
        printf("\nConclusion: Desktop decoder CAN decode synthetic bitmaps.\n");
        printf("            The issue is in our synthetic decoder implementation.\n");
        printf("            We should fix extractRGBChannelsSynthetic() to match\n");
        printf("            the camera binarizer's color classification logic.\n");
    }
    
    free(decoded);
    destroyEncode(enc);
    
    return 0;
}
