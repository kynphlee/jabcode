/**
 * @file test_kdtree_color.c
 * @brief Unit tests for K-d tree color quantization
 */

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <time.h>
#include "jabcode.h"
#include "lab_color.h"
#include "kdtree_color.h"

#define TEST_PASSED 0
#define TEST_FAILED 1

static int tests_passed = 0;
static int tests_failed = 0;

#define ASSERT_NOT_NULL(ptr, msg) do { \
    if ((ptr) == NULL) { \
        printf("FAIL: %s - pointer is NULL\n", msg); \
        tests_failed++; \
        return TEST_FAILED; \
    } \
} while(0)

#define ASSERT_INT_EQUAL(expected, actual, msg) do { \
    if ((expected) != (actual)) { \
        printf("FAIL: %s - expected %d, got %d\n", msg, (expected), (actual)); \
        tests_failed++; \
        return TEST_FAILED; \
    } \
} while(0)

/**
 * Test building a k-d tree from a simple 8-color palette
 */
int test_kdtree_build_8_colors(void)
{
    // Standard 8-color palette (Black, Blue, Green, Cyan, Red, Magenta, Yellow, White)
    jab_byte palette[] = {
        0, 0, 0,       // Black
        0, 0, 255,     // Blue
        0, 255, 0,     // Green
        0, 255, 255,   // Cyan
        255, 0, 0,     // Red
        255, 0, 255,   // Magenta
        255, 255, 0,   // Yellow
        255, 255, 255  // White
    };
    
    kdtree_color* tree = kdtree_build(palette, 8, 0);
    ASSERT_NOT_NULL(tree, "K-d tree should be created");
    ASSERT_INT_EQUAL(8, tree->color_count, "Tree should have 8 colors");
    
    kdtree_free(tree);
    
    printf("PASS: test_kdtree_build_8_colors\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test exact color matching
 */
int test_kdtree_exact_match(void)
{
    jab_byte palette[] = {
        0, 0, 0,       // Black (0)
        255, 0, 0,     // Red (1)
        0, 255, 0,     // Green (2)
        0, 0, 255,     // Blue (3)
        255, 255, 255  // White (4)
    };
    
    kdtree_color* tree = kdtree_build(palette, 5, 0);
    ASSERT_NOT_NULL(tree, "K-d tree should be created");
    
    // Test exact matches
    jab_rgb_color red = {255, 0, 0};
    jab_lab_color red_lab = rgb_to_lab(red);
    jab_byte idx = kdtree_nearest(tree, red_lab);
    ASSERT_INT_EQUAL(1, idx, "Exact red should return index 1");
    
    jab_rgb_color green = {0, 255, 0};
    jab_lab_color green_lab = rgb_to_lab(green);
    idx = kdtree_nearest(tree, green_lab);
    ASSERT_INT_EQUAL(2, idx, "Exact green should return index 2");
    
    jab_rgb_color white = {255, 255, 255};
    jab_lab_color white_lab = rgb_to_lab(white);
    idx = kdtree_nearest(tree, white_lab);
    ASSERT_INT_EQUAL(4, idx, "Exact white should return index 4");
    
    kdtree_free(tree);
    
    printf("PASS: test_kdtree_exact_match\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test nearest neighbor for shifted colors (camera simulation)
 */
int test_kdtree_nearest_shifted(void)
{
    jab_byte palette[] = {
        0, 0, 0,       // Black (0)
        255, 0, 0,     // Red (1)
        0, 255, 0,     // Green (2)
        0, 0, 255,     // Blue (3)
        255, 255, 255  // White (4)
    };
    
    kdtree_color* tree = kdtree_build(palette, 5, 0);
    ASSERT_NOT_NULL(tree, "K-d tree should be created");
    
    // Test shifted colors (simulating camera capture)
    jab_rgb_color shifted_red = {248, 8, 5};  // Should match red
    jab_lab_color shifted_red_lab = rgb_to_lab(shifted_red);
    jab_byte idx = kdtree_nearest(tree, shifted_red_lab);
    ASSERT_INT_EQUAL(1, idx, "Shifted red should match red (index 1)");
    
    jab_rgb_color shifted_green = {10, 245, 8};  // Should match green
    jab_lab_color shifted_green_lab = rgb_to_lab(shifted_green);
    idx = kdtree_nearest(tree, shifted_green_lab);
    ASSERT_INT_EQUAL(2, idx, "Shifted green should match green (index 2)");
    
    jab_rgb_color dark_gray = {30, 30, 30};  // Should match black
    jab_lab_color dark_gray_lab = rgb_to_lab(dark_gray);
    idx = kdtree_nearest(tree, dark_gray_lab);
    ASSERT_INT_EQUAL(0, idx, "Dark gray should match black (index 0)");
    
    jab_rgb_color light_gray = {230, 230, 230};  // Should match white
    jab_lab_color light_gray_lab = rgb_to_lab(light_gray);
    idx = kdtree_nearest(tree, light_gray_lab);
    ASSERT_INT_EQUAL(4, idx, "Light gray should match white (index 4)");
    
    kdtree_free(tree);
    
    printf("PASS: test_kdtree_nearest_shifted\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test 16-color palette with intermediate values
 */
int test_kdtree_16_colors(void)
{
    // Generate 16-color palette with intermediate values
    jab_byte palette[16 * 3];
    int idx = 0;
    for (int r = 0; r <= 255; r += 85) {
        for (int g = 0; g <= 255; g += 85) {
            if (idx < 16) {
                palette[idx * 3 + 0] = (jab_byte)r;
                palette[idx * 3 + 1] = (jab_byte)g;
                palette[idx * 3 + 2] = 0;
                idx++;
            }
        }
    }
    
    kdtree_color* tree = kdtree_build(palette, 16, 0);
    ASSERT_NOT_NULL(tree, "K-d tree should be created for 16 colors");
    ASSERT_INT_EQUAL(16, tree->color_count, "Tree should have 16 colors");
    
    // Test that slightly shifted color finds correct match
    // RGB(85, 0, 0) is in the palette
    jab_rgb_color observed = {88, 3, 2};  // Slightly shifted
    jab_lab_color observed_lab = rgb_to_lab(observed);
    jab_byte result = kdtree_nearest(tree, observed_lab);
    
    // Find expected index for RGB(85, 0, 0)
    int expected = -1;
    for (int i = 0; i < 16; i++) {
        if (palette[i * 3] == 85 && palette[i * 3 + 1] == 0 && palette[i * 3 + 2] == 0) {
            expected = i;
            break;
        }
    }
    
    ASSERT_INT_EQUAL(expected, result, "Shifted RGB(88,3,2) should match RGB(85,0,0)");
    
    kdtree_free(tree);
    
    printf("PASS: test_kdtree_16_colors\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test performance: K-d tree vs linear search
 */
int test_kdtree_performance(void)
{
    // Generate 128-color palette
    jab_byte palette[128 * 3];
    for (int i = 0; i < 128; i++) {
        palette[i * 3 + 0] = (jab_byte)(i * 2);
        palette[i * 3 + 1] = (jab_byte)((i * 3) % 256);
        palette[i * 3 + 2] = (jab_byte)((i * 5) % 256);
    }
    
    kdtree_color* tree = kdtree_build(palette, 128, 0);
    ASSERT_NOT_NULL(tree, "K-d tree should be created for 128 colors");
    
    // Convert palette to jab_rgb_color for linear search comparison
    jab_rgb_color palette_rgb[128];
    for (int i = 0; i < 128; i++) {
        palette_rgb[i].r = palette[i * 3 + 0];
        palette_rgb[i].g = palette[i * 3 + 1];
        palette_rgb[i].b = palette[i * 3 + 2];
    }
    
    // Test multiple queries and compare results
    int num_queries = 1000;
    int mismatches = 0;
    
    clock_t kdtree_start = clock();
    for (int i = 0; i < num_queries; i++) {
        jab_rgb_color query_rgb = {
            (jab_byte)(rand() % 256),
            (jab_byte)(rand() % 256),
            (jab_byte)(rand() % 256)
        };
        jab_lab_color query_lab = rgb_to_lab(query_rgb);
        jab_byte kdtree_result = kdtree_nearest(tree, query_lab);
        jab_int32 linear_result = find_nearest_color_lab(query_rgb, palette_rgb, 128);
        
        if (kdtree_result != (jab_byte)linear_result) {
            mismatches++;
        }
    }
    clock_t kdtree_end = clock();
    
    double kdtree_time = (double)(kdtree_end - kdtree_start) / CLOCKS_PER_SEC;
    
    // K-d tree and linear should produce same results
    if (mismatches > 0) {
        printf("FAIL: K-d tree and linear search produced %d mismatches out of %d queries\n", 
               mismatches, num_queries);
        kdtree_free(tree);
        tests_failed++;
        return TEST_FAILED;
    }
    
    printf("PASS: test_kdtree_performance - %d queries in %.4f seconds, 0 mismatches\n", 
           num_queries, kdtree_time);
    
    kdtree_free(tree);
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test edge case: NULL inputs
 */
int test_kdtree_null_inputs(void)
{
    kdtree_color* tree = kdtree_build(NULL, 8, 0);
    if (tree != NULL) {
        printf("FAIL: kdtree_build should return NULL for NULL palette\n");
        kdtree_free(tree);
        tests_failed++;
        return TEST_FAILED;
    }
    
    tree = kdtree_build((jab_byte*)"test", 0, 0);
    if (tree != NULL) {
        printf("FAIL: kdtree_build should return NULL for color_number=0\n");
        kdtree_free(tree);
        tests_failed++;
        return TEST_FAILED;
    }
    
    // Test kdtree_nearest with NULL tree
    jab_lab_color query = {50.0f, 0.0f, 0.0f};
    jab_byte result = kdtree_nearest(NULL, query);
    // Should return 0 (default) without crashing
    
    // Test kdtree_free with NULL (should not crash)
    kdtree_free(NULL);
    
    printf("PASS: test_kdtree_null_inputs\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test single color palette
 */
int test_kdtree_single_color(void)
{
    jab_byte palette[] = {128, 64, 32};
    
    kdtree_color* tree = kdtree_build(palette, 1, 0);
    ASSERT_NOT_NULL(tree, "K-d tree should be created for single color");
    ASSERT_INT_EQUAL(1, tree->color_count, "Tree should have 1 color");
    
    // Any query should return index 0
    jab_rgb_color query = {255, 0, 0};
    jab_lab_color query_lab = rgb_to_lab(query);
    jab_byte result = kdtree_nearest(tree, query_lab);
    ASSERT_INT_EQUAL(0, result, "Single color tree should always return index 0");
    
    kdtree_free(tree);
    
    printf("PASS: test_kdtree_single_color\n");
    tests_passed++;
    return TEST_PASSED;
}

int main(void)
{
    printf("\n=== K-d Tree Color Quantization Unit Tests ===\n\n");
    
    srand(42);  // Fixed seed for reproducibility
    
    test_kdtree_build_8_colors();
    test_kdtree_exact_match();
    test_kdtree_nearest_shifted();
    test_kdtree_16_colors();
    test_kdtree_performance();
    test_kdtree_null_inputs();
    test_kdtree_single_color();
    
    printf("\n=== Test Summary ===\n");
    printf("Passed: %d\n", tests_passed);
    printf("Failed: %d\n", tests_failed);
    printf("Total:  %d\n", tests_passed + tests_failed);
    
    return (tests_failed > 0) ? 1 : 0;
}
