package com.jabauth.diagnostic.benchmark

import kotlin.system.measureTimeMillis

/**
 * Base class for benchmark suites
 * 
 * Provides infrastructure for running performance benchmarks with warmup and measurement phases.
 * 
 * **Usage:**
 * ```kotlin
 * class MyBenchmark : BenchmarkSuite() {
 *     @Benchmark
 *     fun testPerformance(): BenchmarkResult {
 *         return runBenchmark("my_test") {
 *             // Code to benchmark
 *         }
 *     }
 * }
 * ```
 */
abstract class BenchmarkSuite {
    
    /**
     * Default number of warmup iterations
     */
    protected open val defaultWarmupIterations: Int = 3
    
    /**
     * Default number of measurement iterations
     */
    protected open val defaultMeasurementIterations: Int = 10
    
    /**
     * Run a benchmark with the given block
     * 
     * @param name Benchmark name
     * @param warmupIterations Number of warmup runs (default: 3)
     * @param measurementIterations Number of measurement runs (default: 10)
     * @param block Code to benchmark
     * @return BenchmarkResult with timing statistics
     */
    protected open fun runBenchmark(
        name: String,
        warmupIterations: Int = defaultWarmupIterations,
        measurementIterations: Int = defaultMeasurementIterations,
        block: () -> Unit
    ): BenchmarkResult {
        // Warmup phase
        repeat(warmupIterations) {
            block()
        }
        
        // Measurement phase
        val measurements = mutableListOf<Double>()
        repeat(measurementIterations) {
            val timeMs = measureTimeMillis {
                block()
            }.toDouble()
            measurements.add(timeMs)
        }
        
        return calculateStatistics(name, warmupIterations, measurements)
    }
    
    /**
     * Calculate benchmark statistics from measurements
     */
    private fun calculateStatistics(
        name: String,
        warmupIterations: Int,
        measurements: List<Double>
    ): BenchmarkResult {
        val sorted = measurements.sorted()
        val mean = measurements.average()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else {
            sorted[sorted.size / 2]
        }
        val min = sorted.first()
        val max = sorted.last()
        val variance = measurements.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        
        return BenchmarkResult(
            benchmarkName = name,
            warmupIterations = warmupIterations,
            measurementIterations = measurements.size,
            meanTimeMs = mean,
            medianTimeMs = median,
            minTimeMs = min,
            maxTimeMs = max,
            stdDevMs = stdDev
        )
    }
}

/**
 * Annotation to mark benchmark methods
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Benchmark
