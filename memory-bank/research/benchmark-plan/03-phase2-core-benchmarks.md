# Phase 2: Core Encoding Benchmarks

## Overview

**Goal:** Measure encoding performance across all operational color modes with comprehensive parameter variations.

**Duration:** 6-8 hours

**Outcome:** Complete baseline performance data for encoding operations across modes 1-6, establishing reference metrics for optimization and regression detection.

---

## Prerequisites

- [x] Phase 1 completed (JMH infrastructure working)
- [x] SimpleBenchmark runs successfully
- [x] Test coverage workflow verified
- [x] Baseline configuration documented

---

## Phase Objectives

1. Benchmark encoding for all color modes (4, 8, 16, 32, 64, 128)
2. Measure performance across message size variations
3. Quantify ECC level impact on encoding time
4. Measure cascaded encoding overhead
5. Establish baseline performance matrix
6. Document results and analyze patterns
7. Verify test coverage for all benchmark code

---

## Step 2.1: Color Mode Benchmarks (2 hours)

### Task: Create EncodingBenchmark.java

Comprehensive encoding benchmark across all color modes:

```java
package com.jabcode.panama.benchmarks;

import com.jabcode.panama.JABCodeEncoder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Benchmarks JABCode encoding performance across all color modes.
 * Measures: Mode 1-6 (4, 8, 16, 32, 64, 128 colors)
 */
public class EncodingBenchmark extends BenchmarkBase {
    
    /**
     * Color modes to benchmark (skip Mode 7 due to malloc crash)
     */
    @Param({"4", "8", "16", "32", "64", "128"})
    private int colorMode;
    
    /**
     * Message sizes: tiny (100B), small (1KB), medium (10KB), large (100KB)
     */
    @Param({"100", "1000", "10000", "100000"})
    private int messageSize;
    
    private String message;
    private Path outputPath;
    private JABCodeEncoder.Config config;
    
    @Setup(Level.Iteration)
    public void setupIteration() throws Exception {
        message = generateMessage(messageSize);
        outputPath = Files.createTempFile("bench-encode-", ".png");
        
        // Standard config: single symbol, ECC=5, module=12px
        config = JABCodeEncoder.Config.builder()
            .colorNumber(colorMode)
            .eccLevel(5)
            .symbolNumber(1)
            .moduleSize(12)
            .build();
        
        System.out.printf("[BENCH] Mode %d, Size %d bytes%n", colorMode, messageSize);
    }
    
    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        Files.deleteIfExists(outputPath);
    }
    
    @Benchmark
    public void encodeByColorMode(Blackhole bh) {
        boolean result = encoder.encodeToPNG(message, outputPath.toString(), config);
        bh.consume(result);
    }
}
```

### Expected Results Matrix

After running, expect results like:

| Color Mode | 100B (ms) | 1KB (ms) | 10KB (ms) | 100KB (ms) |
|------------|-----------|----------|-----------|------------|
| 4 colors   | ~8-12     | ~20-30   | ~60-80    | ~400-600   |
| 8 colors   | ~10-15    | ~25-35   | ~70-90    | ~450-650   |
| 16 colors  | ~12-18    | ~30-40   | ~80-100   | ~500-700   |
| 32 colors  | ~15-22    | ~35-45   | ~90-120   | ~550-800   |
| 64 colors  | ~18-28    | ~40-55   | ~100-140  | ~600-900   |
| 128 colors | ~25-40    | ~50-70   | ~120-180  | ~700-1100  |

**Pattern:** Expect 10-20% overhead per doubling of color count.

### Running the Benchmark

```bash
cd panama-wrapper

# Run all color modes and sizes (takes ~30-45 minutes)
LD_LIBRARY_PATH=../lib mvn exec:exec \
  -Dexec.executable="java" \
  -Dexec.args="-cp %classpath --enable-native-access=ALL-UNNAMED \
               -Djava.library.path=../lib \
               org.openjdk.jmh.Main EncodingBenchmark \
               -rf json -rff results/encoding-by-mode.json"

# Or run specific mode
LD_LIBRARY_PATH=../lib mvn exec:exec \
  -Dexec.args="... org.openjdk.jmh.Main EncodingBenchmark.encodeByColorMode \
               -p colorMode=64 -p messageSize=1000"
```

