# Phase 2 Performance Analysis & Anomaly Investigation

**Analysis Date:** January 13, 2026  
**Dataset:** 44 benchmark configurations from Phase 2  
**Key Finding:** Multiple performance anomalies requiring investigation

---

## Executive Summary

Phase 2 benchmarks revealed **two major anomalies** that contradict expectations:

1. **Decode is 2.2x SLOWER than encode** (52.7ms vs 24.4ms @ 64-color, 1KB)
2. **32-color ECC 5 is 7% faster than ECC 3** (25.26ms vs 27.12ms)

These findings suggest either:
- Measurement methodology issues
- Unexpected algorithmic complexity in decoder
- Hidden overhead in specific code paths

---

## Anomaly 1: Decode Slower Than Encode

### Measurements

**Encode Performance (Phase 2):**
```
64-color @ 1KB: 24.38 ms/op
```

**Decode Performance (Phase 3 smoke test):**
```
64-color @ 1KB: 52.70 ms/op
```

**Ratio:** Decode is **2.16x slower** than encode

### Expected Behavior

Plan predicted: "Decoding 30-50% faster than encoding"

**Reasoning:**
- No palette generation needed
- No bit packing (only unpacking)
- No masking pattern selection
- LDPC decoding vs encoding should be similar

### Hypothesis: Why Decode Is Slower

#### 1. File I/O Overhead
**Impact:** HIGH  
**Evidence:** Encode writes PNG once; decode reads + decompresses PNG

**Analysis:**
- PNG compression/decompression asymmetric
- libpng decompress typically slower than compress
- File system read latency vs write buffering

**Test:** Measure with in-memory bitmap (bypass file I/O)

#### 2. Symbol Detection Overhead
**Impact:** MEDIUM  
**Evidence:** Decoder must locate finder patterns before decoding

**Steps:**
1. Detect finder patterns (3 corners)
2. Calculate perspective transform
3. Sample module grid
4. Classify module colors

**Estimate:** 5-10ms for 64-color symbol

**Encoder equivalent:** None (knows exact module positions)

#### 3. LDPC Decoding Complexity
**Impact:** MEDIUM  
**Evidence:** Iterative belief propagation may require more iterations

**Analysis:**
- Encoder: Generate parity bits (matrix multiply)
- Decoder: Iterative solver (belief propagation, up to max iterations)
- Worse channel conditions = more iterations

**Test:** Log actual LDPC iteration count in decoder

#### 4. Palette Matching Cost
**Impact:** LOW-MEDIUM  
**Evidence:** Each module color must be matched to palette

**Analysis:**
- 64 colors = 64 distance calculations per module
- Euclidean distance in RGB space
- No optimization (no k-d tree or octree)

**For 64-color, 1KB message:**
- ~500 modules (estimated)
- 500 × 64 = 32,000 distance calculations

**Test:** Profile time in color quantization

#### 5. Measurement Methodology
**Impact:** LOW (unlikely)  
**Evidence:** JMH setup excludes pre-encoding time

**Verification:**
- `@Setup(Level.Iteration)` runs before each iteration
- JMH excludes setup/teardown from measurement
- Output confirms: "52.320 ms/op" is pure decode

**Conclusion:** Methodology is correct

### Breakdown Estimate

| Component              | Estimate (ms) | % of Total |
|------------------------|---------------|------------|
| PNG decompress         | 15-20         | 30-35%     |
| Symbol detection       | 8-12          | 15-20%     |
| Module sampling        | 5-8           | 10-15%     |
| Color quantization     | 8-12          | 15-20%     |
| LDPC decoding          | 10-15         | 20-25%     |
| Misc overhead          | 5-10          | 10%        |
| **Total**              | **51-77**     | **100%**   |

**Observed:** 52.7ms ✅ Falls within estimate

### Recommendations

