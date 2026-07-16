# 12. Benchmark estate

<!-- objective: A performance engineer can run every benchmark target with correct arguments and output formats (JSON/JSONL/table), profile decode stages via the decode_profile hooks and plotting scripts, and interpret results against the cascade and concurrency regressions the benches guard. -->

**Scope.** Six `.PHONY` targets in `src/jabcode/Makefile` constitute the benchmark estate: `bench`, `bench-concurrent`, `bench-cascade`, `profile`, `sweep`, `transcode`. <!-- anchor: src/jabcode/Makefile:24 --> Every one statically links `build/libjabcode.a` for a self-contained binary ("adb-pushable" <!-- anchor: src/jabcode/Makefile:77 -->) and builds into `build/`. The Makefile targets **build only** — none of them runs the binary (unlike the `test-*` targets of [13-regression-suite.md](13-regression-suite.md), which build *and* run). Build-unit context: [01-repository-and-build.md](01-repository-and-build.md). Methodology provenance: `src/jabcode/test/README-bench.md` (§12.8).

Shared output convention, stated in each source header: machine-readable JSON (array or JSONL) on **stdout**, a human-readable table on **stderr** — so `build/<bench> > data.json` captures clean data while the table still displays. <!-- anchor: src/jabcode/test/bench_codec.c:20-22; test/bench_concurrent.c:36-39; test/bench_cascade.c:67-69 --> All timing uses `clock_gettime(CLOCK_MONOTONIC)` (`now_ms()` in each source), which is why the estate compiles with `-D_POSIX_C_SOURCE=199309L`. <!-- anchor: src/jabcode/test/bench_codec.c:26, 44-48; src/jabcode/Makefile:5-8 -->

## 12.1 `bench` — single-symbol latency microbenchmark (Suite A)

```
make -C src/jabcode bench
build/bench_codec [warmup] [iters]        # defaults: 10 50
```

<!-- anchor: src/jabcode/Makefile:79-81; test/bench_codec.c:24, 93-94 -->

Build line: `$(CC) -O2 -std=c11 -I. -I./include test/bench_codec.c $(STATIC_LIB) -lpng16 -ltiff -lz -lm -o $(CORE_DIR)/bench_codec`. <!-- anchor: src/jabcode/Makefile:80 --> Makefile comment, verbatim: "Suite A: native codec microbenchmark -- encode+decode timing across Nc 0..7." <!-- anchor: src/jabcode/Makefile:76 -->

What is timed, from the source header: "encode -> `generateJABCode()` only (createEncode/data alloc excluded -- setup); decode -> `decodeJABCode()` on the pristine in-memory bitmap (no PNG read)". <!-- anchor: src/jabcode/test/bench_codec.c:14-16 --> Fixed workload: payload `"BENCH-0001"`, ECC level 3, colour ladder `{2, 4, 8, 16, 32, 64, 128, 256}` (Nc 0..7). <!-- anchor: src/jabcode/test/bench_codec.c:98-100 --> Warmup iterations populate the LDPC matrix cache, so the measured window is "cached steady-state -- the regime a server/app handling many same-mode ops runs in". <!-- anchor: src/jabcode/test/bench_codec.c:17-18 -->

Output record (JSON array element, one per `(direction, colours)` cell):

```json
{"platform":"x86_64","direction":"encode","colours":8,
 "ops_per_s":…,"median_ms":…,"mean_ms":…,"p95_ms":…,
 "min_ms":…,"max_ms":…,"stddev_ms":…,"iters":50,"ok":50}
```

<!-- anchor: src/jabcode/test/bench_codec.c:85-89 --> `ops_per_s = 1000.0 / median` <!-- anchor: src/jabcode/test/bench_codec.c:84 -->; `platform` is the compile-time arch macro (`arm64` / `x86_64` / `armv7` / `unknown`), so host and device runs are distinguishable in a merged dataset. <!-- anchor: src/jabcode/test/bench_codec.c:34-42; test/README-bench.md:46-47 --> The stderr table prints `Nc, encode med/p95, decode med/p95, dec_ok`. <!-- anchor: src/jabcode/test/bench_codec.c:105-106, 164-165 --> `ok` on decode rows counts non-NULL `decodeJABCode` returns out of `iters` — the harness reports but does not gate on it (exit status is always 0). <!-- anchor: src/jabcode/test/bench_codec.c:150-160, 169 -->

