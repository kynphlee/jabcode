package com.jabauth.diagnostic.benchmark

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for Benchmark Suite
 * 
 * Tests benchmark execution, timing, and statistics calculation.
 * Coverage Target: 75%+
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BenchmarkSuiteTest {
    
    private lateinit var benchmarkSuite: TestBenchmarkSuite
    
    @Before
    fun setup() {
        benchmarkSuite = TestBenchmarkSuite()
    }
    
    @Test
    fun `runBenchmark executes warmup and measurement iterations`() {
        var executionCount = 0
        
        val result = benchmarkSuite.runBenchmark(
            name = "test",
            warmupIterations = 2,
            measurementIterations = 3
        ) {
            executionCount++
        }
        
        assertThat(executionCount).isEqualTo(5) // 2 warmup + 3 measurement
        assertThat(result.warmupIterations).isEqualTo(2)
        assertThat(result.measurementIterations).isEqualTo(3)
    }
    
    @Test
    fun `runBenchmark calculates mean time correctly`() {
        val result = benchmarkSuite.runBenchmark(
            name = "test",
            warmupIterations = 0,
            measurementIterations = 3
        ) {
            Thread.sleep(10)
        }
        
        assertThat(result.meanTimeMs).isGreaterThan(0.0)
        assertThat(result.meanTimeMs).isAtLeast(10.0)
    }
    
    @Test
    fun `runBenchmark calculates median time correctly`() {
        val result = benchmarkSuite.runBenchmark(
            name = "test",
            warmupIterations = 0,
            measurementIterations = 5
        ) {
            Thread.sleep(5)
        }
        
        assertThat(result.medianTimeMs).isGreaterThan(0.0)
        assertThat(result.medianTimeMs).isAtLeast(5.0)
    }
    
    @Test
    fun `runBenchmark calculates min and max times`() {
        val result = benchmarkSuite.runBenchmark(
            name = "test",
            warmupIterations = 0,
            measurementIterations = 5
        ) {
            Thread.sleep((1..10).random().toLong())
        }
        
        assertThat(result.minTimeMs).isAtMost(result.maxTimeMs)
        assertThat(result.minTimeMs).isGreaterThan(0.0)
    }
    
    @Test
    fun `runBenchmark sets benchmark name`() {
        val result = benchmarkSuite.runBenchmark(
            name = "my_benchmark",
            warmupIterations = 1,
            measurementIterations = 2
        ) {
            // No-op
        }
        
        assertThat(result.benchmarkName).isEqualTo("my_benchmark")
    }
    
    // Test implementation of BenchmarkSuite
    class TestBenchmarkSuite : BenchmarkSuite() {
        public override fun runBenchmark(
            name: String,
            warmupIterations: Int,
            measurementIterations: Int,
            block: () -> Unit
        ): BenchmarkResult {
            return super.runBenchmark(name, warmupIterations, measurementIterations, block)
        }
    }
}
