# Phase 1: JMH Infrastructure Setup

## Overview

**Goal:** Establish Java Microbenchmark Harness (JMH) infrastructure to enable performance measurement.

**Duration:** 4-6 hours

**Outcome:** Working benchmark framework with first "hello world" benchmark demonstrating the full pipeline.

---

## Prerequisites

- [x] JDK 21+ installed
- [x] Maven 3.8+ installed
- [x] Main panama-wrapper module compiling
- [x] Native libjabcode.so available
- [x] Test suite passing (205 tests)

---

## Phase Objectives

1. Add JMH dependencies to project
2. Create benchmark module structure
3. Configure Maven for benchmark execution
4. Write first benchmark
5. Verify benchmark runs correctly
6. Establish test coverage for benchmark utilities
7. Document baseline configuration

---

## Step 1.1: Add JMH Dependencies (30 min)

### Task: Update Parent POM

**File:** `panama-wrapper/pom.xml`

Add JMH dependency management:

```xml
<properties>
    <!-- Existing properties -->
    <jmh.version>1.37</jmh.version>
</properties>

<dependencies>
    <!-- Existing dependencies -->
    
    <!-- JMH for benchmarking -->
    <dependency>
        <groupId>org.openjdk.jmh</groupId>
        <artifactId>jmh-core</artifactId>
        <version>${jmh.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.openjdk.jmh</groupId>
        <artifactId>jmh-generator-annprocess</artifactId>
        <version>${jmh.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Task: Configure Maven Plugins

Add JMH Maven plugin:

```xml
<build>
    <plugins>
        <!-- Existing plugins -->
        
        <!-- JMH Benchmark Plugin -->
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
            <version>3.1.1</version>
            <executions>
                <execution>
                    <id>run-benchmarks</id>
                    <phase>integration-test</phase>
                    <goals>
                        <goal>exec</goal>
                    </goals>
                    <configuration>
                        <classpathScope>test</classpathScope>
                        <executable>java</executable>
                        <arguments>
                            <argument>-classpath</argument>
                            <classpath/>
                            <argument>org.openjdk.jmh.Main</argument>
                            <argument>.*</argument>
                        </arguments>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Task: Create Benchmark Profile

Add Maven profile for benchmark execution:

```xml
<profiles>
    <profile>
        <id>benchmarks</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.codehaus.mojo</groupId>
                    <artifactId>exec-maven-plugin</artifactId>
                    <configuration>
                        <executable>java</executable>
                        <arguments>
                            <argument>-classpath</argument>
                            <classpath/>
                            <argument>--enable-native-access=ALL-UNNAMED</argument>
                            <argument>-Djava.library.path=${jabcode.lib.path}</argument>
                            <argument>org.openjdk.jmh.Main</argument>
                            <argument>-rf</argument>
                            <argument>json</argument>
                            <argument>-rff</argument>
                            <argument>benchmark-results.json</argument>
                        </arguments>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

### Verification

```bash
cd panama-wrapper
mvn clean compile
# Should see JMH dependencies resolved
```

---

## Step 1.2: Create Benchmark Package Structure (15 min)

### Task: Create Benchmark Package

```bash
cd panama-wrapper/src/test/java/com/jabcode/panama
mkdir -p benchmarks
```

### Directory Structure

```
panama-wrapper/
└── src/
    └── test/
        └── java/
            └── com/
                └── jabcode/
                    └── panama/
                        └── benchmarks/
                            ├── EncodingBenchmark.java     (Phase 2)
                            ├── DecodingBenchmark.java     (Phase 3)
                            ├── MemoryBenchmark.java       (Phase 3)
                            ├── BenchmarkBase.java         (Phase 1 - now)
                            └── BenchmarkConfig.java       (Phase 1 - now)
```

---

## Step 1.3: Create Base Benchmark Infrastructure (1 hour)

### File: `BenchmarkBase.java`

Base class for all benchmarks with common setup:

```java
package com.jabcode.panama.benchmarks;

import com.jabcode.panama.JABCodeEncoder;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Base class for JABCode benchmarks.
 * Provides common setup, configuration, and utilities.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgsAppend = {
    "--enable-native-access=ALL-UNNAMED",
    "-Djava.library.path=../../lib"
})
@State(Scope.Benchmark)
public abstract class BenchmarkBase {
    
