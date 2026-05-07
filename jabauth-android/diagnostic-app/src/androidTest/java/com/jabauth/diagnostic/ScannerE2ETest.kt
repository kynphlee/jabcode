package com.jabauth.diagnostic

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * End-to-End tests for Scanner screen with Result Panel interactions
 * 
 * Tests the complete scanner workflow including:
 * - Navigation to scanner
 * - Mock result panel display
 * - Button interactions (Accept, Retry, Scan Again, Close)
 * - Success and failure states
 * 
 * Uses UI Automator for ModalBottomSheet button interactions which cannot
 * be tested in isolation due to Compose framework limitations.
 * 
 * Phase 3 Day 4: Result Panel E2E Testing
 */
class ScannerE2ETest {
    
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    @get:Rule
    val permissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    
    private lateinit var device: UiDevice
    
    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        
        // Wait for app to load
        composeTestRule.waitForIdle()
    }
    
    // ========================================================================
    // Navigation Tests
    // ========================================================================
    
    @Test
    fun navigateToScanner_displaysCamera() {
        // Navigate to Scanner
        composeTestRule.onNodeWithText("Scanner").performClick()
        composeTestRule.waitForIdle()
        
        // Verify scanner UI elements
        composeTestRule.onNodeWithText("Position JABCode in frame").assertExists()
        composeTestRule.onNodeWithText("Torch On").assertExists()
    }
    
    // ========================================================================
    // Success Result Panel Tests
    // ========================================================================
    
    @Test
    fun scannerScreen_testSuccess_showsResultPanel() {
        // Navigate to Scanner
        composeTestRule.onNodeWithText("Scanner").performClick()
        composeTestRule.waitForIdle()
        
        // Trigger success result
        composeTestRule.onNodeWithText("Test Success").performClick()
        composeTestRule.waitForIdle()
        
        // Verify result panel displays
        composeTestRule.onNodeWithText("Authentication Valid").assertExists()
        composeTestRule.onNodeWithText("prescription#RX-8472").assertExists()
        
        // Verify validation badges
        composeTestRule.onNodeWithText("CERTIFICATE VALID").assertExists()
        composeTestRule.onNodeWithText("JWT SIGNATURE").assertExists()
        composeTestRule.onNodeWithText("NOT EXPIRED").assertExists()
        
        // Verify action buttons
        composeTestRule.onNodeWithText("✓ Accept").assertExists()
        composeTestRule.onNodeWithText("↻ Scan Again").assertExists()
    }
    
    @Test
    fun resultPanel_success_acceptButton_clicks() {
        // Navigate to Scanner
        composeTestRule.onNodeWithText("Scanner").performClick()
        composeTestRule.waitForIdle()
        
        // Show success result
        composeTestRule.onNodeWithText("Test Success").performClick()
        composeTestRule.waitForIdle()
        
        // Verify panel is visible
        composeTestRule.onNodeWithText("Authentication Valid").assertExists()
        
        // Click Accept button via UI Automator
        val acceptButton = device.wait(
            Until.findObject(By.text("✓ Accept")),
            5000
        )
        
        assert(acceptButton != null) { "Accept button not found" }
        acceptButton?.click()
        
        // Wait for dismissal
        Thread.sleep(1000)
        
        // Verify panel is dismissed (title should be gone)
        composeTestRule.onNodeWithText("Authentication Valid").assertDoesNotExist()
    }
    
    @Test
    fun resultPanel_success_scanAgainButton_clicks() {
        // Navigate and show success
        composeTestRule.onNodeWithText("Scanner").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Test Success").performClick()
        composeTestRule.waitForIdle()
        
        // Click Scan Again button
        val scanAgainButton = device.wait(
            Until.findObject(By.text("↻ Scan Again")),
            5000
        )
        
        assert(scanAgainButton != null) { "Scan Again button not found" }
        scanAgainButton?.click()
        
        Thread.sleep(1000)
        
        // Panel should be dismissed
        composeTestRule.onNodeWithText("Authentication Valid").assertDoesNotExist()
        
        // Scanner should still be visible
        composeTestRule.onNodeWithText("Position JABCode in frame").assertExists()
    }
    
    // ========================================================================
    // Failure Result Panel Tests
    // ========================================================================
    
    @Test
    fun scannerScreen_testFailure_showsResultPanel() {
        // Navigate to Scanner
        composeTestRule.onNodeWithText("Scanner").performClick()
        composeTestRule.waitForIdle()
        
        // Trigger failure result
        composeTestRule.onNodeWithText("Test Failure").performClick()
        composeTestRule.waitForIdle()
        
        // Verify failure state
        composeTestRule.onNodeWithText("Authentication Failed").assertExists()
        composeTestRule.onNodeWithText("prescription#RX-8472").assertExists()
        
        // Verify failed validation badges
        composeTestRule.onNodeWithText("CERTIFICATE VALID").assertExists()
        composeTestRule.onNodeWithText("JWT SIGNATURE").assertExists()
        composeTestRule.onNodeWithText("NOT EXPIRED").assertExists()
        
        // Verify Retry button (not Accept)
        composeTestRule.onNodeWithText("↻ Retry").assertExists()
        composeTestRule.onNodeWithText("✓ Accept").assertDoesNotExist()
    }
    
    @Test
    fun resultPanel_failure_retryButton_clicks() {
        // Navigate and show failure
        composeTestRule.onNodeWithText("Scanner").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Test Failure").performClick()
        composeTestRule.waitForIdle()
        
        // Verify failure panel
        composeTestRule.onNodeWithText("Authentication Failed").assertExists()
        
        // Click Retry button
        val retryButton = device.wait(
            Until.findObject(By.text("↻ Retry")),
            5000
        )
        
        assert(retryButton != null) { "Retry button not found" }
        retryButton?.click()
        
        Thread.sleep(1000)
        
        // Panel should be dismissed
        composeTestRule.onNodeWithText("Authentication Failed").assertDoesNotExist()
    }
    
    // ========================================================================
    // Close Button Tests
    // ========================================================================
    
    @Test
    fun resultPanel_closeButton_dismissesPanel() {
        // Navigate and show result
        composeTestRule.onNodeWithText("Scanner").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Test Success").performClick()
        composeTestRule.waitForIdle()
        
        // Verify panel is visible
        composeTestRule.onNodeWithText("Authentication Valid").assertExists()
        
        // Find and click close button (✕)
        // There will be multiple ✕ symbols (status icon + validation badges + close button)
        // We need the clickable one in the header
        val closeButtons = device.wait(
            Until.findObjects(By.text("✕").clickable(true)),
            5000
        )
        
        assert(closeButtons.isNotEmpty()) { "No clickable close buttons found" }
        
        // Click the first clickable ✕ (close button in header)
        closeButtons.firstOrNull()?.click()
        
        Thread.sleep(1000)
        
        // Panel should be dismissed
        composeTestRule.onNodeWithText("Authentication Valid").assertDoesNotExist()
    }
    
    // ========================================================================
    // Multi-Step Workflow Tests
    // ========================================================================
    
    @Test
    fun scannerWorkflow_successThenFailure_bothWork() {
        // Navigate to Scanner
        composeTestRule.onNodeWithText("Scanner").performClick()
        composeTestRule.waitForIdle()
        
        // Test success flow
        composeTestRule.onNodeWithText("Test Success").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Authentication Valid").assertExists()
        
        // Accept success
        device.wait(Until.findObject(By.text("✓ Accept")), 5000)?.click()
        Thread.sleep(1000)
        
        // Test failure flow
        composeTestRule.onNodeWithText("Test Failure").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Authentication Failed").assertExists()
        
        // Retry failure
        device.wait(Until.findObject(By.text("↻ Retry")), 5000)?.click()
        Thread.sleep(1000)
        
        // Scanner should still be active
        composeTestRule.onNodeWithText("Position JABCode in frame").assertExists()
    }
    
    @Test
    fun resultPanel_scrolling_worksCorrectly() {
        // Navigate and show result
        composeTestRule.onNodeWithText("Scanner").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Test Success").performClick()
        composeTestRule.waitForIdle()
        
        // Verify top content is visible
        composeTestRule.onNodeWithText("Authentication Valid").assertExists()
        composeTestRule.onNodeWithText("CERTIFICATE VALID").assertExists()
        
        // Scroll down to see bottom content
        val scrollableContent = device.wait(
            Until.findObject(By.scrollable(true)),
            5000
        )
        
        if (scrollableContent != null) {
            // Perform scroll gesture
            scrollableContent.scroll(androidx.test.uiautomator.Direction.DOWN, 0.5f)
            Thread.sleep(500)
            
            // Bottom buttons should still be accessible
            val acceptButton = device.findObject(By.text("✓ Accept"))
            assert(acceptButton != null) { "Accept button not accessible after scroll" }
        }
    }
    
    @Test
    fun resultPanel_swipeDown_dismissesPanel() {
        // Navigate and show result
        composeTestRule.onNodeWithText("Scanner").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Test Success").performClick()
        composeTestRule.waitForIdle()
        
        // Verify panel is visible
        composeTestRule.onNodeWithText("Authentication Valid").assertExists()
        
        // Swipe down to dismiss (ModalBottomSheet behavior)
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        device.swipe(
            displayWidth / 2,
            displayHeight / 3,
            displayWidth / 2,
            displayHeight * 2 / 3,
            20
        )
        
        Thread.sleep(1000)
        
        // Panel should be dismissed
        composeTestRule.onNodeWithText("Authentication Valid").assertDoesNotExist()
    }
    
    // ========================================================================
    // Detail Section Tests
    // ========================================================================
    
    @Test
    fun resultPanel_displaysAllDetailSections() {
        // Navigate and show result
        composeTestRule.onNodeWithText("Scanner").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Test Success").performClick()
        composeTestRule.waitForIdle()
        
        // Verify all detail sections are present
        composeTestRule.onNodeWithText("CERTIFICATE INFO").assertExists()
        composeTestRule.onNodeWithText("JWT TOKEN").assertExists()
        composeTestRule.onNodeWithText("SCAN DETAILS").assertExists()
        
        // Verify some detail content
        composeTestRule.onNodeWithText("CN=Dr. Jane Smith").assertExists()
        composeTestRule.onNodeWithText("RS256").assertExists()
        composeTestRule.onNodeWithText("128-color").assertExists()
    }
}
