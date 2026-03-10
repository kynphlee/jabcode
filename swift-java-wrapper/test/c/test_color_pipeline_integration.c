/**
 * @file test_color_pipeline_integration.c
 * @brief Integration tests for LAB + K-d tree + Adaptive Palette pipeline
 * 
 * Validates that all components work together for 16+ color mode decoding
 */

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <time.h>
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

/**
 * Simulate camera capture noise
 */
static jab_rgb_color simulate_camera_capture(jab_rgb_color original, float noise_level)
{
    jab_rgb_color captured;
    int r = original.r + (int)((rand() % 21 - 10) * noise_level);
    int g = original.g + (int)((rand() % 21 - 10) * noise_level);
    int b = original.b + (int)((rand() % 21 - 10) * noise_level);
    
    captured.r = (jab_byte)(r < 0 ? 0 : (r > 255 ? 255 : r));
    captured.g = (jab_byte)(g < 0 ? 0 : (g > 255 ? 255 : g));
    captured.b = (jab_byte)(b < 0 ? 0 : (b > 255 ? 255 : b));
    
    return captured;
}

/**
 * Apply camera color shift (brightness/saturation change)
 */
static jab_rgb_color apply_camera_shift(jab_rgb_color original, 
                                         float brightness_scale,
                                         float saturation_scale)
{
    jab_lab_color lab = rgb_to_lab(original);
    
    // Apply brightness shift
    lab.L = lab.L * brightness_scale;
    if (lab.L > 100.0f) lab.L = 100.0f;
    if (lab.L < 0.0f) lab.L = 0.0f;
    
    // Apply saturation shift
    lab.a *= saturation_scale;
    lab.b *= saturation_scale;
    
    return lab_to_rgb(lab);
}

/**
 * Integration test: 8-color mode with camera simulation
 */
int test_integration_8_color_camera_sim(void)
{
    printf("\n--- 8-Color Mode Camera Simulation ---\n");
    
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 8) == JAB_SUCCESS, "Palette init failed");
    
    jab_rgb_color* expected = adaptive_palette_get_expected(&palette);
    
    // Simulate camera with brightness=0.92, saturation=0.95
    float brightness = 0.92f;
    float saturation = 0.95f;
    
    // Add reference samples from "finder patterns"
    for (int i = 0; i < 4; i++) {
        jab_rgb_color obs = apply_camera_shift(expected[i], brightness, saturation);
        adaptive_palette_add_sample(&palette, expected[i], obs, 1.0f);
    }
    
    // Learn transform
    ASSERT_TRUE(adaptive_palette_learn_transform(&palette) == JAB_SUCCESS, "Learn failed");
    adaptive_palette_apply_transform(&palette);
    
    // Test matching with camera-shifted colors
    int correct = 0;
    for (int i = 0; i < 8; i++) {
        jab_rgb_color observed = apply_camera_shift(expected[i], brightness, saturation);
        observed = simulate_camera_capture(observed, 0.5f);  // Add noise
        
        jab_byte matched = adaptive_palette_match(&palette, observed);
        if (matched == i) correct++;
    }
    
    printf("  Correct matches: %d/8 (%.1f%%)\n", correct, correct * 100.0f / 8);
    ASSERT_TRUE(correct >= 7, "Should match at least 7/8 colors");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_integration_8_color_camera_sim\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Apply per-channel camera shift (simulates camera white balance issues)
 */
jab_rgb_color apply_per_channel_shift(jab_rgb_color color, float r_scale, float g_scale, float b_scale)
{
    jab_rgb_color result;
    float r = color.r * r_scale;
    float g = color.g * g_scale;
    float b = color.b * b_scale;
    result.r = (jab_byte)(r > 255 ? 255 : (r < 0 ? 0 : r));
    result.g = (jab_byte)(g > 255 ? 255 : (g < 0 ? 0 : g));
    result.b = (jab_byte)(b > 255 ? 255 : (b < 0 ? 0 : b));
    return result;
}

/**
 * Integration test: 16-color mode with global camera simulation
 */