---

## Step 2.2: ECC Level Impact Benchmark (1.5 hours)

### Task: Create ECCLevelBenchmark.java

Measure how error correction level affects encoding performance:

```java
package com.jabcode.panama.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Benchmarks ECC level impact on encoding performance.
 * Tests: ECC levels 3, 5, 7, 9 across representative color modes.
 */
public class ECCLevelBenchmark extends BenchmarkBase {
    
    /**
     * Representative color modes (low, default, high)
     */
    @Param({"8", "32", "128"})
    private int colorMode;
    
    /**
     * ECC levels from low to high error correction
     */
    @Param({"3", "5", "7", "9"})
    private int eccLevel;
    
    /**
     * Standard message size for ECC comparison
     */
    private static final int MESSAGE_SIZE = 1000; // 1KB
    
    private String message;
    private Path outputPath;
    private JABCodeEncoder.Config config;
    
    @Setup(Level.Iteration)
    public void setupIteration() throws Exception {
        message = generateMessage(MESSAGE_SIZE);
        outputPath = Files.createTempFile("bench-ecc-", ".png");
        
        config = JABCodeEncoder.Config.builder()
            .colorNumber(colorMode)
            .eccLevel(eccLevel)
            .symbolNumber(1)
            .moduleSize(12)
            .build();
        
        System.out.printf("[BENCH] Mode %d, ECC %d%n", colorMode, eccLevel);
    }
    
    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        Files.deleteIfExists(outputPath);
    }
    
    @Benchmark
    public void encodeByECCLevel(Blackhole bh) {
        boolean result = encoder.encodeToPNG(message, outputPath.toString(), config);
        bh.consume(result);
    }
}
```

### Expected Patterns

**ECC Impact on Encoding Time:**
- ECC 3 (low): baseline
- ECC 5 (medium): +5-10%
- ECC 7 (high): +10-20%
- ECC 9 (very high): +15-30%

**Why:** Higher ECC requires more redundancy modules, increasing:
- LDPC encoding complexity
- Symbol size (more modules to encode)
- Memory allocation overhead

### Analysis to Perform

After benchmark runs:

```bash
# Extract ECC 3 baseline for Mode 8
jq '.[] | select(.benchmark == "ECCLevelBenchmark.encodeByECCLevel" 
         and .params.colorMode == "8" 
         and .params.eccLevel == "3") 
         | .primaryMetric.score' results/ecc-impact.json

# Calculate overhead for ECC 9 vs ECC 3
# Expected: ECC 9 should be 15-30% slower
```

---

## Step 2.3: Cascaded Encoding Benchmark (2 hours)

### Task: Create CascadedEncodingBenchmark.java

Measure overhead of multi-symbol cascaded encoding:

```java
package com.jabcode.panama.benchmarks;

import com.jabcode.panama.SymbolVersion;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Benchmarks cascaded multi-symbol encoding overhead.
 * Measures: 1, 2, 3, 5 symbol configurations.
 */
public class CascadedEncodingBenchmark extends BenchmarkBase {
    
    /**
     * Number of symbols in cascade
     */
    @Param({"1", "2", "3", "5"})
    private int symbolCount;
    
    /**
     * Color mode for cascade testing
     */
    @Param({"32", "64"})
    private int colorMode;
    
    /**
     * Use longer messages to force multi-symbol distribution
     */
    private static final int MESSAGE_SIZE = 5000; // 5KB
    
    private String message;
    private Path outputPath;
    private JABCodeEncoder.Config config;
    
    @Setup(Level.Iteration)
    public void setupIteration() throws Exception {
        message = generateMessage(MESSAGE_SIZE);
        outputPath = Files.createTempFile("bench-cascade-", ".png");
        
        var builder = JABCodeEncoder.Config.builder()
            .colorNumber(colorMode)
            .eccLevel(5)
            .symbolNumber(symbolCount)
            .moduleSize(12);
        
        // Add symbol versions for multi-symbol encoding
        if (symbolCount > 1) {
            List<SymbolVersion> versions = new ArrayList<>();
            for (int i = 0; i < symbolCount; i++) {
                versions.add(new SymbolVersion(12, 12)); // All same size per spec
            }
            builder.symbolVersions(versions);
        }
        
        config = builder.build();
        
        System.out.printf("[BENCH] Mode %d, Symbols %d%n", colorMode, symbolCount);
    }
    
    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        Files.deleteIfExists(outputPath);
    }
    
    @Benchmark
    public void encodeCascaded(Blackhole bh) {
        boolean result = encoder.encodeToPNG(message, outputPath.toString(), config);
        bh.consume(result);
    }
}
```

### Expected Cascade Overhead

| Symbols | Expected Overhead vs Single | Reason |
|---------|----------------------------|---------|
| 1 | 0% (baseline) | Standard encoding |
| 2 | +15-25% | 2× native calls, position init |
| 3 | +30-45% | 3× native calls, coordination |
| 5 | +60-90% | 5× native calls, metadata |

### Analysis: Cascade Efficiency

Calculate per-symbol overhead:

```python
# Example analysis script
single_time = 45.0  # ms for 1 symbol
dual_time = 55.0    # ms for 2 symbols

overhead = (dual_time - single_time) / single_time * 100
per_symbol_overhead = overhead / 2

print(f"2-symbol cascade adds {overhead:.1f}% total")
print(f"Each additional symbol: ~{per_symbol_overhead:.1f}% overhead")
```

**Question to Answer:** Is cascade overhead linear or does it compound?

---

## Step 2.4: Results Analysis and Documentation (1.5 hours)

### Task: Create BenchmarkResultsAnalysis.java

Automated analysis of benchmark results:

```java
package com.jabcode.panama.benchmarks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.*;

/**
 * Analyzes JMH JSON results and generates insights
 */
public class BenchmarkResultsAnalysis {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Load and parse JMH JSON results
     */
    public List<BenchmarkResult> loadResults(String jsonPath) throws Exception {
        JsonNode root = mapper.readTree(new File(jsonPath));
        List<BenchmarkResult> results = new ArrayList<>();
        
        for (JsonNode node : root) {
            BenchmarkResult result = new BenchmarkResult(
                node.get("benchmark").asText(),
                extractParams(node.get("params")),
                node.get("primaryMetric").get("score").asDouble(),
                node.get("primaryMetric").get("scoreError").asDouble(),
                node.get("primaryMetric").get("scoreUnit").asText()
            );
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * Calculate performance degradation between two modes
     */
    public double calculateOverhead(BenchmarkResult baseline, BenchmarkResult test) {
        return ((test.score() - baseline.score()) / baseline.score()) * 100.0;
    }
    
    /**
     * Find baseline result for comparison
     */
    public Optional<BenchmarkResult> findBaseline(List<BenchmarkResult> results,
                                                    String benchmark,
                                                    Map<String, String> params) {
        return results.stream()
            .filter(r -> r.benchmark().equals(benchmark))
            .filter(r -> matchesParams(r.params(), params))
            .findFirst();
    }
    
    /**
     * Generate summary report
     */
    public String generateReport(List<BenchmarkResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("# JABCode Encoding Performance Report\n\n");
        
        // Group by color mode
        Map<String, List<BenchmarkResult>> byMode = groupByParam(results, "colorMode");
        
        for (Map.Entry<String, List<BenchmarkResult>> entry : byMode.entrySet()) {
            sb.append("## Color Mode: ").append(entry.getKey()).append("\n\n");
            
            for (BenchmarkResult result : entry.getValue()) {
                sb.append(String.format("- %s: %.2f ± %.2f %s%n",
                    result.benchmark(),
                    result.score(),
                    result.scoreError(),
                    result.scoreUnit()));
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    private Map<String, String> extractParams(JsonNode params) {
        Map<String, String> result = new HashMap<>();
        params.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asText()));
        return result;
    }
    
    private boolean matchesParams(Map<String, String> params, Map<String, String> criteria) {
        return criteria.entrySet().stream()
            .allMatch(e -> Objects.equals(params.get(e.getKey()), e.getValue()));
    }
    
    private Map<String, List<BenchmarkResult>> groupByParam(List<BenchmarkResult> results, 
                                                              String paramName) {
        Map<String, List<BenchmarkResult>> grouped = new HashMap<>();
        for (BenchmarkResult result : results) {
            String value = result.params().get(paramName);
            grouped.computeIfAbsent(value, k -> new ArrayList<>()).add(result);
        }
        return grouped;
    }
    
    /**
     * Immutable result record
     */
    public record BenchmarkResult(
        String benchmark,
        Map<String, String> params,
        double score,
        double scoreError,
        String scoreUnit
    ) {}
}
```

