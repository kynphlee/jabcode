/*
 * bench_cascade.c -- MULTI-SYMBOL (cascade) benchmark for the JABCode codec.
 *
 * Every other harness in this suite is single-symbol: bench_codec.c times one
 * symbol's encode/decode LATENCY, bench_sweep.c maps one symbol's CAPACITY, and
 * bench_concurrent.c scales one round-trip across THREADS. None of them touch the
 * axis PR #113 made sound: CASCADE -- a payload split across N>1 docked symbols
 * (createEncode(nc, N), N up to MAX_SYMBOL_NUMBER=61). This harness adds that axis.
 *
 * A cascade is NOT just "a bigger symbol". It is N tiled symbols sharing a code:
 *   - capacity grows ~N x (each symbol carries a slice of the payload),
 *   - but each small symbol pays its OWN finder-pattern / palette / metadata
 *     overhead, so N small symbols are LESS dense than one big symbol of the same
 *     total module count -- the tiling cost this benchmark makes visible, and
 *   - decode is all-or-nothing in NORMAL mode: the decoder reassembles every
 *     symbol's slice into one payload, so one unreadable slave fails the whole
 *     decode. (This is why cascade robustness is a real, separate concern.)
 *
 * CASCADE CONSTRUCTION (the correctness crux -- get this wrong and the numbers are
 * meaningless). For N>1 createEncode zero-initialises the symbol arrays, so the
 * caller MUST populate them before generateJABCode:
 *   - symbol_positions[i] = i for i in 0..N-1. jab_symbol_pos (encoder.h) is
 *     ordered so sequential dock indices are edge-adjacent, so {0,1,2,...} is a
 *     valid connected docking chain (index 0 is the master). assignDockedSymbols
 *     fails ("slave has no host") if a slave isn't edge-adjacent to an earlier one.
 *   - symbol_versions[i] = {V,V} for every symbol. Unlike the single-symbol path,
 *     the master version is NOT auto-derived when symbol_number>1 (encoder.c only
 *     auto-sizes when symbol_number==1), so all N versions must be set explicitly.
 *   - symbol_ecc_levels[i] = ecc for every symbol.
 * Then generateJABCode(enc, data); decode with decodeJABCode(bitmap, NORMAL_DECODE,
 * &status); a correct cascade returns the EXACT input bytes (asserted via memcmp).
 *
 * KNOWN EDGE (NOT a bug to fix here): at high colour, cascade fails at slave
 * versions == 0 (mod 5) that are >=10 (v10, v15, v20...) -- a separate pre-existing
 * slave capacity/alignment-geometry resonance, documented in PR #113. The CURVES
 * use a SAFE version (V=8) so the capacity/latency/density curves are clean; the
 * SUCCESS MATRIX deliberately INCLUDES v10 and v15 so the edge is visible, not
 * hidden, and so the matrix doubles as a regression guard for the #113 high-colour
 * fix (the >=16-colour cells that used to overflow the slave palette tables).
 *
 * Two datasets are produced (selected by argv[1], "curves" | "matrix"; default
 * runs both, curves then matrix, into the two files named below):
 *
 *   (a) CURVES  -> benchmarks/data/cascade.jsonl
 *       Sweep symbol_number N in {1,2,4,8,16,32,61} x colour Nc in {4,8,64,256},
 *       fixed version V=8, fixed ecc. Per (N,Nc): encode_ms (generateJABCode only,
 *       warmup+iters, median+p95 like bench_codec), decode_ms (round-trip,
 *       median+p95), capacity_bytes (largest payload that round-trips), total_modules
 *       (sum of each symbol's side_size.x*side_size.y), density = capacity/modules,
 *       and ok. One JSON object per (N,Nc).
 *
 *   (b) MATRIX  -> benchmarks/data/cascade_matrix.jsonl
 *       Sweep Nc in {4,8,16,32,64,128,256} x version V in {8,10,12,15} x N in {2,3}
 *       with a fixed small payload; emit {nc, version, symbol_number, ok}. Guards
 *       the high-colour fix AND maps the v==0-mod-5 edge.
 *
 * CAPACITY METHOD: capacity_bytes is measured EMPIRICALLY -- a binary search for the
 * largest byte payload that round-trips losslessly through the N-symbol cascade at
 * (Nc, V). This is medium-agnostic (it asks the only question that matters: "how
 * many bytes actually survive?") and needs no internal ECC bookkeeping. The search
 * is seeded by an analytic upper bound: getSymbolCapacity() (a linkable encoder
 * function) gives each symbol's GROSS bit capacity; summed and divided by 8 it
 * bounds the byte payload from above (the true net payload is smaller -- LDPC ECC
 * + per-symbol metadata + the inter-symbol split all subtract), so it is a safe,
 * tight upper bracket for the search, never the reported value.
 *
 * Output:
 *   stdout -> one JSON object per config (JSONL, bench_concurrent.c style)
 *   stderr -> human-readable table
 *
 * Usage: bench_cascade [which=both] [warmup=5] [iters=20]
 *        which in {curves, matrix, both}
 *        (CURVES write to data/cascade.jsonl, MATRIX to data/cascade_matrix.jsonl;
 *         "both" with no redirection prints curves then matrix to stdout -- the
 *         Makefile target / README show the split-file invocation.)
 */
