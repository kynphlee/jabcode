/*
 * WS-3 Step 3.1: All-Nc roundtrip test
 *
 * Iterates Nc=0..7, encodes "HELLO", decodes, and asserts the decoded
 * data matches the input. Reports per-Nc pass/fail so the failure
 * boundary is visible.
 *
 * Currently expected:
 *   Nc=0..6 PASS
 *   Nc=7    FAIL (library bug — being fixed in this workstream)
 *
 * See: docs/jabcode-all-nc-plan/03-phase-a-library-fix.md
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

typedef struct {
    jab_int32 color_number;
    jab_int32 nc;
    const char* name;
} nc_mode_t;

static const nc_mode_t modes[] = {
    {2,   0, "Nc=0 (2-color/monochrome)"},
    {4,   1, "Nc=1 (4-color)"},
    {8,   2, "Nc=2 (8-color)"},
    {16,  3, "Nc=3 (16-color)"},
    {32,  4, "Nc=4 (32-color)"},
    {64,  5, "Nc=5 (64-color)"},
    {128, 6, "Nc=6 (128-color)"},
    {256, 7, "Nc=7 (256-color)"},
};

static int test_one(const nc_mode_t* m) {
    const char* payload = "HELLO";
    const int payload_len = 5;

    jab_encode* enc = createEncode(m->color_number, 1);
    if (!enc) {
        printf("  FAIL: createEncode(%d) returned NULL\n", m->color_number);
        return 1;
    }
    if (enc->color_number != m->color_number) {
        printf("  FAIL: silent upgrade — requested=%d actual=%d\n",
               m->color_number, enc->color_number);
        destroyEncode(enc);
        return 1;
    }

    jab_data* in = (jab_data*)malloc(sizeof(jab_data) + payload_len);
    in->length = payload_len;
    memcpy(in->data, payload, payload_len);

    jab_int32 gen_rc = generateJABCode(enc, in);
    if (gen_rc != 0 || !enc->bitmap) {
        printf("  FAIL: generateJABCode returned %d (0=success)\n", gen_rc);
        free(in);
        destroyEncode(enc);
        return 1;
    }

    jab_int32 status = -1;
    jab_data* result = decodeJABCode(enc->bitmap, NORMAL_DECODE, &status);
    if (!result) {
        printf("  FAIL: decode returned NULL (status=%d)\n", status);
        free(in);
        destroyEncode(enc);
        return 1;
    }

    int ok = (result->length == payload_len &&
              memcmp(result->data, payload, payload_len) == 0);
    if (!ok) {
        printf("  FAIL: roundtrip mismatch (decoded %d bytes)\n", result->length);
        free(result);
        free(in);
        destroyEncode(enc);
        return 1;
    }

    printf("  PASS: %d bytes decoded correctly\n", result->length);
    free(result);
    free(in);
    destroyEncode(enc);
    return 0;
}

int main(void) {
    printf("================================================\n");
    printf("All-Nc Roundtrip Test (Nc=0..7)\n");
    printf("================================================\n");

    int failures = 0;
    int n_modes = sizeof(modes) / sizeof(modes[0]);
    for (int i = 0; i < n_modes; i++) {
        printf("--- %s ---\n", modes[i].name);
        failures += test_one(&modes[i]);
    }

    printf("\n================================================\n");
    printf("Summary: %d/%d Nc levels PASS\n", n_modes - failures, n_modes);
    printf("================================================\n");
    return failures > 0 ? 1 : 0;
}
