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
    "-Djava.library.path=../lib"
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
