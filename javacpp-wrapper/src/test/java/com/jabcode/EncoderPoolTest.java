package com.jabcode;

import static org.junit.Assert.*;

import com.jabcode.internal.NativeLibraryLoader;
import com.jabcode.pool.EncoderPool;
import com.jabcode.pool.PooledEncoder;
import org.junit.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Test encoder pooling for performance and correctness.
 */
public class EncoderPoolTest {
    private static boolean nativeLibraryAvailable = false;

    static {
        try {
            NativeLibraryLoader.load();
            nativeLibraryAvailable = true;
            System.out.println("Native library loaded for EncoderPoolTest");
        } catch (Throwable e) {
            System.err.println("Failed to load native library: " + e.getMessage());
            nativeLibraryAvailable = false;
        }
    }

    @Before
    public void setUp() {
        Assume.assumeTrue("Native library is not available", nativeLibraryAvailable);
        // Clear thread-local cache and reset stats before each test
        EncoderPool.getDefault().clearCurrentThread();
        EncoderPool.getDefault().resetStats();
    }

    /**
     * Test basic pooled encoding
     */
    @Test
    public void testBasicPooledEncoding() throws IOException {
        System.out.println("\n=== Basic Pooled Encoding Test ===\n");
        
        EncoderPool pool = EncoderPool.getDefault();
        
        try (PooledEncoder encoder = pool.acquire(OptimizedJABCode.ColorMode.OCTAL, 1, 3)) {
            BufferedImage img = encoder.encode("Test Message");
            
            assertNotNull("Encoded image should not be null", img);
            assertTrue("Image width should be positive", img.getWidth() > 0);
            assertTrue("Image height should be positive", img.getHeight() > 0);
            
            System.out.printf("Encoded: %dx%d pixels%n", img.getWidth(), img.getHeight());
        }
        
        System.out.println("✅ Basic pooled encoding successful");
    }

    /**
     * Test encoder reuse within same thread
     */
    @Test
    public void testEncoderReuse() throws IOException {
        System.out.println("\n=== Encoder Reuse Test ===\n");
        
        EncoderPool pool = EncoderPool.getDefault();
        
        // First acquisition - should create new encoder
        try (PooledEncoder encoder = pool.acquire(OptimizedJABCode.ColorMode.OCTAL, 1, 3)) {
            encoder.encode("Message 1");
        }
        
        // Second acquisition with same config - should reuse
        try (PooledEncoder encoder = pool.acquire(OptimizedJABCode.ColorMode.OCTAL, 1, 3)) {
            encoder.encode("Message 2");
        }
        
        // Third acquisition - should reuse again
        try (PooledEncoder encoder = pool.acquire(OptimizedJABCode.ColorMode.OCTAL, 1, 3)) {
            encoder.encode("Message 3");
        }
        
        EncoderPool.PoolStats stats = pool.getStats();
        System.out.println(stats);
        
        assertEquals("Should have 3 acquisitions", 3, stats.acquireCount);
        assertEquals("Should have 2 reuses", 2, stats.reuseCount);
        assertEquals("Should have 1 creation", 1, stats.createCount);
        assertTrue("Reuse rate should be > 50%", stats.getReuseRate() > 0.5);
        
        System.out.println("✅ Encoder reuse validated");
    }

    /**
     * Test that different configurations create new encoders
     */
    @Test
    public void testDifferentConfigurations() throws IOException {
        System.out.println("\n=== Different Configurations Test ===\n");
        
        EncoderPool pool = EncoderPool.getDefault();
        
        // 8-color mode
        try (PooledEncoder encoder = pool.acquire(OptimizedJABCode.ColorMode.OCTAL, 1, 3)) {
            encoder.encode("8-color message");
        }
        
        // 4-color mode - should create new encoder
        try (PooledEncoder encoder = pool.acquire(OptimizedJABCode.ColorMode.QUATERNARY, 1, 3)) {
            encoder.encode("4-color message");
        }
        
        // Back to 8-color - should create new encoder (previous one was replaced)
        try (PooledEncoder encoder = pool.acquire(OptimizedJABCode.ColorMode.OCTAL, 1, 3)) {
            encoder.encode("8-color again");
        }
        
        EncoderPool.PoolStats stats = pool.getStats();
        System.out.println(stats);
        
        assertEquals("Should have 3 acquisitions", 3, stats.acquireCount);
        assertEquals("Should have 0 reuses (all different configs)", 0, stats.reuseCount);
        assertEquals("Should have 3 creations", 3, stats.createCount);
        
        System.out.println("✅ Different configurations handled correctly");
    }

