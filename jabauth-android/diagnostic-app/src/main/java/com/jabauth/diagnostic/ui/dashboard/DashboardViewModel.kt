package com.jabauth.diagnostic.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Dashboard ViewModel
 * 
 * Provides data for diagnostic dashboard UI components.
 * 
 * Future: Will integrate with :diagnostic-engine via Hilt DI (Phase 4)
 * Current: Uses mock data for Phase 2 completion
 */
class DashboardViewModel : ViewModel() {
    
    // ========================================
    // State
    // ========================================
    
    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    // ========================================
    // Actions
    // ========================================
    
    /**
     * Refresh all dashboard metrics
     */
    fun refreshMetrics() {
        viewModelScope.launch {
            _isRefreshing.value = true
            
            // Simulate network/engine delay
            delay(500)
            
            // Update metrics with new "live" data
            _dashboardState.value = _dashboardState.value.copy(
                avgEncodeMs = (40..80).random().toDouble() + (0..9).random() / 10.0,
                avgDecodeMs = (35..75).random().toDouble() + (0..9).random() / 10.0,
                successRate = (92..99).random().toDouble() + (0..9).random() / 10.0,
                activeTests = (15..25).random(),
                lastRefreshTimestamp = System.currentTimeMillis()
            )
            
            _isRefreshing.value = false
        }
    }
    
    /**
     * Select a color mode
     */
    fun selectColorMode(mode: Int) {
        _dashboardState.value = _dashboardState.value.copy(
            selectedColorMode = mode
        )
    }
    
    /**
     * Generate diagnostic report
     */
    fun generateReport(): String {
        val state = _dashboardState.value
        return buildString {
            appendLine("=== JABAuth Diagnostic Report ===")
            appendLine()
            appendLine("Metrics:")
            appendLine("  Avg Encode: ${state.avgEncodeMs} ms")
            appendLine("  Avg Decode: ${state.avgDecodeMs} ms")
            appendLine("  Success Rate: ${state.successRate}%")
            appendLine("  Active Tests: ${state.activeTests}")
            appendLine("  Device: ${state.deviceName}")
            appendLine()
            appendLine("Color Modes:")
            state.colorModeLatency.forEach { (mode, latency) ->
                appendLine("  $mode-color: $latency ms")
            }
            appendLine()
            appendLine("Generated: ${System.currentTimeMillis()}")
        }
    }
}

/**
 * Dashboard UI State
 */
data class DashboardState(
    // Metrics
    val avgEncodeMs: Double = 62.3,
    val avgDecodeMs: Double = 58.7,
    val successRate: Double = 94.2,
    val activeTests: Int = 18,
    val deviceName: String = "SM-S938U",
    
    // Color Mode Data
    val selectedColorMode: Int = 128,
    val colorModeLatency: Map<Int, Double> = mapOf(
        4 to 45.2,
        8 to 67.8,
        16 to 89.5,
        32 to 112.3,
        64 to 145.7,
        128 to 189.4
    ),
    
    // Live Feed Events (most recent first)
    val feedEvents: List<FeedEvent> = listOf(
        FeedEvent(
            id = "evt_6",
            type = FeedEventType.SUCCESS,
            title = "128-color decode completed",
            description = "Successfully decoded in 187.2ms",
            timestamp = System.currentTimeMillis() - 15000,
            metadata = mapOf("latency" to "187.2ms", "mode" to "128")
        ),
        FeedEvent(
            id = "evt_5",
            type = FeedEventType.SUCCESS,
            title = "64-color encode completed",
            description = "Encoded 2KB payload",
            timestamp = System.currentTimeMillis() - 42000,
            metadata = mapOf("size" to "2KB", "mode" to "64")
        ),
        FeedEvent(
            id = "evt_4",
            type = FeedEventType.WARNING,
            title = "High latency detected",
            description = "32-color mode: 234ms (expected <120ms)",
            timestamp = System.currentTimeMillis() - 89000,
            metadata = mapOf("latency" to "234ms", "threshold" to "120ms")
        ),
        FeedEvent(
            id = "evt_3",
            type = FeedEventType.ERROR,
            title = "8-color decode failed",
            description = "Invalid symbol matrix",
            timestamp = System.currentTimeMillis() - 125000,
            metadata = mapOf("error" to "INVALID_MATRIX")
        ),
        FeedEvent(
            id = "evt_2",
            type = FeedEventType.SUCCESS,
            title = "16-color test completed",
            description = "Round-trip verified",
            timestamp = System.currentTimeMillis() - 178000,
            metadata = mapOf("mode" to "16", "result" to "verified")
        ),
        FeedEvent(
            id = "evt_1",
            type = FeedEventType.SUCCESS,
            title = "4-color test completed",
            description = "Baseline latency: 44.8ms",
            timestamp = System.currentTimeMillis() - 245000,
            metadata = mapOf("latency" to "44.8ms", "mode" to "4")
        )
    ),
    
    // Alerts
    val alerts: List<Alert> = listOf(
        Alert(
            id = "alert_1",
            severity = AlertSeverity.CRITICAL,
            title = "Critical: High failure rate",
            description = "8-color mode showing 15% failure rate in last 100 tests",
            recommendation = "Reduce scan distance or improve lighting",
            timestamp = System.currentTimeMillis() - 3600000
        ),
        Alert(
            id = "alert_2",
            severity = AlertSeverity.WARNING,
            title = "Performance degradation",
            description = "Average decode latency increased 23% in last hour",
            recommendation = "Check device temperature and background processes",
            timestamp = System.currentTimeMillis() - 1800000
        )
    ),
    
    // Metadata
    val lastRefreshTimestamp: Long = System.currentTimeMillis()
)

/**
 * Feed Event Types
 */
enum class FeedEventType {
    SUCCESS,
    WARNING,
    ERROR
}

/**
 * Feed Event
 */
data class FeedEvent(
    val id: String,
    val type: FeedEventType,
    val title: String,
    val description: String,
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Alert Severity
 */
enum class AlertSeverity {
    CRITICAL,
    WARNING
}

/**
 * Alert
 */
data class Alert(
    val id: String,
    val severity: AlertSeverity,
    val title: String,
    val description: String,
    val recommendation: String,
    val timestamp: Long
)