int test_integration_16_color_camera_sim(void)
{
    printf("\n--- 16-Color Mode Camera Simulation (Global) ---\n");
    
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 16) == JAB_SUCCESS, "Palette init failed");
    
    jab_rgb_color* expected = adaptive_palette_get_expected(&palette);
    
    // Camera shift simulation (moderate shift)
    float brightness = 0.95f;
    float saturation = 0.93f;
    
    // Add reference samples (corners + middle)
    int ref_indices[] = {0, 5, 10, 15};
    for (int i = 0; i < 4; i++) {
        int idx = ref_indices[i];
        jab_rgb_color obs = apply_camera_shift(expected[idx], brightness, saturation);
        adaptive_palette_add_sample(&palette, expected[idx], obs, 1.0f);
    }
    
    ASSERT_TRUE(adaptive_palette_learn_transform(&palette) == JAB_SUCCESS, "Learn failed");
    adaptive_palette_apply_transform(&palette);
    
    // Test all 16 colors
    int correct = 0;
    for (int i = 0; i < 16; i++) {
        jab_rgb_color observed = apply_camera_shift(expected[i], brightness, saturation);
        observed = simulate_camera_capture(observed, 0.3f);
        
        jab_byte matched = adaptive_palette_match(&palette, observed);
        if (matched == i) correct++;
    }
    
    printf("  Correct matches: %d/16 (%.1f%%)\n", correct, correct * 100.0f / 16);
    ASSERT_TRUE(correct >= 8, "Should match at least 8/16 colors (50%)");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_integration_16_color_camera_sim\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Integration test: 16-color mode with PER-CHANNEL camera shift
 * This tests if per-channel calibration helps when camera has different R/G/B responses
 */
int test_integration_16_color_per_channel(void)
{
    printf("\n--- 16-Color Mode Per-Channel Calibration ---\n");
    
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 16) == JAB_SUCCESS, "Palette init failed");
    
    jab_rgb_color* expected = adaptive_palette_get_expected(&palette);
    
    // Per-channel camera shift (simulates white balance issues)
    // R channel weak, G normal, B boosted
    float r_scale = 0.85f;
    float g_scale = 0.98f;
    float b_scale = 1.10f;
    
    // Add reference samples with per-channel shift
    int ref_indices[] = {0, 5, 10, 15};
    for (int i = 0; i < 4; i++) {
        int idx = ref_indices[i];
        jab_rgb_color obs = apply_per_channel_shift(expected[idx], r_scale, g_scale, b_scale);
        adaptive_palette_add_sample(&palette, expected[idx], obs, 1.0f);
    }
    
    ASSERT_TRUE(adaptive_palette_learn_transform(&palette) == JAB_SUCCESS, "Learn failed");
    adaptive_palette_apply_transform(&palette);
    
    // Test all 16 colors with per-channel shift
    int correct = 0;
    for (int i = 0; i < 16; i++) {
        jab_rgb_color observed = apply_per_channel_shift(expected[i], r_scale, g_scale, b_scale);
        observed = simulate_camera_capture(observed, 0.2f);  // Low noise
        
        jab_byte matched = adaptive_palette_match(&palette, observed);
        if (matched == i) correct++;
    }
    
    printf("  Correct matches: %d/16 (%.1f%%)\n", correct, correct * 100.0f / 16);
    printf("  (Per-channel: R=%.0f%%, G=%.0f%%, B=%.0f%%)\n", 
           r_scale*100, g_scale*100, b_scale*100);
    // 16-color (4×2×2) has limited G/B variation for calibration - expect similar to global
    ASSERT_TRUE(correct >= 7, "Should match at least 7/16 colors with per-channel calibration");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_integration_16_color_per_channel\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Integration test: 64-color mode with camera simulation
 */
int test_integration_64_color_camera_sim(void)
{
    printf("\n--- 64-Color Mode Camera Simulation ---\n");
    
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 64) == JAB_SUCCESS, "Palette init failed");
    
    jab_rgb_color* expected = adaptive_palette_get_expected(&palette);
    
    // Camera shift
    float brightness = 0.95f;
    float saturation = 0.92f;
    
    // Reference samples
    int ref_indices[] = {0, 21, 42, 63};
    for (int i = 0; i < 4; i++) {
        int idx = ref_indices[i];
        jab_rgb_color obs = apply_camera_shift(expected[idx], brightness, saturation);
        adaptive_palette_add_sample(&palette, expected[idx], obs, 1.0f);
    }
    
    ASSERT_TRUE(adaptive_palette_learn_transform(&palette) == JAB_SUCCESS, "Learn failed");
    adaptive_palette_apply_transform(&palette);
    
    // Test all 64 colors
    int correct = 0;
    for (int i = 0; i < 64; i++) {
        jab_rgb_color observed = apply_camera_shift(expected[i], brightness, saturation);
        observed = simulate_camera_capture(observed, 0.2f);
        
        jab_byte matched = adaptive_palette_match(&palette, observed);
        if (matched == i) correct++;
    }
    
    printf("  Correct matches: %d/64 (%.1f%%)\n", correct, correct * 100.0f / 64);
    ASSERT_TRUE(correct >= 48, "Should match at least 48/64 colors (75%)");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_integration_64_color_camera_sim\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Integration test: 32-color mode with camera simulation
 */
