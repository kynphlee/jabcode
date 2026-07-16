# 14. Concurrency

<!-- objective: A maintainer can explain the fork's reentrancy posture — _Thread_local PRNG state, per-operation reseeding, TSan-guarded round trips — and avoid the downstream JNA pitfall (-Djna.protected=true livelocks concurrent benchmarks; use a JMH timeout instead). -->

**Scope.** Upstream jabcode 2.0.0 was not reentrant: per-operation codec state lived in plain process globals, so the only safe multi-threaded use was one global lock around every call. The fork (PR #110 lineage) moved the per-operation state to `_Thread_local`, guarded the property with a ThreadSanitizer test (`test-concurrent`) and a throughput benchmark (`bench-concurrent`), and left a small, documented set of process-global *configuration* toggles shared. This chapter states exactly which state is which, how the guards work, and the one downstream JVM pitfall with its mitigation.

## 14.1 What upstream shared, what the fork isolated

The PRNG is the paradigm case. Its entire state is one variable:

```c
static _Thread_local uint64_t lcg64_seed = 42;
```

<!-- anchor: src/jabcode/pseudo_random.c:10 --> The design comment above it carries the full argument, quoted because every clause is load-bearing:

> "Thread-safety (codec reentrancy): the LCG state is per-operation, not cross-call — every consumer re-seeds it to a fixed constant via setSeed() immediately before use (interleave.c INTERLEAVE_SEED; ldpc.c LPDC_MESSAGE_SEED / LPDC_METADATA_SEED). Making it \_Thread\_local gives each worker thread its own deterministic sequence, so concurrent encode/decode no longer race on this global AND single-threaded output stays byte-identical. Mirrors the established #91 LDPC-cache \_Thread\_local pattern (ldpc.c)."

<!-- anchor: src/jabcode/pseudo_random.c:3-9 -->

Two properties follow. **Wire compatibility:** because each operation reseeds (`setSeed`) to `INTERLEAVE_SEED 226759` / `LPDC_MESSAGE_SEED 785465` / `LPDC_METADATA_SEED 38545` before drawing, the emitted permutations are functions of the fixed seeds alone — thread-locality changes *who owns the variable*, never *the sequence* — so single-threaded output is byte-identical to upstream. <!-- anchor: src/jabcode/pseudo_random.c:27-30; src/jabcode/interleave.c:20; src/jabcode/ldpc.h:17-18 --> **Reentrancy:** two threads mid-encode can no longer interleave each other's draws.

Other fork-isolated (`_Thread_local`) state, same pattern:

| Variable | Definition | Role | Note |
|---|---|---|---|
| `lcg64_seed` | pseudo_random.c:10 | PRNG state (interleave + LDPC matrix gen) | reseeded per operation |
| `g_mode0_decode` | detector.c:104 | Mode 0 chroma-probe verdict, set per decode in `detectMaster`, read by decoder | "a \_Thread\_local object must be declared \_Thread\_local in EVERY translation unit — the extern in decoder.c is kept in sync" <!-- anchor: src/jabcode/detector.c:99-104; src/jabcode/decoder.c:142 --> |
| `g_symbology_identifier` | decoder.c:203 | per-decode Annex H identifier buffer, reset at `decodeData` entry, published on success | `_Thread_local jab_char g_symbology_identifier[4] = "";` <!-- anchor: src/jabcode/decoder.c:198-207, 2553, 2987 --> |
| LDPC matrix cache | ldpc.c (the "#91 pattern" the PRNG comment cites) | per-thread cached generator matrices | <!-- anchor: src/jabcode/pseudo_random.c:9 --> |

The TSan test's header names the pre-fix race set explicitly: "lcg64_seed / g_mode0_decode / g_symbology_identifier / g_calibration". <!-- anchor: src/jabcode/test/test_concurrent_roundtrip.c:16-18 -->

The maintenance rule the `g_mode0_decode` comment encodes generalises: when you add per-operation state, it must be `_Thread_local` **at every declaration site** (definition *and* externs), or the TUs silently disagree about its storage.

## 14.2 The guard: `test-concurrent` under ThreadSanitizer

The Makefile target compiles the codec **sources** — not the pre-built archive — together with the test, under `-fsanitize=thread`:

```
test-concurrent: $(CORE_DIR)
	$(CC) -O1 -g -std=c11 -fsanitize=thread -D_POSIX_C_SOURCE=199309L -I. -I./include \
	    test/test_concurrent_roundtrip.c $(SOURCES) \
	    -lpng16 -ltiff -lz -lm -lpthread -o $(CORE_DIR)/test_concurrent_roundtrip
	$(CORE_DIR)/test_concurrent_roundtrip
```

<!-- anchor: src/jabcode/Makefile:182-186 --> Why sources: "TSan instruments the library internals -- the shared codec globals live there, so linking the non-instrumented $(STATIC_LIB) would hide exactly the races we guard against. -O1 keeps TSan's stacks readable." <!-- anchor: src/jabcode/Makefile:174-178 -->

Test shape: N ≥ 8 pthreads x M ≥ 50 encode→decode round-trips each, cycling `color_number ∈ {4, 8, 16, 32, 64, 128, 256}` (Nc 1..7), every payload unique to `(thread, iteration)` with varied length "so a cross-thread leak is detectable: if thread A ever decodes thread B's bytes, the memcmp fails" and "length-dependent shared state" is shaken loose too. Defaults `8 50`; run directly as `build/test_concurrent_roundtrip [threads] [iters]`. <!-- anchor: src/jabcode/test/test_concurrent_roundtrip.c:4-10, 21-22, 34-40, 49-54 --> Deterministic-vs-probabilistic framing from the header: build with TSan "to make the race deterministic rather than probabilistic"; before the fix TSan reports the races and/or assertions fail across repeated runs, after it is "TSan clean + all assertions pass". <!-- anchor: src/jabcode/test/test_concurrent_roundtrip.c:12-19 -->

CI nuance: `codec-regression.yml` re-runs the binary under `setarch -R` because modern kernels' high-entropy mmap layout "aborts TSan with 'unexpected memory mapping' before any test runs. Disabling randomization fixes only that startup crash -- a genuine data race still fails the run (and the job)." <!-- anchor: .github/workflows/codec-regression.yml:112-128 --> Reproduce locally with `setarch -R ./build/test_concurrent_roundtrip` if TSan aborts at startup.

## 14.3 The remaining shared state: process-global toggles

Four globals are deliberately **not** thread-local — they are process-wide configuration, not per-operation state:

| Global | Declared | Setter/getter | Semantics |
|---|---|---|---|
| `unsigned char g_diag_verbose` | jabcode.h:90 | `jabSetDiagVerbose` / `jabIsDiagVerbose` (jabcode.h:255-256) | gates `JAB_DIAG_INFO` high-volume markers (the WS-5 "Heisenberg gate"); default OFF <!-- anchor: src/jabcode/include/jabcode.h:72-91 --> |
| `unsigned char g_permissive_color_classification` | jabcode.h:98 | `jabSetPermissiveColorClassification` / `jabIsPermissiveColorClassification` (jabcode.h:261-262) | Path β magenta→yellow PartI substitution; default OFF <!-- anchor: src/jabcode/include/jabcode.h:93-98 --> |
| `int g_preferred_color_count` | jabcode.h:105 | `jabSetPreferredColorCount` / `jabGetPreferredColorCount` (jabcode.h:270-271) | pins the decoder's Nc fallback ladder; 0 = auto <!-- anchor: src/jabcode/include/jabcode.h:100-105; src/jabcode/decoder.c:181-193 --> |
| `unsigned char g_profile_stages` + `jab_decode_profile g_decode_profile` | decode_profile.h:93-94 | `jabSetProfileStages` / `jabGetDecodeProfile` / `jabResetDecodeProfile` (jabcode.h:282-285) | "Process-global profiling state" — one shared accumulator <!-- anchor: src/jabcode/decode_profile.h:90-94 --> |

The thread-safety contract for all four is the same and must be stated to binding authors: they are plain non-atomic globals with no synchronisation. Set them **before** spawning codec threads and treat them as immutable while operations are in flight. Two specific consequences:

- **Profiling under concurrency is aggregate-only.** Concurrent decodes with `g_profile_stages` on fold their intervals into the single `g_decode_profile` accumulator; the per-decode averages of [12-benchmark-estate.md](12-benchmark-estate.md) §12.4 are meaningful only for serialized profiling runs. The macros' only concession to racing is the torn-toggle re-check ("JAB_PROF_END re-checks so a torn toggle mid-decode cannot fold a bogus interval"), which protects the accumulator from a *toggle* mid-decode, not from concurrent accumulation. <!-- anchor: src/jabcode/decode_profile.h:104-118 -->
- **Mode/toggle flips are process-wide.** Pinning `g_preferred_color_count` for one consumer pins every thread's decoder ladder (decoder.c:2128-2150; see [16-extended-colour-modes.md](16-extended-colour-modes.md) §16.4).

## 14.4 `bench-concurrent`: quantifying the property

The benchmark exists because the latency suite cannot see reentrancy: "#110 changed nothing about how fast a single round-trip runs -- it made the C codec REENTRANT … The value of #110 is THROUGHPUT SCALING, and that is what this benchmark measures." <!-- anchor: src/jabcode/test/bench_concurrent.c:4-9 --> Method (invocation and output schema in §12.2): at each thread count it runs the same 8-colour/256-byte/ECC-3 round-trip both **CONCURRENT** (no lock; throughput should climb toward T x single-thread, "capped by cores / memory bandwidth / malloc contention") and **SERIALIZED** (one global `pthread_mutex` around each round-trip; "throughput stays pinned at ~the single-thread rate"). Correctness is asserted every iteration — the same byte-identity invariant as the TSan test — and the run exits non-zero on any mismatch: "post-#110 this must be 100%". <!-- anchor: src/jabcode/test/bench_concurrent.c:14-31, 72-74, 319-321 -->

Reading results: `speedup` is normalised to the single-thread concurrent rate and `efficiency = speedup/T`; a regression that re-introduces shared per-operation state shows up twice — `ops_ok:false` (correctness) and the concurrent line collapsing onto the serialized line (performance). <!-- anchor: src/jabcode/test/bench_concurrent.c:283-299 -->

## 14.5 The downstream boundary: Panama and JNA

Two different JVM native boundaries consume this property downstream (framework detail in [17-downstream-bindings.md](17-downstream-bindings.md)):

- **Codec — Panama FFM.** `PanamaJabCodeService` reflectively loads `com.jabcode.panama.JABCodeEncoder`/`JABCodeDecoder` from the vendored wrapper jar; the native `libjabcode.so` "is loaded BY NAME via the Panama FFM binding (SymbolLookup.libraryLookup), so it must sit on java.library.path / LD_LIBRARY_PATH", and every consumer task passes `--enable-native-access=ALL-UNNAMED`. <!-- anchor: framework corpus §3.12 (PanamaJabCodeService.java:37-38; jab-auth-emulator/build.gradle:70-75; build.gradle:75-81) --> The by-name lookup is also why the fork's link line sets `-Wl,-soname,libjabcode.so` — "glibc only matches the already-loaded library if its DT_SONAME says so". <!-- anchor: src/jabcode/Makefile:35-41 --> The reentrancy contract of §14.1 is exactly what lets the framework's concurrency soak (`RabeCodecConcurrencySoakTest`) and JMH suites run the codec unlocked from many JVM threads; the pre-#110 thread-unsafe `.so` sitting undetected in the vendored `lib/` is the incident the `check-lib` guard commemorates ("how the pre-#110 thread-UNSAFE .so sat there undetected for weeks"). <!-- anchor: src/jabcode/Makefile:46-48; framework corpus §2.5 -->
- **ABE KEM — JNA.** The rabe CP-ABE library is bound via JNA 5.14.0 (`Native.load("rabe_kem", …)`); both native libraries share the consumer JVMs and library paths in the framework's compose and benchmark tasks. <!-- anchor: framework corpus §3.12 (RabeJna.java:94; jab-auth-abe/build.gradle:23) -->

## 14.6 The `jna.protected` livelock lesson

Operational fact (project memory-bank lesson, 2026): running multithreaded JNA benchmarks with `-Djna.protected=true` **livelocks** them. The recorded mitigation is *not* to reach for the flag as a safety net in benchmark JVMs, but to bound the run with a JMH timeout (`-to`) so a wedged fork fails fast and visibly instead of hanging the build.

Boundary of applicability, stated precisely: this bites the **JNA** boundary (the rabe KEM path and any JNA-bound benchmark harness). The codec path in the framework is Panama, which has no `jna.protected` equivalent; but because framework benchmark tasks put both native libraries in one JVM (§14.5), a JNA-side livelock stalls codec measurements sharing that run. For crash-safety in benchmarks, prefer fail-fast (timeout + non-zero exit) over in-process protection.
