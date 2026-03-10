package com.jabcode.test.calibration;

import org.junit.Test;
import static org.junit.Assert.*;

public class CalibrationPatternGeneratorTest {
    
    @Test
    public void testColorSampleGeneration() {
        CalibrationPatternGenerator.TestPattern pattern = 
            CalibrationPatternGenerator.generateColorSamples();
        
        assertNotNull(pattern);
        assertEquals(4096, pattern.sampleCount);
        assertEquals(16, pattern.quantization);
        assertEquals(4096 * 3, pattern.colorSamples.length);
    }
    
    @Test
    public void testColorQuantization() {
        CalibrationPatternGenerator.TestPattern pattern = 
            CalibrationPatternGenerator.generateColorSamples();
        
        int[] expectedSteps = {0, 17, 34, 51, 68, 85, 102, 119, 136, 153, 170, 187, 204, 221, 238, 255};
        
        for (int i = 0; i < pattern.colorSamples.length; i++) {
            int value = pattern.colorSamples[i] & 0xFF;
            boolean validStep = false;
            for (int step : expectedSteps) {
                if (value == step) {
                    validStep = true;
                    break;
                }
            }
            assertTrue("Invalid quantization value: " + value, validStep);
        }
    }
    
    @Test
    public void testColorSpaceCoverage() {
        CalibrationPatternGenerator.TestPattern pattern = 
            CalibrationPatternGenerator.generateColorSamples();
        
        boolean hasBlack = false;
        boolean hasWhite = false;
        boolean hasRed = false;
        boolean hasGreen = false;
        boolean hasBlue = false;
        
        for (int i = 0; i < pattern.sampleCount; i++) {
            int r = pattern.colorSamples[i * 3] & 0xFF;
            int g = pattern.colorSamples[i * 3 + 1] & 0xFF;
            int b = pattern.colorSamples[i * 3 + 2] & 0xFF;
            
            if (r == 0 && g == 0 && b == 0) hasBlack = true;
            if (r == 255 && g == 255 && b == 255) hasWhite = true;
            if (r == 255 && g == 0 && b == 0) hasRed = true;
            if (r == 0 && g == 255 && b == 0) hasGreen = true;
            if (r == 0 && g == 0 && b == 255) hasBlue = true;
        }
        
        assertTrue("Missing black corner", hasBlack);
        assertTrue("Missing white corner", hasWhite);
        assertTrue("Missing red corner", hasRed);
        assertTrue("Missing green corner", hasGreen);
        assertTrue("Missing blue corner", hasBlue);
    }
    
    @Test
    public void testPatternInfo() {
        String info = CalibrationPatternGenerator.getPatternInfo();
        
        assertNotNull(info);
        assertTrue(info.contains("77x77 modules"));
        assertTrue(info.contains("4096"));
        assertTrue(info.contains("16 steps"));
    }
}
