package com.jabauth.diagnostic.ui.dashboard.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jabauth.diagnostic.ui.theme.JABAuthTheme
import org.junit.Rule
import org.junit.Test

/**
 * Isolated tests for LiveFeed component
 * Tests the component independently from DashboardScreen
 */
class LiveFeedTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun liveFeed_displaysHeader() {
        composeTestRule.setContent {
            JABAuthTheme {
                LiveFeed()
            }
        }
        
        // Verify header text exists
        composeTestRule
            .onNodeWithText("LIVE FEED")
            .assertExists()
            .assertIsDisplayed()
    }
    
    @Test
    fun liveFeed_displaysEventCount() {
        composeTestRule.setContent {
            JABAuthTheme {
                LiveFeed()
            }
        }
        
        // Verify event count is displayed
        composeTestRule
            .onNodeWithText("6 events")
            .assertExists()
            .assertIsDisplayed()
    }
    
    @Test
    fun liveFeed_displaysEvents() {
        composeTestRule.setContent {
            JABAuthTheme {
                LiveFeed()
            }
        }
        
        // Verify at least one event is displayed
        composeTestRule
            .onNodeWithText("128-color decode completed")
            .assertExists()
    }
    
    @Test
    fun liveFeed_displaysMultipleEventTypes() {
        composeTestRule.setContent {
            JABAuthTheme {
                LiveFeed()
            }
        }
        
        // Verify different event types exist (success, warning, error)
        composeTestRule
            .onNodeWithText("128-color decode completed") // Success event
            .assertExists()
        
        composeTestRule
            .onNodeWithText("High latency detected") // Warning event
            .assertExists()
        
        composeTestRule
            .onNodeWithText("8-color decode failed") // Error event
            .assertExists()
    }
}
