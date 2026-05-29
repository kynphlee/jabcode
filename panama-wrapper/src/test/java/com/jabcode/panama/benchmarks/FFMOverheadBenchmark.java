package com.jabcode.panama.benchmarks;

import com.jabcode.panama.bindings.jabcode_h;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Microbenchmarks to isolate FFM overhead components.
 * 
 * Compares total decode time (61.6ms) vs native baseline (27.2ms)
 * to identify where FFM overhead (34.4ms) is spent:
 * 
 * - PNG I/O (readImage call)
 * - Native decode call (decodeJABCode)
 * - Memory segment allocation
 * - String marshalling (UTF-8 conversion)
 * - Struct copying (jab_data extraction)
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 2)
@Fork(1)
public class FFMOverheadBenchmark extends BenchmarkBase {
    
    private Path encodedFile;
    private MemorySegment bitmapPtr;
    private MemorySegment decodedDataPtr;
    private String testMessage;
    
    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        super.setup();
        testMessage = generateMessage(1000);
        
        // Pre-encode a test file
        encodedFile = Files.createTempFile("ffm-bench-", ".png");
        var config = createConfig(8, 5, 1);
        encoder.encodeToPNG(testMessage, encodedFile.toString(), config);
        
        System.out.println("[FFM] Trial setup complete: " + encodedFile);
    }
    
    @Setup(Level.Iteration)
    public void setupIteration() {
        // Pre-load bitmap for decode-only benchmarks
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pathSegment = arena.allocateFrom(encodedFile.toString());
            bitmapPtr = jabcode_h.readImage(pathSegment);
            
            if (bitmapPtr.address() == 0) {
                throw new RuntimeException("Failed to load bitmap");
            }
        }
    }
    
    @TearDown(Level.Iteration)
    public void teardownIteration() {
        // Bitmap cleanup handled by C library
        bitmapPtr = null;
        decodedDataPtr = null;
    }
    
    @TearDown(Level.Trial)
    public void teardownTrial() throws Exception {
        Files.deleteIfExists(encodedFile);
        super.teardown();
    }
    
    /**
     * BASELINE: Full decode path (PNG load + decode)
     * Expected: ~61.6ms (matches DecodingBenchmark)
     */
    @Benchmark
    public void fullDecode(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            // Load image
            MemorySegment pathSegment = arena.allocateFrom(encodedFile.toString());
            MemorySegment bitmap = jabcode_h.readImage(pathSegment);
            
            if (bitmap.address() == 0) {
                throw new RuntimeException("readImage failed");
            }
            
            // Decode
            MemorySegment statusPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment dataPtr = jabcode_h.decodeJABCode(bitmap, 0, statusPtr);
            
            if (dataPtr.address() == 0) {
                throw new RuntimeException("decode failed");
            }
            
            // Extract result
            int length = dataPtr.get(ValueLayout.JAVA_INT, 0);
            byte[] decodedBytes = new byte[length];
            MemorySegment dataSegment = dataPtr.asSlice(4, length);
            MemorySegment.copy(dataSegment, ValueLayout.JAVA_BYTE, 0,
                             decodedBytes, 0, length);
            
            String result = new String(decodedBytes, StandardCharsets.UTF_8);
            bh.consume(result);
        }
    }
    
    /**
     * COMPONENT 1: PNG load only (readImage)
     * Isolates file I/O + PNG decode overhead
     */
    @Benchmark
    public void pngLoadOnly(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pathSegment = arena.allocateFrom(encodedFile.toString());
            MemorySegment bitmap = jabcode_h.readImage(pathSegment);
            bh.consume(bitmap.address());
        }
    }
    
    /**
     * COMPONENT 2: Decode only (skip PNG load)
     * Pre-loaded bitmap, measures pure decode + result extraction
     */
    @Benchmark
    public void decodeOnly(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment statusPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment dataPtr = jabcode_h.decodeJABCode(bitmapPtr, 0, statusPtr);
            
            if (dataPtr.address() == 0) {
                throw new RuntimeException("decode failed");
            }
            
            // Extract result
            int length = dataPtr.get(ValueLayout.JAVA_INT, 0);
            byte[] decodedBytes = new byte[length];
            MemorySegment dataSegment = dataPtr.asSlice(4, length);
            MemorySegment.copy(dataSegment, ValueLayout.JAVA_BYTE, 0,
                             decodedBytes, 0, length);
            
            String result = new String(decodedBytes, StandardCharsets.UTF_8);
            bh.consume(result);
        }
    }
    
    /**
     * COMPONENT 3: Native call only (no result extraction)
     * Measures pure FFM boundary crossing + native execution
     */
    @Benchmark
    public void nativeCallOnly(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment statusPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment dataPtr = jabcode_h.decodeJABCode(bitmapPtr, 0, statusPtr);
            bh.consume(dataPtr.address());
        }
    }
    
    /**
     * COMPONENT 4: String marshalling overhead
     * Simulates UTF-8 string creation from native data
     */
    @Benchmark
    public void stringMarshallingOverhead(Blackhole bh) {
        // Simulate 1000-byte string marshalling
        byte[] data = testMessage.getBytes(StandardCharsets.UTF_8);
        String result = new String(data, StandardCharsets.UTF_8);
        bh.consume(result);
    }
    
    /**
     * COMPONENT 5: Memory segment operations
     * Allocate path string, allocate status int
     */
    @Benchmark
    public void memorySegmentAllocation(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pathSegment = arena.allocateFrom(encodedFile.toString());
            MemorySegment statusPtr = arena.allocate(ValueLayout.JAVA_INT);
            bh.consume(pathSegment.address());
            bh.consume(statusPtr.address());
        }
    }
    
    /**
     * COMPONENT 6: Data extraction overhead
     * Copy bytes from native memory to Java array
     */
    @Benchmark
    public void dataExtractionOverhead(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            // Simulate copying 1000 bytes from native memory
            MemorySegment nativeData = arena.allocate(1000);
            byte[] javaData = new byte[1000];
            MemorySegment.copy(nativeData, ValueLayout.JAVA_BYTE, 0,
                             javaData, 0, 1000);
            bh.consume(javaData);
        }
    }
}
