/*
 * test_color_calibration.c — WS-4 Step 4.3
 *
 * Phase B-Classical color calibration test. Creates a synthetic
 * "camera shift" scenario where standard colors (R, G, B, W, M) are
 * displaced from their canonical values by a known amount, then verifies
 * that color_calibration.c correctly loads the calibration profile and
 * remaps each standard color to its calibrated equivalent. Non-standard
 * pixels (e.g., grey) must pass through unchanged.
 *
 * The "synthetic image" is a small 7-pixel test sequence covering:
 *   - 5 standard calibrated colors (R, G, B, W, M)
 *   - 2 colors NOT in the calibration map (Black, Mid-Grey) — must be
 *     unchanged because the static lookup only remaps exact matches
 *
 * Current implementation under test: src/jabcode/color_calibration.c
 *   - jabLoadCalibrationFromJSON  parse JSON, populate calibration table
 *   - jabHasCalibration           query active state
 *   - jabRemapColor               apply standard → calibrated remap
 *   - jabClearCalibration         reset to inactive
 *
 * This test pins down the current behavior contract. WS-4 Step 4.4 will
 * extend this with FP-core-based dynamic calibration; that step must
 * preserve these assertions (regression gate).
 *
 * Build (from src/jabcode/):
 *   gcc -O2 -std=c11 -I. -I./include \
 *       test/test_color_calibration.c color_calibration.c \
 *       -o test/test_color_calibration
 *
 * See: docs/jabcode-all-nc-plan/00-CHECKLIST.md item 4.3
 *      docs/jabcode-all-nc-plan/04-phase-b-classical.md (WS-4 plan)
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"
#include "color_calibration.h"

/* Test palette: simulates a camera that renders standard colors with a
 * known systematic shift. The shift values are intentionally distinct so
 * a remap error (wrong index lookup) would be visible in the assertion. */
typedef struct {
    const char* name;
    jab_byte standard[3];   /* What the encoder produces */
    jab_byte shifted[3];    /* What the camera reports — calibration target */
} cal_entry_t;

/* The five colors the current color_calibration.c parses from JSON */
static const cal_entry_t CAL_ENTRIES[] = {
    {"red",     {255,   0,   0}, {220,  30,  30}},
    {"green",   {  0, 255,   0}, { 40, 200,  20}},
    {"blue",    {  0,   0, 255}, { 20,  40, 210}},
    {"white",   {255, 255, 255}, {245, 240, 235}},
    {"magenta", {255,   0, 255}, {230,  20, 240}},
};
#define N_CAL_ENTRIES (sizeof(CAL_ENTRIES) / sizeof(CAL_ENTRIES[0]))

/* Build a minimal JSON profile matching color_calibration.c's parser
 * expectations. The parser uses strstr lookups and sscanf; it does NOT
 * require strict JSON, only the keyword/array layout.                */
static int build_calibration_json(char* buf, size_t bufsz)
{
    int written = snprintf(buf, bufsz,
        "{\n"
        "  \"red\":     { \"calibrated\": [%d,%d,%d] },\n"
        "  \"green\":   { \"calibrated\": [%d,%d,%d] },\n"
        "  \"blue\":    { \"calibrated\": [%d,%d,%d] },\n"
        "  \"white\":   { \"calibrated\": [%d,%d,%d] },\n"
        "  \"magenta\": { \"calibrated\": [%d,%d,%d] }\n"
        "}\n",
        CAL_ENTRIES[0].shifted[0], CAL_ENTRIES[0].shifted[1], CAL_ENTRIES[0].shifted[2],
        CAL_ENTRIES[1].shifted[0], CAL_ENTRIES[1].shifted[1], CAL_ENTRIES[1].shifted[2],
        CAL_ENTRIES[2].shifted[0], CAL_ENTRIES[2].shifted[1], CAL_ENTRIES[2].shifted[2],
        CAL_ENTRIES[3].shifted[0], CAL_ENTRIES[3].shifted[1], CAL_ENTRIES[3].shifted[2],
        CAL_ENTRIES[4].shifted[0], CAL_ENTRIES[4].shifted[1], CAL_ENTRIES[4].shifted[2]
    );
    return (written > 0 && (size_t)written < bufsz) ? 1 : 0;
}