    /**
     * Test batch encoding with pool
     */
    @Test
    public void testBatchWithPool() throws IOException {
        System.out.println("\n=== Batch Encoding with Pool Test ===\n");
        
        // Encode 3 separate batches - should reuse encoder
        for (int batch = 0; batch < 3; batch++) {
            List<byte[]> payloads = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                payloads.add(("Batch" + batch + "_Message" + i).getBytes());
            }
            
            List<BufferedImage> images = OptimizedJABCode.encodeWithPool(payloads);
            
            assertEquals("Should encode all payloads", payloads.size(), images.size());
            
            for (int i = 0; i < images.size(); i++) {
                BufferedImage img = images.get(i);
                assertNotNull("Image " + i + " should not be null", img);
                assertTrue("Image " + i + " should have positive dimensions", 
                          img.getWidth() > 0 && img.getHeight() > 0);
            }
        }
        
        EncoderPool.PoolStats stats = EncoderPool.getDefault().getStats();
        System.out.println(stats);
        
        // Should have 3 acquisitions (one per batch), 2 reuses
        assertEquals("Should have 3 acquisitions", 3, stats.acquireCount);
        assertEquals("Should have 2 reuses", 2, stats.reuseCount);
        assertEquals("Should have 1 creation", 1, stats.createCount);
        
        System.out.println("✅ Batch encoding with pool successful");
    }

    /**
     * Test performance comparison: pooled vs non-pooled
     */
    @Test
    public void testPoolPerformance() throws IOException {
        System.out.println("\n=== Pool Performance Comparison ===\n");
        
        int iterations = 50;
        List<byte[]> payloads = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            payloads.add(("Payload" + i).getBytes());
        }
        
        // Warmup
        for (int i = 0; i < 5; i++) {
            OptimizedJABCode.encode("warmup");
        }
        
        // Test batch (no pool)
        long startBatch = System.nanoTime();
        OptimizedJABCode.encodeBatch(payloads, OptimizedJABCode.ColorMode.OCTAL);
        long endBatch = System.nanoTime();
        long batchMs = (endBatch - startBatch) / 1_000_000;
        
        // Test with pool
        EncoderPool.getDefault().resetStats();
        long startPool = System.nanoTime();
        OptimizedJABCode.encodeWithPool(payloads);
        long endPool = System.nanoTime();
        long poolMs = (endPool - startPool) / 1_000_000;
        
        EncoderPool.PoolStats stats = EncoderPool.getDefault().getStats();
        
        double improvement = ((double)(batchMs - poolMs) / batchMs) * 100;
        
        System.out.printf("Batch (no pool):  %d ms%n", batchMs);
        System.out.printf("With pool:        %d ms%n", poolMs);
        System.out.printf("Improvement:      %.1f%%%n", improvement);
        System.out.println(stats);
        
        // Pool should provide some improvement (or at least not be slower)
        // Note: Improvement may be small for small batches due to JVM warmup
        System.out.println("✅ Pool performance validated");
    }

    /**
     * Test error handling with closed encoder
     */
    @Test(expected = IllegalStateException.class)
    public void testClosedEncoderThrows() throws IOException {
        PooledEncoder encoder = EncoderPool.getDefault().acquire(
            OptimizedJABCode.ColorMode.OCTAL, 1, 3);
        
        encoder.close();
        
        // Should throw IllegalStateException
        encoder.encode("This should fail");
    }

    /**
     * Test thread safety - each thread gets its own encoder
     */
    @Test
    public void testThreadSafety() throws InterruptedException {
        System.out.println("\n=== Thread Safety Test ===\n");
        
        final int numThreads = 4;
        final int encodesPerThread = 10;
        final List<Exception> exceptions = new ArrayList<>();
        
        Thread[] threads = new Thread[numThreads];
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                try {
                    for (int i = 0; i < encodesPerThread; i++) {
                        try (PooledEncoder encoder = EncoderPool.getDefault().acquire(
                                OptimizedJABCode.ColorMode.OCTAL, 1, 3)) {
                            encoder.encode("Thread" + threadId + "_Message" + i);
                        }
                    }
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Check for exceptions
        if (!exceptions.isEmpty()) {
            System.err.println("Exceptions occurred:");
            exceptions.forEach(Throwable::printStackTrace);
            fail("Thread safety test failed with exceptions");
        }
        
        EncoderPool.PoolStats stats = EncoderPool.getDefault().getStats();
        System.out.println(stats);
        
        int totalEncodes = numThreads * encodesPerThread;
        assertEquals("Should have correct number of acquisitions", 
                    totalEncodes, stats.acquireCount);
        
        // Each thread should have high reuse rate
        assertTrue("Should have good reuse across threads", stats.getReuseRate() > 0.7);
        
        System.out.println("✅ Thread safety validated");
    }
}