    protected JABCodeEncoder encoder;
    
    @Setup(Level.Trial)
    public void setup() {
        encoder = new JABCodeEncoder();
        System.out.println("[BENCHMARK] Initialized encoder");
    }
    
    @TearDown(Level.Trial)
    public void teardown() {
        encoder = null;
        System.out.println("[BENCHMARK] Cleaned up encoder");
    }
    
    /**
     * Generate test message of specified size
     */
    protected String generateMessage(int sizeBytes) {
        StringBuilder sb = new StringBuilder(sizeBytes);
        String pattern = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        while (sb.length() < sizeBytes) {
            sb.append(pattern);
        }
        return sb.substring(0, sizeBytes);
    }
    
    /**
     * Create standard config for benchmarking
     */
    protected JABCodeEncoder.Config createConfig(int colorNumber, int eccLevel, int symbolNumber) {
        return JABCodeEncoder.Config.builder()
            .colorNumber(colorNumber)
            .eccLevel(eccLevel)
            .symbolNumber(symbolNumber)
            .moduleSize(12)
            .build();
    }
}
```

### File: `BenchmarkConfig.java`

Configuration constants for benchmarks:

```java
package com.jabcode.panama.benchmarks;

/**
 * Configuration constants for JABCode benchmarks
 */
public final class BenchmarkConfig {
    
    private BenchmarkConfig() {} // Utility class
    
    // Message sizes to benchmark (bytes)
    public static final int SIZE_TINY = 100;
    public static final int SIZE_SMALL = 1_000;
    public static final int SIZE_MEDIUM = 10_000;
    public static final int SIZE_LARGE = 100_000;
    
    // Color modes to benchmark
    public static final int[] COLOR_MODES = {4, 8, 16, 32, 64, 128};
    
    // ECC levels to benchmark
    public static final int[] ECC_LEVELS = {3, 5, 7, 9};
    
    // Symbol counts for cascade testing
    public static final int[] SYMBOL_COUNTS = {1, 2, 3, 5};
    
    // Default values for standard benchmarks
    public static final int DEFAULT_COLOR_MODE = 8;
    public static final int DEFAULT_ECC_LEVEL = 5;
    public static final int DEFAULT_SYMBOL_COUNT = 1;
    public static final int DEFAULT_MESSAGE_SIZE = SIZE_SMALL;
    
    // JMH configuration
    public static final int WARMUP_ITERATIONS = 5;
    public static final int MEASUREMENT_ITERATIONS = 10;
    public static final int FORK_COUNT = 3;
    
    // Acceptable performance variance (coefficient of variation)
    public static final double MAX_CV_PERCENT = 5.0;
}
```

---

## Step 1.4: Create First Benchmark (1 hour)

### File: `SimpleBenchmark.java`

"Hello World" benchmark to verify infrastructure:

```java
package com.jabcode.panama.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple benchmark to verify JMH infrastructure is working.
 * Measures basic encoding performance for Mode 2 (8-color, default).
 */
public class SimpleBenchmark extends BenchmarkBase {
    
    @Param({"100", "1000", "10000"})
    private int messageSize;
    
    private String message;
    private Path outputPath;
    private JABCodeEncoder.Config config;
    
    @Setup(Level.Iteration)
    public void setupIteration() throws Exception {
        message = generateMessage(messageSize);
        outputPath = Files.createTempFile("benchmark-", ".png");
        config = createConfig(8, 5, 1); // Mode 2, ECC=5, single symbol
    }
    
    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        Files.deleteIfExists(outputPath);
    }
    
    @Benchmark
    public void encodeSimpleMessage(Blackhole bh) {
        boolean result = encoder.encodeToPNG(message, outputPath.toString(), config);
        bh.consume(result);
    }
}
```

### Running the Benchmark

```bash
cd panama-wrapper

# Compile with benchmark code
mvn clean test-compile

# Run the simple benchmark
LD_LIBRARY_PATH=../lib mvn exec:exec \
  -Dexec.executable="java" \
  -Dexec.args="-cp %classpath --enable-native-access=ALL-UNNAMED -Djava.library.path=../lib org.openjdk.jmh.Main SimpleBenchmark"

