/*
 * test_concurrent_roundtrip.c -- codec reentrancy (thread-safety) stress test.
 *
 * Spawns N (>=8) pthreads, each performing M (>=50) encode->decode round-trips
 * over a per-thread-varied payload across colour modes Nc=1..7 (color_number
 * 4,8,16,32,64,128,256). Each thread asserts that what it decodes is
 * byte-identical to what IT encoded -- never another thread's payload. A torn
 * read of any shared codec global (the LCG seed, g_mode0_decode,
 * g_symbology_identifier, g_calibration) corrupts a decode and trips an
 * assertion here.
 *
 * This is the guard for the "_Thread_local per-operation state" change. Build
 * it with ThreadSanitizer to make the race deterministic rather than
 * probabilistic:
 *
 *   BEFORE the fix:  TSan reports data races on lcg64_seed / g_mode0_decode /
 *                    g_symbology_identifier / g_calibration, and/or the
 *                    round-trip assertions fail across repeated runs.
 *   AFTER  the fix:  TSan clean + all assertions pass across repeated runs.
 *
 * Run: build/test_concurrent_roundtrip [threads] [iters]   (defaults: 8 50)
 *      make -C src/jabcode test-concurrent       (TSan build + run)
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include "jabcode.h"

/* Colour modes under test: Nc=1..7 -> color_number = 2^(Nc+1). Nc=0
 * (monochrome, color_number 2) is intentionally exercised too via the
 * g_mode0_decode path being shared, but the round-trip sweep below pins the
 * colour breadth to Nc>=1 per the stress-test spec. */
static const int kColorModes[] = {4, 8, 16, 32, 64, 128, 256};
static const int kNumColorModes = (int)(sizeof(kColorModes) / sizeof(kColorModes[0]));

#define DEFAULT_THREADS 8
#define DEFAULT_ITERS   50
#define MAX_THREADS     64
#define ECC_LEVEL       3

typedef struct {
    int thread_id;
    int iters;
    int failures;   /* round-trip mismatches / API failures observed */
    int completed;  /* successful round-trips */
} worker_arg;

/* Build a payload unique to (thread, iteration) so a cross-thread leak is
 * detectable: if thread A ever decodes thread B's bytes, the memcmp fails.
 * Length is varied to also shake loose any length-dependent shared state. */
static jab_data *make_payload(int thread_id, int iter, char *scratch, int scratch_cap) {
    int n = snprintf(scratch, scratch_cap,
                     "T%02d-I%03d-payload-data-%d", thread_id, iter, thread_id * 1000 + iter);
    if (n < 0) n = 0;
    if (n >= scratch_cap) n = scratch_cap - 1;
    jab_data *d = (jab_data *)malloc(sizeof(jab_data) + n);
    if (!d) return NULL;
    d->length = n;
    memcpy(d->data, scratch, n);
    return d;
}

static void *worker(void *p) {
    worker_arg *a = (worker_arg *)p;
    char scratch[64];

    for (int i = 0; i < a->iters; i++) {
        int color = kColorModes[i % kNumColorModes];

        jab_data *in = make_payload(a->thread_id, i, scratch, sizeof scratch);
        if (!in) { a->failures++; continue; }

        jab_encode *enc = createEncode(color, 1);
        if (!enc) {
            fprintf(stderr, "[T%02d i%03d c%d] createEncode failed\n", a->thread_id, i, color);
            a->failures++;
            free(in);
            continue;
        }
        if (enc->symbol_ecc_levels) enc->symbol_ecc_levels[0] = ECC_LEVEL;

        jab_int32 gen_rc = generateJABCode(enc, in);
        if (gen_rc != 0 || !enc->bitmap) {
            fprintf(stderr, "[T%02d i%03d c%d] generateJABCode rc=%d\n",
                    a->thread_id, i, color, gen_rc);
            a->failures++;
            destroyEncode(enc);
            free(in);
            continue;
        }

        jab_int32 status = -1;
        jab_data *out = decodeJABCode(enc->bitmap, NORMAL_DECODE, &status);
        if (!out) {
            fprintf(stderr, "[T%02d i%03d c%d] decode NULL status=%d\n",
                    a->thread_id, i, color, status);
            a->failures++;
            destroyEncode(enc);
            free(in);
            continue;
        }

        /* The core reentrancy assertion: decoded bytes == THIS thread's own
         * encoded payload, byte-for-byte. */
        if (out->length != in->length ||
            memcmp(out->data, in->data, in->length) != 0) {
            fprintf(stderr,
                    "[T%02d i%03d c%d] ROUNDTRIP MISMATCH: encoded %d bytes, decoded %d bytes\n",
                    a->thread_id, i, color, in->length, out->length);
            a->failures++;
        } else {
            a->completed++;
        }

        free(out);
        destroyEncode(enc);
        free(in);
    }
    return NULL;
}

int main(int argc, char **argv) {
    int threads = argc > 1 ? atoi(argv[1]) : DEFAULT_THREADS;
    int iters   = argc > 2 ? atoi(argv[2]) : DEFAULT_ITERS;
    if (threads < 1) threads = 1;
    if (threads > MAX_THREADS) threads = MAX_THREADS;
    if (iters < 1) iters = 1;

    printf("================================================\n");
    printf("Concurrent Roundtrip Stress Test (codec reentrancy)\n");
    printf("threads=%d iters/thread=%d colour modes Nc=1..7\n", threads, iters);
    printf("================================================\n");

    pthread_t tids[MAX_THREADS];
    worker_arg args[MAX_THREADS];

    for (int i = 0; i < threads; i++) {
        args[i].thread_id = i;
        args[i].iters = iters;
        args[i].failures = 0;
        args[i].completed = 0;
        if (pthread_create(&tids[i], NULL, worker, &args[i]) != 0) {
            fprintf(stderr, "pthread_create failed for thread %d\n", i);
            return 2;
        }
    }

    int total_failures = 0;
    int total_completed = 0;
    for (int i = 0; i < threads; i++) {
        pthread_join(tids[i], NULL);
        total_failures += args[i].failures;
        total_completed += args[i].completed;
    }

    int expected = threads * iters;
    printf("\n------------------------------------------------\n");
    printf("round-trips: %d/%d byte-identical, %d failure(s)\n",
           total_completed, expected, total_failures);
    printf("RESULT: %s\n", (total_failures == 0 && total_completed == expected) ? "PASS" : "FAIL");
    printf("================================================\n");

    return (total_failures == 0 && total_completed == expected) ? 0 : 1;
}
