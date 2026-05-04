package com.jabauth.diagnostic.benchmark

/**
 * Result of a benchmark execution
 * 
 * Contains timing metrics and iteration statistics.
 */
data class BenchmarkResult(
    /**
     * Name of the benchmark
     */
    val benchmarkName: String,
    
    /**
     * Number of warmup iterations performed
     */
    val warmupIterations: Int,
    
    /**
     * Number of measurement iterations performed
     */
    val measurementIterations: Int,
    
    /**
     * Mean execution time in milliseconds
     */
    val meanTimeMs: Double,
    
    /**
     * Median execution time in milliseconds
     */
    val medianTimeMs: Double,
    
    /**
     * Minimum execution time in milliseconds
     */
    val minTimeMs: Double,
    
    /**
     * Maximum execution time in milliseconds
     */
    val maxTimeMs: Double,
    
    /**
     * Standard deviation of execution time
     */
    val stdDevMs: Double,
    
    /**
     * Additional metadata (e.g., throughput, memory usage)
     */
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Format result as human-readable string
     */
    override fun toString(): String {
        return buildString {
            appendLine("Benchmark: $benchmarkName")
            appendLine("  Warmup: $warmupIterations iterations")
            appendLine("  Measurement: $measurementIterations iterations")
            appendLine("  Mean: ${"%.2f".format(meanTimeMs)} ms")
            appendLine("  Median: ${"%.2f".format(medianTimeMs)} ms")
            appendLine("  Min: ${"%.2f".format(minTimeMs)} ms")
            appendLine("  Max: ${"%.2f".format(maxTimeMs)} ms")
            appendLine("  StdDev: ${"%.2f".format(stdDevMs)} ms")
            if (metadata.isNotEmpty()) {
                appendLine("  Metadata:")
                metadata.forEach { (key, value) ->
                    appendLine("    $key: $value")
                }
            }
        }
    }
}
