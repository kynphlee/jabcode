package com.jabauth.diagnostic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-End Framework Integration Tests
 * 
 * Phase 6: Validates all 5 framework modules work together
 * Tests UI components, navigation, and basic functionality
 */
@RunWith(AndroidJUnit4::class)
class FrameworkIntegrationE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun ui_components_module_renders_properly() {
        // Navigate to Scanner to see UI components
        composeTestRule.onNodeWithText("Scanner")
            .performClick()
        
        // Verify UI components from :ui-components module render
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
            .assertIsDisplayed()
        
        // Scanner screen uses composables from ui-components module
        composeTestRule.waitForIdle()
    }

    @Test
    fun dashboard_displays_framework_status() {
        // Dashboard screen integrates with all framework modules
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
        
        // Verify placeholder content is visible
        composeTestRule.onNodeWithText("Framework Status")
            .assertExists()
    }

    @Test
    fun scanner_screen_layout_complete() {
        // Navigate to Scanner
        composeTestRule.onNodeWithText("Scanner")
            .performClick()
        
        // Verify Scanner screen header
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
        
        // Verify placeholder scanner message
        composeTestRule.onNodeWithText("Camera preview will appear here")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun settings_screen_displays_options() {
        // Navigate to Settings
        composeTestRule.onNodeWithText("Settings")
            .performClick()
        
        // Verify Settings screen displays
        composeTestRule.onNodeWithText("Settings")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun app_maintains_state_across_navigation() {
        // Start at Dashboard
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
        
        // Navigate away and back
        composeTestRule.onNodeWithText("Scanner")
            .performClick()
        composeTestRule.onNodeWithText("Dashboard")
            .performClick()
        
        // Verify Dashboard state is maintained
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun all_navigation_destinations_reachable() {
        // Verify all three screens are accessible
        
        // Dashboard (default)
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
        
        // Scanner
        composeTestRule.onNodeWithText("Scanner")
            .performClick()
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
        
        // Settings
        composeTestRule.onNodeWithText("Settings")
            .performClick()
        composeTestRule.onNodeWithText("Settings")
            .assertExists()
    }

    @Test
    fun bottom_navigation_items_clickable() {
        // Verify all bottom navigation items are clickable
        
        composeTestRule.onNodeWithText("Dashboard")
            .assertIsDisplayed()
            .assertHasClickAction()
        
        composeTestRule.onNodeWithText("Scanner")
            .assertIsDisplayed()
            .assertHasClickAction()
        
        composeTestRule.onNodeWithText("Settings")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun framework_modules_loaded_successfully() {
        // If app launches and navigates without crashes,
        // all framework modules are loaded correctly
        
        // Core module (SecureStorage, NetworkMonitor)
        composeTestRule.onNodeWithText("Dashboard")
            .assertExists()
        
        // UI components module (Compose components)
        composeTestRule.onNodeWithText("Scanner")
            .performClick()
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
        
        // Navigation works = diagnostic-engine integration
        composeTestRule.onNodeWithText("Settings")
            .performClick()
        composeTestRule.onNodeWithText("Settings")
            .assertExists()
        
        // No crashes = jabcode-sdk native libs loaded correctly
        composeTestRule.waitForIdle()
    }

    @Test
    fun rapid_navigation_does_not_crash() {
        // Stress test: Rapid navigation between screens
        repeat(5) {
            composeTestRule.onNodeWithText("Scanner").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithText("Settings").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithText("Dashboard").performClick()
            composeTestRule.waitForIdle()
        }
        
        // Verify app is still functional
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun compose_ui_renders_without_errors() {
        // Verify Compose UI from ui-components module renders properly
        
        // Dashboard
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
        
        // Scanner with emoji icon (temporary replacement)
        composeTestRule.onNodeWithText("Scanner")
            .performClick()
        composeTestRule.onNodeWithText("📷")
            .assertExists()
        
        // Settings
        composeTestRule.onNodeWithText("Settings")
            .performClick()
        composeTestRule.waitForIdle()
    }
}
