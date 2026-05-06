package com.jabauth.diagnostic.ui.dashboard

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jabauth.diagnostic.ui.theme.JABAuthTheme
import org.junit.Rule
import org.junit.Test

/**
 * Screenshot tests for Dashboard Screen components
 * 
 * Phase 2 Requirements:
 * - Day 1: Header, Metrics (2 tests)
 * - Day 2: ColorModeGrid (2 tests)
 * - Day 3: PerformanceChart (2 tests)
 * - Day 4: LiveFeed, Alerts (3 tests)
 * 
 * Total: 9 screenshot tests
 */
class DashboardScreenshotTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    // ========================================
    // Day 1: Header & Metrics (2 tests)
    // ========================================
    
    @Test
    fun dashboardHeader_displaysCorrectly() {
        composeTestRule.setContent {
            JABAuthTheme {
                DashboardScreen(
                    onNavigateToScanner = {},
                    onNavigateToSettings = {}
                )
            }
        }
        
        // Verify header exists
        composeTestRule
            .onNodeWithText("JABAuth Diagnostic")
            .assertExists()
            .assertIsDisplayed()
        
        // Verify refresh and share buttons exist
        composeTestRule
            .onNodeWithContentDescription("Refresh diagnostics")
            .assertExists()
        
        composeTestRule
            .onNodeWithContentDescription("Share diagnostic report")
            .assertExists()
    }
    
    @Test
    fun metricsBar_displaysAllMetrics() {
        composeTestRule.setContent {
            JABAuthTheme {
                DashboardScreen(
                    onNavigateToScanner = {},
                    onNavigateToSettings = {}
                )
            }
        }
        
        // Verify all 5 metric labels are displayed
        composeTestRule
            .onNodeWithText("ENCODE TIME")
            .assertExists()
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("DECODE TIME")
            .assertExists()
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("SUCCESS RATE")
            .assertExists()
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("ACTIVE TESTS")
            .assertExists()
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("TEST DEVICE")
            .assertExists()
            .assertIsDisplayed()
    }
    
    // ========================================
    // Day 2: ColorModeGrid (2 tests)
    // ========================================
    
    @Test
    fun colorModeGrid_displaysAllSixModes() {
        composeTestRule.setContent {
            JABAuthTheme {
                DashboardScreen(
                    onNavigateToScanner = {},
                    onNavigateToSettings = {}
                )
            }
        }
        
        // Verify section header
        composeTestRule
            .onNodeWithText("Color Mode Comparison")
            .assertExists()
            .assertIsDisplayed()
        
        // Verify all 6 color mode numbers are displayed
        val colorModes = listOf("4", "8", "16", "32", "64", "128")
        
        colorModes.forEach { mode ->
            composeTestRule
                .onAllNodesWithText(mode)
                .onFirst()
                .assertExists()
        }
        
        // Verify latency values are displayed
        composeTestRule
            .onNodeWithText("45.2 ms")
            .assertExists()
        
        composeTestRule
            .onNodeWithText("189.4 ms")
            .assertExists()
    }
    
    @Test
    fun colorModeGrid_displaysCardVariants() {
        composeTestRule.setContent {
            JABAuthTheme {
                DashboardScreen(
                    onNavigateToScanner = {},
                    onNavigateToSettings = {}
                )
            }
        }
        
        // Verify all cards are clickable (have click semantics)
        val colorModes = listOf("4", "8", "16", "32", "64", "128")
        colorModes.forEach { mode ->
            composeTestRule
                .onAllNodesWithText(mode)
                .onFirst()
                .assertHasClickAction()
        }
    }
    
    // ========================================
    // Day 3 & 4: Dashboard Integration (1 test)
    // ========================================
    
    @Test
    fun dashboard_displaysAllVisibleComponents() {
        composeTestRule.setContent {
            JABAuthTheme {
                DashboardScreen(
                    onNavigateToScanner = {},
                    onNavigateToSettings = {}
                )
            }
        }
        
        // Verify immediately visible components
        composeTestRule.onNodeWithText("ENCODE TIME").assertExists()
        composeTestRule.onNodeWithText("DECODE TIME").assertExists()
        composeTestRule.onNodeWithText("Color Mode Comparison").assertExists()
        
        // Note: PerformanceChart, AlertSection, and LiveFeed are tested
        // in isolation (components/ package) since they may be lazy-loaded
    }
}
