package com.jabcode.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.jabcode.OptimizedJABCode.ColorMode;

/**
 * JUnit test class for JABCode color modes
 * 
 * This test verifies that all color modes are properly defined and have the expected values.
 * It does not actually generate or decode JABCodes, as that would require the native library.
 */
public class ColorModeTest {
    
    /**
     * Test that the BINARY color mode has the correct value
     */
    @Test
    public void testBinaryColorMode() {
        assertEquals("BINARY color mode should have value 2", 2, ColorMode.BINARY.getColorCount());
    }
    
    /**
     * Test that the QUATERNARY color mode has the correct value
     */
    @Test
    public void testQuaternaryColorMode() {
        assertEquals("QUATERNARY color mode should have value 4", 4, ColorMode.QUATERNARY.getColorCount());
    }
    
    /**
     * Test that the OCTAL color mode has the correct value
     */
    @Test
    public void testOctalColorMode() {
        assertEquals("OCTAL color mode should have value 8", 8, ColorMode.OCTAL.getColorCount());
    }
    
    /**
     * Test that the HEXADECIMAL color mode has the correct value
     */
    @Test
    public void testHexadecimalColorMode() {
        assertEquals("HEXADECIMAL color mode should have value 16", 16, ColorMode.HEXADECIMAL.getColorCount());
    }
    
    /**
     * Test that the MODE_32 color mode has the correct value
     */
    @Test
    public void test32ColorMode() {
        assertEquals("MODE_32 color mode should have value 32", 32, ColorMode.MODE_32.getColorCount());
    }
    
    /**
     * Test that the MODE_64 color mode has the correct value
     */
    @Test
    public void test64ColorMode() {
        assertEquals("MODE_64 color mode should have value 64", 64, ColorMode.MODE_64.getColorCount());
    }
    
    /**
     * Test that the MODE_128 color mode has the correct value
     */
    @Test
    public void test128ColorMode() {
        assertEquals("MODE_128 color mode should have value 128", 128, ColorMode.MODE_128.getColorCount());
    }
    
    /**
     * Test that the MODE_256 color mode has the correct value
     */
    @Test
    public void test256ColorMode() {
        assertEquals("MODE_256 color mode should have value 256", 256, ColorMode.MODE_256.getColorCount());
    }
    
    /**
     * Test that all color modes have unique values
     */
    @Test
    public void testAllColorModesUnique() {
        ColorMode[] modes = ColorMode.values();
        for (int i = 0; i < modes.length; i++) {
            for (int j = i + 1; j < modes.length; j++) {
                assertTrue(
                    "Color modes should have unique values: " + modes[i] + " and " + modes[j],
                    modes[i].getColorCount() != modes[j].getColorCount()
                );
            }
        }
    }
    
    /**
     * Test that the fromColorCount method returns the correct color mode
     */
    @Test
    public void testFromColorCount() {
        assertEquals("fromColorCount(2) should return BINARY", ColorMode.BINARY, ColorMode.fromColorCount(2));
        assertEquals("fromColorCount(4) should return QUATERNARY", ColorMode.QUATERNARY, ColorMode.fromColorCount(4));
        assertEquals("fromColorCount(8) should return OCTAL", ColorMode.OCTAL, ColorMode.fromColorCount(8));
        assertEquals("fromColorCount(16) should return HEXADECIMAL", ColorMode.HEXADECIMAL, ColorMode.fromColorCount(16));
        assertEquals("fromColorCount(32) should return MODE_32", ColorMode.MODE_32, ColorMode.fromColorCount(32));
        assertEquals("fromColorCount(64) should return MODE_64", ColorMode.MODE_64, ColorMode.fromColorCount(64));
        assertEquals("fromColorCount(128) should return MODE_128", ColorMode.MODE_128, ColorMode.fromColorCount(128));
        assertEquals("fromColorCount(256) should return MODE_256", ColorMode.MODE_256, ColorMode.fromColorCount(256));
    }
}
