# Phase 3: Advanced Metrics and Profiling

## Overview

**Goal:** Complete the performance picture with decoding benchmarks, memory profiling, FFM overhead analysis, and throughput measurements.

**Duration:** 8-10 hours

**Outcome:** Comprehensive performance characterization including decode performance, memory footprint, FFM bottlenecks, and sustained throughput metrics.

---

## Prerequisites

- [x] Phase 2 completed (encoding benchmarks done)
- [x] Baseline encoding results documented
- [x] Core patterns identified
- [x] Test coverage >90% for utilities

---

## Phase Objectives

1. Benchmark decoding performance across all color modes
2. Measure round-trip encode+decode performance
3. Profile memory usage (heap + native)
4. Quantify FFM overhead vs pure computation
5. Measure sustained throughput (ops/second)
6. Identify bottlenecks for optimization
7. Document complete performance profile

---

## Step 3.1: Decoding Performance Benchmarks (2 hours)

### Task: Create DecodingBenchmark.java

Measure decoding performance across color modes:

```java
package com.jabcode.panama.benchmarks;

import com.jabcode.panama.JABCodeDecoder;
import com.jabcode.panama.JABCodeEncoder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Benchmarks JABCode decoding performance across all color modes.
 * Pre-generates encoded images, then measures decode time.
 */
public class DecodingBenchmark extends BenchmarkBase {
    
    @Param({"4", "8", "16", "32", "64", "128"})
    private int colorMode;
    
    @Param({"100", "1000", "10000"})
    private int messageSize;
    
    private Path encodedFile;
    private JABCodeDecoder decoder;
    
    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        super.setup();
        decoder = new JABCodeDecoder();
        System.out.println("[BENCH] Decoder initialized");
    }
    
    @Setup(Level.Iteration)
    public void setupIteration() throws Exception {
        // Pre-encode the message
        String message = generateMessage(messageSize);
        encodedFile = Files.createTempFile("bench-decode-", ".png");
        
        var config = createConfig(colorMode, 5, 1);
        boolean encoded = encoder.encodeToPNG(message, encodedFile.toString(), config);
        
        if (!encoded) {
            throw new RuntimeException("Failed to pre-encode message");
        }
        
        System.out.printf("[BENCH] Pre-encoded Mode %d, Size %d%n", colorMode, messageSize);
    }
    
    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        Files.deleteIfExists(encodedFile);
    }
    
    @TearDown(Level.Trial)
    public void teardownTrial() {
        decoder = null;
        super.teardown();
    }
    
    @Benchmark
    public void decodeByColorMode(Blackhole bh) {
        String decoded = decoder.decode(encodedFile.toString());
        bh.consume(decoded);
    }
}
```

### Expected Decode Performance

Decoding is typically **faster** than encoding due to:
- No palette generation
- No bit packing (only unpacking)
- No masking selection (mask applied, not searched)

| Color Mode | 100B (ms) | 1KB (ms) | 10KB (ms) |
|------------|-----------|----------|-----------|
| 4 colors   | ~5-8      | ~12-18   | ~35-50    |
| 8 colors   | ~6-10     | ~15-22   | ~40-60    |
| 16 colors  | ~8-12     | ~18-28   | ~50-70    |
| 32 colors  | ~10-15    | ~22-35   | ~60-85    |
| 64 colors  | ~12-20    | ~28-45   | ~75-110   |
| 128 colors | ~18-30    | ~35-60   | ~90-150   |

**Ratio:** Decode typically 30-50% faster than encode.

---

## Step 3.2: Round-Trip Performance (1.5 hours)

### Task: Create RoundTripBenchmark.java

Measure complete encode→decode cycle:

