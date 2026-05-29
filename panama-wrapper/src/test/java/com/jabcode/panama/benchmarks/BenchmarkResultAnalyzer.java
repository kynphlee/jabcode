package com.jabcode.panama.benchmarks;

import java.util.List;

/**
 * Analyzes benchmark results for quality metrics
 */
public class BenchmarkResultAnalyzer {
    
    /**
     * Calculate Coefficient of Variation (CV) as percentage
     * CV = (stddev / mean) * 100
     * 
     * Lower is better. CV < 5% indicates stable benchmark.
     */
    public static double calculateCV(List<Double> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Samples cannot be null or empty");
        }
        
        double mean = calculateMean(samples);
        double stddev = calculateStdDev(samples, mean);
        
        return (stddev / mean) * 100.0;
    }
    
    /**
     * Calculate mean of samples
     */
    public static double calculateMean(List<Double> samples) {
        return samples.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }
    
    /**
     * Calculate standard deviation
     */
    public static double calculateStdDev(List<Double> samples, double mean) {
        double variance = samples.stream()
            .mapToDouble(d -> Math.pow(d - mean, 2))
            .average()
            .orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    /**
     * Check if benchmark results are stable (CV < threshold)
     */
    public static boolean isStable(List<Double> samples, double maxCVPercent) {
        return calculateCV(samples) < maxCVPercent;
    }
}