#define _POSIX_C_SOURCE 199309L  /* clock_gettime / CLOCK_MONOTONIC + POSIX dup/dup2 under -std=c11 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <math.h>
#include <unistd.h>   /* dup / dup2 / STDOUT_FILENO -- to mute the codec's stdout error spew */
#include <fcntl.h>    /* open() /dev/null */
#include "jabcode.h"

/* getSymbolCapacity() is a global (non-static) encoder function -- it computes a
 * symbol's GROSS bit capacity from its version + colour count -- but it has no
 * prototype in jabcode.h (it is an internal encoder.c entry point). It links from
 * the static lib (nm shows it as a 'T' global), so we declare it locally and use it
 * only to seed the capacity binary search's upper bracket (see cascade_capacity_upper).
 * Signature mirrors encoder.c:651. */
extern jab_int32 getSymbolCapacity(jab_encode *enc, jab_int32 index);

#if defined(__aarch64__)
#  define PLATFORM "arm64"
#elif defined(__x86_64__)
#  define PLATFORM "x86_64"
#elif defined(__arm__)
#  define PLATFORM "armv7"
#else
#  define PLATFORM "unknown"
#endif

/* CURVES sweep axes. */
static const int CURVE_N[]  = {1, 2, 4, 8, 16, 32, 61};   /* 61 = MAX_SYMBOL_NUMBER */
static const int CURVE_NC[] = {4, 8, 64, 256};
#define CURVE_V    8     /* SAFE version (not ==0 mod 5) -> clean curves */
#define CURVE_ECC  3     /* default ECC level */

/* MATRIX sweep axes -- deliberately spans the v==0-mod-5 edge (v10, v15). */
static const int MAT_NC[]  = {4, 8, 16, 32, 64, 128, 256};
static const int MAT_V[]   = {8, 10, 12, 15};
static const int MAT_N[]   = {2, 3};
#define MAT_ECC      3
#define MAT_PAYLOAD  16  /* fixed small payload (bytes) the smallest cell can hold */

static double now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000.0 + ts.tv_nsec / 1.0e6;
}

/* The JABCode core reports failures via reportError()/JAB_REPORT_ERROR, which
 * printf to STDOUT (encoder.c:2442, jabcode.h:66 -- a library convention). The
 * capacity binary search DELIBERATELY probes over-capacity payloads ("Message does
 * not fit..."), and a broken cell's failed generate also prints -- all of which
 * would pollute the JSONL on stdout. We mute stdout at the fd level around every
 * codec call and unmute only to emit the JSON record, so data/cascade*.jsonl stay
 * clean (matching the committed sweep.jsonl/transcode.jsonl, which carry zero
 * non-JSON lines). gen_charts.py also filters stray lines, but a clean file is the
 * suite convention. mute is refcounted-simple: save the real stdout fd once, point
 * fd 1 at /dev/null; restore flips it back. */
static int g_saved_stdout = -1;
static void mute_stdout(void) {
    if (g_saved_stdout != -1) return;          /* already muted */
    fflush(stdout);
    int devnull = open("/dev/null", O_WRONLY);
    if (devnull < 0) return;                    /* can't mute -> leave stdout as-is */
    g_saved_stdout = dup(STDOUT_FILENO);
    dup2(devnull, STDOUT_FILENO);
    close(devnull);
}
static void unmute_stdout(void) {
    if (g_saved_stdout == -1) return;           /* not muted */
    fflush(stdout);
    dup2(g_saved_stdout, STDOUT_FILENO);
    close(g_saved_stdout);
    g_saved_stdout = -1;
}

static int dcmp(const void *a, const void *b) {
    double x = *(const double *)a, y = *(const double *)b;
    return (x > y) - (x < y);
}