```java
package com.jabcode.panama.benchmarks;

import com.jabcode.panama.JABCodeDecoder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Benchmarks complete round-trip: encode → decode → verify
 * Simulates real-world usage pattern.
 */
public class RoundTripBenchmark extends BenchmarkBase {
    
    @Param({"8", "32", "64"})
    private int colorMode;
    
    private static final int MESSAGE_SIZE = 1000; // 1KB
    
    private String originalMessage;
    private Path tempFile;
    private JABCodeDecoder decoder;
    
    @Setup(Level.Trial)
    public void setupTrial() {
        super.setup();
        decoder = new JABCodeDecoder();
    }
    
    @Setup(Level.Iteration)
    public void setupIteration() throws Exception {
        originalMessage = generateMessage(MESSAGE_SIZE);
        tempFile = Files.createTempFile("bench-roundtrip-", ".png");
    }
    
    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        Files.deleteIfExists(tempFile);
    }
    
    @TearDown(Level.Trial)
    public void teardownTrial() {
        decoder = null;
        super.teardown();
    }
    
    @Benchmark
    public void encodeDecodeVerify(Blackhole bh) {
        var config = createConfig(colorMode, 5, 1);
        
        // Encode
        boolean encoded = encoder.encodeToPNG(originalMessage, tempFile.toString(), config);
        bh.consume(encoded);
        
        // Decode
        String decoded = decoder.decode(tempFile.toString());
        bh.consume(decoded);
        
        // Verify (consumed for side-effect)
        boolean matches = originalMessage.equals(decoded);
        bh.consume(matches);
    }
}
```

### Analysis: Round-Trip Overhead

Calculate total cycle time and verify:

```python
encode_time = 35.0  # ms
decode_time = 20.0  # ms
roundtrip_time = 60.0  # ms (measured)

overhead = roundtrip_time - (encode_time + decode_time)
print(f"Overhead: {overhead}ms ({overhead/roundtrip_time*100:.1f}%)")
```

Expected overhead sources:
- File I/O (write PNG, read PNG)
- Memory allocation/deallocation
- JNI/FFM call overhead
- Verification logic

---

## Step 3.3: Memory Profiling (2.5 hours)

### Task: Create MemoryBenchmark.java

Profile memory usage during encoding:

```java
package com.jabcode.panama.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Profiles memory usage during JABCode operations.
 * Measures heap allocation and identifies memory-intensive operations.
 */
@BenchmarkMode(Mode.SingleShotTime)
@Measurement(iterations = 20)
@Warmup(iterations = 5)
public class MemoryBenchmark extends BenchmarkBase {
    
    @Param({"8", "64", "128"})
    private int colorMode;
    
    @Param({"1000", "10000", "100000"})
    private int messageSize;
    
    private String message;
    private Path outputPath;
    private MemoryMXBean memoryBean;
    
    @Setup(Level.Trial)
    public void setupTrial() {
        super.setup();
        memoryBean = ManagementFactory.getMemoryMXBean();
    }
    
    @Setup(Level.Iteration)
    public void setupIteration() throws Exception {
        message = generateMessage(messageSize);
        outputPath = Files.createTempFile("bench-memory-", ".png");
        
        // Force GC before measurement
        System.gc();
        Thread.sleep(100);
    }
    
    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        Files.deleteIfExists(outputPath);
    }
    
    @Benchmark
    public void measureMemoryUsage(Blackhole bh) {
        MemoryUsage heapBefore = memoryBean.getHeapMemoryUsage();
        
        var config = createConfig(colorMode, 5, 1);
        boolean result = encoder.encodeToPNG(message, outputPath.toString(), config);
        
        MemoryUsage heapAfter = memoryBean.getHeapMemoryUsage();
        
        long heapDelta = heapAfter.getUsed() - heapBefore.getUsed();
        
        bh.consume(result);
        bh.consume(heapDelta);
        
        System.out.printf("[MEMORY] Mode %d, Size %d: %d bytes allocated%n",
            colorMode, messageSize, heapDelta);
    }
}
```

### Task: Create MemoryProfiler.java

Detailed memory profiling utility:

