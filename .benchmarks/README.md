# Benchmark Baselines

This directory stores baseline benchmark results for regression detection in CI.

## Structure

- `baseline.json` - Current baseline for performance comparison
- Format: JMH JSON output format

## Baseline Management

### Automatic Updates

The baseline is automatically updated when commits are pushed to `main`:
- GitHub Actions runs benchmarks
- Results are committed to `.benchmarks/baseline.json`
- Commit message: `chore: update benchmark baseline [skip ci]`

### Manual Updates

To manually update the baseline:

```bash
# Run benchmarks locally
cd panama-wrapper
LD_LIBRARY_PATH=../lib java \
  -cp target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) \
  --enable-native-access=ALL-UNNAMED \
  -Djava.library.path=../lib \
  org.openjdk.jmh.Main "EncodingBenchmark|DecodingBenchmark|RoundTripBenchmark" \
  -wi 3 -i 5 -f 1 \
  -p colorMode=8,32,64 \
  -p messageSize=1000 \
  -rf json -rff ../baseline-new.json

# Review results
python3 .github/scripts/compare-benchmarks.py \
  .benchmarks/baseline.json \
  baseline-new.json

# Update baseline if satisfied
mv baseline-new.json .benchmarks/baseline.json
git add .benchmarks/baseline.json
git commit -m "chore: update benchmark baseline"
```

## Baseline Criteria

A good baseline should:
- ✅ Be run on clean system (no background load)
- ✅ Use sufficient warmup (3+ iterations)
- ✅ Use sufficient measurement (5+ iterations)
- ✅ Have low variance (<15% error margin)
- ✅ Represent production build (optimized native library)

## Current Baseline

**Created:** 2026-01-15  
**Phases:** 0-3 complete (17 hours)  
**Optimizations:**
- LDPC matrix caching (33% speedup)
- Native library: `-O3 -march=native`

**Key Results:**
- 8-color encode: 53.1ms
- 32-color encode: 34.7ms ⚡ (48% faster than 4-color)
- 64-color encode: 34.4ms ⚡
- 8-color decode: 61.6ms
- Round-trip (32-color): 87.2ms

## Regression Thresholds

- **Alert threshold:** >20% slower than baseline
- **Stability range:** ±5% considered stable
- **Improvement:** >5% faster (celebrate! 🎉)

## Troubleshooting

### High Variance

If benchmarks show high variance (>20% error):
1. Close background applications
2. Increase warmup/measurement iterations
3. Run on dedicated hardware (not shared CI)
4. Consider using self-hosted GitHub runner

### False Positives

If regression alerts are frequent but invalid:
1. Review baseline quality (was it noisy?)
2. Adjust threshold (currently 20%)
3. Run multiple times and average
4. Use statistical significance testing

### Baseline Drift

If baseline becomes stale (old optimizations):
1. Run full benchmark suite
2. Review for legitimate improvements
3. Update baseline with explanation
4. Document optimization that caused drift

---

**Phase 4 Status:** CI Integration Complete  
**Documentation:** `memory-bank/research/benchmark-plan/`