int test_integration_32_color_camera_sim(void)
{
    printf("\n--- 32-Color Mode Camera Simulation ---\n");
    
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 32) == JAB_SUCCESS, "Palette init failed");
    
    jab_rgb_color* expected = adaptive_palette_get_expected(&palette);
    
    // Camera shift
    float brightness = 0.94f;
    float saturation = 0.91f;
    
    // Reference samples spread across palette
    int ref_indices[] = {0, 10, 21, 31};
    for (int i = 0; i < 4; i++) {
        int idx = ref_indices[i];
        jab_rgb_color obs = apply_camera_shift(expected[idx], brightness, saturation);
        adaptive_palette_add_sample(&palette, expected[idx], obs, 1.0f);
    }
    
    ASSERT_TRUE(adaptive_palette_learn_transform(&palette) == JAB_SUCCESS, "Learn failed");
    adaptive_palette_apply_transform(&palette);
    
    // Test all 32 colors
    int correct = 0;
    for (int i = 0; i < 32; i++) {
        jab_rgb_color observed = apply_camera_shift(expected[i], brightness, saturation);
        observed = simulate_camera_capture(observed, 0.25f);
        
        jab_byte matched = adaptive_palette_match(&palette, observed);
        if (matched == i) correct++;
    }
    
    printf("  Correct matches: %d/32 (%.1f%%)\n", correct, correct * 100.0f / 32);
    // 32-color has 4×4×2 distribution (only 2 blue levels) making it harder to classify
    ASSERT_TRUE(correct >= 12, "Should match at least 12/32 colors (37.5%) with linear transform");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_integration_32_color_camera_sim\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Integration test: 128-color mode with global camera simulation
 */
int test_integration_128_color_camera_sim(void)
{
    printf("\n--- 128-Color Mode Camera Simulation (Global) ---\n");
    
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 128) == JAB_SUCCESS, "Palette init failed");
    
    jab_rgb_color* expected = adaptive_palette_get_expected(&palette);
    
    // Camera shift
    float brightness = 0.96f;
    float saturation = 0.93f;
    
    // Reference samples spread across palette
    int ref_indices[] = {0, 42, 85, 127};
    for (int i = 0; i < 4; i++) {
        int idx = ref_indices[i];
        jab_rgb_color obs = apply_camera_shift(expected[idx], brightness, saturation);
        adaptive_palette_add_sample(&palette, expected[idx], obs, 1.0f);
    }
    
    ASSERT_TRUE(adaptive_palette_learn_transform(&palette) == JAB_SUCCESS, "Learn failed");
    adaptive_palette_apply_transform(&palette);
    
    // Test all 128 colors
    int correct = 0;
    for (int i = 0; i < 128; i++) {
        jab_rgb_color observed = apply_camera_shift(expected[i], brightness, saturation);
        observed = simulate_camera_capture(observed, 0.15f);
        
        jab_byte matched = adaptive_palette_match(&palette, observed);
        if (matched == i) correct++;
    }
    
    printf("  Correct matches: %d/128 (%.1f%%)\n", correct, correct * 100.0f / 128);
    ASSERT_TRUE(correct >= 15, "Should match at least 15/128 colors with global transform");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_integration_128_color_camera_sim\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Integration test: 128-color mode with PER-CHANNEL camera shift
 * 128-color has 8×4×4 distribution - should benefit from per-channel calibration
 */
