/*
 * bench_concurrent.c -- concurrent-THROUGHPUT benchmark for the reentrant codec.
 *
 * The single-threaded suite (bench_codec.c) measures per-op LATENCY. It cannot
 * see what PR #110 actually bought, because #110 changed nothing about how fast
 * a single round-trip runs -- it made the C codec REENTRANT (per-operation
 * process-global state is now _Thread_local), which only pays off when many
 * threads run the codec at once. The value of #110 is THROUGHPUT SCALING, and
 * that is what this benchmark measures.
 *
 * To make the measurement honest it runs the SAME codec two ways at each thread
 * count T and reports both:
 *
 *   CONCURRENT  -- the reentrant codec on T threads with NO lock. Post-#110 each
 *                  thread owns its codec state, so throughput should climb toward
 *                  T x the single-thread rate (capped by cores / memory
 *                  bandwidth / malloc contention).
 *   SERIALIZED  -- the exact same codec on T threads, but each encode+decode
 *                  round-trip is wrapped in ONE global pthread_mutex. This
 *                  reproduces the pre-#110 reality where the only safe way to use
 *                  the codec from many threads was to serialize every call behind
 *                  a lock. Throughput stays pinned at ~the single-thread rate no
 *                  matter how many threads you add.
 *
 * The GAP between the two lines is exactly the throughput PR #110 unlocked.
 *
 * Correctness under load is asserted every iteration: each round-trip checks the
 * decoded bytes are byte-identical to what THAT thread encoded (the same
 * reentrancy invariant test_concurrent_roundtrip.c guards). ops_ok is reported
 * per measurement and the run aborts/flags if any thread ever sees a mismatch --
 * post-#110 this must be 100%.
 *
 * Config: a single representative payload -- 8-colour (COLOR_8), 256-byte
 * payload, default ECC -- encode -> decode round-trip.
 *
 * Output:
 *   stdout -> one JSON object per thread count (JSONL, bench_codec.c style),
 *             feeds benchmarks/data/concurrent_throughput.jsonl
 *   stderr -> human-readable table
 *
 * Usage: bench_concurrent [duration_ms=2000] [max_threads=nproc]
 */
#define _POSIX_C_SOURCE 199309L  /* clock_gettime / CLOCK_MONOTONIC under -std=c11 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <time.h>
#include <unistd.h>
#include <pthread.h>
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

/* Representative single config: 8-colour, 256-byte payload, default ECC. */
#define BENCH_COLORS   8        /* COLOR_8 */
#define BENCH_PAYLOAD  256      /* bytes */
#define BENCH_ECC      3        /* default ECC level */

#define MAX_THREADS    256
#define WARMUP_OPS     3        /* untimed round-trips before each measurement */
#define DEFAULT_MS     2000

/* One global lock shared by ALL serialized workers -- the pre-#110 "lock
 * everything" simulation. Unused in concurrent mode. */
static pthread_mutex_t g_serial_lock = PTHREAD_MUTEX_INITIALIZER;

/* Start gate so all worker threads begin timing together rather than staggered
 * by pthread_create latency. A portable mutex+condvar barrier -- pthread_barrier
 * needs _POSIX_C_SOURCE>=200112L, but this suite pins 199309L (for clock_gettime)
 * and a hand-rolled gate keeps that convention. The last thread to arrive flips
 * the gate open and broadcasts; earlier arrivals wait on it. */
static pthread_mutex_t g_gate_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t  g_gate_cond = PTHREAD_COND_INITIALIZER;
static int             g_gate_count;   /* threads not yet at the gate */
static int             g_gate_open;    /* set once all have arrived */

/* Shared start clock + deadline, set by the gate-releasing (last-arriving)
 * thread so the timed window starts when ALL workers are warmed and at the gate
 * -- excludes pthread_create + warm-up from the measurement. */
static double g_timed_t0;     /* now_ms() at gate open */
static double g_deadline_ms;  /* g_timed_t0 + duration: when workers stop looping */

static double now_ms(void);  /* defined below */

static void gate_reset(int threads) {
    pthread_mutex_lock(&g_gate_lock);
    g_gate_count = threads;
    g_gate_open  = 0;
    pthread_mutex_unlock(&g_gate_lock);
}

/* Each worker calls this once it is warmed up. The LAST arrival starts the timed
 * window (records t0 + the shared deadline_ms) and releases everyone, so the
 * measured wall clock contains only steady-state round-trips. duration_ms is the
 * length of the window the caller requested. */
static void gate_wait(double duration_ms) {
    pthread_mutex_lock(&g_gate_lock);
    if (--g_gate_count == 0) {
        g_timed_t0    = now_ms();
        g_deadline_ms = g_timed_t0 + duration_ms;
        g_gate_open   = 1;
        pthread_cond_broadcast(&g_gate_cond);
    } else {
        while (!g_gate_open)
            pthread_cond_wait(&g_gate_cond, &g_gate_lock);
    }
    pthread_mutex_unlock(&g_gate_lock);
}