What it guards: it is the reference latency series for the LDPC matrix cache (decode rises approximately 3.7x across the whole 2-to-256-colour range instead of the roughly 10x cliff without the cache — README host baseline, §12.8), and the cross-platform "cleanest codec number" that "runs in CI without a device". <!-- anchor: src/jabcode/test/README-bench.md:62-63, 83-84 -->

## 12.2 `bench-concurrent` — throughput scaling (reentrancy value)

```
make -C src/jabcode bench-concurrent
build/bench_concurrent [duration_ms=2000] [max_threads=nproc]
```

<!-- anchor: src/jabcode/Makefile:90-92; test/bench_concurrent.c:41, 263-271 -->

Build line adds `-lpthread` and repeats `-D_POSIX_C_SOURCE=199309L`. <!-- anchor: src/jabcode/Makefile:91 --> Fixed config: `BENCH_COLORS 8`, `BENCH_PAYLOAD 256` bytes, `BENCH_ECC 3`. <!-- anchor: src/jabcode/test/bench_concurrent.c:63-66 -->

Methodology (the analysis is in [14-concurrency.md](14-concurrency.md), §14.4): at each thread count `T` from the dedup'd ladder `{1, 2, 4, 8, 16, nproc}` capped at `max_threads` <!-- anchor: src/jabcode/test/bench_concurrent.c:246-261 -->, the same encode→decode round-trip is run two ways — CONCURRENT (no lock) and SERIALIZED (every round-trip behind one global `pthread_mutex`, "the pre-#110 'lock everything' reality"). "The GAP between the two lines is exactly the throughput PR #110 unlocked." <!-- anchor: src/jabcode/test/bench_concurrent.c:10-25, 72-74 --> A mutex+condvar start gate excludes `pthread_create` latency and warm-up (`WARMUP_OPS 3`) from the timed window; the last-arriving thread stamps `t0` and the shared deadline. <!-- anchor: src/jabcode/test/bench_concurrent.c:69, 76-117 -->

Output: **JSONL** — one object per thread count:

```json
{"platform":…,"cores":…,"threads":T,"payload_bytes":256,"colours":8,"ecc":3,
 "concurrent_ops_per_s":…,"serialized_ops_per_s":…,"speedup":…,"efficiency":…,
 "concurrent_ops":…,"serialized_ops":…,"concurrent_secs":…,"serialized_secs":…,"ops_ok":true}
```

<!-- anchor: src/jabcode/test/bench_concurrent.c:301-311 --> `speedup` is referenced to the single-thread CONCURRENT rate; `efficiency = speedup / T`. <!-- anchor: src/jabcode/test/bench_concurrent.c:283-297 --> Destination file per the header: `benchmarks/data/concurrent_throughput.jsonl`. <!-- anchor: src/jabcode/test/bench_concurrent.c:38 -->

Unlike `bench`, this harness **gates**: every round-trip asserts the decoded bytes are byte-identical to what *that* thread encoded (the `test_concurrent_roundtrip.c` reentrancy invariant); any mismatch sets `ops_ok:false` and the process exits 1 with `RESULT: FAIL (round-trip mismatch under load)`. <!-- anchor: src/jabcode/test/bench_concurrent.c:27-31, 298-299, 319-321 -->

## 12.3 `bench-cascade` — multi-symbol capacity/latency/density + success matrix

```
make -C src/jabcode bench-cascade
build/bench_cascade [curves|matrix|both] [warmup=5] [iters=20]
build/bench_cascade curves > benchmarks/data/cascade.jsonl
build/bench_cascade matrix > benchmarks/data/cascade_matrix.jsonl
```

<!-- anchor: src/jabcode/Makefile:100-105; test/bench_cascade.c:71-75 -->

