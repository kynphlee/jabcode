package com.jabcode.panama.benchmarks;

import com.jabcode.panama.JABCodeEncoder;
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
 * 
 * Expected patterns:
 * - Single symbol: baseline
 * - 2 symbols: ~10-15% overhead
 * - 3 symbols: ~20-30% overhead
 * - 5 symbols: ~40-60% overhead
 * 
 * Overhead sources:
 * - Inter-symbol coordination
 * - Multiple LDPC encoding passes
 * - Cascaded metadata management
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
@Warmup(iterations = BenchmarkConfig.WARMUP_ITERATIONS, time = BenchmarkConfig.WARMUP_TIME_SECONDS, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
@Measurement(iterations = BenchmarkConfig.MEASUREMENT_ITERATIONS, time = BenchmarkConfig.MEASUREMENT_TIME_SECONDS, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
@Fork(BenchmarkConfig.FORK_COUNT)
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
                // All same size per spec (12x12 modules)
                versions.add(new SymbolVersion(12, 12));
            }
            builder.symbolVersions(versions);
        }
        
        config = builder.build();
        
        System.out.printf("[BENCH] Mode %d colors, %d symbols%n", colorMode, symbolCount);
    }
    
    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        Files.deleteIfExists(outputPath);
    }
    
    /**
     * Benchmark cascaded multi-symbol encoding.
     * Measures coordination overhead vs single-symbol baseline.
     */
    @Benchmark
    public void encodeCascaded(Blackhole bh) {
        boolean result = encoder.encodeToPNG(message, outputPath.toString(), config);
        bh.consume(result);
        
        if (!result) {
            throw new RuntimeException("Cascaded encoding failed for mode " + colorMode + 
                ", symbols " + symbolCount);
        }
    }
}
