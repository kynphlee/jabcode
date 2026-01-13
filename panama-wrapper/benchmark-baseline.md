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
- **CPU:** [To be documented on first run]
- **RAM:** [To be documented on first run]
- **OS:** Linux x86_64

### Software
- **JDK:** 21+
- **JMH:** 1.37
- **Maven:** 3.8+

### Native Library
- **libjabcode.so:** Built from source
- **Location:** `../lib/libjabcode.so`

## Baseline Results (SimpleBenchmark)

| Message Size | Mean (ms) | Error (ms) | CV (%) |
|--------------|-----------|------------|--------|
| 100 bytes    | TBD       | TBD        | TBD    |
| 1 KB         | TBD       | TBD        | TBD    |
| 10 KB        | TBD       | TBD        | TBD    |

**Note:** Results will be populated after first successful benchmark run.

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

## Running Benchmarks

### Quick Test
```bash
cd panama-wrapper
mvn clean test-compile
LD_LIBRARY_PATH=../lib mvn exec:exec \
  -Dexec.executable="java" \
  -Dexec.args="-cp %classpath --enable-native-access=ALL-UNNAMED \
               -Djava.library.path=../lib \
               org.openjdk.jmh.Main SimpleBenchmark"
```

### Full Suite with Profile
```bash
mvn clean install -Pbenchmarks
```

### Specific Benchmark
```bash
LD_LIBRARY_PATH=../lib mvn exec:exec \
  -Dexec.args="-cp %classpath --enable-native-access=ALL-UNNAMED \
               -Djava.library.path=../lib \
               org.openjdk.jmh.Main SimpleBenchmark.encodeSimpleMessage \
               -p messageSize=1000"
```

## Next Steps

After Phase 1 completion:
1. Populate baseline results table
2. Document system specifications
3. Proceed to Phase 2 (core encoding benchmarks)
