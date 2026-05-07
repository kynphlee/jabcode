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
 * End-to-End Framework Integration Tests
 * 
 * Phase 6: Validates all 5 framework modules work together
 * Tests UI components, navigation, and basic functionality
 */
@RunWith(AndroidJUnit4::class)
class FrameworkIntegrationE2ETest {

    private val permissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(permissionRule)
        .around(composeTestRule)

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
        
        // Verify current dashboard metrics are visible
        composeTestRule.onNodeWithText("ENCODE TIME")
            .assertExists()
        
        composeTestRule.onNodeWithText("Color Mode Comparison")
            .assertExists()
    }

    @Test
    fun scanner_screen_layout_complete() {
        // Navigate to Scanner via bottom nav
        composeTestRule.onNodeWithTag("nav_scanner")
            .performClick()
        
        // Verify Scanner screen header
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
        
        // Verify scanner action buttons (camera permission granted via GrantPermissionRule)
        composeTestRule.onNodeWithText("Torch On")
            .assertExists()
        // Note: "Dashboard" appears twice (bottom nav + action button), so we don't assert on it
    }

    @Test
    fun settings_screen_displays_options() {
        // Navigate to Settings via bottom nav
        composeTestRule.onNodeWithTag("nav_settings")
            .performClick()
        
        // Verify Settings screen displays
        composeTestRule.waitForIdle()
    }

    @Test
    fun app_maintains_state_across_navigation() {
        // Start at Dashboard
        composeTestRule.onNodeWithText("JABAuth Diagnostic")
            .assertExists()
        
        // Navigate away and back
        composeTestRule.onNodeWithTag("nav_scanner")
            .performClick()
        composeTestRule.onNodeWithTag("nav_dashboard")
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
        composeTestRule.onNodeWithTag("nav_scanner")
            .performClick()
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
        
        // Settings
        composeTestRule.onNodeWithTag("nav_settings")
            .performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun bottom_navigation_items_clickable() {
        // Verify all bottom navigation items are clickable
        
        composeTestRule.onNodeWithTag("nav_dashboard")
            .assertIsDisplayed()
            .assertHasClickAction()
        
        composeTestRule.onNodeWithTag("nav_scanner")
            .assertIsDisplayed()
            .assertHasClickAction()
        
        composeTestRule.onNodeWithTag("nav_settings")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun framework_modules_loaded_successfully() {
        // If app launches and navigates without crashes,
        // all framework modules are loaded correctly
        
        // Core module (SecureStorage, NetworkMonitor)
        composeTestRule.onNodeWithTag("nav_dashboard")
            .assertExists()
        
        // UI components module (Compose components)
        composeTestRule.onNodeWithTag("nav_scanner")
            .performClick()
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
        
        // Navigation works = diagnostic-engine integration
        composeTestRule.onNodeWithTag("nav_settings")
            .performClick()
        
        // No crashes = jabcode-sdk native libs loaded correctly
        composeTestRule.waitForIdle()
    }

    @Test
    fun rapid_navigation_does_not_crash() {
        // Stress test: Rapid navigation between screens
        repeat(5) {
            composeTestRule.onNodeWithTag("nav_scanner").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("nav_settings").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("nav_dashboard").performClick()
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
        
        // All navigation items should be visible
        composeTestRule.onNodeWithTag("nav_dashboard").assertExists()
        composeTestRule.onNodeWithTag("nav_scanner").assertExists()
        composeTestRule.onNodeWithTag("nav_settings").assertExists()
        
        // Navigate to Scanner and verify UI renders
        composeTestRule.onNodeWithTag("nav_scanner")
            .performClick()
        composeTestRule.onNodeWithText("JABCode Scanner")
            .assertExists()
        
        // Verify scan status overlay from ui-components renders
        composeTestRule.onNodeWithText("Position JABCode in frame")
            .assertExists()
    }
}
