/*
 * bench_codec.c -- native JABCode codec microbenchmark (mobile perf "Suite A").
 *
 * Times the pure C encode + decode codec across all eight colour modes
 * (Nc = 0..7) on a fixed, in-memory bitmap -- no PNG I/O, no camera, no JVM.
 * This is the native counterpart to the server-side JMH suite and the cleanest
 * measure of the LDPC matrix cache's effect, because it isolates the codec from
 * the camera/JNI pipeline that dominates on-device end-to-end numbers.
 *
 * Cross-platform: build for the host (`make bench`) or cross-compile for the
 * device with the Android NDK to get real ARM numbers (see test/README-bench.md).
 * The platform is recorded in the output via the compile-time arch macro.
 *
 * What is timed:
 *   encode  -> generateJABCode() only (createEncode/data alloc excluded -- setup)
 *   decode  -> decodeJABCode() on the pristine in-memory bitmap (no PNG read)
 * Warmup populates the LDPC matrix cache, so the measured window is cached
 * steady-state -- the regime a server/app handling many same-mode ops runs in.
 *
 * Output:
 *   stdout -> JSON array of per-(direction,colour) records (feeds docs/benchmarks)
 *   stderr -> human-readable table
 *
 * Usage: bench_codec [warmup] [iters]      (defaults: 10 50)
 */
#define _POSIX_C_SOURCE 199309L  /* expose clock_gettime / CLOCK_MONOTONIC under -std=c11 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <math.h>
#include "jabcode.h"

#if defined(__aarch64__)
#  define PLATFORM "arm64"
#elif defined(__x86_64__)
#  define PLATFORM "x86_64"
#elif defined(__arm__)
#  define PLATFORM "armv7"
#else
#  define PLATFORM "unknown"
#endif

static double now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000.0 + ts.tv_nsec / 1.0e6;
}

static int dcmp(const void *a, const void *b) {
    double x = *(const double *)a, y = *(const double *)b;
    return (x > y) - (x < y);
}

typedef struct { double median, mean, p95, min, max, sd; } stats;

static stats summarize(double *v, int n) {
    qsort(v, n, sizeof(double), dcmp);
    double sum = 0;
    for (int i = 0; i < n; i++) sum += v[i];
    stats r;
    r.mean = sum / n;
    r.min = v[0];
    r.max = v[n - 1];
    r.median = (n & 1) ? v[n / 2] : (v[n / 2 - 1] + v[n / 2]) / 2.0;
    r.p95 = v[(int)(0.95 * (n - 1) + 0.5)];
    double var = 0;
    for (int i = 0; i < n; i++) { double d = v[i] - r.mean; var += d * d; }
    r.sd = sqrt(var / n);
    return r;
}

static jab_data *mkdata(const char *s) {
    int n = (int)strlen(s);
    jab_data *d = (jab_data *)malloc(sizeof(jab_data) + n);
    d->length = n;
    memcpy(d->data, s, n);
    return d;
}

static void emit(int *first, const char *dir, int colours, stats st, int iters, int ok) {
    if (!*first) printf(",\n");
    *first = 0;
    double ops = st.median > 0 ? 1000.0 / st.median : 0.0;
    printf("  {\"platform\":\"%s\",\"direction\":\"%s\",\"colours\":%d,"
           "\"ops_per_s\":%.2f,\"median_ms\":%.4f,\"mean_ms\":%.4f,\"p95_ms\":%.4f,"
           "\"min_ms\":%.4f,\"max_ms\":%.4f,\"stddev_ms\":%.4f,\"iters\":%d,\"ok\":%d}",
           PLATFORM, dir, colours, ops, st.median, st.mean, st.p95,
           st.min, st.max, st.sd, iters, ok);
}

int main(int argc, char **argv) {
    int warmup = argc > 1 ? atoi(argv[1]) : 10;
    int iters  = argc > 2 ? atoi(argv[2]) : 50;
    if (warmup < 0) warmup = 0;
    if (iters < 1) iters = 1;

    const int modes[] = {2, 4, 8, 16, 32, 64, 128, 256};
    const char *payload = "BENCH-0001";
    const int ecc = 3;
    double *t = (double *)malloc(sizeof(double) * iters);

    fprintf(stderr, "platform=%s warmup=%d iters=%d payload=\"%s\" ecc=%d\n",
            PLATFORM, warmup, iters, payload, ecc);
    fprintf(stderr, "%-5s %20s %20s %s\n", "Nc", "encode med/p95 (ms)",
            "decode med/p95 (ms)", "dec_ok");

    printf("[\n");
    int first = 1;
    for (int m = 0; m < 8; m++) {
        int color = modes[m];

        /* ---- encode: time generateJABCode() (the encode codec) ---- */
        for (int i = 0; i < warmup; i++) {
            jab_encode *e = createEncode(color, 1);
            if (e && e->symbol_ecc_levels) e->symbol_ecc_levels[0] = ecc;
            jab_data *d = mkdata(payload);
            generateJABCode(e, d);
            free(d);
            destroyEncode(e);
        }
        for (int i = 0; i < iters; i++) {
            jab_encode *e = createEncode(color, 1);
            if (e && e->symbol_ecc_levels) e->symbol_ecc_levels[0] = ecc;
            jab_data *d = mkdata(payload);
            double a = now_ms();
            generateJABCode(e, d);
            double b = now_ms();
            t[i] = b - a;
            free(d);
            destroyEncode(e);
        }
        stats es = summarize(t, iters);
        emit(&first, "encode", color, es, iters, iters);

        /* ---- decode: time decodeJABCode() on a pristine in-memory bitmap ---- */
        jab_encode *e0 = createEncode(color, 1);
        if (e0 && e0->symbol_ecc_levels) e0->symbol_ecc_levels[0] = ecc;
        jab_data *d0 = mkdata(payload);
        int genrc = generateJABCode(e0, d0);
        int ok = 0;
        stats ds;
        memset(&ds, 0, sizeof ds);
        if (genrc == 0 && e0->bitmap) {
            for (int i = 0; i < warmup; i++) {
                jab_int32 st = 0;
                jab_data *r = decodeJABCode(e0->bitmap, NORMAL_DECODE, &st);
                if (r) free(r);
            }
            for (int i = 0; i < iters; i++) {
                jab_int32 st = 0;
                double a = now_ms();
                jab_data *r = decodeJABCode(e0->bitmap, NORMAL_DECODE, &st);
                double b = now_ms();
                t[i] = b - a;
                if (r) { ok++; free(r); }
            }
            ds = summarize(t, iters);
        }
        emit(&first, "decode", color, ds, iters, ok);
        free(d0);
        destroyEncode(e0);

        fprintf(stderr, "%-5d %9.3f/%-9.3f %9.3f/%-9.3f %d/%d\n",
                color, es.median, es.p95, ds.median, ds.p95, ok, iters);
    }
    printf("\n]\n");
    free(t);
    return 0;
}