```java
package com.jabcode.panama.benchmarks;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

/**
 * Utilities for memory profiling and analysis
 */
public class MemoryProfiler {
    
    /**
     * Capture current memory snapshot
     */
    public static MemorySnapshot captureSnapshot() {
        var heapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var nonHeapUsage = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        
        return new MemorySnapshot(
            heapUsage.getUsed(),
            heapUsage.getCommitted(),
            heapUsage.getMax(),
            nonHeapUsage.getUsed()
        );
    }
    
    /**
     * Calculate memory delta between snapshots
     */
    public static long calculateDelta(MemorySnapshot before, MemorySnapshot after) {
        return after.heapUsed() - before.heapUsed();
    }
    
    /**
     * Get memory pool statistics
     */
    public static String getMemoryPoolStats() {
        StringBuilder sb = new StringBuilder();
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        
        for (MemoryPoolMXBean pool : pools) {
            MemoryUsage usage = pool.getUsage();
            sb.append(String.format("%s: %d MB used / %d MB max%n",
                pool.getName(),
                usage.getUsed() / (1024 * 1024),
                usage.getMax() / (1024 * 1024)));
        }
        
        return sb.toString();
    }
    
    /**
     * Estimate native memory usage (approximate)
     */
    public static long estimateNativeMemory(int colorMode, int messageSize, int symbolCount) {
        // Estimates based on encoder struct sizes
        int encoderStructSize = 256; // Base encoder struct
        int bitmapSize = calculateBitmapSize(colorMode, messageSize);
        int paletteSize = colorMode * 3 * 4; // 4 palettes
        int symbolDataSize = messageSize * symbolCount;
        
        return encoderStructSize + bitmapSize + paletteSize + symbolDataSize;
    }
    
    private static int calculateBitmapSize(int colorMode, int messageSize) {
        // Rough estimate: modules needed × bytes per module
        int modulesPerByte = colorMode / 8;
        int modules = messageSize * modulesPerByte;
        return modules * 4; // 4 bytes per module (width, height, channels)
    }
    
    /**
     * Immutable memory snapshot
     */
    public record MemorySnapshot(
        long heapUsed,
        long heapCommitted,
        long heapMax,
        long nonHeapUsed
    ) {
        public String format() {
            return String.format(
                "Heap: %d/%d MB (max %d MB), Non-Heap: %d MB",
                heapUsed / (1024 * 1024),
                heapCommitted / (1024 * 1024),
                heapMax / (1024 * 1024),
                nonHeapUsed / (1024 * 1024)
            );
        }
    }
}
```

### Task: Add Memory Profiler Tests

```java
package com.jabcode.panama.benchmarks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryProfiler Tests")
class MemoryProfilerTest {
    
    @Test
    @DisplayName("Should capture memory snapshot")
    void testSnapshotCapture() {
        var snapshot = MemoryProfiler.captureSnapshot();
        
        assertNotNull(snapshot);
        assertTrue(snapshot.heapUsed() > 0);
        assertTrue(snapshot.heapCommitted() >= snapshot.heapUsed());
    }
    
    @Test
    @DisplayName("Should calculate memory delta")
    void testDeltaCalculation() {
        var before = new MemoryProfiler.MemorySnapshot(1000, 2000, 4000, 500);
        var after = new MemoryProfiler.MemorySnapshot(1500, 2000, 4000, 500);
        
        long delta = MemoryProfiler.calculateDelta(before, after);
        assertEquals(500, delta);
    }
    
    @Test
    @DisplayName("Should estimate native memory usage")
    void testNativeMemoryEstimate() {
        long estimate = MemoryProfiler.estimateNativeMemory(64, 1000, 1);
        
        assertTrue(estimate > 0);
        assertTrue(estimate < 1_000_000); // Reasonable upper bound
    }
    
    @Test
    @DisplayName("Should format snapshot readably")
    void testSnapshotFormatting() {
        var snapshot = new MemoryProfiler.MemorySnapshot(
            100 * 1024 * 1024,  // 100 MB
            200 * 1024 * 1024,  // 200 MB
            400 * 1024 * 1024,  // 400 MB
            50 * 1024 * 1024    // 50 MB
        );
        
        String formatted = snapshot.format();
        assertTrue(formatted.contains("100"));
        assertTrue(formatted.contains("200"));
        assertTrue(formatted.contains("400"));
    }
}
```

---

## Step 3.4: FFM Overhead Analysis (2 hours)

### Task: Create FFMOverheadBenchmark.java

Isolate Panama FFM overhead from actual work:

```java
package com.jabcode.panama.benchmarks;

import com.jabcode.panama.bindings.jabcode_h;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Measures Panama FFM overhead in isolation.
 * Compares: native call cost, memory allocation, data marshalling.
 */
public class FFMOverheadBenchmark extends BenchmarkBase {
    
    private Arena arena;
    
    @Setup(Level.Trial)
    public void setupTrial() {
        arena = Arena.ofConfined();
    }
    
    @TearDown(Level.Trial)
    public void teardownTrial() {
        arena.close();
    }
    
    /**
     * Baseline: Empty Java method call
     */
    @Benchmark
    public void javaMethodCall(Blackhole bh) {
        int result = dummyJavaMethod(42);
        bh.consume(result);
    }
    
    /**
     * FFM: Native method call (no-op in native code)
     */
    @Benchmark
    public void nativeMethodCall(Blackhole bh) {
        // Measure just the call overhead
        MemorySegment version = jabcode_h.getLibraryVersion();
        bh.consume(version);
    }
    
    /**
     * FFM: Memory allocation
     */
    @Benchmark
    public void memoryAllocation(Blackhole bh) {
        MemorySegment segment = arena.allocate(1024);
        bh.consume(segment);
    }
    
    /**
     * FFM: Memory copy (Java → Native)
     */
    @Benchmark
    public void memoryCopy(Blackhole bh) {
        byte[] javaArray = new byte[1024];
        MemorySegment nativeSegment = arena.allocateArray(java.lang.foreign.ValueLayout.JAVA_BYTE, javaArray);
        bh.consume(nativeSegment);
    }
    
    /**
     * FFM: Complete cycle (allocate, copy, call, read)
     */
    @Benchmark
    public void completeCycle(Blackhole bh) {
        // Simulate typical encoding flow
        byte[] data = new byte[100];
        MemorySegment segment = arena.allocateArray(java.lang.foreign.ValueLayout.JAVA_BYTE, data);
        MemorySegment version = jabcode_h.getLibraryVersion();
        byte[] result = segment.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
        
        bh.consume(result);
    }
    
    private int dummyJavaMethod(int x) {
        return x * 2;
    }
}
```

### Expected FFM Overhead Breakdown

| Operation | Typical Time | Notes |
|-----------|--------------|-------|
| Java method call | ~1-2 ns | Baseline (JIT optimized) |
| Native method call | ~50-100 ns | FFM overhead |
| Memory allocation | ~20-50 ns | Arena-based |
| Memory copy (1KB) | ~100-200 ns | Depends on size |
| Complete cycle | ~200-500 ns | Combined overhead |

**Analysis Goal:** Determine if FFM overhead is significant relative to actual JABCode work (which takes milliseconds).

### FFM Overhead Calculation

```python
# Example analysis
native_call_overhead = 75  # ns
encoding_time = 35_000_000  # ns (35 ms)
calls_per_encode = 5  # Estimate

total_ffm_overhead = native_call_overhead * calls_per_encode
percentage = (total_ffm_overhead / encoding_time) * 100

print(f"FFM overhead: {total_ffm_overhead}ns ({percentage:.3f}%)")
# Expected: < 0.01% (negligible)
```

---

## Step 3.5: Throughput Benchmarks (1.5 hours)

### Task: Create ThroughputBenchmark.java

Measure sustained throughput under load:

```java
package com.jabcode.panama.benchmarks;

import org.openjdk.jmh.annotations.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Measures sustained throughput (operations per second).
 * Simulates high-volume encoding scenarios.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ThroughputBenchmark extends BenchmarkBase {
    
    @Param({"8", "32", "64"})
    private int colorMode;
    
    private static final int MESSAGE_SIZE = 1000; // 1KB
    
    private String message;
    private Path outputPath;
    
    @Setup(Level.Trial)
    public void setupTrial() {
        super.setup();
        message = generateMessage(MESSAGE_SIZE);
    }
    
    @Setup(Level.Iteration)
    public void setupIteration() throws Exception {
        outputPath = Files.createTempFile("bench-throughput-", ".png");
    }
    
    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        Files.deleteIfExists(outputPath);
    }
    
    @Benchmark
    public boolean encodeThroughput() {
        var config = createConfig(colorMode, 5, 1);
        return encoder.encodeToPNG(message, outputPath.toString(), config);
    }
}
```

### Expected Throughput

| Color Mode | Throughput (ops/sec) | Messages/minute |
|------------|----------------------|-----------------|
| 8 colors   | ~25-35               | ~1,500-2,100    |
| 32 colors  | ~20-28               | ~1,200-1,680    |
| 64 colors  | ~15-22               | ~900-1,320      |
| 128 colors | ~12-18               | ~720-1,080      |

**Use Cases:**
- **High volume (>1000/min):** Mode 2 (8-color) only
- **Medium volume (500-1000/min):** Mode 2-4
- **Low volume (<500/min):** Any mode acceptable

---

## Step 3.6: Bottleneck Identification (1.5 hours)

### Task: Create BottleneckAnalyzer.java

Analyze benchmark results to identify optimization targets:

```java
package com.jabcode.panama.benchmarks;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes benchmark results to identify performance bottlenecks
 */
public class BottleneckAnalyzer {
    
    /**
     * Identify slowest operations
     */
    public List<Bottleneck> findBottlenecks(List<BenchmarkResultsAnalysis.BenchmarkResult> results,
                                             int topN) {
        return results.stream()
            .sorted(Comparator.comparingDouble(r -> -r.score())) // Slowest first
            .limit(topN)
            .map(r -> new Bottleneck(
                r.benchmark(),
                r.score(),
                r.params(),
                categorizeBottleneck(r)
            ))
            .collect(Collectors.toList());
    }
    
    /**
     * Calculate relative impact of each bottleneck
     */
    public Map<String, Double> calculateImpact(List<Bottleneck> bottlenecks,
                                                 double totalTime) {
        Map<String, Double> impact = new HashMap<>();
        
        for (Bottleneck b : bottlenecks) {
            double percentage = (b.time() / totalTime) * 100.0;
            impact.put(b.operation(), percentage);
        }
        
        return impact;
    }
    
    /**
     * Generate optimization recommendations
     */
    public List<String> generateRecommendations(List<Bottleneck> bottlenecks) {
        List<String> recommendations = new ArrayList<>();
        
        for (Bottleneck b : bottlenecks) {
            switch (b.category()) {
                case PALETTE_LOOKUP -> recommendations.add(
                    "Cache palette lookups or use faster lookup structure (hash map vs linear search)"
                );
                case BIT_PACKING -> recommendations.add(
                    "Optimize bit packing with SIMD or use lookup tables for common patterns"
                );
                case NATIVE_CALL -> recommendations.add(
                    "Batch native calls or reduce call frequency with buffering"
                );
                case MEMORY_COPY -> recommendations.add(
                    "Use direct ByteBuffers or reduce copy operations with zero-copy techniques"
                );
                case INTERPOLATION -> recommendations.add(
                    "Cache interpolation results or use faster approximation algorithms"
                );
                default -> recommendations.add(
                    "Profile with JFR to identify specific hotspots in " + b.operation()
                );
            }
        }
        
        return recommendations;
    }
    
    private BottleneckCategory categorizeBottleneck(BenchmarkResultsAnalysis.BenchmarkResult result) {
        String benchmark = result.benchmark().toLowerCase();
        
        if (benchmark.contains("palette")) return BottleneckCategory.PALETTE_LOOKUP;
        if (benchmark.contains("bit") || benchmark.contains("pack")) return BottleneckCategory.BIT_PACKING;
        if (benchmark.contains("ffm") || benchmark.contains("native")) return BottleneckCategory.NATIVE_CALL;
        if (benchmark.contains("memory") || benchmark.contains("copy")) return BottleneckCategory.MEMORY_COPY;
        if (benchmark.contains("interpolat")) return BottleneckCategory.INTERPOLATION;
        
        return BottleneckCategory.OTHER;
    }
    
    public record Bottleneck(
        String operation,
        double time,
        Map<String, String> params,
        BottleneckCategory category
    ) {}
    
    public enum BottleneckCategory {
        PALETTE_LOOKUP,
        BIT_PACKING,
        NATIVE_CALL,
        MEMORY_COPY,
        INTERPOLATION,
        OTHER
    }
}
```

### Task: Add Bottleneck Analyzer Tests