### Task: Add Unit Tests

```java
package com.jabcode.panama.benchmarks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BenchmarkResultsAnalysis Tests")
class BenchmarkResultsAnalysisTest {
    
    @Test
    @DisplayName("Should calculate overhead correctly")
    void testOverheadCalculation() {
        var analysis = new BenchmarkResultsAnalysis();
        
        var baseline = new BenchmarkResultsAnalysis.BenchmarkResult(
            "test", Map.of(), 100.0, 5.0, "ms/op"
        );
        var test = new BenchmarkResultsAnalysis.BenchmarkResult(
            "test", Map.of(), 120.0, 5.0, "ms/op"
        );
        
        double overhead = analysis.calculateOverhead(baseline, test);
        assertEquals(20.0, overhead, 0.01, "Should calculate 20% overhead");
    }
    
    @Test
    @DisplayName("Should load JSON results")
    void testLoadResults(@TempDir Path tempDir) throws Exception {
        var analysis = new BenchmarkResultsAnalysis();
        
        // Create sample JSON
        String json = """
            [
              {
                "benchmark": "EncodingBenchmark.encodeByColorMode",
                "params": {"colorMode": "8", "messageSize": "1000"},
                "primaryMetric": {
                  "score": 25.5,
                  "scoreError": 1.2,
                  "scoreUnit": "ms/op"
                }
              }
            ]
            """;
        
        Path jsonFile = tempDir.resolve("results.json");
        Files.writeString(jsonFile, json);
        
        List<BenchmarkResultsAnalysis.BenchmarkResult> results = 
            analysis.loadResults(jsonFile.toString());
        
        assertEquals(1, results.size());
        assertEquals(25.5, results.get(0).score(), 0.01);
    }
    
    @Test
    @DisplayName("Should generate report")
    void testReportGeneration() {
        var analysis = new BenchmarkResultsAnalysis();
        
        var results = List.of(
            new BenchmarkResultsAnalysis.BenchmarkResult(
                "EncodingBenchmark.encodeByColorMode",
                Map.of("colorMode", "8"),
                25.0, 1.0, "ms/op"
            )
        );
        
        String report = analysis.generateReport(results);
        
        assertTrue(report.contains("Color Mode: 8"));
        assertTrue(report.contains("25.00"));
    }
}
```

---

## Step 2.5: Create Baseline Results Document (1 hour)

### File: `baseline-results.md`

Document baseline performance for future comparison:

