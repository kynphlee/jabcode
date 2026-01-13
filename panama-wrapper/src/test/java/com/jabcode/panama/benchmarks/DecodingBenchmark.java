package com.jabcode.panama.benchmarks;

import com.jabcode.panama.JABCodeDecoder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Benchmarks JABCode decoding performance across all color modes.
 * Pre-generates encoded images, then measures pure decode time.
 * 
 * Expected: Decoding 30-50% faster than encoding due to:
 * - No palette generation
 * - No bit packing (only unpacking)
 * - No masking selection (mask already applied)
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
@Warmup(iterations = BenchmarkConfig.WARMUP_ITERATIONS, time = BenchmarkConfig.WARMUP_TIME_SECONDS, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
@Measurement(iterations = BenchmarkConfig.MEASUREMENT_ITERATIONS, time = BenchmarkConfig.MEASUREMENT_TIME_SECONDS, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
@Fork(BenchmarkConfig.FORK_COUNT)
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
        // Pre-encode the message for this iteration
        String message = generateMessage(messageSize);
        encodedFile = Files.createTempFile("bench-decode-", ".png");
        
        var config = createConfig(colorMode, 5, 1);
        boolean encoded = encoder.encodeToPNG(message, encodedFile.toString(), config);
        
        if (!encoded) {
            throw new RuntimeException("Failed to pre-encode message for mode " + colorMode);
        }
        
        System.out.printf("[BENCH] Pre-encoded Mode %d colors, Size %d bytes%n", colorMode, messageSize);
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
    
    /**
     * Benchmark pure decoding performance.
     * Image already generated, measures only decode time.
     */
    @Benchmark
    public void decodeByColorMode(Blackhole bh) {
        String decoded = decoder.decodeFromFile(encodedFile);
        bh.consume(decoded);
        
        if (decoded == null || decoded.isEmpty()) {
            throw new RuntimeException("Decoding failed for mode " + colorMode);
        }
    }
}