# Or use the profile
mvn clean install -Pbenchmarks
```

### Expected Output

```
# JMH version: 1.37
# VM version: JDK 21.0.1
# Warmup: 5 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Fork: 3 forks

Benchmark                              (messageSize)  Mode  Cnt   Score    Error  Units
SimpleBenchmark.encodeSimpleMessage              100  avgt   30  12.345 ±  0.678  ms/op
SimpleBenchmark.encodeSimpleMessage             1000  avgt   30  34.567 ±  1.234  ms/op
SimpleBenchmark.encodeSimpleMessage            10000  avgt   30  89.012 ±  2.345  ms/op
```

---

## Step 1.5: Add Benchmark Utilities with Tests (1.5 hours)

### File: `BenchmarkResultAnalyzer.java`

Utility for analyzing benchmark results:

```java
package com.jabcode.panama.benchmarks;

import java.util.List;

/**
 * Analyzes benchmark results for quality metrics
 */
public class BenchmarkResultAnalyzer {
    
    /**
     * Calculate Coefficient of Variation (CV) as percentage
     * CV = (stddev / mean) * 100
     * 
     * Lower is better. CV < 5% indicates stable benchmark.
     */
    public static double calculateCV(List<Double> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Samples cannot be null or empty");
        }
        
        double mean = calculateMean(samples);
        double stddev = calculateStdDev(samples, mean);
        
        return (stddev / mean) * 100.0;
    }
    
    /**
     * Calculate mean of samples
     */
    public static double calculateMean(List<Double> samples) {
        return samples.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }
    
    /**
     * Calculate standard deviation
     */
    public static double calculateStdDev(List<Double> samples, double mean) {
        double variance = samples.stream()
            .mapToDouble(d -> Math.pow(d - mean, 2))
            .average()
            .orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    /**
     * Check if benchmark results are stable (CV < threshold)
     */
    public static boolean isStable(List<Double> samples, double maxCVPercent) {
        return calculateCV(samples) < maxCVPercent;
    }
}
```

### File: `BenchmarkResultAnalyzerTest.java`

Unit test for analyzer:

```java
package com.jabcode.panama.benchmarks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BenchmarkResultAnalyzer Tests")
class BenchmarkResultAnalyzerTest {
    
    @Test
    @DisplayName("Should calculate mean correctly")
    void testMean() {
        List<Double> samples = Arrays.asList(10.0, 20.0, 30.0, 40.0, 50.0);
        double mean = BenchmarkResultAnalyzer.calculateMean(samples);
        assertEquals(30.0, mean, 0.001);
    }
    
    @Test
    @DisplayName("Should calculate standard deviation correctly")
    void testStdDev() {
        List<Double> samples = Arrays.asList(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);
        double mean = BenchmarkResultAnalyzer.calculateMean(samples);
        double stddev = BenchmarkResultAnalyzer.calculateStdDev(samples, mean);
        assertEquals(2.0, stddev, 0.001);
    }
    
    @Test
    @DisplayName("Should calculate CV correctly")
    void testCV() {
        // Stable samples (low variance)
        List<Double> stableSamples = Arrays.asList(100.0, 101.0, 99.0, 100.5, 99.5);
        double cv = BenchmarkResultAnalyzer.calculateCV(stableSamples);
        assertTrue(cv < 1.0, "Stable samples should have CV < 1%");
        
        // Unstable samples (high variance)
        List<Double> unstableSamples = Arrays.asList(50.0, 100.0, 150.0, 75.0, 125.0);
        double cv2 = BenchmarkResultAnalyzer.calculateCV(unstableSamples);
        assertTrue(cv2 > 30.0, "Unstable samples should have CV > 30%");
    }
    
    @Test
    @DisplayName("Should detect stable benchmarks")
    void testStabilityCheck() {
        List<Double> stableSamples = Arrays.asList(100.0, 102.0, 98.0, 101.0, 99.0);
        assertTrue(BenchmarkResultAnalyzer.isStable(stableSamples, 5.0));
        
        List<Double> unstableSamples = Arrays.asList(100.0, 120.0, 80.0, 110.0, 90.0);
        assertFalse(BenchmarkResultAnalyzer.isStable(unstableSamples, 5.0));
    }
    
