package com.jabauth.diagnostic.ui.dashboard.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jabauth.diagnostic.ui.theme.JABAuthTheme
import org.junit.Rule
import org.junit.Test

/**
 * Isolated tests for AlertSection component
 * Tests the component independently from DashboardScreen
 */
class AlertSectionTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun alertSection_displaysHeader() {
        composeTestRule.setContent {
            JABAuthTheme {
                AlertSection()
            }
        }
        
        // Verify header text exists
        composeTestRule
            .onNodeWithText("ALERTS")
            .assertExists()
            .assertIsDisplayed()
    }
    
    @Test
    fun alertSection_displaysAlerts() {
        composeTestRule.setContent {
            JABAuthTheme {
                AlertSection()
            }
        }
        
        // Verify critical alert exists
        composeTestRule
            .onNodeWithText("Critical: High failure rate")
            .assertExists()
        
        // Verify warning alert exists
        composeTestRule
            .onNodeWithText("Performance degradation")
            .assertExists()
    }
    
    @Test
    fun alertSection_displaysDismissButtons() {
        composeTestRule.setContent {
            JABAuthTheme {
                AlertSection()
            }
        }
        
        // Verify dismiss buttons exist
        composeTestRule
            .onAllNodesWithContentDescription("Dismiss")
            .assertCountEquals(2)
    }
    
    @Test
    fun alertSection_dismissWorks() {
        composeTestRule.setContent {
            JABAuthTheme {
                AlertSection()
            }
        }
        
        // Click first dismiss button
        composeTestRule
            .onAllNodesWithContentDescription("Dismiss")
            .onFirst()
            .performClick()
        
        // Verify count reduced to 1
        composeTestRule
            .onAllNodesWithContentDescription("Dismiss")
            .assertCountEquals(1)
    }
}