typedef struct {
    int      thread_id;
    double   duration_ms;  /* how long to loop AFTER the gate opens */
    bool     serialize;    /* hold g_serial_lock around each round-trip */
    long     ops;          /* round-trips completed (out) */
    long     failures;     /* byte-mismatches / API failures (out) */
} worker_arg;

static double now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000.0 + ts.tv_nsec / 1.0e6;
}

/* Build a payload unique to (thread, iteration) and exactly BENCH_PAYLOAD bytes
 * long, so a cross-thread state leak under load is detectable: if thread A ever
 * decodes thread B's bytes the memcmp fails. The leading tag varies per
 * (thread,iter); the remainder is deterministic filler to hit the fixed length. */
static jab_data *make_payload(int thread_id, long iter) {
    jab_data *d = (jab_data *)malloc(sizeof(jab_data) + BENCH_PAYLOAD);
    if (!d) return NULL;
    d->length = BENCH_PAYLOAD;
    char tag[48];
    int n = snprintf(tag, sizeof tag, "T%03d-I%08ld-", thread_id, iter);
    if (n < 0) n = 0;
    if (n > BENCH_PAYLOAD) n = BENCH_PAYLOAD;
    memcpy(d->data, tag, (size_t)n);
    for (int i = n; i < BENCH_PAYLOAD; i++)
        d->data[i] = (jab_byte)('A' + ((thread_id + i) % 26));
    return d;
}

/* One encode->decode round-trip with a byte-identity assertion. Returns 1 on a
 * verified-correct round-trip, 0 on any failure (and bumps *failures). */
static int roundtrip_once(int thread_id, long iter, long *failures) {
    jab_data *in = make_payload(thread_id, iter);
    if (!in) { (*failures)++; return 0; }

    jab_encode *enc = createEncode(BENCH_COLORS, 1);
    if (!enc) { (*failures)++; free(in); return 0; }
    if (enc->symbol_ecc_levels) enc->symbol_ecc_levels[0] = BENCH_ECC;

    int ok = 0;
    if (generateJABCode(enc, in) == 0 && enc->bitmap) {
        jab_int32 status = -1;
        jab_data *out = decodeJABCode(enc->bitmap, NORMAL_DECODE, &status);
        if (out) {
            if (out->length == in->length &&
                memcmp(out->data, in->data, (size_t)in->length) == 0) {
                ok = 1;
            } else {
                fprintf(stderr,
                        "[T%03d i%ld] ROUNDTRIP MISMATCH: enc %d B, dec %d B\n",
                        thread_id, iter, in->length, out->length);
            }
            free(out);
        }
    }
    if (!ok) (*failures)++;
    destroyEncode(enc);
    free(in);
    return ok;
}

static void *worker(void *p) {
    worker_arg *a = (worker_arg *)p;

    /* Warm up (untimed): primes the LDPC matrix cache + allocator arenas so the
     * timed window is steady-state, matching bench_codec.c's regime. */
    for (int i = 0; i < WARMUP_OPS; i++)
        roundtrip_once(a->thread_id, -(i + 1), &a->failures);
    a->failures = 0;  /* warm-up failures don't count toward the measurement */

    gate_wait(a->duration_ms);  /* all workers released together; window starts now */

    long iter = 0;
    while (now_ms() < g_deadline_ms) {
        if (a->serialize) {
            pthread_mutex_lock(&g_serial_lock);
            a->ops += roundtrip_once(a->thread_id, iter, &a->failures);
            pthread_mutex_unlock(&g_serial_lock);
        } else {
            a->ops += roundtrip_once(a->thread_id, iter, &a->failures);
        }
        iter++;
    }
    return NULL;
}

/* Run one (threads x mode) measurement for duration_ms. Fills *ops_out (total
 * verified round-trips), *secs_out (actual wall seconds), *fail_out (mismatches).
 * Returns ops/sec. */