typedef struct { double median, p95; } stat2;

static stat2 summarize(double *v, int n) {
    qsort(v, n, sizeof(double), dcmp);
    stat2 r;
    r.median = (n & 1) ? v[n / 2] : (v[n / 2 - 1] + v[n / 2]) / 2.0;
    r.p95    = v[(int)(0.95 * (n - 1) + 0.5)];
    return r;
}

/* Build a deterministic payload of exactly len bytes (alphanumeric filler so the
 * text analyzer doesn't change behaviour between runs). Caller frees. */
static jab_data *make_payload(int len) {
    jab_data *d = (jab_data *)malloc(sizeof(jab_data) + (size_t)len);
    if (!d) return NULL;
    d->length = len;
    for (int i = 0; i < len; i++)
        d->data[i] = (jab_byte)('A' + (i % 26));
    return d;
}

/* Configure an N-symbol cascade at (nc, V, ecc): allocate, then populate the
 * zero-initialised symbol arrays per the construction contract in the file header.
 * Returns NULL on allocation failure. Caller destroyEncode()s. */
static jab_encode *make_cascade(int nc, int N, int V, int ecc) {
    jab_encode *enc = createEncode(nc, N);
    if (!enc) return NULL;
    for (int i = 0; i < N; i++) {
        enc->symbol_positions[i]  = i;          /* 0=master, sequential = edge-adjacent docks */
        enc->symbol_versions[i].x = V;
        enc->symbol_versions[i].y = V;
        if (enc->symbol_ecc_levels) enc->symbol_ecc_levels[i] = (jab_byte)ecc;
    }
    return enc;
}

/* One encode+decode round-trip of `len` payload bytes through an N-symbol cascade
 * at (nc, V, ecc). Returns 1 iff generate succeeds AND the decode returns the exact
 * input bytes (cascade reassembly is lossless). 0 on any failure. */
static int cascade_roundtrips(int nc, int N, int V, int ecc, int len) {
    jab_data *in = make_payload(len);
    if (!in) return 0;
    jab_encode *enc = make_cascade(nc, N, V, ecc);
    if (!enc) { free(in); return 0; }

    int ok = 0;
    if (generateJABCode(enc, in) == 0 && enc->bitmap) {
        jab_int32 status = -1;
        jab_data *out = decodeJABCode(enc->bitmap, NORMAL_DECODE, &status);
        if (out) {
            ok = (out->length == in->length &&
                  memcmp(out->data, in->data, (size_t)in->length) == 0);
            free(out);
        }
    }
    destroyEncode(enc);
    free(in);
    return ok;
}

/* Sum of every symbol's module count (side_size.x*side_size.y) after a successful
 * generate. generateJABCode populates symbols[i].side_size for ALL i (master at
 * encoder.c:1919, slaves at :2218), so this is the true tiled module footprint.
 * Returns 0 if generate fails. */
static long cascade_total_modules(int nc, int N, int V, int ecc, int len) {
    jab_data *in = make_payload(len);
    if (!in) return 0;
    jab_encode *enc = make_cascade(nc, N, V, ecc);
    if (!enc) { free(in); return 0; }
    long modules = 0;
    if (generateJABCode(enc, in) == 0 && enc->bitmap) {
        for (int i = 0; i < N; i++)
            modules += (long)enc->symbols[i].side_size.x * enc->symbols[i].side_size.y;
    }
    destroyEncode(enc);
    free(in);
    return modules;
}

/* Analytic UPPER bound (bytes) for the binary search: sum of each symbol's GROSS
 * bit capacity / 8. The true net payload is strictly smaller (LDPC ECC + per-symbol
 * metadata + the inter-symbol split all subtract), so this never under-shoots the
 * real capacity -- it is a safe high bracket, not the reported value. getSymbolCapacity
 * needs symbol_versions set (make_cascade did) and reads color_number; it does not
 * need a prior generate. */
static int cascade_capacity_upper(int nc, int N, int V, int ecc) {
    jab_encode *enc = make_cascade(nc, N, V, ecc);
    if (!enc) return 0;
    long bits = 0;
    for (int i = 0; i < N; i++) {
        jab_int32 c = getSymbolCapacity(enc, i);
        if (c > 0) bits += c;
    }
    destroyEncode(enc);
    long bytes = bits / 8;
    if (bytes < 1) bytes = 1;
    if (bytes > 1 << 20) bytes = 1 << 20;  /* clamp; nothing legitimately needs >1 MiB here */
    return (int)bytes;
}