This is "the axis PR #113 made sound, and the one every single-symbol harness above cannot see". <!-- anchor: src/jabcode/Makefile:94-95; test/bench_cascade.c:5-8 --> Two datasets, selected by `argv[1]` (default runs both):

- **CURVES** — `symbol_number N ∈ {1, 2, 4, 8, 16, 32, 61}` x `Nc ∈ {4, 8, 64, 256}`, fixed version `V = 8` (a SAFE version, deliberately not ≡ 0 mod 5) and ECC 3. Per cell: `encode_ms`/`decode_ms` (median+p95, `bench_codec`-style), `capacity_bytes`, `total_modules`, `density = capacity/modules`, `ok`. <!-- anchor: src/jabcode/test/bench_cascade.c:44-50, 106-109 -->
- **MATRIX** — `Nc ∈ {4, 8, 16, 32, 64, 128, 256}` x `V ∈ {8, 10, 12, 15}` x `N ∈ {2, 3}`, fixed 16-byte payload; emits `{nc, version, symbol_number, ok}`. It "guards the high-colour fix AND maps the v==0-mod-5 edge" — v10 and v15 are included on purpose "so the edge is visible, not hidden". <!-- anchor: src/jabcode/test/bench_cascade.c:52-55, 36-39, 111-116 -->

The known edge, stated verbatim so nobody "fixes" the benchmark: "at high colour, cascade fails at slave versions == 0 (mod 5) that are >=10 (v10, v15, v20...) -- a separate pre-existing slave capacity/alignment-geometry resonance, documented in PR #113". <!-- anchor: src/jabcode/test/bench_cascade.c:33-35; src/jabcode/decoder.h:32-34 -->

Capacity method: `capacity_bytes` is **empirical** — a binary search for the largest payload that round-trips losslessly at `(Nc, V, N)`, seeded from above by `getSymbolCapacity()` (gross bit capacity, summed over symbols, divided by 8 — "a safe, tight upper bracket for the search, never the reported value"). `getSymbolCapacity` has no prototype in `jabcode.h`; the bench declares the extern locally, mirroring `encoder.c:651`. <!-- anchor: src/jabcode/test/bench_cascade.c:57-65, 87-93 -->

Cascade-construction crux (repeated by every downstream consumer, including `test_cascade_highversion.c`): for `N > 1`, `createEncode` zero-initialises the symbol arrays, so the caller must set `symbol_positions[i] = i` (sequential dock indices are edge-adjacent in `jab_symbol_pos`), an explicit `symbol_versions[i]` for **every** symbol (auto-sizing happens only when `symbol_number == 1`), and per-symbol `symbol_ecc_levels[i]`. <!-- anchor: src/jabcode/test/bench_cascade.c:19-31 -->

Implementation note that keeps the JSONL clean: the codec reports errors via `printf` to **stdout** (`JAB_REPORT_ERROR`, `reportError`), and the capacity search deliberately provokes "Message does not fit" failures — so the bench mutes fd 1 to `/dev/null` around every codec call and unmutes only to emit JSON. <!-- anchor: src/jabcode/test/bench_cascade.c:124-140; src/jabcode/include/jabcode.h:66 -->

## 12.4 `profile` — per-stage decode attribution

```
make -C src/jabcode profile
build/bench_profile [warmup] [iters]      # defaults: 5 30
```

<!-- anchor: src/jabcode/Makefile:117-119; test/bench_profile.c:38, 76-77 -->

Encodes a fixed 256-byte printable payload, then decodes it repeatedly with the opt-in profiler ON (`jabSetProfileStages`), across `Nc ∈ {4, 8, 16, 32, 64, 128, 256}` at ECC 3 — "Nc=2 is excluded by design". <!-- anchor: src/jabcode/test/bench_profile.c:3-6, 81-90 --> Reported value: accumulated microseconds per stage divided by decode count → mean µs per decode per stage; the accumulator is reset per mode via `jabResetDecodeProfile`, and warmup decodes populate the LDPC cache before the reset. "The point is attribution, not wall-clock: which stage dominates at LOW Nc vs HIGH Nc." <!-- anchor: src/jabcode/test/bench_profile.c:24-29 -->

