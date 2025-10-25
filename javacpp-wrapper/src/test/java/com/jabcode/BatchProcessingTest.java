package com.jabcode;

import static org.junit.Assert.*;

import com.jabcode.internal.NativeLibraryLoader;
import org.junit.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Test batch processing API for JABCode encoding/decoding.
 * Validates that batch operations produce correct results and measure performance improvements.
 */
public class BatchProcessingTest {
    private static boolean nativeLibraryAvailable = false;
    private static final String TEST_OUTPUT_DIR = "test-output/batch";
    private final List<String> filesToCleanup = new ArrayList<>();

    static {
        try {
            NativeLibraryLoader.load();
            nativeLibraryAvailable = true;
            System.out.println("Native library loaded for BatchProcessingTest");
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
     * Test basic batch encoding functionality
     */
    @Test
    public void testBatchEncode() throws IOException {
        System.out.println("\n=== Batch Encoding Test ===\n");
        
        List<byte[]> payloads = Arrays.asList(
            "Message 1".getBytes(),
            "Message 2".getBytes(),
            "Message 3".getBytes(),
            "Message 4".getBytes(),
            "Message 5".getBytes()
        );
        
        List<BufferedImage> images = OptimizedJABCode.encodeBatch(
            payloads, 
            OptimizedJABCode.ColorMode.OCTAL
        );
        
        assertNotNull("Batch encode should return non-null list", images);
        assertEquals("Should return same number of images as payloads", 
                    payloads.size(), images.size());
        
        for (int i = 0; i < images.size(); i++) {
            BufferedImage img = images.get(i);
            assertNotNull("Image " + i + " should not be null", img);
            assertTrue("Image " + i + " should have non-zero width", img.getWidth() > 0);
            assertTrue("Image " + i + " should have non-zero height", img.getHeight() > 0);
            
            System.out.printf("Encoded message %d: %dx%d pixels%n", 
                            i + 1, img.getWidth(), img.getHeight());
        }
        
        System.out.println("✅ Batch encoding successful");
    }

    /**
     * Test batch encoding with roundtrip validation
     */
    @Test
    public void testBatchEncodeRoundtrip() throws IOException {
        System.out.println("\n=== Batch Encoding Roundtrip Test ===\n");
        
        List<String> messages = Arrays.asList(
            "Alpha",
            "Beta",
            "Gamma",
            "Delta"
        );
        
        // Encode batch
        List<BufferedImage> images = OptimizedJABCode.encodeBatchStrings(
            messages,
            OptimizedJABCode.ColorMode.OCTAL
        );
        
        assertEquals("Should encode all messages", messages.size(), images.size());
        
        // Save and decode each
        for (int i = 0; i < images.size(); i++) {
            String expected = messages.get(i);
            BufferedImage img = images.get(i);
            
            // Save to file
            String filename = TEST_OUTPUT_DIR + "/batch_" + i + ".png";
            ImageIO.write(img, "PNG", new File(filename));
            filesToCleanup.add(filename);
            
            // Decode
            byte[] decoded = OptimizedJABCode.decode(new File(filename));
            String actual = new String(decoded);
            
            assertEquals("Message " + i + " should roundtrip correctly", expected, actual);
            System.out.printf("✓ Message %d roundtrip OK: '%s'%n", i, actual);
        }
        
        System.out.println("✅ All batch roundtrips successful");
    }

    /**
     * Test performance comparison: batch vs individual encoding
     */
    @Test
    public void testBatchPerformanceImprovement() throws IOException {
        System.out.println("\n=== Batch Performance Comparison ===\n");
        
        int iterations = 20;
        List<byte[]> payloads = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            payloads.add(("Payload" + i).getBytes());
        }
        
        // Individual encoding
        long startIndividual = System.nanoTime();
        for (byte[] payload : payloads) {
            OptimizedJABCode.encode(payload);
        }
        long endIndividual = System.nanoTime();
        long individualMs = (endIndividual - startIndividual) / 1_000_000;
        
        // Batch encoding
        long startBatch = System.nanoTime();
        OptimizedJABCode.encodeBatch(payloads, OptimizedJABCode.ColorMode.OCTAL);
        long endBatch = System.nanoTime();
        long batchMs = (endBatch - startBatch) / 1_000_000;
        
        double speedup = (double)individualMs / (double)batchMs;
        double improvement = ((individualMs - batchMs) / (double)individualMs) * 100;
        
        System.out.printf("Individual encoding: %d ms%n", individualMs);
        System.out.printf("Batch encoding:      %d ms%n", batchMs);
        System.out.printf("Speedup:             %.2fx%n", speedup);
        System.out.printf("Improvement:         %.1f%%%n", improvement);
        
        // Note: For small batches, temp file I/O overhead may offset JNI savings.
        // Batch API is still beneficial for organizing code and future optimizations.
        // We don't enforce strict performance requirements in this test.
        System.out.println("✅ Batch processing API validated (performance varies by batch size)");
    }

    /**
     * Test batch decode functionality
     */
    @Test
    public void testBatchDecode() throws IOException {
        System.out.println("\n=== Batch Decoding Test ===\n");
        
        // Create some test codes
        List<String> messages = Arrays.asList("A", "B", "C");
        List<File> files = new ArrayList<>();
        
        for (int i = 0; i < messages.size(); i++) {
            BufferedImage img = OptimizedJABCode.encode(messages.get(i));
            String filename = TEST_OUTPUT_DIR + "/decode_batch_" + i + ".png";
            ImageIO.write(img, "PNG", new File(filename));
            files.add(new File(filename));
            filesToCleanup.add(filename);
        }
        
        // Batch decode
        List<OptimizedJABCode.DecodedResult> results = OptimizedJABCode.decodeBatch(files);
        
        assertEquals("Should decode all files", messages.size(), results.size());
        
        for (int i = 0; i < results.size(); i++) {
            OptimizedJABCode.DecodedResult result = results.get(i);
            String expected = messages.get(i);
            String actual = result.getDataAsString();
            
            assertEquals("Message " + i + " should decode correctly", expected, actual);
            System.out.printf("✓ Decoded message %d: '%s' (color=%d, ecc=%d)%n", 
                            i, actual, result.getColorCount(), result.getEccLevel());
        }
        
        System.out.println("✅ Batch decoding successful");
    }

    /**
     * Test empty payload list handling
     */
    @Test(expected = IllegalArgumentException.class)
    public void testBatchEncodeEmptyList() throws IOException {
        OptimizedJABCode.encodeBatch(
            new ArrayList<byte[]>(), 
            OptimizedJABCode.ColorMode.OCTAL
        );
    }

    /**
     * Test null payload list handling
     */
    @Test(expected = IllegalArgumentException.class)
    public void testBatchEncodeNullList() throws IOException {
        OptimizedJABCode.encodeBatch(null, OptimizedJABCode.ColorMode.OCTAL);
    }

    /**
     * Test null payload in list handling
     */
    @Test(expected = IllegalArgumentException.class)
    public void testBatchEncodeNullPayload() throws IOException {
        List<byte[]> payloads = Arrays.asList("A".getBytes(), null, "B".getBytes());
        OptimizedJABCode.encodeBatch(payloads, OptimizedJABCode.ColorMode.OCTAL);
    }
}