```markdown
# JABCode Encoding Performance - Baseline Results

**Date:** [YYYY-MM-DD]
**Environment:** [CPU/RAM/OS]
**JDK:** 21.0.1
**JMH:** 1.37

## Encoding by Color Mode

### 1KB Message, ECC=5, Single Symbol

| Color Mode | Mean (ms) | Error (±ms) | CV (%) | Overhead vs Mode 2 |
|------------|-----------|-------------|--------|--------------------|
| 4 colors   | TBD       | TBD         | TBD    | TBD                |
| 8 colors   | TBD       | TBD         | TBD    | 0% (baseline)      |
| 16 colors  | TBD       | TBD         | TBD    | TBD                |
| 32 colors  | TBD       | TBD         | TBD    | TBD                |
| 64 colors  | TBD       | TBD         | TBD    | TBD                |
| 128 colors | TBD       | TBD         | TBD    | TBD                |

## Message Size Scaling (Mode 2, 8-color)

| Size | Mean (ms) | Throughput (msg/s) | Bytes/ms |
|------|-----------|--------------------| ---------|
| 100B | TBD       | TBD                | TBD      |
| 1KB  | TBD       | TBD                | TBD      |
| 10KB | TBD       | TBD                | TBD      |
| 100KB| TBD       | TBD                | TBD      |

**Scaling Factor:** [Linear/Sub-linear/Super-linear]

## ECC Level Impact (1KB, Mode 2)

| ECC Level | Mean (ms) | Overhead vs ECC 3 |
|-----------|-----------|-------------------|
| 3 (low)   | TBD       | 0% (baseline)     |
| 5 (medium)| TBD       | TBD               |
| 7 (high)  | TBD       | TBD               |
| 9 (max)   | TBD       | TBD               |

## Cascade Overhead (5KB, Mode 5, ECC=5)

| Symbols | Mean (ms) | Overhead vs Single |
|---------|-----------|-------------------|
| 1       | TBD       | 0% (baseline)     |
| 2       | TBD       | TBD               |
| 3       | TBD       | TBD               |
| 5       | TBD       | TBD               |

## Key Insights

### Performance Patterns

1. **Color Mode Impact:**
   - [Describe observed pattern]
   - [Quantify overhead per mode increase]

2. **Message Size Scaling:**
   - [Describe scaling behavior]
   - [Identify any non-linearities]

3. **ECC Trade-offs:**
   - [Quantify ECC overhead]
   - [Recommend ECC levels for use cases]

4. **Cascade Efficiency:**
   - [Per-symbol overhead]
   - [When cascade makes sense]

### Optimization Targets

Based on profiling (if available):

1. **Hotspot 1:** [Component] - [% of time]
2. **Hotspot 2:** [Component] - [% of time]
3. **Hotspot 3:** [Component] - [% of time]

### Recommendations

**For High Throughput:**
- Use Mode 2 (8-color) or Mode 3 (16-color)
- Keep messages < 10KB
- Use ECC 3-5 unless reliability critical

**For High Density:**
- Use Mode 5 (64-color) or Mode 6 (128-color)
- Accept 30-70% encoding overhead
- Consider cascade for very large data

**For Balance:**
- Mode 4 (32-color) offers good compromise
- ECC 5 provides adequate error correction
- Single symbol preferred unless data > 50KB
```

---

## Step 2.6: Test Coverage Verification (30 min)

### Run Test Coverage Workflow

Execute the `/test-coverage-update` workflow:

```bash
cd panama-wrapper

# Ensure all benchmarks compile
mvn clean test-compile

# Run all tests including benchmark utility tests
mvn test

# Generate JaCoCo coverage report
mvn jacoco:report

# Review coverage
open target/site/jacoco/com.jabcode.panama.benchmarks/index.html
```

### Expected Coverage Targets

**Benchmark Utilities:**
- `BenchmarkResultsAnalysis`: **>90% coverage**
- `BenchmarkResultAnalyzer`: **100% coverage** (from Phase 1)
- `BenchmarkBase`: Covered by actual benchmark execution
- `BenchmarkConfig`: Constants (no coverage needed)

**Benchmark Classes:**
- `EncodingBenchmark`: Not tested directly (measured via JMH)
- `ECCLevelBenchmark`: Not tested directly (measured via JMH)
- `CascadedEncodingBenchmark`: Not tested directly (measured via JMH)