static double measure(int threads, bool serialize, double duration_ms,
                      long *ops_out, double *secs_out, long *fail_out) {
    pthread_t   tids[MAX_THREADS];
    worker_arg  args[MAX_THREADS];

    gate_reset(threads);  /* the timed window begins when the last worker arrives */
    for (int i = 0; i < threads; i++) {
        args[i].thread_id  = i;
        args[i].duration_ms = duration_ms;
        args[i].serialize  = serialize;
        args[i].ops        = 0;
        args[i].failures   = 0;
    }

    for (int i = 0; i < threads; i++) {
        if (pthread_create(&tids[i], NULL, worker, &args[i]) != 0) {
            fprintf(stderr, "pthread_create failed for thread %d\n", i);
            exit(2);
        }
    }
    long total_ops = 0, total_fail = 0;
    for (int i = 0; i < threads; i++) {
        pthread_join(tids[i], NULL);
        total_ops  += args[i].ops;
        total_fail += args[i].failures;
    }
    /* Authoritative wall time: from the gate opening (g_timed_t0, all workers
     * warmed) to the last join. Excludes pthread_create + warm-up. */
    double secs = (now_ms() - g_timed_t0) / 1000.0;
    *ops_out  = total_ops;
    *secs_out = secs;
    *fail_out = total_fail;
    return secs > 0 ? total_ops / secs : 0.0;
}

/* Build the dedup'd, nproc-capped thread-count ladder {1,2,4,8,16,nproc}. */
static int build_ladder(int nproc, int *out) {
    static const int base[] = {1, 2, 4, 8, 16};
    int n = 0;
    for (int i = 0; i < (int)(sizeof(base) / sizeof(base[0])); i++) {
        int t = base[i];
        if (t > nproc) continue;
        int dup = 0;
        for (int j = 0; j < n; j++) if (out[j] == t) { dup = 1; break; }
        if (!dup) out[n++] = t;
    }
    int dup = 0;
    for (int j = 0; j < n; j++) if (out[j] == nproc) { dup = 1; break; }
    if (!dup && nproc >= 1) out[n++] = nproc;
    return n;
}

int main(int argc, char **argv) {
    double duration_ms = argc > 1 ? atof(argv[1]) : DEFAULT_MS;
    if (duration_ms < 1) duration_ms = DEFAULT_MS;

    long online = sysconf(_SC_NPROCESSORS_ONLN);
    int  cores  = online > 0 ? (int)online : 1;
    int  max_threads = argc > 2 ? atoi(argv[2]) : cores;
    if (max_threads < 1) max_threads = 1;
    if (max_threads > MAX_THREADS) max_threads = MAX_THREADS;

    int ladder[16];
    int nlad = build_ladder(max_threads, ladder);

    fprintf(stderr,
            "platform=%s cores=%d duration=%.0fms payload=%dB colours=%d ecc=%d\n",
            PLATFORM, cores, duration_ms, BENCH_PAYLOAD, BENCH_COLORS, BENCH_ECC);
    fprintf(stderr, "%-8s %14s %14s %9s %11s %8s\n",
            "threads", "concurrent/s", "serialized/s", "speedup", "efficiency",
            "ops_ok");

    /* speedup is referenced to the single-thread CONCURRENT rate. */
    double concurrent_1 = 0.0;
    int    all_ok = 1;

    for (int k = 0; k < nlad; k++) {
        int T = ladder[k];

        long c_ops = 0, c_fail = 0, s_ops = 0, s_fail = 0;
        double c_secs = 0, s_secs = 0;
        double c_rate = measure(T, false, duration_ms, &c_ops, &c_secs, &c_fail);
        double s_rate = measure(T, true,  duration_ms, &s_ops, &s_secs, &s_fail);

        if (T == 1) concurrent_1 = c_rate;
        double speedup    = concurrent_1 > 0 ? c_rate / concurrent_1 : 0.0;
        double efficiency = T > 0 ? speedup / T : 0.0;
        bool   ops_ok     = (c_fail == 0 && s_fail == 0);
        if (!ops_ok) all_ok = 0;

        printf("{\"platform\":\"%s\",\"cores\":%d,\"threads\":%d,"
               "\"payload_bytes\":%d,\"colours\":%d,\"ecc\":%d,"
               "\"concurrent_ops_per_s\":%.2f,\"serialized_ops_per_s\":%.2f,"
               "\"speedup\":%.4f,\"efficiency\":%.4f,"
               "\"concurrent_ops\":%ld,\"serialized_ops\":%ld,"
               "\"concurrent_secs\":%.4f,\"serialized_secs\":%.4f,"
               "\"ops_ok\":%s}\n",
               PLATFORM, cores, T, BENCH_PAYLOAD, BENCH_COLORS, BENCH_ECC,
               c_rate, s_rate, speedup, efficiency,
               c_ops, s_ops, c_secs, s_secs,
               ops_ok ? "true" : "false");
        fflush(stdout);

        fprintf(stderr, "%-8d %14.1f %14.1f %8.2fx %10.1f%% %8s\n",
                T, c_rate, s_rate, speedup, efficiency * 100.0,
                ops_ok ? "OK" : "FAIL");
    }

    fprintf(stderr, "RESULT: %s\n", all_ok ? "PASS (byte-identical under load at every T)"
                                           : "FAIL (round-trip mismatch under load)");
    return all_ok ? 0 : 1;
}
