/*
 * WS-0 Mode 0 trigger tolerance test
 *
 * Verifies that the chroma-tolerance Mode 0 sample-check (replacing the
 * strict R==G==B equality at detector.c:3631) correctly:
 *
 *   (a) classifies encoder-generated Nc=0 (monochrome) bitmaps as Mode 0
 *       both clean AND after synthetic camera-style chroma noise injection
 *   (b) does NOT classify encoder-generated Nc>=1 colored bitmaps as
 *       Mode 0, either clean or noisy
 *
 * The empirical failure that motivated this test:
 *   tolerance4-test-20260527_031332.logcat — 36/36 status=0 fails on the
 *   nc0-2c-20260521.png fixture, because real camera chroma noise made
 *   the strict equality check reject every greyscale pixel.
 *
 * See: docs/jabcode-all-nc-plan/00b-mode-0-monochrome.md
 *      memory: project_jabcode_screen_vs_print_physics.md (Mode 0 section)
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "jabcode.h"

#define LOG_PATH "/tmp/mode0_tolerance_capture.log"

typedef struct {
    int nc;
    int color_number;
    int expected_mode0;
    const char* desc;
} test_case_t;

static const test_case_t cases[] = {
    {0, 2,  1, "Nc=0 monochrome"},
    {1, 4,  0, "Nc=1 4-color   "},
    {2, 8,  0, "Nc=2 8-color   "},
    {3, 16, 0, "Nc=3 16-color  "},
    {4, 32, 0, "Nc=4 32-color  "},
};

/* Add synthetic ±5 ADU per-channel chroma noise to a bitmap, simulating
 * what a phone camera adds via Bayer demosaicing on real captures. */
static void add_chroma_noise(jab_bitmap* bitmap, unsigned int seed) {
    unsigned long pixel_count = (unsigned long)bitmap->width * bitmap->height;
    unsigned int rng = seed;
    for (unsigned long i = 0; i < pixel_count; i++) {
        jab_int32 off = (jab_int32)(i * 4);  /* RGBA layout, 4 bytes/pixel */
        for (int c = 0; c < 3; c++) {  /* R, G, B; leave A alone */
            rng = rng * 1103515245u + 12345u;
            int noise = (int)((rng >> 16) % 11u) - 5;  /* -5..+5 */
            int v = (int)bitmap->pixel[off + c] + noise;
            if (v < 0) v = 0;
            if (v > 255) v = 255;
            bitmap->pixel[off + c] = (jab_byte)v;
        }
    }
}

static int extract_mode0_from_capture(const char* logpath) {
    FILE* fp = fopen(logpath, "r");
    if (!fp) return -1;
    char buf[8192];
    size_t n = fread(buf, 1, sizeof(buf) - 1, fp);
    buf[n] = '\0';
    fclose(fp);
    char* p = strstr(buf, "g_mode0_decode=");
    if (!p) return -1;
    return atoi(p + strlen("g_mode0_decode="));
}

static int test_one(const test_case_t* tc, int add_noise) {
    /* 1. Encode "HELLO" at this Nc to produce a fresh in-memory bitmap. */
    jab_encode* enc = createEncode(tc->color_number, 1);
    if (!enc) {
        printf("  [%s %-6s] SETUP_FAIL: createEncode(%d) returned NULL\n",
               tc->desc, add_noise ? "+noise" : "clean  ", tc->color_number);
        return 1;
    }
    jab_data* in = (jab_data*)malloc(sizeof(jab_data) + 5);
    in->length = 5;
    memcpy(in->data, "HELLO", 5);
    if (generateJABCode(enc, in) != 0 || !enc->bitmap) {
        printf("  [%s %-6s] SETUP_FAIL: generateJABCode failed\n",
               tc->desc, add_noise ? "+noise" : "clean  ");
        free(in);
        destroyEncode(enc);
        return 1;
    }

    if (add_noise) {
        add_chroma_noise(enc->bitmap, 0xDECAFBADu ^ (unsigned int)tc->nc);
    }

    /* 2. Enable verbose marker emission and capture stdout while decoding. */
    jabSetDiagVerbose(1);
    fflush(stdout);
    int saved_stdout_fd = dup(fileno(stdout));
    if (!freopen(LOG_PATH, "w", stdout)) {
        printf("  [%s] SETUP_FAIL: freopen capture path\n", tc->desc);
        close(saved_stdout_fd);
        free(in);
        destroyEncode(enc);
        return 1;
    }

    jab_int32 status = -1;
    jab_data* result = decodeJABCode(enc->bitmap, NORMAL_DECODE, &status);

    fflush(stdout);
    if (freopen("/dev/null", "w", stdout)) { /* keep going */ }
    dup2(saved_stdout_fd, fileno(stdout));
    close(saved_stdout_fd);
    clearerr(stdout);
    jabSetDiagVerbose(0);

    /* 3. Parse DIAG_MODE0_DETECT marker from the captured log. */
    int actual_mode0 = extract_mode0_from_capture(LOG_PATH);

    int pass = (actual_mode0 == tc->expected_mode0);
    printf("  [%s %-6s] %s: g_mode0_decode=%d expected=%d\n",
           tc->desc, add_noise ? "+noise" : "clean ",
           pass ? "PASS" : "FAIL", actual_mode0, tc->expected_mode0);

    if (result) free(result);
    free(in);
    destroyEncode(enc);
    return pass ? 0 : 1;
}

int main(void) {
    printf("================================================\n");
    printf("WS-0 Mode 0 Trigger Tolerance Test\n");
    printf("Verifies MODE0_MEAN_CHROMA_TOLERANCE in detector.c\n");
    printf("================================================\n");

    int failures = 0;
    int n_cases = (int)(sizeof(cases) / sizeof(cases[0]));

    printf("\n--- Clean fixtures (no noise) ---\n");
    for (int i = 0; i < n_cases; i++) failures += test_one(&cases[i], 0);

    printf("\n--- Same fixtures with synthetic +-5 ADU chroma noise ---\n");
    for (int i = 0; i < n_cases; i++) failures += test_one(&cases[i], 1);

    int total = n_cases * 2;
    printf("\n================================================\n");
    printf("Summary: %d/%d PASS, %d FAIL\n", total - failures, total, failures);
    printf("================================================\n");
    return failures > 0 ? 1 : 0;
}
