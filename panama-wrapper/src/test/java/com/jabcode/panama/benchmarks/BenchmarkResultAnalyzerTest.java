package com.jabcode.panama.benchmarks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BenchmarkResultAnalyzer Tests")
class BenchmarkResultAnalyzerTest {
    
    @Test
    @DisplayName("Should calculate mean correctly")
    void testMean() {
        List<Double> samples = Arrays.asList(10.0, 20.0, 30.0, 40.0, 50.0);
        double mean = BenchmarkResultAnalyzer.calculateMean(samples);
        assertEquals(30.0, mean, 0.001);
    }
    
    @Test
    @DisplayName("Should calculate standard deviation correctly")
    void testStdDev() {
        List<Double> samples = Arrays.asList(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);
        double mean = BenchmarkResultAnalyzer.calculateMean(samples);
        double stddev = BenchmarkResultAnalyzer.calculateStdDev(samples, mean);
        assertEquals(2.0, stddev, 0.001);
    }
    
    @Test
    @DisplayName("Should calculate CV correctly")
    void testCV() {
        // Stable samples (low variance)
        List<Double> stableSamples = Arrays.asList(100.0, 101.0, 99.0, 100.5, 99.5);
        double cv = BenchmarkResultAnalyzer.calculateCV(stableSamples);
        assertTrue(cv < 1.0, "Stable samples should have CV < 1%");
        
        // Unstable samples (high variance)
        List<Double> unstableSamples = Arrays.asList(50.0, 100.0, 150.0, 75.0, 125.0);
        double cv2 = BenchmarkResultAnalyzer.calculateCV(unstableSamples);
        assertTrue(cv2 > 30.0, "Unstable samples should have CV > 30%");
    }
    
    @Test
    @DisplayName("Should detect stable benchmarks")
    void testStabilityCheck() {
        List<Double> stableSamples = Arrays.asList(100.0, 102.0, 98.0, 101.0, 99.0);
        assertTrue(BenchmarkResultAnalyzer.isStable(stableSamples, 5.0));
        
        List<Double> unstableSamples = Arrays.asList(100.0, 120.0, 80.0, 110.0, 90.0);
        assertFalse(BenchmarkResultAnalyzer.isStable(unstableSamples, 5.0));
    }
    
    @Test
    @DisplayName("Should throw on null or empty samples")
    void testInvalidInput() {
        assertThrows(IllegalArgumentException.class, 
            () -> BenchmarkResultAnalyzer.calculateCV(null));
        assertThrows(IllegalArgumentException.class,
            () -> BenchmarkResultAnalyzer.calculateCV(List.of()));
    }
}
