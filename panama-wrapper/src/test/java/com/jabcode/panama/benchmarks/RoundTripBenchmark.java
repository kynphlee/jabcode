package com.jabcode.panama.benchmarks;

import com.jabcode.panama.JABCodeDecoder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Benchmarks complete round-trip: encode → decode → verify
 * Simulates real-world usage pattern with full cycle.
 * 
 * Measures total overhead including:
 * - Encoding time
 * - File I/O
 * - Decoding time
 * - Data verification
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
@Warmup(iterations = BenchmarkConfig.WARMUP_ITERATIONS, time = BenchmarkConfig.WARMUP_TIME_SECONDS, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
@Measurement(iterations = BenchmarkConfig.MEASUREMENT_ITERATIONS, time = BenchmarkConfig.MEASUREMENT_TIME_SECONDS, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
@Fork(BenchmarkConfig.FORK_COUNT)
public class RoundTripBenchmark extends BenchmarkBase {
    
    /**
     * Representative color modes for round-trip testing
     */
    @Param({"8", "32", "64", "128"})
    private int colorMode;
    
    /**
     * Standard message size for round-trip (1KB)
     */
    private static final int MESSAGE_SIZE = 1000;
    
    private String originalMessage;
    private Path tempFile;
    private JABCodeDecoder decoder;
    
    @Setup(Level.Trial)
    public void setupTrial() {
        super.setup();
        decoder = new JABCodeDecoder();
        System.out.println("[BENCH] Round-trip decoder initialized");
    }
    
    @Setup(Level.Iteration)
    public void setupIteration() throws Exception {
        originalMessage = generateMessage(MESSAGE_SIZE);
        tempFile = Files.createTempFile("bench-roundtrip-", ".png");
        System.out.printf("[BENCH] Round-trip Mode %d colors%n", colorMode);
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
    
    /**
     * Complete encode → decode → verify cycle.
     * Measures end-to-end latency including all overhead.
     */
    @Benchmark
    public void encodeDecodeVerify(Blackhole bh) {
        var config = createConfig(colorMode, 5, 1);
        
        // Step 1: Encode
        boolean encoded = encoder.encodeToPNG(originalMessage, tempFile.toString(), config);
        bh.consume(encoded);
        
        if (!encoded) {
            throw new RuntimeException("Encoding failed in round-trip for mode " + colorMode);
        }
        
        // Step 2: Decode
        String decoded = decoder.decodeFromFile(tempFile);
        bh.consume(decoded);
        
        if (decoded == null || decoded.isEmpty()) {
            throw new RuntimeException("Decoding failed in round-trip for mode " + colorMode);
        }
        
        // Step 3: Verify
        boolean matches = originalMessage.equals(decoded);
        bh.consume(matches);
        
        if (!matches) {
            throw new RuntimeException("Round-trip verification failed for mode " + colorMode);
        }
    }
}
