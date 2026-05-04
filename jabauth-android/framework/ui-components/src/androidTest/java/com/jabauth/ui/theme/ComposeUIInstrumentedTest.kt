package com.jabauth.ui.theme

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import com.jabauth.ui.scanner.ScanStatus
import com.jabauth.ui.scanner.ScanStatusOverlay
import com.jabauth.ui.scanner.ScannerHeader
import com.jabauth.ui.scanner.QualityIndicator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for Compose UI components
 * 
 * Tests real rendering on Android device.
 * Validates theme application, composables, and interactions.
 */
@RunWith(AndroidJUnit4::class)
class ComposeUIInstrumentedTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun jabAuthThemeRendersSuccessfully() {
        composeTestRule.setContent {
            JABAuthTheme {
                Text("Theme Test")
            }
        }
        
        composeTestRule.onNodeWithText("Theme Test").assertIsDisplayed()
    }
    
    @Test
    fun scannerHeaderDisplaysTitle() {
        composeTestRule.setContent {
            JABAuthTheme {
                ScannerHeader(
                    title = "Scan JABCode",
                    onBackClick = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("Scan JABCode").assertIsDisplayed()
    }
    
    @Test
    fun scannerHeaderBackButtonWorks() {
        var backClicked = false
        
        composeTestRule.setContent {
            JABAuthTheme {
                ScannerHeader(
                    title = "Test",
                    onBackClick = { backClicked = true }
                )
            }
        }
        
        composeTestRule.onNodeWithContentDescription("Back")
            .assertIsDisplayed()
            .performClick()
        
        assertTrue(backClicked)
    }
    
    @Test
    fun scanStatusOverlayShowsScanningState() {
        composeTestRule.setContent {
            JABAuthTheme {
                ScanStatusOverlay(
                    status = ScanStatus.SCANNING,
                    message = "Scanning..."
                )
            }
        }
        
        composeTestRule.onNodeWithText("Scanning...").assertIsDisplayed()
    }
    
    @Test
    fun scanStatusOverlayShowsSuccessState() {
        composeTestRule.setContent {
            JABAuthTheme {
                ScanStatusOverlay(
                    status = ScanStatus.SUCCESS,
                    message = "Success"
                )
            }
        }
        
        composeTestRule.onNodeWithText("Success").assertIsDisplayed()
    }
    
    @Test
    fun qualityIndicatorDisplaysLabel() {
        composeTestRule.setContent {
            JABAuthTheme {
                QualityIndicator(
                    label = "Brightness",
                    value = 0.85f
                )
            }
        }
        
        composeTestRule.onNodeWithText("Brightness").assertIsDisplayed()
        composeTestRule.onNodeWithText("85%").assertIsDisplayed()
    }
    
    @Test
    fun qualityIndicatorShowsPercentage() {
        composeTestRule.setContent {
            JABAuthTheme {
                QualityIndicator(
                    label = "Contrast",
                    value = 0.72f
                )
            }
        }
        
        composeTestRule.onNodeWithText("72%").assertIsDisplayed()
    }
}
