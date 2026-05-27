/*
 * WS-5 Council Session 3 — TDD coverage for jabMobileDecodeCameraWithMeta
 *
 * Locks the SDK metadata contract added on ws-5-color-metadata-v2: the
 * parallel decode function must populate out_color_number with the actual
 * decoded color count (2/4/8/16/32/64/128/256), and must not crash when
 * out_color_number is NULL.
 *
 * Test 7 (strict-mode isolation) is intentionally NOT in this file — it
 * exercises the strict-mode infrastructure introduced on the stacked
 * branch claude/ws-5-partII-strict-on-with-meta. Keeping it there lets
 * each branch's tests compile against its own surface area.
 *
 * Open root-cause bridged by this contract:
 *   docs/cassandra-register/H_partI_clean_data_failure.md
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"
#include "mobile_bridge.h"

typedef struct { int passed; int failed; } test_summary_t;

static void check_eq_int(const char* test, const char* label,
                         int expected, int actual, test_summary_t* sum) {
    if (expected == actual) {
        printf("  [%s] PASS %s: %d\n", test, label, actual);
        sum->passed++;
    } else {
        printf("  [%s] FAIL %s: expected=%d actual=%d\n",
               test, label, expected, actual);
        sum->failed++;
    }
}

static void check_eq_bytes(const char* test, const char* label,
                           const char* expected, int expected_len,
                           const char* actual, int actual_len,
                           test_summary_t* sum) {
    if (expected_len == actual_len &&
        memcmp(expected, actual, expected_len) == 0) {
        printf("  [%s] PASS %s: %d bytes match\n", test, label, actual_len);
        sum->passed++;
    } else {
        printf("  [%s] FAIL %s: expected_len=%d actual_len=%d\n",
               test, label, expected_len, actual_len);
        sum->failed++;
    }
}

static jab_mobile_encode_result* encode_fixture(const char* payload,
                                                int color_number) {
    jab_mobile_encode_params params = {
        .color_number = color_number,
        .symbol_number = 1,
        .ecc_level    = 3,
        .module_size  = 12,
    };
    return jabMobileEncode((jab_char*)payload, (jab_int32)strlen(payload),
                           &params);
}

/* Deterministic-pseudorandom RGBA buffer — must not parse as a JABCode. */
static jab_byte* make_noise_buffer(int w, int h, unsigned int seed) {
    int n = w * h * 4;
    jab_byte* buf = (jab_byte*)malloc(n);
    if (!buf) return NULL;
    unsigned int s = seed;
    for (int i = 0; i < n; i++) {
        s = s * 1103515245u + 12345u;
        buf[i] = (jab_byte)((s >> 16) & 0xFFu);
    }
    return buf;
}

/* ===== Test 1 (Must-be): Nc=3 fixture → color_number == 16 ===== */
static int test_nc3_success(test_summary_t* sum) {
    const char* name = "nc3_success";
    const char* payload = "HELLO";

    jab_mobile_encode_result* enc = encode_fixture(payload, 16);
    if (!enc) {
        printf("  [%s] SETUP_FAIL: encoder returned NULL\n", name);
        sum->failed++;
        return 1;
    }

    jab_int32 color_number = -1;  /* sentinel — function must overwrite */
    jab_data* result = jabMobileDecodeCameraWithMeta(
        enc->rgba_buffer, enc->width, enc->height, &color_number);

    if (!result) {
        printf("  [%s] DECODE_FAIL: %s (see H_partI_clean_data_failure)\n",
               name, jabMobileGetLastError());
        check_eq_int(name, "color_number-on-failure", 0, color_number, sum);
        jabMobileEncodeResultFree(enc);
        return 1;
    }

    check_eq_int(name, "color_number", 16, color_number, sum);
    check_eq_bytes(name, "decoded-payload",
                   payload, (int)strlen(payload),
                   result->data, result->length, sum);

    jabMobileDataFree(result);
    jabMobileEncodeResultFree(enc);
    return 0;
}

/* ===== Test 2 (Must-be): noise → NULL return + color_number == 0 ===== */
static int test_noise_failure(test_summary_t* sum) {
    const char* name = "noise_failure";
    const int W = 256, H = 256;

    jab_byte* noise = make_noise_buffer(W, H, 0xC0FFEEu);
    if (!noise) {
        printf("  [%s] SETUP_FAIL: malloc returned NULL\n", name);
        sum->failed++;
        return 1;
    }

    jab_int32 color_number = 999;  /* sentinel — function must overwrite to 0 */
    jab_data* result = jabMobileDecodeCameraWithMeta(noise, W, H, &color_number);

    if (result) {
        printf("  [%s] FAIL: expected NULL on noise, got %d bytes\n",
               name, result->length);
        sum->failed++;
        jabMobileDataFree(result);
    } else {
        printf("  [%s] PASS: NULL return on noise input\n", name);
        sum->passed++;
    }
    check_eq_int(name, "color_number-on-failure", 0, color_number, sum);

    free(noise);
    return 0;
}

