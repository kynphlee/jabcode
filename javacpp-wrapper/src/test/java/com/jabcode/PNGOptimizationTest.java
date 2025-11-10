package com.jabcode;

import static org.junit.Assert.*;

import com.jabcode.internal.NativeLibraryLoader;
import com.jabcode.util.PNGOptimizer;
import org.junit.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Test PNG optimization for file size reduction.
 */
public class PNGOptimizationTest {
    private static boolean nativeLibraryAvailable = false;
    private static File tempDir;

    static {
        try {
            NativeLibraryLoader.load();
            nativeLibraryAvailable = true;
            System.out.println("Native library loaded for PNGOptimizationTest");
        } catch (Throwable e) {
            System.err.println("Failed to load native library: " + e.getMessage());
            nativeLibraryAvailable = false;
        }
    }

    @BeforeClass
    public static void setUpClass() throws IOException {
        tempDir = new File(System.getProperty("java.io.tmpdir"), "jabcode-png-test");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
    }

    @Before
    public void setUp() {
        Assume.assumeTrue("Native library is not available", nativeLibraryAvailable);
    }

    @AfterClass
    public static void tearDownClass() {
        if (tempDir != null && tempDir.exists()) {
            // Clean up test files
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            tempDir.delete();
        }
    }

    /**
     * Test basic indexed color conversion
     */
    @Test
    public void testIndexedColorConversion() {
        System.out.println("\n=== Indexed Color Conversion Test ===\n");
        
        BufferedImage original = OptimizedJABCode.encode("Test message");
        
        assertNotNull("Original image should not be null", original);
        
        System.out.printf("Original image type: %d%n", original.getType());
        
        BufferedImage indexed = OptimizedJABCode.toIndexedColor(original);
        
        assertNotNull("Indexed image should not be null", indexed);
        assertEquals("Indexed should be BYTE_INDEXED", BufferedImage.TYPE_BYTE_INDEXED, indexed.getType());
        assertEquals("Dimensions should match", original.getWidth(), indexed.getWidth());
        assertEquals("Dimensions should match", original.getHeight(), indexed.getHeight());
        
        System.out.printf("Converted %dx%d image (type %d) to indexed color (type %d)%n", 
                         original.getWidth(), original.getHeight(), 
                         original.getType(), indexed.getType());
        System.out.println("✅ Indexed color conversion successful");
    }

    /**
     * Test file size reduction with 8-color mode
     */
    @Test
    public void testFileSizeReduction8Color() throws IOException {
        System.out.println("\n=== File Size Reduction Test (8-color) ===\n");
        
        BufferedImage image = OptimizedJABCode.encode("Testing 8-color optimization");
        
        File standardFile = new File(tempDir, "standard_8color.png");
        File optimizedFile = new File(tempDir, "optimized_8color.png");
        
        // Save with standard method
        OptimizedJABCode.saveToFile(image, standardFile);
        long standardSize = standardFile.length();
        
        // Save with optimized method
        OptimizedJABCode.saveOptimized(image, optimizedFile);
        long optimizedSize = optimizedFile.length();
        
        System.out.printf("Standard PNG:  %,d bytes%n", standardSize);
        System.out.printf("Optimized PNG: %,d bytes%n", optimizedSize);
        
        long saved = standardSize - optimizedSize;
        double savingsPercent = ((double)saved / standardSize) * 100;
        
        System.out.printf("Savings:       %,d bytes (%.1f%% smaller)%n", saved, savingsPercent);
        
        assertTrue("Optimized should be smaller", optimizedSize < standardSize);
        assertTrue("Should save at least 20%", savingsPercent >= 20.0);
        // Note: Actual savings can be 80-90% due to inefficient ARGB PNG encoding
        assertTrue("Should save less than 95%", savingsPercent < 95.0);
        
        System.out.println("✅ File size reduction validated");
    }

    /**
     * Test file size reduction with 4-color mode
     */
    @Test
    public void testFileSizeReduction4Color() throws IOException {
        System.out.println("\n=== File Size Reduction Test (4-color) ===\n");
        
        BufferedImage image = OptimizedJABCode.encode("Testing".getBytes(), 
                                                     OptimizedJABCode.ColorMode.QUATERNARY, 
                                                     1, 3, false);
        
        File standardFile = new File(tempDir, "standard_4color.png");
        File optimizedFile = new File(tempDir, "optimized_4color.png");
        
        OptimizedJABCode.saveToFile(image, standardFile);
        OptimizedJABCode.saveOptimized(image, optimizedFile);
        
        long standardSize = standardFile.length();
        long optimizedSize = optimizedFile.length();
        
        double savingsPercent = ((double)(standardSize - optimizedSize) / standardSize) * 100;
        
        System.out.printf("Standard:  %,d bytes%n", standardSize);
        System.out.printf("Optimized: %,d bytes (%.1f%% smaller)%n", optimizedSize, savingsPercent);
        
        assertTrue("Optimized should be smaller", optimizedSize < standardSize);
        
        System.out.println("✅ 4-color optimization validated");
    }