int test_integration_128_color_per_channel(void)
{
    printf("\n--- 128-Color Mode Per-Channel Calibration ---\n");
    
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 128) == JAB_SUCCESS, "Palette init failed");
    
    jab_rgb_color* expected = adaptive_palette_get_expected(&palette);
    
    // Per-channel camera shift (simulates white balance issues)
    // R channel weak (8 levels affected), G/B normal
    float r_scale = 0.88f;
    float g_scale = 0.95f;
    float b_scale = 1.05f;
    
    // Reference samples with diverse RGB values
    // Pick samples that have non-zero values in all channels
    int ref_indices[] = {21, 42, 85, 106};  // Avoid black (0) and extremes
    for (int i = 0; i < 4; i++) {
        int idx = ref_indices[i];
        jab_rgb_color obs = apply_per_channel_shift(expected[idx], r_scale, g_scale, b_scale);
        adaptive_palette_add_sample(&palette, expected[idx], obs, 1.0f);
    }
    
    ASSERT_TRUE(adaptive_palette_learn_transform(&palette) == JAB_SUCCESS, "Learn failed");
    adaptive_palette_apply_transform(&palette);
    
    // Test all 128 colors with per-channel shift
    int correct = 0;
    for (int i = 0; i < 128; i++) {
        jab_rgb_color observed = apply_per_channel_shift(expected[i], r_scale, g_scale, b_scale);
        observed = simulate_camera_capture(observed, 0.1f);  // Low noise
        
        jab_byte matched = adaptive_palette_match(&palette, observed);
        if (matched == i) correct++;
    }
    
    printf("  Correct matches: %d/128 (%.1f%%)\n", correct, correct * 100.0f / 128);
    printf("  (Per-channel: R=%.0f%%, G=%.0f%%, B=%.0f%%)\n", 
           r_scale*100, g_scale*100, b_scale*100);
    // 128-color has 8×4×4 - should improve with per-channel calibration
    ASSERT_TRUE(correct >= 25, "Should match at least 25/128 colors (20%) with per-channel");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_integration_128_color_per_channel\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Integration test: Compare K-d tree vs linear search accuracy
 */
int test_integration_kdtree_vs_linear(void)
{
    printf("\n--- K-d Tree vs Linear Search Comparison ---\n");
    
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 128) == JAB_SUCCESS, "Palette init failed");
    
    jab_rgb_color* expected = adaptive_palette_get_expected(&palette);
    
    // Generate random test colors
    int num_tests = 500;
    int kdtree_correct = 0;
    int linear_correct = 0;
    
    clock_t kdtree_time = 0;
    clock_t linear_time = 0;
    
    for (int t = 0; t < num_tests; t++) {
        // Random color
        jab_rgb_color test = {
            (jab_byte)(rand() % 256),
            (jab_byte)(rand() % 256),
            (jab_byte)(rand() % 256)
        };
        
        // Find nearest using K-d tree (via adaptive palette)
        clock_t start = clock();
        jab_byte kdtree_idx = adaptive_palette_match(&palette, test);
        kdtree_time += clock() - start;
        
        // Find nearest using linear search
        start = clock();
        jab_int32 linear_idx = find_nearest_color_lab(test, expected, 128);
        linear_time += clock() - start;
        
        // Both should find the same result
        if (kdtree_idx == linear_idx) {
            kdtree_correct++;
            linear_correct++;
        }
    }
    
    float kdtree_ms = (float)kdtree_time * 1000.0f / CLOCKS_PER_SEC;
    float linear_ms = (float)linear_time * 1000.0f / CLOCKS_PER_SEC;
    
    printf("  K-d tree time: %.2f ms for %d queries\n", kdtree_ms, num_tests);
    printf("  Linear time:   %.2f ms for %d queries\n", linear_ms, num_tests);
    printf("  Agreement:     %d/%d (%.1f%%)\n", kdtree_correct, num_tests, 
           kdtree_correct * 100.0f / num_tests);
    
    ASSERT_TRUE(kdtree_correct == num_tests, "K-d tree and linear should agree");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_integration_kdtree_vs_linear\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Integration test: LAB perceptual matching quality
 */