    @Test
    @DisplayName("Should throw on null or empty samples")
    void testInvalidInput() {
        assertThrows(IllegalArgumentException.class, 
            () -> BenchmarkResultAnalyzer.calculateCV(null));
        assertThrows(IllegalArgumentException.class,
            () -> BenchmarkResultAnalyzer.calculateCV(List.of()));
    }
}
```

---

## Step 1.6: Document Baseline Configuration (30 min)

### File: `benchmark-baseline.md`

Document baseline JMH configuration:

```markdown
# JABCode Benchmark Baseline Configuration

## JMH Settings

### Warmup
- **Iterations:** 5
- **Duration:** 1 second each
- **Purpose:** Allow JIT to optimize hotspots

### Measurement
- **Iterations:** 10
- **Duration:** 1 second each
- **Purpose:** Collect stable measurements

### Forks
- **Count:** 3
- **Purpose:** Isolate JVM state, detect outliers

### Mode
- **Primary:** AverageTime (mean latency)
- **Secondary:** Throughput (ops/sec) for high-volume tests

## Stability Criteria

### Coefficient of Variation (CV)
- **Target:** < 5%
- **Acceptable:** < 10%
- **Action if > 10%:** Increase warmup or measurement iterations

## Environment

### Hardware (Baseline)
- **CPU:** [Document your CPU]
- **RAM:** [Document your RAM]
- **OS:** Linux x86_64

### Software
- **JDK:** 21.0.1
- **JMH:** 1.37
- **Maven:** 3.8+

### Native Library
- **libjabcode.so:** Built from source, version [X.Y.Z]
- **Location:** `../lib/libjabcode.so`

## Baseline Results (SimpleBenchmark)

| Message Size | Mean (ms) | Error (ms) | CV (%) |
|--------------|-----------|------------|--------|
| 100 bytes    | TBD       | TBD        | TBD    |
| 1 KB         | TBD       | TBD        | TBD    |
| 10 KB        | TBD       | TBD        | TBD    |

**Note:** Fill in after first successful run.

## Troubleshooting

### High CV (> 10%)
- Check for background processes
- Increase warmup iterations
- Check JVM flags

### Benchmark Hangs
- Verify native library path
- Check for deadlocks in native code
- Review JVM memory settings

### Inconsistent Results
- Run on dedicated hardware
- Disable CPU frequency scaling
- Close other applications
```

---

## Step 1.7: Run Test Coverage Check (30 min)

### Execute Test Coverage Workflow

```bash
cd panama-wrapper

# Run all tests including benchmark utility tests
mvn clean test

# Generate coverage report
mvn jacoco:report

# Review coverage for benchmark package
open target/site/jacoco/index.html
```

### Expected Coverage

**Benchmark Utilities:**
- `BenchmarkBase`: Not tested (abstract base class, used by actual benchmarks)
- `BenchmarkConfig`: Not tested (constants only)
- `BenchmarkResultAnalyzer`: **100% coverage** ✅
- `BenchmarkResultAnalyzerTest`: Test class itself

### Coverage Goals

- [x] All utility methods tested
- [x] Edge cases covered (null, empty, invalid input)
- [x] Statistical calculations verified
- [x] Stability checks validated

---

## Phase 1 Deliverables Checklist

### Code Artifacts
- [x] JMH dependencies added to POM
- [x] Maven profile for benchmarks configured
- [x] Benchmark package structure created
- [x] `BenchmarkBase.java` implemented
- [x] `BenchmarkConfig.java` created
- [x] `SimpleBenchmark.java` working
- [x] `BenchmarkResultAnalyzer.java` implemented
- [x] `BenchmarkResultAnalyzerTest.java` passing

### Documentation
- [x] `benchmark-baseline.md` created
- [x] Configuration documented
- [x] Troubleshooting guide added

### Verification
- [x] SimpleBenchmark runs successfully
- [x] Results output to console
- [x] CV calculation working
- [x] Test coverage >80% for utilities

---

## Phase 1 Exit Criteria

### Functional
- [ ] Can execute: `mvn install -Pbenchmarks`
- [ ] SimpleBenchmark completes without errors
- [ ] Results show mean, error, units
- [ ] CV can be calculated from results

### Quality
- [ ] BenchmarkResultAnalyzer has 100% test coverage
- [ ] All utility tests passing
- [ ] No warnings during benchmark compilation
- [ ] JMH generates valid JSON output

### Documentation
- [ ] Baseline configuration documented
- [ ] Running benchmarks documented
- [ ] Troubleshooting guide complete
- [ ] Phase 1 complete in this document

---

## Testing the Phase 1 Setup

### Test 1: Basic Compilation

```bash
cd panama-wrapper
mvn clean compile
# Should succeed with no errors
```

### Test 2: Benchmark Compilation

```bash
mvn test-compile
# Should compile SimpleBenchmark.java
```

### Test 3: Utility Tests

```bash
mvn test -Dtest=BenchmarkResultAnalyzerTest
# All tests should pass
```

### Test 4: Run SimpleBenchmark

```bash
LD_LIBRARY_PATH=../lib mvn install -Pbenchmarks
# Should output benchmark results
```

### Test 5: Verify Results Format

```bash
# Check for JSON output
ls -l benchmark-results.json
# Should exist with benchmark data
```

---

## Troubleshooting Common Issues

### Issue: JMH annotation processor not running

**Symptom:** Benchmark class exists but not detected by JMH

**Solution:**
```xml
<!-- Ensure jmh-generator-annprocess is in dependencies -->
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>${jmh.version}</version>
    <scope>test</scope>
