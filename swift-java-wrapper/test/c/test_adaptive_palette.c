/**
 * @file test_adaptive_palette.c
 * @brief Unit tests for adaptive palette system
 */

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include "jabcode.h"
#include "lab_color.h"
#include "kdtree_color.h"
#include "adaptive_palette.h"

#define TEST_PASSED 0
#define TEST_FAILED 1

static int tests_passed = 0;
static int tests_failed = 0;

#define ASSERT_TRUE(cond, msg) do { \
    if (!(cond)) { \
        printf("FAIL: %s\n", msg); \
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
 * Test initialization for various color counts
 */
int test_adaptive_palette_init(void)
{
    jab_adaptive_palette palette;
    
    // Test 8-color init
    ASSERT_TRUE(adaptive_palette_init(&palette, 8) == JAB_SUCCESS, "8-color init should succeed");
    ASSERT_INT_EQUAL(8, palette.color_count, "Color count should be 8");
    ASSERT_TRUE(palette.lookup_tree != NULL, "K-d tree should be created");
    adaptive_palette_free(&palette);
    
    // Test 16-color init
    ASSERT_TRUE(adaptive_palette_init(&palette, 16) == JAB_SUCCESS, "16-color init should succeed");
    ASSERT_INT_EQUAL(16, palette.color_count, "Color count should be 16");
    adaptive_palette_free(&palette);
    
    // Test 64-color init
    ASSERT_TRUE(adaptive_palette_init(&palette, 64) == JAB_SUCCESS, "64-color init should succeed");
    ASSERT_INT_EQUAL(64, palette.color_count, "Color count should be 64");
    adaptive_palette_free(&palette);
    
    // Test 128-color init
    ASSERT_TRUE(adaptive_palette_init(&palette, 128) == JAB_SUCCESS, "128-color init should succeed");
    ASSERT_INT_EQUAL(128, palette.color_count, "Color count should be 128");
    adaptive_palette_free(&palette);
    
    // Test invalid inputs
    ASSERT_TRUE(adaptive_palette_init(NULL, 8) == JAB_FAILURE, "NULL palette should fail");
    ASSERT_TRUE(adaptive_palette_init(&palette, 0) == JAB_FAILURE, "0 colors should fail");
    ASSERT_TRUE(adaptive_palette_init(&palette, 300) == JAB_FAILURE, "300 colors should fail");
    
    printf("PASS: test_adaptive_palette_init\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test exact color matching without transform
 */
int test_adaptive_palette_exact_match(void)
{
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 8) == JAB_SUCCESS, "Init should succeed");
    
    // Test exact matches for 8-color palette
    jab_rgb_color black = {0, 0, 0};
    jab_rgb_color white = {255, 255, 255};
    jab_rgb_color red = {255, 0, 0};
    jab_rgb_color green = {0, 255, 0};
    
    ASSERT_INT_EQUAL(0, adaptive_palette_match(&palette, black), "Black should match index 0");
    ASSERT_INT_EQUAL(7, adaptive_palette_match(&palette, white), "White should match index 7");
    ASSERT_INT_EQUAL(4, adaptive_palette_match(&palette, red), "Red should match index 4");
    ASSERT_INT_EQUAL(2, adaptive_palette_match(&palette, green), "Green should match index 2");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_adaptive_palette_exact_match\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test color matching with simulated camera shift
 */
int test_adaptive_palette_shifted_match(void)
{
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 8) == JAB_SUCCESS, "Init should succeed");
    
    // Simulated camera-shifted colors (slight tint/brightness change)
    jab_rgb_color shifted_black = {10, 8, 12};      // Should match black (0)
    jab_rgb_color shifted_white = {245, 250, 248};  // Should match white (7)
    jab_rgb_color shifted_red = {248, 15, 8};       // Should match red (4)
    jab_rgb_color shifted_green = {12, 240, 15};    // Should match green (2)
    
    ASSERT_INT_EQUAL(0, adaptive_palette_match(&palette, shifted_black), "Shifted black should match black");
    ASSERT_INT_EQUAL(7, adaptive_palette_match(&palette, shifted_white), "Shifted white should match white");
    ASSERT_INT_EQUAL(4, adaptive_palette_match(&palette, shifted_red), "Shifted red should match red");
    ASSERT_INT_EQUAL(2, adaptive_palette_match(&palette, shifted_green), "Shifted green should match green");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_adaptive_palette_shifted_match\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test adding reference samples
 */
int test_adaptive_palette_add_samples(void)
{
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 8) == JAB_SUCCESS, "Init should succeed");
    
    ASSERT_INT_EQUAL(0, palette.sample_count, "Initial sample count should be 0");
    
    // Add reference samples (expected vs observed)
    jab_rgb_color expected_white = {255, 255, 255};
    jab_rgb_color observed_white = {240, 245, 235};  // Camera sees slightly different
    adaptive_palette_add_sample(&palette, expected_white, observed_white, 1.0f);
    
    ASSERT_INT_EQUAL(1, palette.sample_count, "Sample count should be 1");
    
    jab_rgb_color expected_black = {0, 0, 0};
    jab_rgb_color observed_black = {15, 12, 18};
    adaptive_palette_add_sample(&palette, expected_black, observed_black, 0.9f);
    
    ASSERT_INT_EQUAL(2, palette.sample_count, "Sample count should be 2");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_adaptive_palette_add_samples\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test learning color transform from samples
 */