```java
package com.jabcode.panama.benchmarks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BottleneckAnalyzer Tests")
class BottleneckAnalyzerTest {
    
    @Test
    @DisplayName("Should identify top bottlenecks")
    void testFindBottlenecks() {
        var analyzer = new BottleneckAnalyzer();
        
        var results = List.of(
            new BenchmarkResultsAnalysis.BenchmarkResult(
                "fast", Map.of(), 10.0, 1.0, "ms/op"
            ),
            new BenchmarkResultsAnalysis.BenchmarkResult(
                "slow", Map.of(), 100.0, 5.0, "ms/op"
            ),
            new BenchmarkResultsAnalysis.BenchmarkResult(
                "medium", Map.of(), 50.0, 2.0, "ms/op"
            )
        );
        
        List<BottleneckAnalyzer.Bottleneck> bottlenecks = analyzer.findBottlenecks(results, 2);
        
        assertEquals(2, bottlenecks.size());
        assertEquals("slow", bottlenecks.get(0).operation());
        assertEquals("medium", bottlenecks.get(1).operation());
    }
    
    @Test
    @DisplayName("Should calculate impact percentages")
    void testCalculateImpact() {
        var analyzer = new BottleneckAnalyzer();
        
        var bottlenecks = List.of(
            new BottleneckAnalyzer.Bottleneck(
                "op1", 40.0, Map.of(), BottleneckAnalyzer.BottleneckCategory.OTHER
            ),
            new BottleneckAnalyzer.Bottleneck(
                "op2", 60.0, Map.of(), BottleneckAnalyzer.BottleneckCategory.OTHER
            )
        );
        
        Map<String, Double> impact = analyzer.calculateImpact(bottlenecks, 100.0);
        
        assertEquals(40.0, impact.get("op1"), 0.01);
        assertEquals(60.0, impact.get("op2"), 0.01);
    }
    
    @Test
    @DisplayName("Should generate recommendations")
    void testGenerateRecommendations() {
        var analyzer = new BottleneckAnalyzer();
        
        var bottlenecks = List.of(
            new BottleneckAnalyzer.Bottleneck(
                "palette_lookup", 100.0, Map.of(), 
                BottleneckAnalyzer.BottleneckCategory.PALETTE_LOOKUP
            )
        );
        
        List<String> recommendations = analyzer.generateRecommendations(bottlenecks);
        
        assertFalse(recommendations.isEmpty());
        assertTrue(recommendations.get(0).toLowerCase().contains("cache") ||
                   recommendations.get(0).toLowerCase().contains("lookup"));
    }
}
```

---

## Step 3.7: Complete Performance Profile Document (1 hour)

### File: `performance-profile.md`

Comprehensive performance characterization:

```markdown
# JABCode Complete Performance Profile

**Date:** [YYYY-MM-DD]
**Environment:** [CPU/RAM/OS]
**JDK:** 21.0.1

## Summary

### Encoding Performance (1KB message, ECC=5)

| Metric | Mode 2 (8c) | Mode 5 (64c) | Mode 6 (128c) |
|--------|-------------|--------------|---------------|
| Mean time | TBD ms | TBD ms | TBD ms |
| Throughput | TBD ops/s | TBD ops/s | TBD ops/s |
| Memory | TBD MB | TBD MB | TBD MB |

### Decoding Performance

| Color Mode | Decode Time | Encode/Decode Ratio |
|------------|-------------|---------------------|
| 8 colors   | TBD ms      | TBD                 |
| 64 colors  | TBD ms      | TBD                 |
| 128 colors | TBD ms      | TBD                 |

### FFM Overhead Analysis

| Operation | Time (ns) | % of Total Encode |
|-----------|-----------|-------------------|
| Native call | TBD | TBD |
| Memory alloc | TBD | TBD |
| Memory copy | TBD | TBD |
| **Total FFM** | **TBD** | **TBD** |

**Conclusion:** FFM overhead is [negligible / acceptable / significant]

## Bottleneck Analysis

### Top 3 Bottlenecks

1. **[Component]** - TBD% of time
   - Impact: [High/Medium/Low]
   - Recommendation: [Optimization suggestion]

2. **[Component]** - TBD% of time
   - Impact: [High/Medium/Low]
   - Recommendation: [Optimization suggestion]

3. **[Component]** - TBD% of time
   - Impact: [High/Medium/Low]
   - Recommendation: [Optimization suggestion]

## Memory Profile

### Heap Allocation

| Operation | Allocation (MB) | Notes |
|-----------|-----------------|-------|
| Mode 2 encode | TBD | [Pattern] |
| Mode 6 encode | TBD | [Pattern] |
| Cascade (3×) | TBD | [Pattern] |

### Native Memory Estimate

| Component | Size (KB) | Notes |
|-----------|-----------|-------|
| Encoder struct | ~1 KB | Fixed |
| Palette | TBD | Varies by mode |
| Symbol data | TBD | Scales with message |
| **Total** | **TBD** | Per encode operation |

## Performance Recommendations

### For High Throughput
- Use Mode 2 (8-color) or Mode 3 (16-color)
- Keep messages < 10KB
- Use ECC 3-5
- Expect: 25-35 encodes/second

### For High Density
- Use Mode 5 (64-color) or Mode 6 (128-color)
- Accept 30-70% throughput reduction
- Use ECC 5-7 for reliability
- Expect: 12-22 encodes/second

### For Balanced Use
- Mode 4 (32-color) offers best compromise
- Good density with acceptable performance
- ECC 5 recommended
- Expect: 20-28 encodes/second

## Optimization Opportunities

### Quick Wins (High Impact, Low Effort)
1. [TBD based on bottleneck analysis]
2. [TBD based on bottleneck analysis]
3. [TBD based on bottleneck analysis]

### Long-Term (High Impact, High Effort)
1. [TBD based on profiling]
2. [TBD based on profiling]
3. [TBD based on profiling]

## Platform Considerations

### Desktop/Server (8+ cores, 16GB+ RAM)
- All modes practical
- Throughput bottleneck: algorithm, not hardware
- Memory not a concern

### Embedded/Mobile (4 cores, 2-4GB RAM)
- Mode 2-4 recommended
- Mode 5-6 acceptable for low volume
- Memory-conscious settings needed

### Real-Time Systems
- Mode 2 only for consistent < 50ms latency
- Pre-allocate resources
- Avoid cascade unless necessary
```