int test_integration_lab_perceptual_quality(void)
{
    printf("\n--- LAB Perceptual Matching Quality ---\n");
    
    // Test that perceptually similar colors map to same palette entry
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 8) == JAB_SUCCESS, "Palette init failed");
    
    // Test: Slightly different reds should all map to red (index 4)
    jab_rgb_color reds[] = {
        {255, 0, 0},     // Pure red
        {250, 5, 5},     // Near red
        {245, 10, 8},    // Shifted red
        {240, 15, 12},   // More shifted
    };
    
    int all_red = 1;
    for (int i = 0; i < 4; i++) {
        jab_byte idx = adaptive_palette_match(&palette, reds[i]);
        if (idx != 4) {
            printf("  Red variant %d mapped to %d instead of 4\n", i, idx);
            all_red = 0;
        }
    }
    ASSERT_TRUE(all_red, "All red variants should map to red (4)");
    
    // Test: Different grays should map to black or white appropriately
    jab_rgb_color dark_gray = {40, 40, 40};
    jab_rgb_color light_gray = {215, 215, 215};
    
    jab_byte dark_idx = adaptive_palette_match(&palette, dark_gray);
    jab_byte light_idx = adaptive_palette_match(&palette, light_gray);
    
    ASSERT_TRUE(dark_idx == 0, "Dark gray should map to black (0)");
    ASSERT_TRUE(light_idx == 7, "Light gray should map to white (7)");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_integration_lab_perceptual_quality\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Integration test: Full encode-decode simulation with color mapping
 */
int test_integration_full_roundtrip_sim(void)
{
    printf("\n--- Full Roundtrip Simulation (16 colors) ---\n");
    
    jab_adaptive_palette palette;
    ASSERT_TRUE(adaptive_palette_init(&palette, 16) == JAB_SUCCESS, "Palette init failed");
    
    jab_rgb_color* expected = adaptive_palette_get_expected(&palette);
    
    // Simulate encoding: generate a sequence of color indices
    int data_length = 100;
    jab_byte original_data[100];
    jab_byte decoded_data[100];
    
    srand(12345);  // Fixed seed for reproducibility
    for (int i = 0; i < data_length; i++) {
        original_data[i] = (jab_byte)(rand() % 16);
    }
    
    // Add calibration samples
    int refs[] = {0, 7, 10, 15};
    float brightness = 0.96f;
    float saturation = 0.94f;
    
    for (int i = 0; i < 4; i++) {
        jab_rgb_color obs = apply_camera_shift(expected[refs[i]], brightness, saturation);
        adaptive_palette_add_sample(&palette, expected[refs[i]], obs, 1.0f);
    }
    
    adaptive_palette_learn_transform(&palette);
    adaptive_palette_apply_transform(&palette);
    
    // Simulate capture and decode
    int correct = 0;
    for (int i = 0; i < data_length; i++) {
        // "Encode": get expected color for this index
        jab_rgb_color pixel_color = expected[original_data[i]];
        
        // "Capture": apply camera shift + noise
        jab_rgb_color captured = apply_camera_shift(pixel_color, brightness, saturation);
        captured = simulate_camera_capture(captured, 0.4f);
        
        // "Decode": find nearest color
        decoded_data[i] = adaptive_palette_match(&palette, captured);
        
        if (decoded_data[i] == original_data[i]) {
            correct++;
        }
    }
    
    float accuracy = correct * 100.0f / data_length;
    printf("  Data accuracy: %d/%d (%.1f%%)\n", correct, data_length, accuracy);
    
    ASSERT_TRUE(accuracy >= 40.0f, "Should achieve at least 40% accuracy (16-color has tight spacing)");
    
    adaptive_palette_free(&palette);
    
    printf("PASS: test_integration_full_roundtrip_sim\n");
    tests_passed++;
    return TEST_PASSED;
}

int main(void)
{
    printf("\n========================================\n");
    printf("  Color Pipeline Integration Tests\n");
    printf("  LAB + K-d Tree + Adaptive Palette\n");
    printf("========================================\n");
    
    srand(42);
    
    test_integration_8_color_camera_sim();
    test_integration_16_color_camera_sim();
    test_integration_16_color_per_channel();
    test_integration_32_color_camera_sim();
    test_integration_64_color_camera_sim();
    test_integration_128_color_camera_sim();
    test_integration_128_color_per_channel();
    test_integration_kdtree_vs_linear();
    test_integration_lab_perceptual_quality();
    test_integration_full_roundtrip_sim();
    
    printf("\n========================================\n");
    printf("  Integration Test Summary\n");
    printf("========================================\n");
    printf("Passed: %d\n", tests_passed);
    printf("Failed: %d\n", tests_failed);
    printf("Total:  %d\n", tests_passed + tests_failed);
    printf("========================================\n");
    
    return (tests_failed > 0) ? 1 : 0;
}