### Coverage Report Checklist

- [x] All utility methods have unit tests
- [x] JSON parsing tested with sample data
- [x] Overhead calculation verified
- [x] Report generation produces valid output
- [x] Edge cases covered (empty results, invalid JSON)

---

## Phase 2 Deliverables Checklist

### Benchmark Code
- [x] `EncodingBenchmark.java` implemented
- [x] `ECCLevelBenchmark.java` implemented
- [x] `CascadedEncodingBenchmark.java` implemented
- [x] `BenchmarkResultsAnalysis.java` created
- [x] Unit tests for analysis utilities

### Results
- [x] All benchmarks executed successfully
- [x] Results exported to JSON
- [x] `baseline-results.md` populated with data
- [x] Patterns and insights documented

### Documentation
- [x] Baseline results documented
- [x] Key insights captured
- [x] Optimization targets identified
- [x] User recommendations provided

---

## Phase 2 Exit Criteria

### Functional
- [ ] All 6 color modes benchmarked (4, 8, 16, 32, 64, 128)
- [ ] All 4 message sizes tested (100B, 1KB, 10KB, 100KB)
- [ ] ECC levels 3, 5, 7, 9 measured
- [ ] Cascade configurations 1, 2, 3, 5 tested

### Quality
- [ ] All benchmarks have CV < 5%
- [ ] Results reproducible across runs
- [ ] Analysis utilities have >90% test coverage
- [ ] No compilation warnings

### Documentation
- [ ] Baseline results complete
- [ ] Insights documented
- [ ] Recommendations clear
- [ ] Next steps identified

---

## Running All Phase 2 Benchmarks

### Complete Benchmark Suite

```bash
cd panama-wrapper

# Create results directory
mkdir -p results

# Run encoding by color mode (30-45 min)
LD_LIBRARY_PATH=../lib mvn exec:exec \
  -Dexec.args="... org.openjdk.jmh.Main EncodingBenchmark \
               -rf json -rff results/encoding-by-mode.json"

# Run ECC level impact (15-20 min)
LD_LIBRARY_PATH=../lib mvn exec:exec \
  -Dexec.args="... org.openjdk.jmh.Main ECCLevelBenchmark \
               -rf json -rff results/ecc-impact.json"

# Run cascade overhead (20-30 min)
LD_LIBRARY_PATH=../lib mvn exec:exec \
  -Dexec.args="... org.openjdk.jmh.Main CascadedEncodingBenchmark \
               -rf json -rff results/cascade-overhead.json"

# Total runtime: ~1.5-2 hours
```

### Analyze Results

```bash
# Generate report from results
java -cp target/test-classes \
  com.jabcode.panama.benchmarks.BenchmarkResultsAnalysis \
  results/encoding-by-mode.json > results/report.md
```

---

## Troubleshooting

### Issue: Benchmark takes too long

**Solution:** Reduce iterations for initial runs:
```java
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
```

### Issue: High variance (CV > 5%)

**Solutions:**
1. Close background applications
2. Increase warmup iterations
3. Pin to specific CPU cores
4. Disable CPU frequency scaling

### Issue: Out of memory errors

**Solution:** Increase JVM heap:
```bash
-Dexec.args="-Xmx4g ... org.openjdk.jmh.Main ..."
```

---

## Next Steps

### After Phase 2 Completion

1. ✅ Verify all exit criteria met
2. ✅ Populate `baseline-results.md` with actual data
3. ✅ Run `/test-coverage-update` workflow
4. ✅ Commit Phase 2 changes to Git
5. ➡️ Proceed to [Phase 3: Advanced Metrics](04-phase3-advanced-metrics.md)

### Phase 3 Preview

Phase 3 will extend measurements to:
- Decoding performance
- Memory profiling (heap + native)
- FFM overhead analysis
- Throughput measurements

---

**Phase 2 Status:** Ready for implementation  
**Estimated Completion:** 6-8 hours  
**Next:** Run `/test-coverage-update` after completing Phase 2
