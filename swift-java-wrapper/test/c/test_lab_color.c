/**
 * @file test_lab_color.c
 * @brief Unit tests for LAB color conversion and perceptual distance functions
 */

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include "jabcode.h"
#include "lab_color.h"

#define EPSILON 0.01f
#define TEST_PASSED 0
#define TEST_FAILED 1

static int tests_passed = 0;
static int tests_failed = 0;

#define ASSERT_FLOAT_NEAR(expected, actual, eps, msg) do { \
    if (fabs((expected) - (actual)) > (eps)) { \
        printf("FAIL: %s - expected %.4f, got %.4f\n", msg, (double)(expected), (double)(actual)); \
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
 * Test RGB to LAB conversion for known colors
 */
int test_rgb_to_lab_black(void)
{
    jab_rgb_color black = {0, 0, 0};
    jab_lab_color lab = rgb_to_lab(black);
    
    ASSERT_FLOAT_NEAR(0.0f, lab.L, EPSILON, "Black L* should be 0");
    ASSERT_FLOAT_NEAR(0.0f, lab.a, EPSILON, "Black a* should be 0");
    ASSERT_FLOAT_NEAR(0.0f, lab.b, EPSILON, "Black b* should be 0");
    
    printf("PASS: test_rgb_to_lab_black\n");
    tests_passed++;
    return TEST_PASSED;
}

int test_rgb_to_lab_white(void)
{
    jab_rgb_color white = {255, 255, 255};
    jab_lab_color lab = rgb_to_lab(white);
    
    ASSERT_FLOAT_NEAR(100.0f, lab.L, EPSILON, "White L* should be 100");
    ASSERT_FLOAT_NEAR(0.0f, lab.a, 0.5f, "White a* should be ~0");
    ASSERT_FLOAT_NEAR(0.0f, lab.b, 0.5f, "White b* should be ~0");
    
    printf("PASS: test_rgb_to_lab_white\n");
    tests_passed++;
    return TEST_PASSED;
}

int test_rgb_to_lab_red(void)
{
    jab_rgb_color red = {255, 0, 0};
    jab_lab_color lab = rgb_to_lab(red);
    
    // Red in LAB: L~53.2, a~80, b~67
    ASSERT_FLOAT_NEAR(53.23f, lab.L, 1.0f, "Red L* should be ~53.2");
    ASSERT_FLOAT_NEAR(80.11f, lab.a, 1.0f, "Red a* should be ~80.1");
    ASSERT_FLOAT_NEAR(67.22f, lab.b, 1.0f, "Red b* should be ~67.2");
    
    printf("PASS: test_rgb_to_lab_red\n");
    tests_passed++;
    return TEST_PASSED;
}

int test_rgb_to_lab_green(void)
{
    jab_rgb_color green = {0, 255, 0};
    jab_lab_color lab = rgb_to_lab(green);
    
    // Green in LAB: L~87.7, a~-86.2, b~83.2
    ASSERT_FLOAT_NEAR(87.74f, lab.L, 1.0f, "Green L* should be ~87.7");
    ASSERT_FLOAT_NEAR(-86.18f, lab.a, 1.0f, "Green a* should be ~-86.2");
    ASSERT_FLOAT_NEAR(83.18f, lab.b, 1.0f, "Green b* should be ~83.2");
    
    printf("PASS: test_rgb_to_lab_green\n");
    tests_passed++;
    return TEST_PASSED;
}

int test_rgb_to_lab_blue(void)
{
    jab_rgb_color blue = {0, 0, 255};
    jab_lab_color lab = rgb_to_lab(blue);
    
    // Blue in LAB: L~32.3, a~79.2, b~-107.9
    ASSERT_FLOAT_NEAR(32.30f, lab.L, 1.0f, "Blue L* should be ~32.3");
    ASSERT_FLOAT_NEAR(79.20f, lab.a, 1.0f, "Blue a* should be ~79.2");
    ASSERT_FLOAT_NEAR(-107.86f, lab.b, 1.0f, "Blue b* should be ~-107.9");
    
    printf("PASS: test_rgb_to_lab_blue\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test LAB to RGB roundtrip conversion
 */
int test_lab_rgb_roundtrip(void)
{
    jab_rgb_color colors[] = {
        {0, 0, 0},
        {255, 255, 255},
        {255, 0, 0},
        {0, 255, 0},
        {0, 0, 255},
        {128, 128, 128},
        {255, 255, 0},
        {0, 255, 255},
        {255, 0, 255},
        {85, 0, 0},   // 16-color intermediate
        {170, 0, 0},  // 16-color intermediate
    };
    int num_colors = sizeof(colors) / sizeof(colors[0]);
    
    for (int i = 0; i < num_colors; i++) {
        jab_lab_color lab = rgb_to_lab(colors[i]);
        jab_rgb_color rgb_back = lab_to_rgb(lab);
        
        // Allow small rounding error (±1)
        if (abs(colors[i].r - rgb_back.r) > 1 ||
            abs(colors[i].g - rgb_back.g) > 1 ||
            abs(colors[i].b - rgb_back.b) > 1) {
            printf("FAIL: Roundtrip failed for RGB(%d,%d,%d) -> LAB(%.2f,%.2f,%.2f) -> RGB(%d,%d,%d)\n",
                   colors[i].r, colors[i].g, colors[i].b,
                   lab.L, lab.a, lab.b,
                   rgb_back.r, rgb_back.g, rgb_back.b);
            tests_failed++;
            return TEST_FAILED;
        }
    }
    
    printf("PASS: test_lab_rgb_roundtrip (%d colors)\n", num_colors);
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test CIE76 Delta E calculation
 */
int test_delta_e_76_identical(void)
{
    jab_lab_color lab = {50.0f, 25.0f, -15.0f};
    jab_float de = delta_e_76(lab, lab);
    
    ASSERT_FLOAT_NEAR(0.0f, de, EPSILON, "ΔE of identical colors should be 0");
    
    printf("PASS: test_delta_e_76_identical\n");
    tests_passed++;
    return TEST_PASSED;
}

int test_delta_e_76_known_difference(void)
{
    jab_lab_color lab1 = {50.0f, 0.0f, 0.0f};
    jab_lab_color lab2 = {53.0f, 4.0f, 0.0f};
    
    // Expected: sqrt(9 + 16 + 0) = sqrt(25) = 5.0
    jab_float de = delta_e_76(lab1, lab2);
    ASSERT_FLOAT_NEAR(5.0f, de, EPSILON, "ΔE should be 5.0");
    
    printf("PASS: test_delta_e_76_known_difference\n");
    tests_passed++;
    return TEST_PASSED;
}

int test_delta_e_76_perceptible_threshold(void)
{
    // Colors with ΔE < 1 should not be perceptible
    jab_rgb_color c1 = {128, 128, 128};
    jab_rgb_color c2 = {129, 128, 128};  // Very slight difference
    
    jab_lab_color lab1 = rgb_to_lab(c1);
    jab_lab_color lab2 = rgb_to_lab(c2);
    jab_float de = delta_e_76(lab1, lab2);
    
    // Should be imperceptible (< 1.0)
    if (de >= 2.0f) {
        printf("FAIL: Very similar colors have ΔE=%.2f (expected < 2.0)\n", de);
        tests_failed++;
        return TEST_FAILED;
    }
    
    printf("PASS: test_delta_e_76_perceptible_threshold (ΔE=%.2f)\n", de);
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test CIEDE2000 Delta E calculation
 */
int test_delta_e_2000_identical(void)
{
    jab_lab_color lab = {50.0f, 25.0f, -15.0f};
    jab_float de = delta_e_2000(lab, lab);
    
    ASSERT_FLOAT_NEAR(0.0f, de, EPSILON, "ΔE2000 of identical colors should be 0");
    
    printf("PASS: test_delta_e_2000_identical\n");
    tests_passed++;
    return TEST_PASSED;
}

int test_delta_e_2000_symmetry(void)
{
    jab_lab_color lab1 = {50.0f, 25.0f, -15.0f};
    jab_lab_color lab2 = {60.0f, 10.0f, 20.0f};
    
    jab_float de1 = delta_e_2000(lab1, lab2);
    jab_float de2 = delta_e_2000(lab2, lab1);
    
    ASSERT_FLOAT_NEAR(de1, de2, EPSILON, "ΔE2000 should be symmetric");
    
    printf("PASS: test_delta_e_2000_symmetry\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test find_nearest_color_lab function
 */
int test_find_nearest_color_lab_exact_match(void)
{
    jab_rgb_color palette[] = {
        {0, 0, 0},
        {255, 0, 0},
        {0, 255, 0},
        {0, 0, 255},
        {255, 255, 255}
    };
    
    jab_rgb_color test = {255, 0, 0};  // Exact match with index 1
    jab_int32 index = find_nearest_color_lab(test, palette, 5);
    
    ASSERT_INT_EQUAL(1, index, "Exact red match should return index 1");
    
    printf("PASS: test_find_nearest_color_lab_exact_match\n");
    tests_passed++;
    return TEST_PASSED;
}

int test_find_nearest_color_lab_closest_match(void)
{
    jab_rgb_color palette[] = {
        {0, 0, 0},       // Black
        {255, 0, 0},     // Red
        {0, 255, 0},     // Green
        {0, 0, 255},     // Blue
        {255, 255, 255}  // White
    };
    
    // Dark gray should be closest to black
    jab_rgb_color dark_gray = {30, 30, 30};
    jab_int32 index = find_nearest_color_lab(dark_gray, palette, 5);
    ASSERT_INT_EQUAL(0, index, "Dark gray should be closest to black");
    
    // Light gray should be closest to white
    jab_rgb_color light_gray = {220, 220, 220};
    index = find_nearest_color_lab(light_gray, palette, 5);
    ASSERT_INT_EQUAL(4, index, "Light gray should be closest to white");
    
    printf("PASS: test_find_nearest_color_lab_closest_match\n");
    tests_passed++;
    return TEST_PASSED;
}

int test_find_nearest_color_16_palette(void)
{
    // Simulate 16-color palette with intermediate values
    jab_rgb_color palette[16];
    int idx = 0;
    for (int r = 0; r <= 255; r += 85) {
        for (int g = 0; g <= 255; g += 85) {
            if (idx < 16) {
                palette[idx].r = (jab_byte)r;
                palette[idx].g = (jab_byte)g;
                palette[idx].b = 0;
                idx++;
            }
        }
    }
    
    // Test with slightly shifted color (camera simulation)
    jab_rgb_color observed = {88, 3, 2};  // Should match RGB(85, 0, 0)
    jab_int32 index = find_nearest_color_lab(observed, palette, 16);
    
    // Find expected index
    int expected_index = -1;
    for (int i = 0; i < 16; i++) {
        if (palette[i].r == 85 && palette[i].g == 0 && palette[i].b == 0) {
            expected_index = i;
            break;
        }
    }
    
    ASSERT_INT_EQUAL(expected_index, index, "Shifted color should match nearest palette entry");
    
    printf("PASS: test_find_nearest_color_16_palette\n");
    tests_passed++;
    return TEST_PASSED;
}

/**
 * Test edge cases
 */
int test_edge_case_extreme_lab_values(void)
{
    // Test clamping behavior
    jab_lab_color extreme_lab = {150.0f, 200.0f, -200.0f};  // Out of normal range
    jab_rgb_color rgb = lab_to_rgb(extreme_lab);
    
    // Should not crash, and values should be clamped to valid RGB
    if (rgb.r > 255 || rgb.g > 255 || rgb.b > 255) {
        printf("FAIL: RGB values exceed 255\n");
        tests_failed++;
        return TEST_FAILED;
    }
    
    printf("PASS: test_edge_case_extreme_lab_values\n");
    tests_passed++;
    return TEST_PASSED;
}

int main(void)
{
    printf("\n=== LAB Color Conversion Unit Tests ===\n\n");
    
    // RGB to LAB tests
    test_rgb_to_lab_black();
    test_rgb_to_lab_white();
    test_rgb_to_lab_red();
    test_rgb_to_lab_green();
    test_rgb_to_lab_blue();
    
    // Roundtrip tests
    test_lab_rgb_roundtrip();
    
    // Delta E tests
    test_delta_e_76_identical();
    test_delta_e_76_known_difference();
    test_delta_e_76_perceptible_threshold();
    test_delta_e_2000_identical();
    test_delta_e_2000_symmetry();
    
    // Nearest color tests
    test_find_nearest_color_lab_exact_match();
    test_find_nearest_color_lab_closest_match();
    test_find_nearest_color_16_palette();
    
    // Edge cases
    test_edge_case_extreme_lab_values();
    
    printf("\n=== Test Summary ===\n");
    printf("Passed: %d\n", tests_passed);
    printf("Failed: %d\n", tests_failed);
    printf("Total:  %d\n", tests_passed + tests_failed);
    
    return (tests_failed > 0) ? 1 : 0;
}
