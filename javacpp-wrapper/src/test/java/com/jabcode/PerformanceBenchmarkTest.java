package com.jabcode;

import static org.junit.Assert.*;

import com.jabcode.internal.NativeLibraryLoader;
import org.junit.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive performance benchmark test for JABCode library.
 * Measures encoding/decoding speed, image file sizes, and memory usage.
 */
public class PerformanceBenchmarkTest {
    private static boolean nativeLibraryAvailable = false;
    private static final String TEST_OUTPUT_DIR = "test-output/benchmarks";
    private final List<String> filesToCleanup = new ArrayList<>();

    static {
        try {
            NativeLibraryLoader.load();
            nativeLibraryAvailable = true;
            System.out.println("Native library loaded from: " + NativeLibraryLoader.getLoadedLibraryPath());
        } catch (Throwable e) {
            System.err.println("Failed to load native library: " + e.getMessage());
            nativeLibraryAvailable = false;
        }
    }

    @Before
    public void setUp() {
        Assume.assumeTrue("Native library is not available", nativeLibraryAvailable);
        File outDir = new File(TEST_OUTPUT_DIR);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
    }

    @After
    public void tearDown() {
        for (String path : filesToCleanup) {
            try {
                File f = new File(path);
                if (f.exists()) f.delete();
            } catch (Throwable ignore) {}
        }
    }

    /**
     * Comprehensive benchmark comparing 4-color vs 8-color performance
     */
    @Test
    public void testColorModePerformanceComparison() throws IOException {
        System.out.println("\n=== Color Mode Performance Benchmark ===\n");
        
        // Test payloads of different sizes
        String[] payloads = {
            "Hello",                                    // 5 bytes
            "Hello, JABCode!",                         // 15 bytes
            generateString(50),                        // 50 bytes
            generateString(100),                       // 100 bytes
            generateString(500),                       // 500 bytes
            generateString(1000)                       // 1KB
        };
        
        OptimizedJABCode.ColorMode[] modes = {
            OptimizedJABCode.ColorMode.QUATERNARY,     // 4-color
            OptimizedJABCode.ColorMode.OCTAL           // 8-color
        };
        
        System.out.printf("%-12s %-10s %-15s %-15s %-15s %-15s %-20s%n",
            "Payload", "Mode", "Encode(ms)", "Decode(ms)", "Total(ms)", "FileSize(KB)", "Throughput(KB/s)");
        System.out.println("-".repeat(120));
        
        for (String payload : payloads) {
            byte[] data = payload.getBytes();
            
            for (OptimizedJABCode.ColorMode mode : modes) {
                BenchmarkResult result = benchmarkRoundtrip(data, mode);
                
                System.out.printf("%-12s %-10s %-15d %-15d %-15d %-15.2f %-20.2f%n",
                    data.length + "B",
                    mode.getColorCount() + "-color",
                    result.encodeTimeMs,
                    result.decodeTimeMs,
                    result.totalTimeMs,
                    result.fileSizeKB,
                    result.throughputKBps);
                
                filesToCleanup.add(result.filePath);
            }
            System.out.println();
        }
        
        // Verify roundtrips succeeded
        assertTrue("Benchmark completed", true);
    }

    /**
     * Memory usage benchmark
     */
    @Test
    public void testMemoryUsageBenchmark() throws IOException {
        System.out.println("\n=== Memory Usage Benchmark ===\n");
        
        Runtime runtime = Runtime.getRuntime();
        
        OptimizedJABCode.ColorMode[] modes = {
            OptimizedJABCode.ColorMode.QUATERNARY,
            OptimizedJABCode.ColorMode.OCTAL
        };
        
        System.out.printf("%-10s %-15s %-20s %-20s %-20s%n",
            "Mode", "Iterations", "Mem Before(MB)", "Mem After(MB)", "Mem Delta(MB)");
        System.out.println("-".repeat(90));
        
        for (OptimizedJABCode.ColorMode mode : modes) {
            // Force GC before measurement
            System.gc();
            Thread.yield();
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            
            long memBefore = runtime.totalMemory() - runtime.freeMemory();
            
            // Perform repeated encode/decode operations
            int iterations = 20;
            for (int i = 0; i < iterations; i++) {
                String payload = "MemTest" + i;
                String out = TEST_OUTPUT_DIR + "/mem_" + mode.getColorCount() + "_" + i + ".png";
                
                OptimizedJABCode.encodeToFileNative(payload.getBytes(), mode, 1, 2, false, new File(out));
                byte[] decoded = OptimizedJABCode.decode(new File(out));
                
                // Cleanup immediately to avoid disk space issues
                new File(out).delete();
                
                assertEquals("Roundtrip " + i, payload, new String(decoded));
            }
            
            long memAfter = runtime.totalMemory() - runtime.freeMemory();
            double memDeltaMB = (memAfter - memBefore) / (1024.0 * 1024.0);
            
            System.out.printf("%-10s %-15d %-20.2f %-20.2f %-20.2f%n",
                mode.getColorCount() + "-color",
                iterations,
                memBefore / (1024.0 * 1024.0),
                memAfter / (1024.0 * 1024.0),
                memDeltaMB);
        }
        
        System.out.println();
    }