/* Assertion helper — returns 1 on PASS, 0 on FAIL; prints diagnostics */
static int assert_remap(const char* label,
                        const jab_byte input[3],
                        const jab_byte expected[3])
{
    jab_byte output[3] = {0, 0, 0};
    jabRemapColor(input, output);

    int ok = (output[0] == expected[0] &&
              output[1] == expected[1] &&
              output[2] == expected[2]);

    printf("  %-30s in=(%3d,%3d,%3d) → out=(%3d,%3d,%3d)  expected=(%3d,%3d,%3d)  %s\n",
        label,
        input[0], input[1], input[2],
        output[0], output[1], output[2],
        expected[0], expected[1], expected[2],
        ok ? "PASS" : "FAIL");
    return ok;
}

int main(void)
{
    printf("================================================\n");
    printf("WS-4 Step 4.3: color_calibration.c TDD test\n");
    printf("================================================\n");

    int failures = 0;

    /* ---- Phase 1: Initial state should be inactive ---- */
    printf("--- Phase 1: Initial state ---\n");
    jabClearCalibration();
    if (jabHasCalibration()) {
        printf("  FAIL: jabHasCalibration() should return false after clear\n");
        failures++;
    } else {
        printf("  PASS: jabHasCalibration() is false after clear\n");
    }

    /* ---- Phase 2: Load calibration profile from JSON ---- */
    printf("--- Phase 2: Load calibration JSON ---\n");
    char json_buf[1024];
    if (!build_calibration_json(json_buf, sizeof(json_buf))) {
        printf("  FAIL: build_calibration_json failed\n");
        return 1;
    }

    jab_int32 load_rc = jabLoadCalibrationFromJSON(json_buf);
    if (load_rc != 1) {
        printf("  FAIL: jabLoadCalibrationFromJSON returned %d (expected 1)\n", load_rc);
        failures++;
    } else if (!jabHasCalibration()) {
        printf("  FAIL: jabHasCalibration() is false after successful load\n");
        failures++;
    } else {
        printf("  PASS: JSON loaded; calibration is active\n");
    }

    /* ---- Phase 3: Standard colors remap to calibrated equivalents ---- */
    printf("--- Phase 3: Standard color remap ---\n");
    for (size_t i = 0; i < N_CAL_ENTRIES; i++) {
        if (!assert_remap(CAL_ENTRIES[i].name,
                          CAL_ENTRIES[i].standard,
                          CAL_ENTRIES[i].shifted)) {
            failures++;
        }
    }

    /* ---- Phase 4: Non-standard colors pass through unchanged ---- */
    printf("--- Phase 4: Non-standard pass-through ---\n");
    const jab_byte mid_grey[3]    = {128, 128, 128};
    const jab_byte off_red[3]     = {200,  50,  50};   /* Close to but not equal to standard red */
    if (!assert_remap("mid_grey (unmapped)", mid_grey, mid_grey))   failures++;
    if (!assert_remap("off_red  (unmapped)", off_red,  off_red))    failures++;

    /* ---- Phase 5: After clear, all colors pass through unchanged ---- */
    printf("--- Phase 5: Clear restores pass-through ---\n");
    jabClearCalibration();
    if (jabHasCalibration()) {
        printf("  FAIL: jabHasCalibration() true after clear\n");
        failures++;
    }
    /* Even standard red should pass through unchanged after clear */
    if (!assert_remap("standard red (cleared)",
                      CAL_ENTRIES[0].standard,
                      CAL_ENTRIES[0].standard)) {
        failures++;
    }

    printf("\n================================================\n");
    if (failures == 0) {
        printf("Summary: PASS (all phases green — calibration contract preserved)\n");
    } else {
        printf("Summary: FAIL (%d assertion(s) failed)\n", failures);
    }
    printf("================================================\n");

    return failures > 0 ? 1 : 0;
}
