/*
 * Mode 1 (Nc=1, 4-color) Regression Suite
 *
 * Captures baseline behavior of the most-used production color mode.
 * Required gate for ALL C library changes in WS-0 (Mode 0) and WS-3 (Nc=7 fix).
 *
 * Rationale: Mode 0 and Mode 1 share 5/9 encoding and 7/13 decoding steps.
 * A boundary-parameter change for Mode 0 COULD silently break Mode 1. This
 * suite captures the contract: identical output must be produced before and
 * after any boundary parameter change.
 *
 * See: docs/jabcode-all-nc-plan/00b-mode-0-monochrome.md Step 0.1
 *      docs/jabcode-all-nc-plan/DECISIONS.md ADR-012
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

/*
 * NOTE on return value conventions:
 * - generateJABCode returns Unix-style integer codes: 0 = success, non-zero = error code
 * - decodeJABCode returns a non-NULL jab_data* on success, NULL on failure
 *   AND populates *status (0/1/2/3 per the API documentation)
 */

typedef struct {
    const char* payload;
    int         payload_len;
    const char* description;
} test_case_t;

static const test_case_t mode1_baseline_cases[] = {
    {"HELLO",                                          5,  "case1_short_ascii"},
    {"\x01\xFF\x7F\x80\x00",                           5,  "case2_binary_edges"},
    {"The quick brown fox jumps over the lazy dog",   43, "case3_medium_ascii"},
    {NULL, 0, NULL}
};

static int test_one_case(const test_case_t* tc) {
    printf("--- %s (\"%.30s%s\" %d bytes) ---\n",
           tc->description, tc->payload,
           tc->payload_len > 30 ? "..." : "", tc->payload_len);

    /* === Encode at Nc=1 (color_number=4) === */
    jab_encode* enc = createEncode(4, 1);
    if (!enc) {
        printf("  RESULT: FAIL (createEncode returned NULL)\n");
        return 1;
    }

    jab_data* in = (jab_data*)malloc(sizeof(jab_data) + tc->payload_len);
    in->length = tc->payload_len;
    memcpy(in->data, tc->payload, tc->payload_len);

    jab_int32 gen_rc = generateJABCode(enc, in);
    if (gen_rc != 0) {
        printf("  RESULT: FAIL (generateJABCode returned %d)\n", gen_rc);
        free(in);
        destroyEncode(enc);
        return 1;
    }
    if (!enc->bitmap) {
        printf("  RESULT: FAIL (enc->bitmap is NULL after successful generate)\n");
        free(in);
        destroyEncode(enc);
        return 1;
    }
    printf("  encode_ok: bitmap=%dx%d color_number=%d\n",
           enc->bitmap->width, enc->bitmap->height, enc->color_number);

    /* === Decode === */
    jab_int32 status = -1;
    jab_data* out = decodeJABCode(enc->bitmap, NORMAL_DECODE, &status);
    if (!out) {
        printf("  RESULT: FAIL (decode returned NULL, status=%d)\n", status);
        free(in);
        destroyEncode(enc);
        return 1;
    }

    /* === Verify roundtrip integrity === */
    int integrity_ok = (out->length == in->length &&
                        memcmp(out->data, in->data, in->length) == 0);
    if (!integrity_ok) {
        printf("  RESULT: FAIL (decoded %d bytes vs %d expected; data mismatch)\n",
               out->length, in->length);
        free(in);
        free(out);
        destroyEncode(enc);
        return 1;
    }

    printf("  decode_ok: %d bytes decoded\n", out->length);
    printf("  RESULT: PASS\n");

    free(in);
    free(out);
    destroyEncode(enc);
    return 0;
}

int main(void) {
    printf("========================================\n");
    printf("Mode 1 (Nc=1) Regression Suite\n");
    printf("Purpose: capture Mode 1 baseline behavior\n");
    printf("Gate for WS-0 / WS-3 C library changes\n");
    printf("========================================\n\n");

    int total_failures = 0;
    int total_cases = 0;
    for (int i = 0; mode1_baseline_cases[i].payload != NULL; i++) {
        total_cases++;
        total_failures += test_one_case(&mode1_baseline_cases[i]);
        printf("\n");
    }

    printf("========================================\n");
    printf("Summary: %d/%d cases passed (%d failures)\n",
           total_cases - total_failures, total_cases, total_failures);
    printf("========================================\n");

    return total_failures > 0 ? 1 : 0;
}
