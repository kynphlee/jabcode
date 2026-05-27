/*
 * WS-2 Step 2.1: Diagnostic instrumentation test
 *
 * Verifies that three per-stage diagnostic markers fire during a normal
 * master-symbol decode:
 *   - DIAG_PALETTE_LEARNED  (after readColorPaletteInMaster succeeds)
 *   - DIAG_PARTII_RESULT    (after decodeMasterMetadataPartII)
 *   - DIAG_SYMBOL_DECODE    (after decodeSymbol classifies modules)
 *
 * Approach: capture stdout via fmemopen (Linux/macOS portable; documented
 * platform constraint — Windows would need an alternative). The decode
 * library writes JAB_REPORT_INFO output to stdout per jabcode.h:67.
 *
 * See: docs/jabcode-all-nc-plan/02-diagnostic-instrumentation.md
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "jabcode.h"

static char log_buffer[65536];

/* Generate a Mode 1 "HELLO" image in memory and decode it, capturing stdout
 * via freopen to a tempfile. After decode, read the tempfile back into
 * log_buffer for substring matching. Portable on Linux/macOS. */
static int run_decode_capturing_stdout(void) {
    /* Step A: encode "HELLO" at Nc=1 */
    jab_encode* enc = createEncode(4 /* color_number */, 1 /* symbol_number */);
    if (!enc) return 1;
    const char* payload = "HELLO";
    jab_data* in = (jab_data*)malloc(sizeof(jab_data) + 5);
    in->length = 5;
    memcpy(in->data, payload, 5);
    if (generateJABCode(enc, in) != 0 || !enc->bitmap) {
        free(in);
        destroyEncode(enc);
        return 2;
    }

    /* Step B: redirect stdout to a tempfile */
    const char* logpath = "/tmp/ws2_diag_capture.log";
    fflush(stdout);
    /* Save current stdout via dup */
    int saved_stdout_fd = dup(fileno(stdout));
    if (saved_stdout_fd < 0) { free(in); destroyEncode(enc); return 3; }
    /* Replace stdout with the tempfile */
    if (!freopen(logpath, "w", stdout)) {
        close(saved_stdout_fd);
        free(in); destroyEncode(enc);
        return 3;
    }

    /* Step C: enable verbose diagnostic logging (gated since WS-5 Heisenberg
     * fix to keep production camera-thread overhead low). Then decode. */
    jabSetDiagVerbose(1);
    jab_int32 status = -1;
    jab_data* result = decodeJABCode(enc->bitmap, NORMAL_DECODE, &status);
    jabSetDiagVerbose(0);

    /* Step D: restore stdout */
    fflush(stdout);
    /* freopen with /dev/null and then dup2 the saved fd back */
    if (freopen("/dev/null", "w", stdout)) { /* keep going */ }
    dup2(saved_stdout_fd, fileno(stdout));
    close(saved_stdout_fd);
    /* Reset stdout buffering after restoration */
    clearerr(stdout);

    /* Step E: read the captured log */
    FILE* fp = fopen(logpath, "r");
    if (!fp) {
        if (result) free(result);
        free(in); destroyEncode(enc);
        return 5;
    }
    size_t n = fread(log_buffer, 1, sizeof(log_buffer) - 1, fp);
    log_buffer[n] = '\0';
    fclose(fp);

    /* Step F: verify roundtrip succeeded and free */
    int roundtrip_ok = (result &&
                        result->length == 5 &&
                        memcmp(result->data, payload, 5) == 0);
    if (result) free(result);
    free(in);
    destroyEncode(enc);

    if (!roundtrip_ok) {
        printf("FAIL: decode roundtrip did not return \"HELLO\"\n");
        return 4;
    }
    return 0;
}

int main(void) {
    printf("========================================\n");
    printf("WS-2 Diagnostic Logging Test\n");
    printf("========================================\n");

    int rc = run_decode_capturing_stdout();
    if (rc != 0) {
        printf("FAIL: capture infrastructure or decode failed (rc=%d)\n", rc);
        return 1;
    }

    /* Print the captured buffer for visibility (truncated) */
    printf("\nCaptured log (first 600 chars):\n%.600s\n\n", log_buffer);

    /* Assertions: all three markers must appear */
    int failures = 0;

    if (!strstr(log_buffer, "DIAG_PALETTE_LEARNED")) {
        printf("FAIL: missing DIAG_PALETTE_LEARNED marker in captured log\n");
        failures++;
    } else {
        printf("PASS: DIAG_PALETTE_LEARNED marker present\n");
    }

    if (!strstr(log_buffer, "DIAG_PARTII_RESULT")) {
        printf("FAIL: missing DIAG_PARTII_RESULT marker in captured log\n");
        failures++;
    } else {
        printf("PASS: DIAG_PARTII_RESULT marker present\n");
    }

    if (!strstr(log_buffer, "DIAG_SYMBOL_DECODE")) {
        printf("FAIL: missing DIAG_SYMBOL_DECODE marker in captured log\n");
        failures++;
    } else {
        printf("PASS: DIAG_SYMBOL_DECODE marker present\n");
    }

    printf("\n========================================\n");
    printf("Result: %s (%d marker failures)\n", failures == 0 ? "PASS" : "FAIL", failures);
    printf("========================================\n");
    return failures > 0 ? 1 : 0;
}
