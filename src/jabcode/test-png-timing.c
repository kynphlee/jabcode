/**
 * Standalone C test to profile PNG I/O timing
 * 
 * This bypasses the JVM to get accurate timing of:
 * 1. PNG file read + decompression
 * 2. Full decode (including PNG)
 * 3. Decode without PNG (pre-loaded bitmap)
 * 
 * Build: gcc -O2 test-png-timing.c -o test-png-timing -L./build -ljabcode -lpng -lz -lm
 * Run: LD_LIBRARY_PATH=./build ./test-png-timing test_image.png
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "jabcode.h"

// High-resolution timing
static double get_time_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000.0 + ts.tv_nsec / 1000000.0;
}

// Use the public API from jabcode.h

int main(int argc, char* argv[]) {
    if (argc < 2) {
        printf("Usage: %s <png_file> [iterations]\n", argv[0]);
        printf("\nThis test measures:\n");
        printf("  1. PNG load time (file read + zlib decompress)\n");
        printf("  2. Full decode time (PNG + decode)\n");
        printf("  3. Decode-only time (pre-loaded bitmap)\n");
        return 1;
    }
    
    char* png_file = argv[1];
    int iterations = (argc > 2) ? atoi(argv[2]) : 10;
    
    printf("==============================================\n");
    printf("PNG I/O Profiling Test\n");
    printf("==============================================\n");
    printf("File: %s\n", png_file);
    printf("Iterations: %d\n", iterations);
    printf("\n");
    
    // Warmup: Load once to warm up file cache
    printf("Warming up file cache...\n");
    jab_bitmap* warmup = readImage(png_file);
    if (!warmup) {
        printf("ERROR: Failed to load PNG file: %s\n", png_file);
        return 1;
    }
    // pixel is embedded in bitmap struct (flexible array member)
    free(warmup);
    
    // ========================================
    // Test 1: PNG Load Time Only
    // ========================================
    printf("\n--- Test 1: PNG Load Time ---\n");
    double png_times[100];
    double png_total = 0;
    
    for (int i = 0; i < iterations; i++) {
        double start = get_time_ms();
        jab_bitmap* bitmap = readImage(png_file);
        double end = get_time_ms();
        
        if (!bitmap) {
            printf("ERROR: Failed to load PNG on iteration %d\n", i);
            return 1;
        }
        
        png_times[i] = end - start;
        png_total += png_times[i];
        
        if (i == 0) {
            printf("  Image size: %dx%d, %d bpp\n", 
                   bitmap->width, bitmap->height, bitmap->bits_per_pixel);
        }
        
        free(bitmap);
    }
    
    double png_avg = png_total / iterations;
    printf("  PNG Load: %.2f ms (avg over %d iterations)\n", png_avg, iterations);
    
    // ========================================
    // Test 2: Full Decode (PNG + Decode)
    // ========================================
    printf("\n--- Test 2: Full Decode (PNG + Decode) ---\n");
    double full_times[100];
    double full_total = 0;
    
    for (int i = 0; i < iterations; i++) {
        double start = get_time_ms();
        
        // Load PNG
        jab_bitmap* bitmap = readImage(png_file);
        if (!bitmap) {
            printf("ERROR: Failed to load PNG on iteration %d\n", i);
            return 1;
        }
        
        // Decode
        jab_int32 status = 0;
        jab_data* decoded = decodeJABCode(bitmap, 0, &status);
        double end = get_time_ms();
        
        full_times[i] = end - start;
        full_total += full_times[i];
        
        if (i == 0 && decoded) {
            printf("  Decoded %d bytes\n", decoded->length);
        }
        
        if (decoded) free(decoded);
        free(bitmap);
    }
    
    double full_avg = full_total / iterations;
    printf("  Full Decode: %.2f ms (avg over %d iterations)\n", full_avg, iterations);
    
    // ========================================
    // Test 3: Decode Only (calculated from Full - PNG)
    // ========================================
    printf("\n--- Test 3: Decode Only (calculated) ---\n");
    
    // Since jab_bitmap uses flexible array member, we can't easily copy it
    // Instead, calculate decode-only time as: Full Decode - PNG Load
    double decode_total = full_total - png_total;
    
    double decode_avg = decode_total / iterations;
    printf("  Decode Only: %.2f ms (avg over %d iterations)\n", decode_avg, iterations);
    
    // ========================================
    // Summary
    // ========================================
    printf("\n==============================================\n");
    printf("SUMMARY\n");
    printf("==============================================\n");
    printf("PNG Load:      %7.2f ms (%5.1f%%)\n", png_avg, (png_avg / full_avg) * 100);
    printf("Decode Only:   %7.2f ms (%5.1f%%)\n", decode_avg, (decode_avg / full_avg) * 100);
    printf("Full Decode:   %7.2f ms (100.0%%)\n", full_avg);
    printf("----------------------------------------------\n");
    printf("PNG Overhead:  %7.2f ms\n", full_avg - decode_avg);
    printf("==============================================\n");
    
    return 0;
}
