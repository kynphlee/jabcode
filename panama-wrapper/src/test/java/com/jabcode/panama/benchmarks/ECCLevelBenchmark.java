package com.jabcode.panama.benchmarks;

import com.jabcode.panama.JABCodeEncoder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Benchmarks ECC level impact on encoding performance.
 * Tests: ECC levels 3, 5, 7, 9 across representative color modes.
 * 
 * Expected patterns:
 * - ECC 3 (low): baseline
 * - ECC 5 (medium): +5-10%
 * - ECC 7 (high): +10-20%
 * - ECC 9 (very high): +15-30%
 * 
 * Higher ECC increases encoding time due to:
 * - More redundancy modules (larger symbol)
 * - LDPC encoding complexity
 * - Memory allocation overhead
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
@Warmup(iterations = BenchmarkConfig.WARMUP_ITERATIONS, time = BenchmarkConfig.WARMUP_TIME_SECONDS, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
@Measurement(iterations = BenchmarkConfig.MEASUREMENT_ITERATIONS, time = BenchmarkConfig.MEASUREMENT_TIME_SECONDS, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
@Fork(BenchmarkConfig.FORK_COUNT)
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
        
        System.out.printf("[BENCH] Mode %d colors, ECC %d%n", colorMode, eccLevel);
    }
    
    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        Files.deleteIfExists(outputPath);
    }
    
    /**
     * Benchmark encoding with different ECC levels.
     * Measures redundancy encoding overhead.
     */
    @Benchmark
    public void encodeByECCLevel(Blackhole bh) {
        boolean result = encoder.encodeToPNG(message, outputPath.toString(), config);
        bh.consume(result);
        
        if (!result) {
            throw new RuntimeException("Encoding failed for mode " + colorMode + ", ECC " + eccLevel);
        }
    }
}
