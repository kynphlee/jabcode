# Native codec microbenchmark (`bench_codec`)

**Suite A** of the mobile performance benchmarks — the native-C counterpart to the
server-side JMH suite (`jab-auth-spring-integration` in COA). It times the pure
**encode + decode codec** across all eight colour modes (Nc = 0..7) on a fixed,
in-memory bitmap — **no PNG I/O, no camera, no JVM** — so it isolates the codec
from the camera/JNI pipeline that dominates on-device end-to-end numbers, and it
is the cleanest measure of the LDPC matrix cache.

## What it measures

| direction | timed call | notes |
|---|---|---|
| encode | `generateJABCode()` | `createEncode`/data-alloc excluded (setup) |
| decode | `decodeJABCode()` on the pristine in-memory bitmap | no PNG read |

Warmup populates the LDPC matrix cache, so the measured window is **cached
steady-state** — the regime a server or app handling many same-mode operations
runs in. Per cell: median / mean / p95 / min / max / stddev, plus
`ops_per_s = 1000/median`.

## Build & run — host (x86_64)

```
cd src/jabcode
make bench                            # builds build/bench_codec (static-links libjabcode)
build/bench_codec [warmup] [iters]    # defaults: 10 50 ; JSON -> stdout, table -> stderr
```

## Build & run — device (arm64, Android NDK)

The codec compiles **without `image.c`** (PNG/TIFF I/O), exactly like the mobile
`.so`, so no libpng/zlib is needed — only `-llog` (Android logging) and `-lm`:

```
NDK=$ANDROID_SDK/ndk/<version>
CC=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang
cd src/jabcode
SRCS=$(ls *.c | grep -v '^image.c$')
$CC -O2 -std=c11 -D_POSIX_C_SOURCE=199309L -I. -I./include \
    $SRCS test/bench_codec.c -llog -lm -o /tmp/bench_arm64
adb push /tmp/bench_arm64 /data/local/tmp/ && adb shell chmod 755 /data/local/tmp/bench_arm64
adb shell /data/local/tmp/bench_arm64 10 50
```

The binary records its platform (`arm64` / `x86_64`) in the JSON, so host and
device runs are distinguishable in the merged dataset.

## Host baseline (x86_64, cache build, warmup 10 / iters 50)

| Nc (colours) | encode median (ms) | decode median (ms) |
|---|---|---|
| 2   | 0.16 | 1.86 |
| 4   | 0.15 | 2.21 |
| 8   | 0.50 | 2.32 |
| 16  | 0.45 | 5.51 |
| 32  | 0.51 | 6.06 |
| 64  | 0.39 | 5.09 |
| 128 | 0.49 | 6.00 |
| 256 | 0.60 | 7.01 |

The LDPC cache shows here: decode rises ~3.7× across the *whole* 2→256 range
(8→16 is ~2.4×) instead of the ~10× cliff seen without the cache; encode is flat.
These track the server JMH (decode 8c≈2.7 ms, 16c≈6 ms), validating the harness.

## Criteria (parity with server rigor)

- sub-ms timing (`clock_gettime(CLOCK_MONOTONIC)`), warmup + N measurement iters, p95 + stddev
- encode **and** decode × Nc 0..7
- pristine isolated input (the codec, not the camera)
- JSON output → can feed the shared `docs/benchmarks` chart pipeline
- **variant comparison:** run against a cache vs no-cache `.so` (rebuild the lib from
  the relevant commit) to quantify the cache on a given platform

## Relation to the other mobile suites

- **Suite B** — `framework/diagnostic-engine` Android Microbenchmark: the same codec
  via JNI on real hardware (recommend upgrading its custom harness to
  `androidx.benchmark` for clock/thermal rigor + nanosecond resolution; add encode).
- **Suite C** — `benchmark-macro` Macrobenchmark: end-to-end camera→decode UX
  (no codec isolation; currently `@Ignore`d on a camera-pipeline stall).

`bench_codec` is the cleanest, cross-platform-comparable codec number and the only
one that runs in CI without a device.