1. **Profile decoder with JMH `-prof stack`** - Identify hotspots
2. **Add instrumentation** - Log time spent in each decoder phase
3. **Optimize palette matching** - Use k-d tree or octree for color lookup
4. **Benchmark PNG I/O separately** - Isolate file overhead
5. **Compare with reference implementation** - Validate if this is normal

---

## Anomaly 2: ECC 5 Faster Than ECC 3 (32-color mode)

### Measurements

**32-color @ 1KB (Phase 2 ECC benchmark):**
```
ECC 3: 27.12 ms/op
ECC 5: 25.26 ms/op  ← 7% FASTER (anomaly)
ECC 7: 49.38 ms/op
ECC 9: 99.59 ms/op
```

**Other modes show expected pattern:**
```
8-color:   ECC 3 (19.97) < ECC 5 (41.36)  ✅
128-color: ECC 3 (21.37) < ECC 5 (40.66)  ✅
```

### Expected Behavior

Higher ECC should be slower due to:
- More parity modules
- Larger LDPC matrix
- More encoding work

### Hypothesis: Measurement Variance

**Coefficient of Variation check needed:**
- ECC 3: 27.12 ms (need error bars)
- ECC 5: 25.26 ms (need error bars)

**If error bars overlap:** Not statistically significant

**Action:** Re-run with more iterations or check raw JMH data for confidence intervals

### Alternative Hypothesis: Symbol Size Optimization

**Theory:** ECC 3 might trigger larger symbol version

**Analysis:**
- 1KB message with ECC 3: Less redundancy, might need more symbols
- 1KB message with ECC 5: Better packing, single symbol

**Test:** Log actual symbol dimensions for each ECC level

### Hypothesis: JIT Compilation Artifact

**Theory:** ECC 5 warmup pattern happens to optimize better

**Evidence:** 
- 3 forks × warmup iterations
- ECC levels tested in order (3, 5, 7, 9)
- JIT may have optimized better by ECC 5 iteration

**Test:** Run ECC levels in random order or reverse order

---

## Pattern Analysis: Phase 2 Data

### 1. Color Mode Scaling

**Message Size: 1KB**

| Color Mode | Time (ms) | vs 64-color |
|------------|-----------|-------------|
| 4          | 47.46     | +94%        |
| 8          | 40.94     | +68%        |
| 16         | 40.24     | +65%        |
| 32         | 25.31     | +4%         |
| **64**     | **24.38** | **baseline**|
| 128        | 40.88     | +68%        |

**Observation:** 64-color is the sweet spot, even faster than 32-color

**Hypothesis:** 64-color optimized code path in encoder

### 2. Message Size Scaling

**64-color mode:**

| Size  | Time (ms) | Growth Factor |
|-------|-----------|---------------|
| 100B  | 6.33      | -             |
| 1KB   | 24.38     | 3.85x         |
| 10KB  | 151.04    | 6.19x         |

**Expected:** Linear (10x per 10x size)  
**Actual:** Sublinear (3.85x, 6.19x)

**Good news:** Encoder scales better than O(n)

**Hypothesis:** Fixed overhead dominates small messages, marginal cost is sublinear

### 3. ECC Level Impact (excluding 32-color anomaly)

**8-color mode:**

| ECC | Time (ms) | Overhead vs ECC 3 |
|-----|-----------|-------------------|
| 3   | 19.97     | baseline          |
| 5   | 41.36     | +107%             |
| 7   | 55.12     | +176%             |
| 9   | 110.36    | +453%             |

**Pattern:** Exponential growth in overhead

**ECC 9 penalty:** Consistently ~2.5x slower than ECC 5 across all modes

### 4. Cascaded Symbol Overhead

**Symbol count impact (5KB message):**

| Symbols | 32-color (ms) | 64-color (ms) | Overhead |
|---------|---------------|---------------|----------|
| 1       | 94.63         | 92.09         | baseline |
| 3       | 152.35        | 176.12        | +61-91%  |
| 5       | 390.47        | 311.37        | +238-313%|