Stages (indexed by `jab_decode_stage`): `DETECT`, `PALETTE`, `COLOR_CLASSIFY`, `DEINTERLEAVE`, `LDPC`, `DATA_DECODE`; DETECT additionally breaks into `DETECT_BINARIZE`, `DETECT_FINDER`, `DETECT_TRANSFORM`, `DETECT_SAMPLE`, which sum to ~DETECT by construction — `JAB_PROF_DET_END` folds each interval into both the sub-stage and the `JAB_STAGE_DETECT` roll-up from a single clock read. <!-- anchor: src/jabcode/decode_profile.h:55-77, 120-133; test/bench_profile.c:59-66 --> When profiling is OFF the macros are no-ops with zero clock reads, and `JAB_PROF_END` re-checks the flag so a torn toggle mid-decode cannot fold a bogus interval. <!-- anchor: src/jabcode/decode_profile.h:104-118 -->

Output: JSON array of per-`(Nc, stage)` records — `{"platform":…, "colours":4, "stage":"DETECT", "us_per_decode":…, "decodes":…, "ok":…}`; DETECT sub-stage records carry the additional tag `"detect_substage": true`. Two stderr tables (stages, sub-stages). <!-- anchor: src/jabcode/scripts/plot_stage_profile.py:4-8; scripts/plot_detect_substage.py:4-10; test/bench_profile.c:34-37 -->

Plotting:

```
python scripts/plot_stage_profile.py INPUT.json [-o OUTPUT.png]     # stacked pipeline-stage chart
python scripts/plot_detect_substage.py INPUT.json [-o OUTPUT.png]   # stacked DETECT-sub-stage chart
```

<!-- anchor: src/jabcode/scripts/plot_stage_profile.py:15-16; scripts/plot_detect_substage.py:18-19; src/jabcode/Makefile:114-115 --> Both scripts run headless (`matplotlib.use("Agg")`) and both docstrings note a matplotlib venv at `/tmp/bench-venv` (an environment-local convenience, not a repository guarantee). <!-- anchor: src/jabcode/scripts/plot_stage_profile.py:18-19, 26; scripts/plot_detect_substage.py:21-22 -->

## 12.5 `sweep` — capacity/latency/ECC full picture

```
make -C src/jabcode sweep
build/bench_sweep <text-fixture-path>
```

<!-- anchor: src/jabcode/Makefile:121-124; test/bench_sweep.c:11 -->

"Comprehensive capacity/latency/ECC sweep for the full-picture benchmark suite." <!-- anchor: src/jabcode/Makefile:121 --> Emits JSONL, one object per line, across four sweeps distinguished by a `sweep` field:

| `sweep=` | Axes → measurement |
|---|---|
| `capacity` | Nc x ECC → max single-symbol bytes (text fixture vs binary) |
| `latency` | Nc x payload-size (ecc=3) → encode/decode median ms |
| `ecc` | 8-colour x ECC 1..10 → latency + capacity |
| `wikipedia` | Nc → how much of the text fixture fits at ecc=3 |

<!-- anchor: src/jabcode/test/bench_sweep.c:4-9 --> Constants `ITER 15`, `WARM 3`; capacity again via monotonic binary search over `generateJABCode` fit. <!-- anchor: src/jabcode/test/bench_sweep.c:28-29, 58-64 --> The single positional argument is the text fixture file (loaded whole); no default path is compiled in. <!-- anchor: src/jabcode/test/bench_sweep.c:11, 38-43 -->

## 12.6 `transcode` — transcode-survival probe tool

```
make -C src/jabcode transcode
build/transcode_tool enc <colours> <ecc> <out.png>    # writes a JABCode PNG; exit 0/2
build/transcode_tool dec <in.png>                     # prints SURVIVE | FAIL; exit 0/1
```

<!-- anchor: src/jabcode/Makefile:126-129; test/transcode_tool.c:7-8, 30-46 -->

