package com.jabcode.panama.benchmarks;

import com.jabcode.panama.JABCodeEncoder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Benchmarks JABCode encoding performance across all color modes.
 * Measures: Mode 1-6 (4, 8, 16, 32, 64, 128 colors)
 * 
 * Test matrix:
 * - Color modes: 4, 8, 16, 32, 64, 128
 * - Message sizes: 100B, 1KB, 10KB, 100KB
 * - Fixed: ECC=5, single symbol, module=12px
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
@Warmup(iterations = BenchmarkConfig.WARMUP_ITERATIONS, time = BenchmarkConfig.WARMUP_TIME_SECONDS, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
@Measurement(iterations = BenchmarkConfig.MEASUREMENT_ITERATIONS, time = BenchmarkConfig.MEASUREMENT_TIME_SECONDS, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
@Fork(BenchmarkConfig.FORK_COUNT)
public class EncodingBenchmark extends BenchmarkBase {
    
    /**
     * Color modes to benchmark (skip Mode 7/256-color due to encoder malloc issue)
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
        
        System.out.printf("[BENCH] Mode %d colors, Size %d bytes%n", colorMode, messageSize);
    }
    
    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        Files.deleteIfExists(outputPath);
    }
    
    /**
     * Benchmark encoding across color modes and message sizes.
     * Expected patterns:
     * - 10-20% overhead per doubling of color count
     * - Linear scaling with message size
     * - 128-color mode now functional after decoder fix
     */
    @Benchmark
    public void encodeByColorMode(Blackhole bh) {
        boolean result = encoder.encodeToPNG(message, outputPath.toString(), config);
        bh.consume(result);
        
        if (!result) {
            throw new RuntimeException("Encoding failed for mode " + colorMode + ", size " + messageSize);
        }
    }
}
