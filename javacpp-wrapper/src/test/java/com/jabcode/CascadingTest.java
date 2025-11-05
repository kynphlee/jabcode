package com.jabcode;

import org.junit.Test;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Tests for ISO-IEC-23634 Section 4.5 Symbol Cascading support
 */
public class CascadingTest {
    
    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();
    
    @Test
    public void testCascadeLayout3x3() throws IOException {
        // Create 3×3 grid with primary in center
        String data = "ISO-IEC-23634 Section 4.5 Cascading Test with 3x3 Grid Layout!";
        
        BufferedImage img = OptimizedJABCode.encodeWithCascade(
            data,
            OptimizedJABCode.ColorMode.QUATERNARY,
            OptimizedJABCode.CascadeLayout.GRID_3X3,
            3  // ECC for multi-symbol
        );
        
        assertNotNull("3×3 cascaded image should not be null", img);
        assertTrue("3×3 cascaded image should have positive width", img.getWidth() > 0);
        assertTrue("3×3 cascaded image should have positive height", img.getHeight() > 0);
        
        // Save for visual inspection
        File outputFile = new File(tempDir.getRoot(), "cascade_3x3.png");
        OptimizedJABCode.saveOptimized(img, outputFile);
        assertTrue("Output file should exist", outputFile.exists());
        
        System.out.println("3×3 Grid cascade created: " + outputFile.getAbsolutePath());
        System.out.println("  Size: " + img.getWidth() + "×" + img.getHeight());
        System.out.println("  File: " + outputFile.length() + " bytes");
    }
    
    @Test
    public void testCascadeLayoutPlusShape() throws IOException {
        // Create plus/cross shape with primary in center
        String data = "ISO-IEC-23634 Section 4.5 - Plus Shape Cascade Test";
        
        BufferedImage img = OptimizedJABCode.encodeWithCascade(
            data,
            OptimizedJABCode.ColorMode.OCTAL,
            OptimizedJABCode.CascadeLayout.PLUS_SHAPE,
            3
        );
        
        assertNotNull(img);
        
        File outputFile = new File(tempDir.getRoot(), "cascade_plus.png");
        OptimizedJABCode.saveOptimized(img, outputFile);
        
        System.out.println("Plus shape cascade created: " + outputFile.getAbsolutePath());
        System.out.println("  Size: " + img.getWidth() + "×" + img.getHeight());
    }
    
    @Test
    public void testCascadeLayoutHorizontal() throws IOException {
        // Create horizontal line (left-center-right)
        String data = "Horizontal cascade layout test";
        
        BufferedImage img = OptimizedJABCode.encodeWithCascade(
            data,
            OptimizedJABCode.ColorMode.QUATERNARY,
            OptimizedJABCode.CascadeLayout.HORIZONTAL_3,
            3
        );
        
        assertNotNull(img);
        
        File outputFile = new File(tempDir.getRoot(), "cascade_horizontal.png");
        OptimizedJABCode.saveOptimized(img, outputFile);
        
        System.out.println("Horizontal cascade created: " + outputFile.getAbsolutePath());
    }
    
    @Test
    public void testCascadeLayoutVertical() throws IOException {
        // Create vertical line (top-center-bottom)
        String data = "Vertical cascade layout test";
        
        BufferedImage img = OptimizedJABCode.encodeWithCascade(
            data,
            OptimizedJABCode.ColorMode.QUATERNARY,
            OptimizedJABCode.CascadeLayout.VERTICAL_3,
            3
        );
        
        assertNotNull(img);
        
        File outputFile = new File(tempDir.getRoot(), "cascade_vertical.png");
        OptimizedJABCode.saveOptimized(img, outputFile);
        
        System.out.println("Vertical cascade created: " + outputFile.getAbsolutePath());
    }
    