/* Largest byte payload that round-trips losslessly through the (nc,N,V,ecc) cascade,
 * measured by binary search in [0, upper]. lo tracks the largest KNOWN-good length
 * (0 = nothing fit, e.g. a broken cell). The analytic upper bound brackets the search
 * from above; a couple of doublings guard the rare case where the real net capacity
 * exceeds the gross/8 estimate's integer rounding. Returns the measured capacity. */
static int cascade_capacity_bytes(int nc, int N, int V, int ecc) {
    int hi = cascade_capacity_upper(nc, N, V, ecc);
    /* Ensure hi actually FAILS so the search has an upper bracket; if the estimate
     * happens to still round-trip, push it up (bounded) until it doesn't. */
    int guard = 0;
    while (cascade_roundtrips(nc, N, V, ecc, hi) && hi < (1 << 20) && guard++ < 8)
        hi *= 2;
    int lo = 0;                 /* 0 always "fits" (empty); start of known-good */
    /* Standard largest-true binary search on a monotone predicate (fits shrinks
     * with size): invariant lo fits, hi fails. */
    while (lo + 1 < hi) {
        int mid = lo + (hi - lo) / 2;
        if (cascade_roundtrips(nc, N, V, ecc, mid)) lo = mid;
        else                                        hi = mid;
    }
    return lo;
}

/* ---- CURVES: encode/decode latency + capacity/density per (N, Nc) ---- */
static void run_curves(int warmup, int iters) {
    double *t = (double *)malloc(sizeof(double) * iters);
    if (!t) { reportError("bench_cascade: alloc failed"); return; }

    fprintf(stderr,
            "CURVES platform=%s V=%d ecc=%d warmup=%d iters=%d  (capacity = largest "
            "round-tripping payload, binary search)\n",
            PLATFORM, CURVE_V, CURVE_ECC, warmup, iters);
    fprintf(stderr, "%-3s %-4s %12s %12s %9s %9s %8s %5s\n",
            "N", "Nc", "enc med/p95", "dec med/p95", "cap(B)", "modules",
            "dens", "ok");

    int nN  = (int)(sizeof(CURVE_N)  / sizeof(CURVE_N[0]));
    int nNc = (int)(sizeof(CURVE_NC) / sizeof(CURVE_NC[0]));

    for (int a = 0; a < nN; a++) {
        for (int b = 0; b < nNc; b++) {
            int N  = CURVE_N[a];
            int nc = CURVE_NC[b];

            mute_stdout();  /* silence the codec's stdout error spew during probing */

            /* Capacity first: the latency probe encodes a payload sized to this
             * cascade (75% of capacity -> realistic fill that exercises every
             * symbol without sitting on the capacity boundary). */
            int cap = cascade_capacity_bytes(nc, N, CURVE_V, CURVE_ECC);
            long modules = cascade_total_modules(nc, N, CURVE_V, CURVE_ECC, MAT_PAYLOAD);
            int probe = cap > 0 ? cap * 3 / 4 : 0;
            if (probe < 1) probe = 1;

            /* ---- encode latency: time generateJABCode only ---- */
            stat2 es; memset(&es, 0, sizeof es);
            int gen_ok = 0;
            for (int i = 0; i < warmup; i++) {
                jab_encode *e = make_cascade(nc, N, CURVE_V, CURVE_ECC);
                jab_data *d = make_payload(probe);
                if (e && d) generateJABCode(e, d);
                free(d); if (e) destroyEncode(e);
            }
            for (int i = 0; i < iters; i++) {
                jab_encode *e = make_cascade(nc, N, CURVE_V, CURVE_ECC);
                jab_data *d = make_payload(probe);
                double t0 = now_ms();
                int rc = (e && d) ? generateJABCode(e, d) : -1;
                double t1 = now_ms();
                t[i] = t1 - t0;
                if (rc == 0 && e && e->bitmap) gen_ok = 1;
                free(d); if (e) destroyEncode(e);
            }
            es = summarize(t, iters);

            /* ---- decode latency: time decodeJABCode on a pristine cascade bitmap ---- */
            stat2 ds; memset(&ds, 0, sizeof ds);
            int rt_ok = 0;
            jab_encode *e0 = make_cascade(nc, N, CURVE_V, CURVE_ECC);
            jab_data *d0 = make_payload(probe);
            if (e0 && d0 && generateJABCode(e0, d0) == 0 && e0->bitmap) {
                for (int i = 0; i < warmup; i++) {
                    jab_int32 st = 0;
                    jab_data *r = decodeJABCode(e0->bitmap, NORMAL_DECODE, &st);
                    if (r) free(r);
                }
                for (int i = 0; i < iters; i++) {
                    jab_int32 st = 0;
                    double t0 = now_ms();
                    jab_data *r = decodeJABCode(e0->bitmap, NORMAL_DECODE, &st);
                    double t1 = now_ms();
                    t[i] = t1 - t0;
                    if (r) {
                        if (r->length == d0->length &&
                            memcmp(r->data, d0->data, (size_t)r->length) == 0)
                            rt_ok = 1;
                        free(r);
                    }
                }
                ds = summarize(t, iters);
            }
            free(d0); if (e0) destroyEncode(e0);

            int ok = (gen_ok && rt_ok && cap > 0);
            double density = modules > 0 ? (double)cap / (double)modules : 0.0;

            unmute_stdout();  /* restore stdout to emit the clean JSON record */
            printf("{\"platform\":\"%s\",\"sweep\":\"cascade\",\"symbol_number\":%d,"
                   "\"colors\":%d,\"version\":%d,\"ecc\":%d,\"probe_bytes\":%d,"
                   "\"encode_ms\":%.4f,\"encode_p95_ms\":%.4f,"
                   "\"decode_ms\":%.4f,\"decode_p95_ms\":%.4f,"
                   "\"capacity_bytes\":%d,\"total_modules\":%ld,\"density\":%.6f,"
                   "\"ok\":%s}\n",
                   PLATFORM, N, nc, CURVE_V, CURVE_ECC, probe,
                   es.median, es.p95, ds.median, ds.p95,
                   cap, modules, density,
                   ok ? "true" : "false");
            fflush(stdout);

            fprintf(stderr, "%-3d %-4d %5.2f/%-6.2f %5.2f/%-6.2f %8d %8ld %7.4f %5s\n",
                    N, nc, es.median, es.p95, ds.median, ds.p95,
                    cap, modules, density, ok ? "OK" : "FAIL");
        }
    }
    free(t);
}