Fixed payload: `"JABCode transcode-survival probe -- 0123456789 ABCDEFGHIJ abcdefghij +/:.%"`. <!-- anchor: src/jabcode/test/transcode_tool.c:15-16 --> The tool is only the encode/decode endpoint of a pipeline whose middle — "a Python/PIL harness applies real digital transforms (JPEG, downscale, chroma)" — is the external driver `benchmarks/transcode_survival.py`. <!-- anchor: src/jabcode/test/transcode_tool.c:2-5; src/jabcode/Makefile:126 --> **That driver script is NOT FOUND in this repository** (corpus §2.2 NOT FOUND register); the tool builds and runs standalone, but the survival benchmark it serves cannot be reproduced from this tree alone.

## 12.7 What each bench guards — summary

| Target | Regression axis guarded | Gating? |
|---|---|---|
| `bench` | LDPC matrix-cache latency profile across Nc 0..7 | no (report-only) <!-- anchor: src/jabcode/test/bench_codec.c:169 --> |
| `bench-concurrent` | PR #110 reentrancy: throughput scaling and byte-identity under load | yes (exit 1 on mismatch) <!-- anchor: src/jabcode/test/bench_concurrent.c:319-321 --> |
| `bench-cascade` | PR #113 high-colour cascade (matrix `ok` cells) + the v ≡ 0 (mod 5) edge map | matrix doubles as regression guard <!-- anchor: src/jabcode/test/bench_cascade.c:36-39 --> |
| `profile` | Stage-attribution drift (e.g. a change that silently moves cost into COLOR_CLASSIFY at high Nc) | no (attribution tool) |
| `sweep` | Capacity/latency envelope across Nc x ECC | no |
| `transcode` | Payload survival under digital transforms (driver external, NOT FOUND) | per-image exit codes |

The hard, always-run correctness gates live in the test targets and CI ([13-regression-suite.md](13-regression-suite.md)); the benches quantify what those gates protect.

## 12.8 Methodology notes from `test/README-bench.md`

The README frames `bench_codec` as **Suite A** of the mobile performance benchmarks — "the native-C counterpart to the server-side JMH suite (`jab-auth-spring-integration` in COA)" — isolating the codec from "the camera/JNI pipeline that dominates on-device end-to-end numbers". <!-- anchor: src/jabcode/test/README-bench.md:1-8 --> Its criteria list ("parity with server rigor"): sub-ms `CLOCK_MONOTONIC` timing, warmup + N iters, p95 + stddev, encode **and** decode x Nc 0..7, pristine isolated input, JSON output feeding the shared `docs/benchmarks` chart pipeline, and variant comparison (cache vs no-cache `.so` rebuilt from the relevant commit). <!-- anchor: src/jabcode/test/README-bench.md:66-74 -->

Device builds: the codec compiles **without** `image.c` (no libpng/zlib needed), exactly like the mobile `.so` — cross-compile with the NDK clang, link only `-llog -lm`, `adb push`, run with the same `[warmup] [iters]` arguments. <!-- anchor: src/jabcode/test/README-bench.md:30-44 -->

Host baseline (x86_64, cache build, warmup 10 / iters 50), quoted for calibration — decode medians rise 1.86 ms (2-colour) → 7.01 ms (256-colour), encode stays 0.15-0.60 ms; "decode rises ~3.7x across the *whole* 2→256 range (8→16 is ~2.4x) instead of the ~10x cliff seen without the cache; encode is flat. These track the server JMH (decode 8c≈2.7 ms, 16c≈6 ms), validating the harness." <!-- anchor: src/jabcode/test/README-bench.md:49-64 -->

Companion suites (context, not in this tree): Suite B — Android Microbenchmark via JNI on hardware; Suite C — Macrobenchmark camera→decode UX, "currently `@Ignore`d on a camera-pipeline stall". <!-- anchor: src/jabcode/test/README-bench.md:75-84 -->

Concurrency analysis of `bench-concurrent` continues in [14-concurrency.md](14-concurrency.md); downstream JMH counterparts and their pitfalls in §14.6 and [17-downstream-bindings.md](17-downstream-bindings.md).
