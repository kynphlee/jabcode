package com.jabauth.diagnostic.ui.dashboard.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jabauth.diagnostic.ui.theme.JABAuthTheme
import org.junit.Rule
import org.junit.Test

/**
 * Isolated tests for PerformanceChart component
 * Tests the component independently from DashboardScreen
 */
class PerformanceChartTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun performanceChart_displaysHeader() {
        composeTestRule.setContent {
            JABAuthTheme {
                PerformanceChart()
            }
        }
        
        // Verify header text exists
        composeTestRule
            .onNodeWithText("PERFORMANCE COMPARISON")
            .assertExists()
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Average latency by color mode")
            .assertExists()
            .assertIsDisplayed()
    }
    
    @Test
    fun performanceChart_rendersSuccessfully() {
        composeTestRule.setContent {
            JABAuthTheme {
                PerformanceChart()
            }
        }
        
        // Verify component renders without crashing
        composeTestRule
            .onNodeWithText("PERFORMANCE COMPARISON")
            .assertExists()
    }
    
    @Test
    fun performanceChart_displaysWithCustomData() {
        val customData = mapOf(
            4 to 50.0,
            8 to 75.0,
            16 to 100.0
        )
        
        composeTestRule.setContent {
            JABAuthTheme {
                PerformanceChart(latencyData = customData)
            }
        }
        
        // Verify component renders with custom data
        composeTestRule
            .onNodeWithText("PERFORMANCE COMPARISON")
            .assertExists()
    }
}