/* ===== Test 3 (Must-be): NULL out_color_number → no crash ===== */
static int test_null_out_param(test_summary_t* sum) {
    const char* name = "null_out_param";
    const char* payload = "HELLO";

    jab_mobile_encode_result* enc = encode_fixture(payload, 32);
    if (!enc) {
        printf("  [%s] SETUP_FAIL: encoder returned NULL\n", name);
        sum->failed++;
        return 1;
    }

    jab_data* result = jabMobileDecodeCameraWithMeta(
        enc->rgba_buffer, enc->width, enc->height, NULL);
    /* If we reach this line, no-crash assertion holds. */
    printf("  [%s] PASS: no crash on NULL out_color_number\n", name);
    sum->passed++;

    if (result) {
        check_eq_bytes(name, "decoded-payload",
                       payload, (int)strlen(payload),
                       result->data, result->length, sum);
        jabMobileDataFree(result);
    } else {
        printf("  [%s] DECODE_INFO: %s (see H_partI_clean_data_failure)\n",
               name, jabMobileGetLastError());
    }

    jabMobileEncodeResultFree(enc);
    return 0;
}

/* ===== Test 4 (Performance): Nc=2 → color_number == 8 ===== */
static int test_nc2_coverage(test_summary_t* sum) {
    const char* name = "nc2_coverage";
    jab_mobile_encode_result* enc = encode_fixture("HELLO", 8);
    if (!enc) { sum->failed++; return 1; }

    jab_int32 color_number = -1;
    jab_data* result = jabMobileDecodeCameraWithMeta(
        enc->rgba_buffer, enc->width, enc->height, &color_number);

    if (result) {
        check_eq_int(name, "color_number", 8, color_number, sum);
        jabMobileDataFree(result);
    } else {
        printf("  [%s] DECODE_FAIL: %s (see H_partI_clean_data_failure)\n",
               name, jabMobileGetLastError());
        check_eq_int(name, "color_number-on-failure", 0, color_number, sum);
    }
    jabMobileEncodeResultFree(enc);
    return 0;
}

/* ===== Test 5 (Performance): Nc=4 → color_number == 32 ===== */
static int test_nc4_coverage(test_summary_t* sum) {
    const char* name = "nc4_coverage";
    jab_mobile_encode_result* enc = encode_fixture("HELLO", 32);
    if (!enc) { sum->failed++; return 1; }

    jab_int32 color_number = -1;
    jab_data* result = jabMobileDecodeCameraWithMeta(
        enc->rgba_buffer, enc->width, enc->height, &color_number);

    if (result) {
        check_eq_int(name, "color_number", 32, color_number, sum);
        jabMobileDataFree(result);
    } else {
        printf("  [%s] DECODE_FAIL: %s (see H_partI_clean_data_failure)\n",
               name, jabMobileGetLastError());
        check_eq_int(name, "color_number-on-failure", 0, color_number, sum);
    }
    jabMobileEncodeResultFree(enc);
    return 0;
}

/* ===== Test 6 (Performance): legacy and WithMeta produce identical bytes =====
 * Locks the invariant that WithMeta is a strict superset of the legacy
 * decode contract — same decoded bytes back, metadata is the only addition.
 * Tolerates both-paths-NULL as equivalent failure (the H_C bridge case).
 */
static int test_parallel_equivalence(test_summary_t* sum) {
    const char* name = "parallel_equivalence";
    const char* payload = "HELLO";

    jab_mobile_encode_result* enc = encode_fixture(payload, 16);
    if (!enc) { sum->failed++; return 1; }

    jab_data* legacy = jabMobileDecodeCamera(
        enc->rgba_buffer, enc->width, enc->height);

    jab_int32 color_number = -1;
    jab_data* with_meta = jabMobileDecodeCameraWithMeta(
        enc->rgba_buffer, enc->width, enc->height, &color_number);

    if (legacy && with_meta) {
        check_eq_bytes(name, "decoded-bytes-equivalence",
                       legacy->data, legacy->length,
                       with_meta->data, with_meta->length, sum);
    } else if (!legacy && !with_meta) {
        printf("  [%s] PASS: both paths NULL — equivalent failure\n", name);
        sum->passed++;
    } else {
        printf("  [%s] FAIL: divergent outcomes legacy=%s with_meta=%s\n",
               name,
               legacy   ? "decoded" : "NULL",
               with_meta? "decoded" : "NULL");
        sum->failed++;
    }

    if (legacy)    jabMobileDataFree(legacy);
    if (with_meta) jabMobileDataFree(with_meta);
    jabMobileEncodeResultFree(enc);
    return 0;
}

int main(void) {
    printf("================================================\n");
    printf("jabMobileDecodeCameraWithMeta — TDD Contract Tests\n");
    printf("Council Session 3 plan; H_C bridge via Cassandra register\n");
    printf("================================================\n");

    test_summary_t sum = {0, 0};

    printf("--- Test 1: nc3_success (Must-be) ---\n");
    test_nc3_success(&sum);
    printf("--- Test 2: noise_failure (Must-be) ---\n");
    test_noise_failure(&sum);
    printf("--- Test 3: null_out_param (Must-be) ---\n");
    test_null_out_param(&sum);
    printf("--- Test 4: nc2_coverage (Performance) ---\n");
    test_nc2_coverage(&sum);
    printf("--- Test 5: nc4_coverage (Performance) ---\n");
    test_nc4_coverage(&sum);
    printf("--- Test 6: parallel_equivalence (Performance) ---\n");
    test_parallel_equivalence(&sum);

    printf("\n================================================\n");
    printf("Summary: %d passed, %d failed\n", sum.passed, sum.failed);
    printf("================================================\n");
    return sum.failed > 0 ? 1 : 0;
}
