# Panama-wrapper benchmark baselines

Versioned regression-detection baselines for the JABCode panama-wrapper JMH benchmark suite.

## Files

| File | What it captures | Date | Branch / commit |
|---|---|---|---|
| `2026-05-29-decode-baseline.json` | `DecodingBenchmark.decodeByColorMode` results, per-Nc × messageSize | 2026-05-29 | panama-poc @ `d70a684` (post-PR #22) |
| `2026-05-29-encode-baseline.json` | `EncodingBenchmark.encodeByColorMode` results, per-Nc × messageSize | 2026-05-29 | panama-poc @ `d70a684` |

## What the 2026-05-29 baseline contains

**Encode results — 17 succeeding combinations (Nc=0..6, 2-3 message sizes each):**

| Nc | Colors | Best score (ms/op) |
|----|--------|---------------------|
| 0 | 2 | 8.786 (100B) |
| 1 | 4 | 9.514 (100B) |
| 2 | 8 | 9.097 (100B) |
| 3 | 16 | **7.551 (100B) ← encoder sweet spot** |
| 4 | 32 | **7.397 (100B) ← encoder sweet spot** |
| 5 | 64 | 14.959 (100B) |
| 6 | 128 | 26.496 (100B) |
| 7 | 256 | (excluded — encoder malloc) |

**Decode results — 6 succeeding combinations (Nc=0..2 × {100B, 1000B}):**

| Nc | Colors | 100B | 1000B |
|----|--------|------|-------|
| 0 | 2 | 10.526 ms/op | 62.381 ms/op |
| 1 | 4 | 11.229 ms/op | 78.352 ms/op |
| 2 | 8 | 10.216 ms/op | 61.619 ms/op |
| 3..7 | 16..256 | **All failed with LDPC errors** | — |

## The Nc≥3 decode failures are diagnostically meaningful

The 2026-05-29 baseline run produced **102 LDPC errors** ("LDPC decoding for data in symbol 0 failed" / "Too many errors in message. LDPC decoding failed.") across the higher-Nc decode parameter combinations. The pattern:

- The **encoder** produces bytes for Nc=3..6 (EncodingBenchmark passes).
- When the encoder writes those bytes to a **PNG file** and the decoder reads them back, LDPC error correction cannot recover the data.
- This is a **synthetic encode→PNG→decode roundtrip failure** for every Nc ≥ 3.

This is a *different bug* from the camera-pipeline H_nc2_decode_failure track. See `docs/cassandra-register/H_png_roundtrip_high_nc.md` (TODO: file the register entry).

The boundary in JVM panama (Nc=0/1/2 work, Nc=3+ fail) is *different* from the camera-pipeline boundary on Android (Nc=0/2/7 fail, Nc=1/3/4/5/6 work to varying degrees). The two boundaries together produce three classes of Nc values:

| Class | Members | JVM synthetic | Android camera |
|---|---|---|---|
| Universal works | Nc=1 | ✅ | ✅ (93% GA) |
| Camera-pipeline broken only | Nc=0, Nc=2 | ✅ | ❌ (0% scan success) |
| PNG-roundtrip broken only | Nc=3, Nc=4, Nc=5, Nc=6 | ❌ | ✅ varying (35-67% screen success) |
| Both broken | Nc=7 | ❌ LDPC | ❌ (17% scan, status=1 extreme) |

Nc=7 is the only mode broken on BOTH paths, though for apparently distinct mechanisms.

## How to use these for regression detection

After landing Option (B) C-side PartI instrumentation (or any other C-decoder change), re-run the benchmarks:

```bash
cd panama-wrapper
bash run-benchmark.sh DecodingBenchmark "-rf json -rff results/per-nc-decode-postchange.json"
bash run-benchmark.sh EncodingBenchmark "-rf json -rff results/per-nc-encode-postchange.json"
```

Then compare ms/op values for each surviving (Nc, messageSize) combination. A meaningful regression is **>10% slowdown** at any combination, given the typical 1-6% confidence intervals on this run.

## Generation reproducibility

Both files were produced by the standard panama-wrapper benchmark harness on JDK 23.0.1, with 3 forks × (5 warmup + 10 measurement) iterations per parameter combination, JMH 1.37. The run used the panama-wrapper `lib/libjabcode.so` shipped on the panama-poc branch (NOT the swift-java-poc WS-5 .so — keep that in mind when comparing against newer libs).

To reproduce on a different lib build, replace `panama-wrapper/lib/libjabcode.so` and re-run.