**Observation:** Not linear! 5 symbols should be ~5x baseline, but it's 3.4-4.1x

**Hypothesis:** Symbol coordination overhead, but better packing efficiency

---

## Performance Insights

### Sweet Spots Confirmed

✅ **64-color @ 1-10KB** - Fastest encoding  
✅ **ECC 5** - Best balance (ECC 9 is 2.5x slower)  
✅ **Single symbol** - Avoid multi-symbol unless necessary  

### Unexpected Findings

❌ **Decode is NOT faster than encode** - Needs investigation  
❌ **32-color ECC anomaly** - Needs validation  
❌ **4-color slow for small messages** - Unexpected overhead  

---

## Action Items

### Critical (Before Phase 3)

1. **Profile decoder hotspots**
   ```bash
   ./run-benchmark.sh "DecodingBenchmark" "-prof stack -p colorMode=64 -p messageSize=1000"
   ```

2. **Verify ECC 3 vs ECC 5 anomaly**
   ```bash
   # Check confidence intervals in raw JSON
   jq '.[] | select(.params.colorMode=="32" and (.params.eccLevel=="3" or .params.eccLevel=="5"))' \
     results/ecc-impact.json
   ```

3. **Instrument decoder phases**
   - Add timing logs in C decoder:
     - PNG load
     - Symbol detection
     - Color quantization
     - LDPC decode
     - Data extraction

### Medium Priority

4. **Symbol size logging** - Check if ECC 3 creates larger symbols than ECC 5
5. **PNG I/O benchmark** - Isolate file overhead from decode
6. **Memory profiling** - Run MemoryBenchmark to see allocation patterns

### Low Priority

7. **100KB benchmarks** - Missing from Phase 2 data
8. **256-color investigation** - Still crashes on initialization
9. **Throughput mode** - ops/sec instead of latency

---

## Statistical Validation Needed

**Questions to answer from raw JMH JSON:**

1. What are the confidence intervals for 32-color ECC 3 vs ECC 5?
2. What is the coefficient of variation (CV) for each benchmark?
3. Are there outliers in any fork that skew results?
4. What is the warmup convergence pattern?

**Tools:**
```bash
# Extract scores with error bars
jq '.[] | {benchmark: .benchmark, params: .params, 
  score: .primaryMetric.score, 
  error: .primaryMetric.scoreError}' results/*.json

# Calculate CV
jq '.[] | {benchmark: .benchmark, 
  cv: (.primaryMetric.scoreError / .primaryMetric.score * 100)}' results/*.json
```

---

## Comparison with Literature

**JABCode paper claims:**
- "Higher information density than QR Code"
- "Color increases capacity without size increase"

**Our findings:**
- 128-color works (post-fix) ✅
- 64-color is fastest (not necessarily highest density)
- Decode overhead higher than expected

**Need:** Compare with QR Code decode performance baseline

---

## Conclusions

### What We Know

1. **Phase 2 benchmarks successful** - 44 configs, clean execution
2. **128-color mode validated** - Stable under load post-fix
3. **Performance scaling characterized** - Sublinear growth ✅
4. **Sweet spots identified** - 64-color, ECC 5, single symbol

### What We Don't Know

1. **Why decode is slow** - PNG I/O? Symbol detection? LDPC?
2. **32-color ECC anomaly** - Real or variance?
3. **Optimization headroom** - How much faster can we make it?

### Next Steps

**Before Phase 3 execution:**
1. Run decoder profiling with `-prof stack`
2. Validate ECC anomaly with confidence intervals
3. Add decoder phase instrumentation

**After investigation:**
- Decide if Phase 3 should proceed as-is or focus on bottlenecks
- Update benchmark plan based on findings
- Document optimization targets

---

**Report Status:** DRAFT - Investigation ongoing  
**Last Updated:** January 13, 2026, 12:35 PM