int test_adaptive_palette_learn_transform(void)
{
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 8) == JAB_SUCCESS, "Init should succeed");
    
    // Not enough samples should fail
    ASSERT_TRUE(adaptive_palette_learn_transform(&palette) == JAB_FAILURE, 
                "Should fail with no samples");
    
    // Add white sample (brightness reference)
    jab_rgb_color expected_white = {255, 255, 255};
    jab_rgb_color observed_white = {230, 230, 230};  // Camera slightly darker
    adaptive_palette_add_sample(&palette, expected_white, observed_white, 1.0f);
    
    ASSERT_TRUE(adaptive_palette_learn_transform(&palette) == JAB_FAILURE, 
                "Should fail with only 1 sample");
    
    // Add black sample
    jab_rgb_color expected_black = {0, 0, 0};
    jab_rgb_color observed_black = {20, 20, 20};  // Camera doesn't reach true black
    adaptive_palette_add_sample(&palette, expected_black, observed_black, 1.0f);
    
    // Now should succeed
    ASSERT_TRUE(adaptive_palette_learn_transform(&palette) == JAB_SUCCESS, 
                "Should succeed with 2 samples");
    ASSERT_TRUE(palette.transform.is_valid, "Transform should be valid");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_adaptive_palette_learn_transform\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test applying transform to palette
 */
int test_adaptive_palette_apply_transform(void)
{
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 8) == JAB_SUCCESS, "Init should succeed");
    
    // Add calibration samples simulating camera color shift
    jab_rgb_color exp_white = {255, 255, 255};
    jab_rgb_color obs_white = {240, 245, 240};
    adaptive_palette_add_sample(&palette, exp_white, obs_white, 1.0f);
    
    jab_rgb_color exp_black = {0, 0, 0};
    jab_rgb_color obs_black = {10, 12, 10};
    adaptive_palette_add_sample(&palette, exp_black, obs_black, 1.0f);
    
    jab_rgb_color exp_red = {255, 0, 0};
    jab_rgb_color obs_red = {235, 20, 15};
    adaptive_palette_add_sample(&palette, exp_red, obs_red, 0.8f);
    
    // Learn and apply transform
    ASSERT_TRUE(adaptive_palette_learn_transform(&palette) == JAB_SUCCESS, "Learn should succeed");
    adaptive_palette_apply_transform(&palette);
    
    // After transform, adapted palette should be different from expected
    // (adapted to match camera's color characteristics)
    jab_rgb_color* adapted = adaptive_palette_get_adapted(&palette);
    jab_rgb_color* expected = adaptive_palette_get_expected(&palette);
    
    ASSERT_TRUE(adapted != NULL, "Adapted palette should not be NULL");
    ASSERT_TRUE(expected != NULL, "Expected palette should not be NULL");
    
    // Verify adapted palette has changed (at least slightly)
    int changes = 0;
    for (int i = 0; i < 8; i++) {
        if (adapted[i].r != expected[i].r || 
            adapted[i].g != expected[i].g || 
            adapted[i].b != expected[i].b) {
            changes++;
        }
    }
    ASSERT_TRUE(changes > 0, "Adapted palette should differ from expected");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_adaptive_palette_apply_transform\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test confidence scoring
 */
