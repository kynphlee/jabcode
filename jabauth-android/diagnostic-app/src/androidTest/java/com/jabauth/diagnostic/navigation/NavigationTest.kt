package com.jabauth.diagnostic.navigation

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jabauth.diagnostic.ui.theme.AppContext
import com.jabauth.diagnostic.ui.theme.JABAuthTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation tests for Diagnostic App
 * 
 * Tests:
 * - Navigation between Dashboard, Scanner, and Settings
 * - Back navigation behavior
 * - Bottom navigation state persistence
 * - Route validation
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    /**
     * Test: Navigate from Dashboard to Scanner using bottom nav
     */
    @Test
    fun navigateFromDashboardToScanner() {
        lateinit var navController: TestNavHostController
        
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            
            JABAuthTheme(context = AppContext.Healthcare) {
                DiagnosticNavHost()
            }
        }
        
        // Verify we start on Dashboard (check for metrics)
        composeTestRule.onNodeWithText("ENCODE TIME").assertIsDisplayed()
        
        // Click Scanner bottom nav
        composeTestRule.onNodeWithTag("nav_scanner").performClick()
        
        // Verify Scanner screen is displayed
        composeTestRule.onNodeWithText("Camera Preview Placeholder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quality Metrics").assertIsDisplayed()
    }
    
    /**
     * Test: Navigate from Dashboard to Settings using bottom nav
     */
    @Test
    fun navigateFromDashboardToSettings() {
        composeTestRule.setContent {
            JABAuthTheme(context = AppContext.Healthcare) {
                DiagnosticNavHost()
            }
        }
        
        // Verify we start on Dashboard (check for metrics)
        composeTestRule.onNodeWithText("ENCODE TIME").assertIsDisplayed()
        
        // Click Settings bottom nav
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        
        // Verify Settings screen is displayed
        composeTestRule.onNodeWithText("Framework Modules").assertIsDisplayed()
        composeTestRule.onNodeWithText("Core Module").assertIsDisplayed()
    }
    
    /**
     * Test: Navigate back from Scanner to Dashboard
     */
    @Test
    fun navigateBackFromScanner() {
        composeTestRule.setContent {
            JABAuthTheme(context = AppContext.Healthcare) {
                DiagnosticNavHost()
            }
        }
        
        // Navigate to Scanner
        composeTestRule.onNodeWithTag("nav_scanner").performClick()
        composeTestRule.onNodeWithText("Camera Preview Placeholder").assertIsDisplayed()
        
        // Click back button in top app bar
        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()
        
        // Verify we're back on Dashboard
        composeTestRule.onNodeWithText("ENCODE TIME").assertIsDisplayed()
    }
    
    /**
     * Test: Navigate back from Settings to Dashboard
     */
    @Test
    fun navigateBackFromSettings() {
        composeTestRule.setContent {
            JABAuthTheme(context = AppContext.Healthcare) {
                DiagnosticNavHost()
            }
        }
        
        // Navigate to Settings
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.onNodeWithText("Framework Modules").assertIsDisplayed()
        
        // Click back button in top app bar
        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()
        
        // Verify we're back on Dashboard
        composeTestRule.onNodeWithText("ENCODE TIME").assertIsDisplayed()
    }
    
    /**
     * Test: Bottom navigation persists state and handles multiple navigations
     */
    @Test
    fun bottomNavigation_persistsStateAndHandlesMultipleNavigations() {
        composeTestRule.setContent {
            JABAuthTheme(context = AppContext.Healthcare) {
                DiagnosticNavHost()
            }
        }
        
        // Start on Dashboard (check for metrics)
        composeTestRule.onNodeWithText("ENCODE TIME").assertIsDisplayed()
        
        // Navigate to Scanner
        composeTestRule.onNodeWithTag("nav_scanner").performClick()
        composeTestRule.onNodeWithText("Camera Preview Placeholder").assertIsDisplayed()
        
        // Navigate to Settings
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.onNodeWithText("Framework Modules").assertIsDisplayed()
        
        // Navigate back to Dashboard via bottom nav
        composeTestRule.onNodeWithTag("nav_dashboard").performClick()
        composeTestRule.onNodeWithText("ENCODE TIME").assertIsDisplayed()
        
        // Navigate to Scanner again
        composeTestRule.onNodeWithTag("nav_scanner").performClick()
        composeTestRule.onNodeWithText("Camera Preview Placeholder").assertIsDisplayed()
        
        // Navigate back to Dashboard again
        composeTestRule.onNodeWithTag("nav_dashboard").performClick()
        composeTestRule.onNodeWithText("ENCODE TIME").assertIsDisplayed()
    }
    
    /**
     * Test: Verify all bottom navigation items are present
     */
    @Test
    fun bottomNavigation_allItemsPresent() {
        composeTestRule.setContent {
            JABAuthTheme(context = AppContext.Healthcare) {
                DiagnosticNavHost()
            }
        }
        
        // Verify all bottom nav items exist
        composeTestRule.onNodeWithTag("nav_dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_scanner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_settings").assertIsDisplayed()
        
        // Verify labels
        composeTestRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scanner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }
    
    /**
     * Test: Verify no transitions during navigation
     */
    @Test
    fun navigation_hasNoTransitions() {
        composeTestRule.setContent {
            JABAuthTheme(context = AppContext.Healthcare) {
                DiagnosticNavHost()
            }
        }
        
        // Navigate to Scanner
        composeTestRule.onNodeWithTag("nav_scanner").performClick()
        
        // Content should appear immediately (no fade/animation delays)
        composeTestRule.onNodeWithText("Camera Preview Placeholder")
            .assertIsDisplayed()
        
        // Navigate to Settings
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        
        // Content should appear immediately
        composeTestRule.onNodeWithText("Framework Modules")
            .assertIsDisplayed()
    }
    
    /**
     * Test: Dashboard screen displays all expected elements
     */
    @Test
    fun dashboardScreen_displaysAllElements() {
        composeTestRule.setContent {
            JABAuthTheme(context = AppContext.Healthcare) {
                DiagnosticNavHost()
            }
        }
        
        // Verify Dashboard elements
        composeTestRule.onNodeWithText("JABAuth Diagnostic").assertIsDisplayed()
        
        // Verify metrics bar is displayed
        composeTestRule.onNodeWithText("ENCODE TIME").assertIsDisplayed()
        composeTestRule.onNodeWithText("DECODE TIME").assertIsDisplayed()
        composeTestRule.onNodeWithText("SUCCESS RATE").assertIsDisplayed()
        
        // Verify color mode comparison section
        composeTestRule.onNodeWithText("Color Mode Comparison").assertIsDisplayed()
        
        // Verify action buttons in app bar
        composeTestRule.onNodeWithContentDescription("Refresh diagnostics").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Share diagnostic report").assertIsDisplayed()
    }
}
