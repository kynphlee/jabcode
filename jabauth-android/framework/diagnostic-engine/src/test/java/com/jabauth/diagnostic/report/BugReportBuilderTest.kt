package com.jabauth.diagnostic.report

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for BugReportBuilder
 * 
 * Tests bug report generation and content.
 * Coverage Target: 75%+
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BugReportBuilderTest {
    
    private lateinit var builder: BugReportBuilder
    
    @Before
    fun setup() {
        builder = TestBugReportBuilderImpl()
    }
    
    @Test
    fun `build creates report with device info`() {
        val report = builder.build()
        
        assertThat(report.deviceManufacturer).isEqualTo("TestManufacturer")
        assertThat(report.deviceModel).isEqualTo("TestModel")
        assertThat(report.osVersion).isEqualTo("Android 14")
        assertThat(report.appVersion).isEqualTo("1.0.0-test")
    }
    
    @Test
    fun `addLog includes log in report`() {
        val report = builder
            .addLog("Error occurred")
            .addLog("Second log entry")
            .build()
        
        assertThat(report.logs).hasSize(2)
        assertThat(report.logs).containsExactly("Error occurred", "Second log entry")
    }
    
    @Test
    fun `addStackTrace includes trace in report`() {
        val stackTrace = "java.lang.Exception: Test\n  at Test.method(Test.java:10)"
        
        val report = builder
            .addStackTrace(stackTrace)
            .build()
        
        assertThat(report.stackTraces).hasSize(1)
        assertThat(report.stackTraces.first()).isEqualTo(stackTrace)
    }
    
    @Test
    fun `addMetric includes metric in report`() {
        val report = builder
            .addMetric("cpu_usage", 45.5)
            .addMetric("memory_mb", 256)
            .build()
        
        assertThat(report.metrics).hasSize(2)
        assertThat(report.metrics).containsEntry("cpu_usage", 45.5)
        assertThat(report.metrics).containsEntry("memory_mb", 256)
    }
    
    @Test
    fun `addContext includes context in report`() {
        val report = builder
            .addContext("screen", "MainActivity")
            .addContext("action", "button_click")
            .build()
        
        assertThat(report.context).hasSize(2)
        assertThat(report.context).containsEntry("screen", "MainActivity")
        assertThat(report.context).containsEntry("action", "button_click")
    }
    
    @Test
    fun `reset clears builder state`() {
        builder
            .addLog("Test log")
            .addStackTrace("Test trace")
            .addMetric("key", "value")
            .addContext("ctx", "val")
        
        builder.reset()
        
        val report = builder.build()
        assertThat(report.logs).isEmpty()
        assertThat(report.stackTraces).isEmpty()
        assertThat(report.metrics).isEmpty()
        assertThat(report.context).isEmpty()
    }
    
    @Test
    fun `toMarkdown produces formatted output`() {
        val report = builder
            .addLog("Test log entry")
            .addStackTrace("Exception trace")
            .addMetric("duration_ms", 100)
            .addContext("feature", "test_feature")
            .build()
        
        val markdown = report.toMarkdown()
        
        assertThat(markdown).contains("# Bug Report")
        assertThat(markdown).contains("Device Information")
        assertThat(markdown).contains("TestManufacturer")
        assertThat(markdown).contains("TestModel")
        assertThat(markdown).contains("Test log entry")
        assertThat(markdown).contains("Exception trace")
        assertThat(markdown).contains("duration_ms")
        assertThat(markdown).contains("test_feature")
    }
}