    /**
     * File size analysis across different payload sizes
     */
    @Test
    public void testFileSizeScaling() throws IOException {
        System.out.println("\n=== File Size Scaling Analysis ===\n");
        
        int[] payloadSizes = {10, 25, 50, 100, 250, 500, 1000};
        
        OptimizedJABCode.ColorMode[] modes = {
            OptimizedJABCode.ColorMode.QUATERNARY,
            OptimizedJABCode.ColorMode.OCTAL
        };
        
        System.out.printf("%-15s %-15s %-20s %-20s%n",
            "Payload Size", "Mode", "File Size(KB)", "Overhead Ratio");
        System.out.println("-".repeat(75));
        
        for (int size : payloadSizes) {
            String payload = generateString(size);
            byte[] data = payload.getBytes();
            
            for (OptimizedJABCode.ColorMode mode : modes) {
                String out = TEST_OUTPUT_DIR + "/size_" + mode.getColorCount() + "_" + size + ".png";
                
                OptimizedJABCode.encodeToFileNative(data, mode, 1, 2, false, new File(out));
                
                File file = new File(out);
                double fileSizeKB = file.length() / 1024.0;
                double overhead = fileSizeKB / (size / 1024.0);
                
                System.out.printf("%-15s %-15s %-20.2f %-20.2f%n",
                    size + "B",
                    mode.getColorCount() + "-color",
                    fileSizeKB,
                    overhead);
                
                filesToCleanup.add(out);
            }
        }
        
        System.out.println();
    }

    /**
     * Batch processing performance test
     */
    @Test
    public void testBatchProcessingPerformance() throws IOException {
        System.out.println("\n=== Batch Processing Performance ===\n");
        
        int batchSize = 50;
        String payload = "BatchTest";
        
        OptimizedJABCode.ColorMode[] modes = {
            OptimizedJABCode.ColorMode.QUATERNARY,
            OptimizedJABCode.ColorMode.OCTAL
        };
        
        System.out.printf("%-10s %-15s %-20s %-25s%n",
            "Mode", "Batch Size", "Total Time(ms)", "Avg Time/Code(ms)");
        System.out.println("-".repeat(75));
        
        for (OptimizedJABCode.ColorMode mode : modes) {
            long startTime = System.nanoTime();
            
            for (int i = 0; i < batchSize; i++) {
                String data = payload + i;
                String out = TEST_OUTPUT_DIR + "/batch_" + mode.getColorCount() + "_" + i + ".png";
                
                OptimizedJABCode.encodeToFileNative(data.getBytes(), mode, 1, 2, false, new File(out));
                byte[] decoded = OptimizedJABCode.decode(new File(out));
                
                assertEquals("Batch " + i, data, new String(decoded));
                
                // Cleanup immediately
                new File(out).delete();
            }
            
            long endTime = System.nanoTime();
            long totalMs = (endTime - startTime) / 1_000_000;
            double avgMs = totalMs / (double) batchSize;
            
            System.out.printf("%-10s %-15d %-20d %-25.2f%n",
                mode.getColorCount() + "-color",
                batchSize,
                totalMs,
                avgMs);
        }
        
        System.out.println();
    }

    // Helper methods

    private BenchmarkResult benchmarkRoundtrip(byte[] data, OptimizedJABCode.ColorMode mode) throws IOException {
        String out = TEST_OUTPUT_DIR + "/perf_" + mode.getColorCount() + "_" + data.length + ".png";
        
        // Encode
        long encodeStart = System.nanoTime();
        OptimizedJABCode.encodeToFileNative(data, mode, 1, 2, false, new File(out));
        long encodeEnd = System.nanoTime();
        
        // Decode
        long decodeStart = System.nanoTime();
        byte[] decoded = OptimizedJABCode.decode(new File(out));
        long decodeEnd = System.nanoTime();
        
        // Verify
        assertArrayEquals("Roundtrip data mismatch", data, decoded);
        
        // Metrics
        File file = new File(out);
        double fileSizeKB = file.length() / 1024.0;
        long encodeMs = (encodeEnd - encodeStart) / 1_000_000;
        long decodeMs = (decodeEnd - decodeStart) / 1_000_000;
        long totalMs = encodeMs + decodeMs;
        double throughputKBps = (data.length / 1024.0) / (totalMs / 1000.0);
        
        return new BenchmarkResult(out, encodeMs, decodeMs, totalMs, fileSizeKB, throughputKBps);
    }

    private String generateString(int length) {
        StringBuilder sb = new StringBuilder(length);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(i % chars.length()));
        }
        return sb.toString();
    }

    private static class BenchmarkResult {
        String filePath;
        long encodeTimeMs;
        long decodeTimeMs;
        long totalTimeMs;
        double fileSizeKB;
        double throughputKBps;

        BenchmarkResult(String filePath, long encodeTimeMs, long decodeTimeMs, long totalTimeMs,
                       double fileSizeKB, double throughputKBps) {
            this.filePath = filePath;
            this.encodeTimeMs = encodeTimeMs;
            this.decodeTimeMs = decodeTimeMs;
            this.totalTimeMs = totalTimeMs;
            this.fileSizeKB = fileSizeKB;
            this.throughputKBps = throughputKBps;
        }
    }
}