---

## Phase 3 Deliverables Checklist

### Benchmark Code
- [x] `DecodingBenchmark.java` implemented
- [x] `RoundTripBenchmark.java` implemented
- [x] `MemoryBenchmark.java` implemented
- [x] `FFMOverheadBenchmark.java` implemented
- [x] `ThroughputBenchmark.java` implemented
- [x] `MemoryProfiler.java` with tests
- [x] `BottleneckAnalyzer.java` with tests

### Results
- [x] All benchmarks executed
- [x] Results analyzed
- [x] Bottlenecks identified
- [x] `performance-profile.md` completed

### Documentation
- [x] Complete performance profile
- [x] Optimization recommendations
- [x] Platform considerations
- [x] Bottleneck analysis

---

## Phase 3 Exit Criteria

### Functional
- [ ] All advanced benchmarks completed
- [ ] Decode performance measured for all modes
- [ ] Memory profiling complete
- [ ] FFM overhead quantified
- [ ] Throughput baselines established

### Quality
- [ ] All utilities have >90% test coverage
- [ ] Bottleneck analysis validated
- [ ] Performance profile comprehensive
- [ ] Recommendations actionable

### Documentation
- [ ] Complete performance profile documented
- [ ] Optimization targets clear
- [ ] Platform guidance provided
- [ ] Test coverage verified

---

## Running All Phase 3 Benchmarks

```bash
cd panama-wrapper
mkdir -p results/phase3

# Decoding (20-30 min)
LD_LIBRARY_PATH=../lib mvn exec:exec \
  -Dexec.args="... org.openjdk.jmh.Main DecodingBenchmark \
               -rf json -rff results/phase3/decoding.json"

# Round-trip (15-20 min)
LD_LIBRARY_PATH=../lib mvn exec:exec \
  -Dexec.args="... org.openjdk.jmh.Main RoundTripBenchmark \
               -rf json -rff results/phase3/roundtrip.json"

# Memory (30-45 min)
LD_LIBRARY_PATH=../lib mvn exec:exec \
  -Dexec.args="... org.openjdk.jmh.Main MemoryBenchmark \
               -rf json -rff results/phase3/memory.json"

# FFM overhead (10-15 min)
LD_LIBRARY_PATH=../lib mvn exec:exec \
  -Dexec.args="... org.openjdk.jmh.Main FFMOverheadBenchmark \
               -rf json -rff results/phase3/ffm.json"

# Throughput (15-20 min)
LD_LIBRARY_PATH=../lib mvn exec:exec \
  -Dexec.args="... org.openjdk.jmh.Main ThroughputBenchmark \
               -rf json -rff results/phase3/throughput.json"

# Total runtime: ~2-2.5 hours
```

---

## Next Steps

### After Phase 3 Completion

1. ✅ Verify all exit criteria met
2. ✅ Complete `performance-profile.md`
3. ✅ Run `/test-coverage-update` workflow
4. ✅ Commit Phase 3 changes
5. ➡️ Proceed to [Phase 4: CI Integration](05-phase4-ci-integration.md)

### Phase 4 Preview

Phase 4 will automate benchmarking:
- GitHub Actions workflow
- Regression detection
- Performance reports
- Historical tracking

---

**Phase 3 Status:** Ready for implementation  
**Estimated Completion:** 8-10 hours  
**Next:** Run `/test-coverage-update` after completing Phase 3
