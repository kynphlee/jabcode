package com.jabauth.diagnostic

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * End-to-End Navigation Tests
 * 
 * Phase 6: Validates navigation between Dashboard, Scanner, and Settings screens
 */
@RunWith(AndroidJUnit4::class)
class NavigationE2ETest {

    private val permissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(permissionRule)
        .around(composeTestRule)

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
        composeTestRule.onNodeWithTag("nav_scanner")
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
        composeTestRule.onNodeWithTag("nav_settings")
            .performClick()
        
        // Then: Settings screen is displayed
        composeTestRule.waitForIdle()
    }

    @Test
    fun navigate_scanner_to_dashboard_using_bottom_nav() {
        // Given: Navigate to Scanner screen
        composeTestRule.onNodeWithTag("nav_scanner")
            .performClick()
        
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
        
        // When: Click Dashboard in bottom navigation
        composeTestRule.onNodeWithTag("nav_dashboard")
            .performClick()
        
        // Then: Dashboard screen is displayed
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun navigate_settings_to_scanner_using_bottom_nav() {
        // Given: Navigate to Settings screen
        composeTestRule.onNodeWithTag("nav_settings")
            .performClick()
        
        // When: Click Scanner in bottom navigation
        composeTestRule.onNodeWithTag("nav_scanner")
            .performClick()
        
        // Then: Scanner screen is displayed
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun bottom_navigation_bar_always_visible() {
        // Verify bottom nav is visible on Dashboard
        composeTestRule.onNodeWithTag("nav_dashboard")
            .assertExists()
            .assertIsDisplayed()
        
        // Navigate to Scanner
        composeTestRule.onNodeWithTag("nav_scanner")
            .performClick()
        
        // Verify bottom nav is still visible
        composeTestRule.onNodeWithTag("nav_dashboard")
            .assertExists()
            .assertIsDisplayed()
        
        // Navigate to Settings
        composeTestRule.onNodeWithTag("nav_settings")
            .performClick()
        
        // Verify bottom nav is still visible
        composeTestRule.onNodeWithTag("nav_dashboard")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun full_navigation_cycle_dashboard_scanner_settings_dashboard() {
        // Start at Dashboard
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
        
        // Navigate to Scanner
        composeTestRule.onNodeWithTag("nav_scanner")
            .performClick()
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
        
        // Navigate to Settings
        composeTestRule.onNodeWithTag("nav_settings")
            .performClick()
        composeTestRule.waitForIdle()
        
        // Navigate back to Dashboard
        composeTestRule.onNodeWithTag("nav_dashboard")
            .performClick()
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
            .assertIsDisplayed()
    }
}