/* ---- SUCCESS MATRIX: ok(nc, V, N) for a fixed small payload ---- */
static void run_matrix(void) {
    fprintf(stderr, "MATRIX platform=%s ecc=%d payload=%dB  "
            "(ok = N-symbol cascade round-trips; expect FALSE only at v10/v15 high-Nc)\n",
            PLATFORM, MAT_ECC, MAT_PAYLOAD);
    fprintf(stderr, "%-4s %-4s %-4s %5s\n", "Nc", "V", "N", "ok");

    int nNc = (int)(sizeof(MAT_NC) / sizeof(MAT_NC[0]));
    int nV  = (int)(sizeof(MAT_V)  / sizeof(MAT_V[0]));
    int nN  = (int)(sizeof(MAT_N)  / sizeof(MAT_N[0]));

    for (int a = 0; a < nNc; a++) {
        for (int b = 0; b < nV; b++) {
            for (int c = 0; c < nN; c++) {
                int nc = MAT_NC[a], V = MAT_V[b], N = MAT_N[c];
                mute_stdout();  /* v10/v15 high-Nc cells fail -> codec prints to stdout */
                int ok = cascade_roundtrips(nc, N, V, MAT_ECC, MAT_PAYLOAD);
                unmute_stdout();
                printf("{\"platform\":\"%s\",\"sweep\":\"cascade_matrix\","
                       "\"nc\":%d,\"version\":%d,\"symbol_number\":%d,\"ecc\":%d,"
                       "\"payload_bytes\":%d,\"ok\":%s}\n",
                       PLATFORM, nc, V, N, MAT_ECC, MAT_PAYLOAD,
                       ok ? "true" : "false");
                fflush(stdout);
                fprintf(stderr, "%-4d %-4d %-4d %5s\n", nc, V, N, ok ? "OK" : "FAIL");
            }
        }
    }
}

int main(int argc, char **argv) {
    const char *which = argc > 1 ? argv[1] : "both";
    int warmup = argc > 2 ? atoi(argv[2]) : 5;
    int iters  = argc > 3 ? atoi(argv[3]) : 20;
    if (warmup < 0) warmup = 0;
    if (iters < 1) iters = 1;

    if (strcmp(which, "curves") == 0) {
        run_curves(warmup, iters);
    } else if (strcmp(which, "matrix") == 0) {
        run_matrix();
    } else {
        run_curves(warmup, iters);
        run_matrix();
    }
    return 0;
}