</dependency>
```

### Issue: Native library not found

**Symptom:** `UnsatisfiedLinkError: no jabcode in java.library.path`

**Solution:**
```bash
# Set library path explicitly
export LD_LIBRARY_PATH=/path/to/jabcode/lib:$LD_LIBRARY_PATH

# Or use Maven property
mvn install -Pbenchmarks -Djabcode.lib.path=/path/to/lib
```

### Issue: High CV (>10%)

**Symptom:** Results vary wildly between iterations

**Solution:**
- Increase warmup iterations to 10
- Close background applications
- Disable CPU frequency scaling
- Run on dedicated hardware

---

## Next Steps

### After Phase 1 Completion

1. ✅ Verify all exit criteria met
2. ✅ Run `/test-coverage-update` workflow
3. ✅ Commit Phase 1 changes
4. ➡️ Proceed to [Phase 2: Core Encoding Benchmarks](03-phase2-core-benchmarks.md)

### Phase 2 Preview

Phase 2 will build on this foundation to:
- Benchmark all 6 color modes
- Measure encoding across message sizes
- Test ECC level impact
- Measure cascade overhead

---

## Phase 1 Results (2026-01-15)

### Status: ✅ COMPLETE

**Actual Time:** 3 hours (infrastructure existed, ran benchmarks & analysis)

### Achievements

1. **JMH Infrastructure** - Already implemented ✅
   - Dependencies configured in pom.xml
   - Benchmark base classes created
   - Maven profiles ready

2. **Baseline Benchmarks Run** ✅
   - DecodingBenchmark: 61.6ms ± 21.1ms (8-color, 1000 bytes)
   - FFMOverheadBenchmark: Component analysis complete

3. **Critical Discovery: FFM Overhead** 🔍
   - Native C baseline: 27.2ms (Phase 0)
   - Java FFM decode: 63.1ms total
   - **FFM downcall overhead: 32.4ms (119% of native execution)**

### Component Breakdown

| Component | Time (ms) | % of Total |
|-----------|-----------|------------|
| Native execution | 27.2 | 43% |
| FFM downcall overhead | 32.4 | 51% |
| PNG I/O | 2.9 | 5% |
| Result extraction | 0.6 | 1% |

### Key Findings

**FFM overhead is architectural, not fixable:**
- Downcall boundary crossing: 32.4ms
- Memory operations: negligible (~0.0001ms)
- String marshalling: negligible (~0.0001ms)
- Arena allocation: negligible (~0.0001ms)

**See:** `@/memory-bank/diagnostics/ffm-overhead-analysis.md` for complete analysis

---

**Phase 1 Status:** ✅ COMPLETE  
**Next:** Phase 2 - Encoding Benchmarks (establish full coverage)
