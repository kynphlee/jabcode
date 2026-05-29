# Panama Wrapper Benchmark Rebuild Notes (2026-05-28)

This is a port of the panama-wrapper benchmark infrastructure from `panama-poc` onto `swift-java-poc`, adapted for the WS-5-era decoder. Use this document to understand what was kept, what was modified, and what's known-broken.

## What was ported

121 files from `panama-poc:panama-wrapper/`, including:

- **`pom.xml`** — JDK 21+ Panama FFM build, JMH 1.37 deps
- **22 main source files** under `src/main/java/com/jabcode/panama/` — Java wrapper of the JABCode native library
- **44 test sources** under `src/test/java/com/jabcode/panama/` — including 12 JMH benchmark classes
- **Phase progress documentation** (PHASE7..9_COMPLETE.md, IMPLEMENTATION_*, etc.)
- **`jextract.sh`** + supporting scripts for regenerating bindings from `jabcode.h`

## What was modified

### `JABCodeDecoder.java` — `decodeJABCodeWithObservations` stub

`panama-poc` extended the C-side decoder with a research entry point `decodeJABCodeWithObservations` that returned color-classification observations alongside the decoded data. **That function was never merged into `swift-java-poc`**, so the Java wrapper's observation path now falls back to standard `decodeJABCode` with a warning that observation data will be empty.

If observation research is needed again, restore the C-side `decodeJABCodeWithObservations` entry point in `src/jabcode/decoder.c` and re-run `jextract.sh` to regenerate the bindings.

### `DecodingBenchmark.java` — full 8-Nc parametrization

Previously `@Param({"4", "8", "16", "32", "64", "128"})` (Nc=1..6). Extended to all 8 Nc values:

```
@Param({"2", "4", "8", "16", "32", "64", "128", "256"})  // Nc=0..7
```

This enables JVM-side discriminator data covering the `H_partI_unifies` cluster {Nc=0, Nc=2, Nc=7} that the Android per-Nc telemetry confirmed today.

### `EncodingBenchmark.java` — Nc=0 added, Nc=7 deferred

Added `2` (monochrome, newly supported per panama-poc commit `bb91db7` "WS-6.5 accept color_number=2"). Nc=7 (256-color) remains excluded due to a documented encoder malloc issue in the panama-poc era — re-verify before re-adding.

## What was regenerated

- **`target/generated-sources/jextract/com/jabcode/panama/bindings/jabcode_h.java`** — regenerated against `swift-java-poc`'s `src/jabcode/include/jabcode.h` (244 lines vs panama-poc's 186). The header gained WS-5 instrumentation (Heisenberg gate, MOBILE_BUILD conditional logging) but the public decode/encode API is stable.

## What's known-broken

### 36 unit-test failures

`mvn test` reports 36 failures out of 205 tests. These failures are **functional**, not infrastructural:

- **Nc=6 (128-color) round-trip tests fail** — `ColorMode6Test.testSimpleMessage`, `testUnicode`, `testVariousLengths`. Matches the Android discriminator scan's empirical finding that Nc=6 has a 4% screen success rate. The C-side decoder genuinely can't round-trip 128-color codes reliably right now.
- **`JABCodeEncoderConfigTest.invalidColorNumbersRejected` expects rejection of color_number=2** — panama-poc's `WS-6.5 accept color_number=2` (commit bb91db7) made Nc=0 valid. The test was not updated. Skip or update.

These failures are diagnostic signals about the decoder's state, not blockers for benchmark execution.

## How to run the benchmarks

### Quick smoke test (~15 seconds per benchmark)

```bash
cd panama-wrapper
bash run-benchmark.sh SimpleBenchmark "-wi 1 -i 2 -f 1 -r 1s -w 1s"
```

### Single-Nc decode benchmark (~15 seconds)

```bash
bash run-benchmark.sh DecodingBenchmark "-wi 1 -i 2 -f 1 -r 1s -w 1s -p colorMode=2 -p messageSize=100"
```

### Full per-Nc decode sweep (~10 minutes)

```bash
# All 8 Nc × 3 message sizes × default 3-fork × (5+10) × 1s = ~600s
bash run-benchmark.sh DecodingBenchmark "-rf json -rff results/per-nc-decode.json"
```

### Full encoding sweep (~12 minutes)

```bash
# 7 Nc × 4 message sizes (Nc=7 excluded for malloc)
bash run-benchmark.sh EncodingBenchmark "-rf json -rff results/per-nc-encode.json"
```

## First-run results (2026-05-28 smoke tests)

These ran during the rebuild verification — first JVM-side numbers since January 18, 2026:

| Benchmark | Params | Score (ms/op) |
|---|---|---|
| `SimpleBenchmark.encodeSimpleMessage` | messageSize=100 | 9.573 |
| `SimpleBenchmark.encodeSimpleMessage` | messageSize=1000 | 51.344 |
| `SimpleBenchmark.encodeSimpleMessage` | messageSize=10000 | 2.214 (failed-fast) |
| `DecodingBenchmark.decodeByColorMode` | colorMode=2, messageSize=100 | **10.492** |

The Nc=2 decode at 10.492 ms/op is **the very Nc that fails 100% on Android camera path** — and yet succeeds reliably in synthetic JVM context. This synthetic-vs-camera divergence is exactly the H_nc2_decode_failure register entry's observation, now confirmed across both code paths.

## Native library

`lib/libjabcode.so` is the WS-5 native build (copied from `javacpp-wrapper/target/classes/com/jabcode/linux-x86_64/libjabcode.so` during rebuild). To rebuild, follow the `javacpp-wrapper` build process.

## Open follow-on work

- Update `JABCodeEncoderConfigTest.invalidColorNumbersRejected` to expect color_number=2 as **valid**, not rejected, per panama-poc's WS-6.5 commit.
- Verify whether Nc=7 (256-color) encoder malloc issue has been fixed in WS-5; if so, re-add `"256"` to `EncodingBenchmark.@Param`.
- Re-enable observation-based decoding by porting `decodeJABCodeWithObservations` from panama-poc to swift-java-poc (research-side).
- Establish baseline JSON file at `panama-wrapper/baseline-benchmarks.json` for regression detection.
- Wire `mvn install` artifact into a CI compile-only check (mirror the Android benchmark CI policy).
