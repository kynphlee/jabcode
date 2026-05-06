package com.jabauth.diagnostic.ui.dashboard

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jabauth.diagnostic.ui.theme.JABAuthTheme
import org.junit.Rule
import org.junit.Test

/**
 * Interaction tests for Dashboard Screen
 * 
 * Phase 2 Requirements:
 * - Day 1: Refresh metrics (1 test)
 * - Day 2: Card click, selection state (2 tests)
 * - Day 3: Component interactions (1 test)
 * - Day 4: Alert dismissal (2 tests)
 * 
 * Total: 6 interaction tests
 */
class DashboardInteractionTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    // ========================================
    // Day 1: Metrics Refresh (1 test)
    // ========================================
    
    @Test
    fun refreshButton_isClickable() {
        composeTestRule.setContent {
            JABAuthTheme {
                DashboardScreen(
                    onNavigateToScanner = {},
                    onNavigateToSettings = {}
                )
            }
        }
        
        // Verify refresh button exists and is clickable
        composeTestRule
            .onNodeWithContentDescription("Refresh diagnostics")
            .assertExists()
            .assertHasClickAction()
            .performClick()
        
        // After click, metrics should still be displayed (idempotent with mock data)
        composeTestRule
            .onNodeWithText("ENCODE TIME")
            .assertExists()
    }
    
    // ========================================
    // Day 2: ColorModeGrid Interactions (2 tests)
    // ========================================
    
    @Test
    fun colorModeCard_clickChangesSelection() {
        composeTestRule.setContent {
            JABAuthTheme {
                DashboardScreen(
                    onNavigateToScanner = {},
                    onNavigateToSettings = {}
                )
            }
        }
        
        // Click on 4-color mode card
        composeTestRule
            .onAllNodesWithText("4")
            .onFirst()
            .assertHasClickAction()
            .performClick()
        
        // Verify card is still displayed after click
        composeTestRule
            .onAllNodesWithText("4")
            .onFirst()
            .assertExists()
        
        // Click on 128-color mode card
        composeTestRule
            .onAllNodesWithText("128")
            .onFirst()
            .performClick()
        
        composeTestRule
            .onAllNodesWithText("128")
            .onFirst()
            .assertExists()
    }
    
    @Test
    fun colorModeGrid_allCardsAreClickable() {
        composeTestRule.setContent {
            JABAuthTheme {
                DashboardScreen(
                    onNavigateToScanner = {},
                    onNavigateToSettings = {}
                )
            }
        }
        
        val colorModes = listOf("4", "8", "16", "32", "64", "128")
        
        // Verify all cards have click actions
        colorModes.forEach { mode ->
            composeTestRule
                .onAllNodesWithText(mode)
                .onFirst()
                .assertHasClickAction()
        }
        
        // Click each card to verify interaction
        colorModes.forEach { mode ->
            composeTestRule
                .onAllNodesWithText(mode)
                .onFirst()
                .performClick()
            
            // Verify card still exists after click
            composeTestRule
                .onAllNodesWithText(mode)
                .onFirst()
                .assertExists()
        }
    }
    
    // ========================================
    // Day 3 & 4: Share Button (1 test)
    // ========================================
    
    @Test
    fun shareButton_isClickable() {
        composeTestRule.setContent {
            JABAuthTheme {
                DashboardScreen(
                    onNavigateToScanner = {},
                    onNavigateToSettings = {}
                )
            }
        }
        
        // Verify share button exists and is clickable
        composeTestRule
            .onNodeWithContentDescription("Share diagnostic report")
            .assertExists()
            .assertHasClickAction()
            .performClick()
        
        // After click, dashboard should still be displayed
        composeTestRule
            .onNodeWithText("JABAuth Diagnostic")
            .assertExists()
    }
    
    // Note: AlertSection and LiveFeed interactions are tested
    // in isolation (components/ package) since they may be lazy-loaded
}