    /**
     * Test compression statistics analysis
     */
    @Test
    public void testCompressionAnalysis() throws IOException {
        System.out.println("\n=== Compression Analysis Test ===\n");
        
        BufferedImage image = OptimizedJABCode.encode("Compression analysis test");
        
        PNGOptimizer.CompressionStats stats = OptimizedJABCode.analyzeCompression(image);
        
        assertNotNull("Stats should not be null", stats);
        assertTrue("Should have fewer than 256 colors", stats.numColors <= 256);
        assertTrue("ARGB size should be positive", stats.argbBytes > 0);
        assertTrue("Indexed size should be positive", stats.indexedBytes > 0);
        assertTrue("Should save bytes", stats.savedBytes > 0);
        assertTrue("Should have positive savings percent", stats.savingsPercent > 0);
        
        System.out.println(stats);
        
        System.out.println("✅ Compression analysis validated");
    }

    /**
     * Test that visual output is identical (lossless)
     */
    @Test
    public void testLosslessConversion() {
        System.out.println("\n=== Lossless Conversion Test ===\n");
        
        BufferedImage original = OptimizedJABCode.encode("Lossless test");
        BufferedImage indexed = OptimizedJABCode.toIndexedColor(original);
        
        // Compare dimensions
        assertEquals("Width should match", original.getWidth(), indexed.getWidth());
        assertEquals("Height should match", original.getHeight(), indexed.getHeight());
        
        // Compare pixel values (colors might be slightly different due to palette,
        // but for JABCode which uses exact colors, they should match exactly)
        int width = original.getWidth();
        int height = original.getHeight();
        int differentPixels = 0;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int originalRGB = original.getRGB(x, y);
                int indexedRGB = indexed.getRGB(x, y);
                
                if (originalRGB != indexedRGB) {
                    differentPixels++;
                }
            }
        }
        
        double differencePercent = ((double)differentPixels / (width * height)) * 100;
        
        System.out.printf("Image size: %dx%d%n", width, height);
        System.out.printf("Different pixels: %d (%.3f%%)%n", differentPixels, differencePercent);
        
        // For JABCode, conversion should be 100% lossless
        assertEquals("Conversion should be lossless", 0, differentPixels);
        
        System.out.println("✅ Lossless conversion validated (perfect match)");
    }

    /**
     * Test batch optimization
     */
    @Test
    public void testBatchOptimization() throws IOException {
        System.out.println("\n=== Batch Optimization Test ===\n");
        
        int numImages = 5;
        long totalStandardSize = 0;
        long totalOptimizedSize = 0;
        
        for (int i = 0; i < numImages; i++) {
            BufferedImage image = OptimizedJABCode.encode("Batch message " + i);
            
            File standardFile = new File(tempDir, "batch_standard_" + i + ".png");
            File optimizedFile = new File(tempDir, "batch_optimized_" + i + ".png");
            
            OptimizedJABCode.saveToFile(image, standardFile);
            OptimizedJABCode.saveOptimized(image, optimizedFile);
            
            totalStandardSize += standardFile.length();
            totalOptimizedSize += optimizedFile.length();
        }
        
        long totalSaved = totalStandardSize - totalOptimizedSize;
        double savingsPercent = ((double)totalSaved / totalStandardSize) * 100;
        
        System.out.printf("Total standard size:  %,d bytes%n", totalStandardSize);
        System.out.printf("Total optimized size: %,d bytes%n", totalOptimizedSize);
        System.out.printf("Total savings:        %,d bytes (%.1f%%)%n", totalSaved, savingsPercent);
        
        assertTrue("Batch should save space", totalOptimizedSize < totalStandardSize);
        
        System.out.println("✅ Batch optimization validated");
    }

    /**
     * Test with different payload sizes
     */
    @Test
    public void testDifferentPayloadSizes() throws IOException {
        System.out.println("\n=== Different Payload Sizes Test ===\n");
        
        String[] payloads = {
            "Small",
            "Medium sized message for testing",
            "This is a larger message with more content to encode, " +
            "which should result in a larger JABCode symbol with more modules"
        };
        
        for (int i = 0; i < payloads.length; i++) {
            String payload = payloads[i];
            BufferedImage image = OptimizedJABCode.encode(payload);
            
            PNGOptimizer.CompressionStats stats = OptimizedJABCode.analyzeCompression(image);
            
            System.out.printf("Payload %d (%d bytes): %s%n", i + 1, payload.length(), stats);
            
            assertTrue("Should save space", stats.savedBytes > 0);
            assertTrue("Should have reasonable savings", stats.savingsPercent >= 20.0);
        }
        
        System.out.println("✅ Different payload sizes validated");
    }

    /**
     * Test that decoding still works with optimized PNG
     */
    @Test
    public void testDecodeOptimizedPNG() throws IOException {
        System.out.println("\n=== Decode Optimized PNG Test ===\n");
        
        String originalMessage = "Decode test message";
        
        // Encode
        BufferedImage image = OptimizedJABCode.encode(originalMessage);
        
        // Save as optimized PNG
        File optimizedFile = new File(tempDir, "decode_test_optimized.png");
        OptimizedJABCode.saveOptimized(image, optimizedFile);
        
        // Decode from optimized PNG
        String decoded = OptimizedJABCode.decodeToString(optimizedFile);
        
        assertNotNull("Decoded message should not be null", decoded);
        assertEquals("Decoded message should match original", originalMessage, decoded);
        
        System.out.printf("Original:  '%s'%n", originalMessage);
        System.out.printf("Decoded:   '%s'%n", decoded);
        System.out.println("✅ Decode from optimized PNG successful");
    }
}