    @Test
    public void testCascadeLayoutLShape() throws IOException {
        // Create L-shape (primary + top + right)
        String data = "L-shape cascade test";
        
        BufferedImage img = OptimizedJABCode.encodeWithCascade(
            data,
            OptimizedJABCode.ColorMode.OCTAL,
            OptimizedJABCode.CascadeLayout.L_SHAPE,
            3
        );
        
        assertNotNull(img);
        
        File outputFile = new File(tempDir.getRoot(), "cascade_l_shape.png");
        OptimizedJABCode.saveOptimized(img, outputFile);
        
        System.out.println("L-shape cascade created: " + outputFile.getAbsolutePath());
    }
    
    // Disabled: 4x4 layout has disconnected positions (position 13 requires position 5 as host)
    // The ISO-IEC-23634 standard requires all secondary symbols to have a direct or indirect
    // connection to the primary symbol through valid host-slave relationships.
    
    @Test
    public void testCustomCascadeLayout() throws IOException {
        // Custom diamond shape using ISO positions
        int[] diamondPositions = {0, 1, 2, 3, 4, 5, 8};  // Primary + 4 cardinal + 2 diagonal
        
        String data = "Custom diamond cascade layout";
        
        BufferedImage img = OptimizedJABCode.encodeWithCascade(
            data,
            OptimizedJABCode.ColorMode.OCTAL,
            diamondPositions,
            3
        );
        
        assertNotNull(img);
        
        File outputFile = new File(tempDir.getRoot(), "cascade_custom_diamond.png");
        OptimizedJABCode.saveOptimized(img, outputFile);
        
        System.out.println("Custom diamond cascade created: " + outputFile.getAbsolutePath());
    }
    
    @Test
    public void testCascadeValidation() {
        // Test validation of ISO positions
        
        // Valid: has position 0
        OptimizedJABCode.CascadeLayout.validate(new int[]{0, 1, 2});
        
        // Invalid: missing position 0
        assertThrows(IllegalArgumentException.class, () -> {
            OptimizedJABCode.CascadeLayout.validate(new int[]{1, 2, 3});
        });
        
        // Invalid: duplicate position
        assertThrows(IllegalArgumentException.class, () -> {
            OptimizedJABCode.CascadeLayout.validate(new int[]{0, 1, 1});
        });
        
        // Invalid: out of range
        assertThrows(IllegalArgumentException.class, () -> {
            OptimizedJABCode.CascadeLayout.validate(new int[]{0, 1, 61});
        });
        
        // Invalid: null
        assertThrows(IllegalArgumentException.class, () -> {
            OptimizedJABCode.CascadeLayout.validate(null);
        });
        
        // Invalid: empty
        assertThrows(IllegalArgumentException.class, () -> {
            OptimizedJABCode.CascadeLayout.validate(new int[0]);
        });
    }
    
    @Test
    public void testSequentialLayout() {
        // Test sequential layout helper
        int[] seq5 = OptimizedJABCode.CascadeLayout.sequential(5);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, seq5);
        
        int[] seq9 = OptimizedJABCode.CascadeLayout.sequential(9);
        assertEquals(9, seq9.length);
        for (int i = 0; i < 9; i++) {
            assertEquals(i, seq9[i]);
        }
    }
    
    @Test
    public void testCascadeRoundtrip() throws IOException {
        // Test encode + decode roundtrip with 3×3 cascade
        String original = "JABCode cascading roundtrip test!";
        
        // Encode with 3×3 grid
        BufferedImage img = OptimizedJABCode.encodeWithCascade(
            original,
            OptimizedJABCode.ColorMode.QUATERNARY,
            OptimizedJABCode.CascadeLayout.GRID_3X3,
            5
        );
        
        // Save and reload (simulates real-world usage)
        File tempFile = new File(tempDir.getRoot(), "cascade_roundtrip.png");
        OptimizedJABCode.saveOptimized(img, tempFile);
        
        // Decode
        byte[] decoded = OptimizedJABCode.decode(tempFile);
        String result = new String(decoded);
        
        assertEquals("Decoded data should match original", original, result);
        
        System.out.println("Cascade roundtrip successful!");
        System.out.println("  Original: " + original);
        System.out.println("  Decoded:  " + result);
    }
}
