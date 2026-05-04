package com.jabauth.diagnostic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-End Navigation Tests
 * 
 * Phase 6: Validates navigation between Dashboard, Scanner, and Settings screens
 */
@RunWith(AndroidJUnit4::class)
class NavigationE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_launches_and_shows_dashboard() {
        // Verify Dashboard screen is displayed on launch
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun navigate_from_dashboard_to_scanner() {
        // Given: App starts on Dashboard
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
        
        // When: Navigate to Scanner
        composeTestRule.onNodeWithText("Scanner")
            .performClick()
        
        // Then: Scanner screen is displayed
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun navigate_from_dashboard_to_settings() {
        // Given: App starts on Dashboard
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
        
        // When: Navigate to Settings
        composeTestRule.onNodeWithText("Settings")
            .performClick()
        
        // Then: Settings screen is displayed
        composeTestRule.onNodeWithText("Settings")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun navigate_scanner_to_dashboard_using_bottom_nav() {
        // Given: Navigate to Scanner screen
        composeTestRule.onNodeWithText("Scanner")
            .performClick()
        
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
        
        // When: Click Dashboard in bottom navigation
        composeTestRule.onNodeWithText("Dashboard")
            .performClick()
        
        // Then: Dashboard screen is displayed
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun navigate_settings_to_scanner_using_bottom_nav() {
        // Given: Navigate to Settings screen
        composeTestRule.onNodeWithText("Settings")
            .performClick()
        
        // When: Click Scanner in bottom navigation
        composeTestRule.onNodeWithText("Scanner")
            .performClick()
        
        // Then: Scanner screen is displayed
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun bottom_navigation_bar_always_visible() {
        // Verify bottom nav is visible on Dashboard
        composeTestRule.onNodeWithText("Dashboard")
            .assertExists()
            .assertIsDisplayed()
        
        // Navigate to Scanner
        composeTestRule.onNodeWithText("Scanner")
            .performClick()
        
        // Verify bottom nav is still visible
        composeTestRule.onNodeWithText("Dashboard")
            .assertExists()
            .assertIsDisplayed()
        
        // Navigate to Settings
        composeTestRule.onNodeWithText("Settings")
            .performClick()
        
        // Verify bottom nav is still visible
        composeTestRule.onNodeWithText("Dashboard")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun full_navigation_cycle_dashboard_scanner_settings_dashboard() {
        // Start at Dashboard
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
        
        // Navigate to Scanner
        composeTestRule.onNodeWithText("Scanner")
            .performClick()
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
        
        // Navigate to Settings
        composeTestRule.onNodeWithText("Settings")
            .performClick()
        composeTestRule.onNodeWithText("Settings")
            .assertExists()
        
        // Navigate back to Dashboard
        composeTestRule.onNodeWithText("Dashboard")
            .performClick()
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
            .assertIsDisplayed()
    }
}