int test_adaptive_palette_confidence(void)
{
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 8) == JAB_SUCCESS, "Init should succeed");
    
    jab_float confidence;
    
    // Exact match should have high confidence
    jab_rgb_color white = {255, 255, 255};
    jab_byte idx = adaptive_palette_match_with_confidence(&palette, white, &confidence);
    ASSERT_INT_EQUAL(7, idx, "White should match index 7");
    ASSERT_TRUE(confidence > 0.5f, "Exact match should have high confidence");
    
    // Ambiguous color (between two palette colors) should have lower confidence
    jab_rgb_color ambiguous = {128, 128, 0};  // Between black and yellow
    idx = adaptive_palette_match_with_confidence(&palette, ambiguous, &confidence);
    // This might match yellow (6) or another color, but confidence should be reasonable
    ASSERT_TRUE(confidence >= 0.0f && confidence <= 1.0f, "Confidence should be in [0,1]");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_adaptive_palette_confidence\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test reset functionality
 */
int test_adaptive_palette_reset(void)
{
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 8) == JAB_SUCCESS, "Init should succeed");
    
    // Add samples and learn transform
    jab_rgb_color exp = {255, 255, 255};
    jab_rgb_color obs = {230, 230, 230};
    adaptive_palette_add_sample(&palette, exp, obs, 1.0f);
    adaptive_palette_add_sample(&palette, (jab_rgb_color){0,0,0}, (jab_rgb_color){20,20,20}, 1.0f);
    adaptive_palette_learn_transform(&palette);
    
    ASSERT_INT_EQUAL(2, palette.sample_count, "Should have 2 samples");
    ASSERT_TRUE(palette.transform.is_valid, "Transform should be valid");
    
    // Reset
    adaptive_palette_reset(&palette);
    
    ASSERT_INT_EQUAL(0, palette.sample_count, "Sample count should be 0 after reset");
    ASSERT_TRUE(!palette.transform.is_valid, "Transform should be invalid after reset");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_adaptive_palette_reset\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test 16-color mode matching
 */
int test_adaptive_palette_16_colors(void)
{
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 16) == JAB_SUCCESS, "Init should succeed");
    
    // Get the expected palette
    jab_rgb_color* expected = adaptive_palette_get_expected(&palette);
    ASSERT_TRUE(expected != NULL, "Expected palette should not be NULL");
    
    // Test that each palette color matches itself
    for (int i = 0; i < 16; i++) {
        jab_byte idx = adaptive_palette_match(&palette, expected[i]);
        ASSERT_INT_EQUAL(i, idx, "Palette color should match itself");
    }
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_adaptive_palette_16_colors\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test 64-color mode matching
 */
int test_adaptive_palette_64_colors(void)
{
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 64) == JAB_SUCCESS, "Init should succeed");
    
    // Get the expected palette
    jab_rgb_color* expected = adaptive_palette_get_expected(&palette);
    ASSERT_TRUE(expected != NULL, "Expected palette should not be NULL");
    
    // Test that each palette color matches itself
    int exact_matches = 0;
    for (int i = 0; i < 64; i++) {
        jab_byte idx = adaptive_palette_match(&palette, expected[i]);
        if (idx == i) exact_matches++;
    }
    
    // All exact matches should succeed
    ASSERT_INT_EQUAL(64, exact_matches, "All 64 colors should match exactly");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_adaptive_palette_64_colors\n");
    tests_passed++;
    return TEST_PASSED;
}

int main(void)
{
    printf("\n=== Adaptive Palette Unit Tests ===\n\n");
    
    test_adaptive_palette_init();
    test_adaptive_palette_exact_match();
    test_adaptive_palette_shifted_match();
    test_adaptive_palette_add_samples();
    test_adaptive_palette_learn_transform();
    test_adaptive_palette_apply_transform();
    test_adaptive_palette_confidence();
    test_adaptive_palette_reset();
    test_adaptive_palette_16_colors();
    test_adaptive_palette_64_colors();
    
    printf("\n=== Test Summary ===\n");
    printf("Passed: %d\n", tests_passed);
    printf("Failed: %d\n", tests_failed);
    printf("Total:  %d\n", tests_passed + tests_failed);
    
    return (tests_failed > 0) ? 1 : 0;
}
