package com.jabcode.panama.benchmarks;

import com.jabcode.panama.JABCodeEncoder;
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
