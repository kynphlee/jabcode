package com.jabcode.panama.benchmarks;

/**
 * Configuration constants for JABCode benchmarks
 */
public final class BenchmarkConfig {
    
    private BenchmarkConfig() {} // Utility class
    
    // Message sizes to benchmark (bytes)
    public static final int SIZE_TINY = 100;
    public static final int SIZE_SMALL = 1_000;
    public static final int SIZE_MEDIUM = 10_000;
    public static final int SIZE_LARGE = 100_000;
    
    // Color modes to benchmark
    public static final int[] COLOR_MODES = {4, 8, 16, 32, 64, 128};
    
    // ECC levels to benchmark
    public static final int[] ECC_LEVELS = {3, 5, 7, 9};
    
    // Symbol counts for cascade testing
    public static final int[] SYMBOL_COUNTS = {1, 2, 3, 5};
    
    // Default values for standard benchmarks
    public static final int DEFAULT_COLOR_MODE = 8;
    public static final int DEFAULT_ECC_LEVEL = 5;
    public static final int DEFAULT_SYMBOL_COUNT = 1;
    public static final int DEFAULT_MESSAGE_SIZE = SIZE_SMALL;
    
    // JMH configuration
    public static final int WARMUP_ITERATIONS = 5;
    public static final int WARMUP_TIME_SECONDS = 2;
    public static final int MEASUREMENT_ITERATIONS = 10;
    public static final int MEASUREMENT_TIME_SECONDS = 2;
    public static final int FORK_COUNT = 3;
    
    // Acceptable performance variance (coefficient of variation)
    public static final double MAX_CV_PERCENT = 5.0;
}
