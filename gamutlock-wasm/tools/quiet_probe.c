/*
 * quiet_probe — latency-fair native decode probe for the WASM-vs-native A/B.
 *
 * The R0 rig probe (robustness/r0/rig/r0_decode.c) is the decode-RATE
 * authority, but its per-image time includes jabSetDiagVerbose(1) plus an
 * fd-level stdout capture — instrumentation the browser side does not carry
 * (the wrapper's quiet mode makes the module's print callbacks no-ops).
 * Comparing those times against the quiet WASM runs would charge the native
 * decoder for the rig's own diagnostics, inflating the "WASM is faster"
 * cells — most visibly at 16 colours where the Nc ladder logs heavily.
 *
 * This probe runs the IDENTICAL decode call sequence (strict Part-II,
 * decodeJABCodeEx, NORMAL_DECODE) with verbose OFF and no fd games, and
 * reports wall time + the same success fields, so:
 *   - decode-rate must agree with the rig run (cross-checked in join),
 *   - decode_ms is an apples-to-apples native latency.
 */

#define _POSIX_C_SOURCE 199309L
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "jabcode.h"

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: %s <image.png> [...]\n", argv[0]);
        return 2;
    }
    /* Route the codec's always-on failure prints away from our JSONL. */
    for (int i = 1; i < argc; i++) {
        jab_bitmap *bm = readImage((jab_char *)argv[i]);
        if (!bm) {
            printf("{\"file\":\"%s\",\"error\":\"readImage failed\"}\n", argv[i]);
            continue;
        }
        jab_int32 status = -1;
        jab_decoded_symbol symbols[MAX_SYMBOL_NUMBER];
        memset(symbols, 0, sizeof(symbols));

        fflush(stdout);
        FILE *real = stdout;
        stdout = fopen("/dev/null", "w");   /* silence JAB_REPORT_ERROR printf */

        struct timespec t0, t1;
        clock_gettime(CLOCK_MONOTONIC, &t0);
        jabSetStrictPartIIRequired(1);
        jab_data *res = decodeJABCodeEx(bm, NORMAL_DECODE, &status,
                                        symbols, MAX_SYMBOL_NUMBER);
        jabSetStrictPartIIRequired(0);
        clock_gettime(CLOCK_MONOTONIC, &t1);

        fclose(stdout);
        stdout = real;

        double ms = (t1.tv_sec - t0.tv_sec) * 1000.0 +
                    (t1.tv_nsec - t0.tv_nsec) / 1.0e6;
        int nc = res ? (int)symbols[0].metadata.Nc : -1;
        printf("{\"file\":\"%s\",\"decode_ok\":%d,\"status\":%d,\"nc\":%d,"
               "\"decode_ms\":%.3f,\"payload_len\":%d}\n",
               argv[i], res ? 1 : 0, status, nc, ms, res ? res->length : -1);
        if (res) free(res);
        free(bm);
    }
    return 0;
}
