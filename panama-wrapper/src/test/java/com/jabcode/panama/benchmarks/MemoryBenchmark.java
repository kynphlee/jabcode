package com.jabcode.panama.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Profiles memory usage during JABCode encoding operations.
 * Measures heap allocation and identifies memory-intensive operations.
 * 
 * Uses SingleShot mode to capture memory deltas per operation.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 20)
@Fork(1)  // Single fork to reduce variance in memory measurements
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
        System.out.println("[MEMORY] Memory profiling initialized");
    }
    
    @Setup(Level.Iteration)
    public void setupIteration() throws Exception {
        message = generateMessage(messageSize);
        outputPath = Files.createTempFile("bench-memory-", ".png");
        
        // Force GC before measurement for cleaner baseline
        System.gc();
        Thread.sleep(100);
        
        System.out.printf("[MEMORY] Mode %d colors, Size %d bytes%n", colorMode, messageSize);
    }
    
    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        Files.deleteIfExists(outputPath);
    }
    
    /**
     * Measure heap memory delta during encoding.
     * Captures before/after snapshots to calculate allocation.
     */
    @Benchmark
    public void measureMemoryUsage(Blackhole bh) {
        MemoryUsage heapBefore = memoryBean.getHeapMemoryUsage();
        long usedBefore = heapBefore.getUsed();
        
        var config = createConfig(colorMode, 5, 1);
        boolean result = encoder.encodeToPNG(message, outputPath.toString(), config);
        
        MemoryUsage heapAfter = memoryBean.getHeapMemoryUsage();
        long usedAfter = heapAfter.getUsed();
        
        long heapDelta = usedAfter - usedBefore;
        
        bh.consume(result);
        bh.consume(heapDelta);
        
        // Log for analysis (will appear in results)
        System.out.printf("[MEMORY] Mode %d, Size %d: heap delta = %d bytes (%.2f MB)%n",
            colorMode, messageSize, heapDelta, heapDelta / (1024.0 * 1024.0));
    }
}
