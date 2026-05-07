package com.jabauth.ui.scanner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for Result Panel components
 * 
 * Tests bottom sheet authentication result display with:
 * - Success/failure states
 * - Validation badges
 * - Certificate/JWT/Scan details
 * - Action buttons (Accept/Retry/Scan Again)
 * - Dismiss interactions
 * 
 * Phase 3 Day 4: Result Panel Testing
 */
class ResultPanelTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    // ========================================================================
    // Test Data
    // ========================================================================
    
    private val mockSuccessResult = AuthenticationResult(
        status = ResultStatus.SUCCESS,
        subject = "test-user@example.com",
        validations = listOf(
            ValidationCheck("Certificate Valid", passed = true),
            ValidationCheck("JWT Signature", passed = true),
            ValidationCheck("Not Expired", passed = true)
        ),
        certificateInfo = CertificateInfo(
            subject = "CN=Test User",
            issuer = "CN=Test CA",
            validUntil = "2025-12-31",
            serial = "ABC123"
        ),
        jwtInfo = JWTInfo(
            subject = "test-user",
            algorithm = "RS256",
            issued = "2024-01-01",
            expires = "2025-01-01"
        ),
        scanDetails = ScanDetails(
            colorMode = "128-color",
            eccLevel = "High",
            decodeTime = "89ms",
            quality = "Excellent"
        )
    )
    
    private val mockFailureResult = mockSuccessResult.copy(
        status = ResultStatus.FAILED,
        validations = listOf(
            ValidationCheck("Certificate Valid", passed = true),
            ValidationCheck("JWT Signature", passed = false),
            ValidationCheck("Not Expired", passed = false)
        )
    )
    
    // ========================================================================
    // Result Header Tests
    // ========================================================================
    
    @Test
    fun resultHeader_success_displaysCorrectIcon() {
        composeTestRule.setContent {
            ResultHeader(
                status = ResultStatus.SUCCESS,
                title = "Authentication Valid",
                subtitle = "test-user@example.com",
                onClose = {}
            )
        }
        
        composeTestRule.onNodeWithText("✓").assertExists()
        composeTestRule.onNodeWithText("Authentication Valid").assertExists()
        composeTestRule.onNodeWithText("test-user@example.com").assertExists()
    }
    
    @Test
    fun resultHeader_failure_displaysCorrectIcon() {
        composeTestRule.setContent {
            ResultHeader(
                status = ResultStatus.FAILED,
                title = "Authentication Failed",
                subtitle = "test-user@example.com",
                onClose = {}
            )
        }
        
        // Header contains 2 ✕: status icon + close button
        composeTestRule.onAllNodesWithText("✕").assertCountEquals(2)
        composeTestRule.onNodeWithText("Authentication Failed").assertExists()
    }
    
    @Test
    fun resultHeader_closeButton_isClickable() {
        var closeClicked = false
        
        composeTestRule.setContent {
            ResultHeader(
                status = ResultStatus.SUCCESS,
                title = "Test",
                subtitle = "Subtitle",
                onClose = { closeClicked = true }
            )
        }
        
        composeTestRule.onNodeWithText("✕").performClick()
        assert(closeClicked)
    }
    
    // ========================================================================
    // Validation Badges Tests
    // ========================================================================
    
    @Test
    fun validationBadges_displaysAllChecks() {
        val validations = listOf(
            ValidationCheck("Certificate Valid", passed = true),
            ValidationCheck("JWT Signature", passed = true),
            ValidationCheck("Not Expired", passed = false)
        )
        
        composeTestRule.setContent {
            ValidationBadges(validations = validations)
        }
        
        composeTestRule.onNodeWithText("CERTIFICATE VALID").assertExists()
        composeTestRule.onNodeWithText("JWT SIGNATURE").assertExists()
        composeTestRule.onNodeWithText("NOT EXPIRED").assertExists()
    }
    
    @Test
    fun validationBadges_success_showsCheckmark() {
        composeTestRule.setContent {
            ValidationBadges(
                validations = listOf(
                    ValidationCheck("Test Check", passed = true)
                )
            )
        }
        
        // Success badges show ✓ icon
        composeTestRule.onAllNodesWithText("✓").assertCountEquals(1)
    }
    
    @Test
    fun validationBadges_failure_showsCross() {
        composeTestRule.setContent {
            ValidationBadges(
                validations = listOf(
                    ValidationCheck("Failed Check", passed = false)
                )
            )
        }
        
        // Failure badges show ✕ icon
        composeTestRule.onAllNodesWithText("✕").assertCountEquals(1)
    }
    
    // ========================================================================
    // Detail Section Tests
    // ========================================================================
    
    @Test
    fun detailSection_displaysLabel() {
        composeTestRule.setContent {
            DetailSection(
                label = "Certificate Info",
                rows = listOf("Subject" to "CN=Test")
            )
        }
        
        composeTestRule.onNodeWithText("CERTIFICATE INFO").assertExists()
    }
    
    @Test
    fun detailSection_displaysAllRows() {
        val rows = listOf(
            "Subject" to "CN=Test User",
            "Issuer" to "CN=Test CA",
            "Serial" to "ABC123"
        )
        
        composeTestRule.setContent {
            DetailSection(
                label = "Test Section",
                rows = rows
            )
        }
        
        composeTestRule.onNodeWithText("Subject:").assertExists()
        composeTestRule.onNodeWithText("CN=Test User").assertExists()
        composeTestRule.onNodeWithText("Issuer:").assertExists()
        composeTestRule.onNodeWithText("CN=Test CA").assertExists()
        composeTestRule.onNodeWithText("Serial:").assertExists()
        composeTestRule.onNodeWithText("ABC123").assertExists()
    }
    
    // ========================================================================
    // Full Result Panel Tests - Success State
    // ========================================================================
    
    @Test
    fun resultPanel_success_displaysAllComponents() {
        composeTestRule.setContent {
            ResultPanel(
                result = mockSuccessResult,
                onDismiss = {},
                onAccept = {},
                onScanAgain = {}
            )
        }
        
        // Header
        composeTestRule.onNodeWithText("Authentication Valid").assertExists()
        composeTestRule.onNodeWithText(mockSuccessResult.subject).assertExists()
        
        // Validation badges
        composeTestRule.onNodeWithText("CERTIFICATE VALID").assertExists()
        composeTestRule.onNodeWithText("JWT SIGNATURE").assertExists()
        composeTestRule.onNodeWithText("NOT EXPIRED").assertExists()
        
        // Certificate info
        composeTestRule.onNodeWithText("CERTIFICATE INFO").assertExists()
        composeTestRule.onNodeWithText("CN=Test User").assertExists()
        
        // JWT info
        composeTestRule.onNodeWithText("JWT TOKEN").assertExists()
        composeTestRule.onNodeWithText("RS256").assertExists()
        
        // Scan details
        composeTestRule.onNodeWithText("SCAN DETAILS").assertExists()
        composeTestRule.onNodeWithText("128-color").assertExists()
        
        // Action buttons
        composeTestRule.onNodeWithText("✓ Accept").assertExists()
        composeTestRule.onNodeWithText("↻ Scan Again").assertExists()
    }
    
    @Ignore("ModalBottomSheet buttons not clickable in Compose tests - see ResultPanelInteractionTest for UI Automator coverage")
    @Test
    fun resultPanel_success_acceptButton_isClickable() {
        var acceptClicked = false
        
        composeTestRule.setContent {
            ResultPanel(
                result = mockSuccessResult,
                onDismiss = {},
                onAccept = { acceptClicked = true },
                onScanAgain = {}
            )
        }
        
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("✓ Accept").performClick()
        composeTestRule.waitForIdle()
        assert(acceptClicked) { "Accept button was not clicked" }
    }
    
    @Ignore("ModalBottomSheet buttons not clickable in Compose tests - see ResultPanelInteractionTest for UI Automator coverage")
    @Test
    fun resultPanel_success_scanAgainButton_isClickable() {
        var scanAgainClicked = false
        
        composeTestRule.setContent {
            ResultPanel(
                result = mockSuccessResult,
                onDismiss = {},
                onAccept = {},
                onScanAgain = { scanAgainClicked = true }
            )
        }
        
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("↻ Scan Again").performClick()
        composeTestRule.waitForIdle()
        assert(scanAgainClicked) { "Scan Again button was not clicked" }
    }
    
    // ========================================================================
    // Full Result Panel Tests - Failure State
    // ========================================================================
    
    @Test
    fun resultPanel_failure_displaysFailureStatus() {
        composeTestRule.setContent {
            ResultPanel(
                result = mockFailureResult,
                onDismiss = {},
                onAccept = {},
                onScanAgain = {}
            )
        }
        
        composeTestRule.onNodeWithText("Authentication Failed").assertExists()
        // Multiple ✕ exist: header icon + close button + failed validation badges
        composeTestRule.onAllNodesWithText("✕")[0].assertExists()
    }
    
    @Test
    fun resultPanel_failure_showsRetryButton() {
        composeTestRule.setContent {
            ResultPanel(
                result = mockFailureResult,
                onDismiss = {},
                onAccept = {},
                onScanAgain = {}
            )
        }
        
        // Failure shows Retry button instead of Accept
        composeTestRule.onNodeWithText("↻ Retry").assertExists()
        composeTestRule.onNodeWithText("✓ Accept").assertDoesNotExist()
    }
    
    @Ignore("ModalBottomSheet buttons not clickable in Compose tests - see ResultPanelInteractionTest for UI Automator coverage")
    @Test
    fun resultPanel_failure_retryButton_isClickable() {
        var retryClicked = false
        
        composeTestRule.setContent {
            ResultPanel(
                result = mockFailureResult,
                onDismiss = {},
                onAccept = {},
                onScanAgain = { retryClicked = true }
            )
        }
        
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("↻ Retry").performClick()
        composeTestRule.waitForIdle()
        assert(retryClicked) { "Retry button was not clicked" }
    }
    
    @Test
    fun resultPanel_failure_showsFailedValidations() {
        composeTestRule.setContent {
            ResultPanel(
                result = mockFailureResult,
                onDismiss = {},
                onAccept = {},
                onScanAgain = {}
            )
        }
        
        // Should show both passed and failed validations
        composeTestRule.onNodeWithText("CERTIFICATE VALID").assertExists()
        composeTestRule.onNodeWithText("JWT SIGNATURE").assertExists()
        composeTestRule.onNodeWithText("NOT EXPIRED").assertExists()
    }
    
    // ========================================================================
    // Null Result Tests
    // ========================================================================
    
    @Test
    fun resultPanel_nullResult_doesNotDisplay() {
        composeTestRule.setContent {
            ResultPanel(
                result = null,
                onDismiss = {},
                onAccept = {},
                onScanAgain = {}
            )
        }
        
        // Panel should not be visible when result is null
        composeTestRule.onNodeWithText("Authentication Valid").assertDoesNotExist()
        composeTestRule.onNodeWithText("Authentication Failed").assertDoesNotExist()
    }
    
    // ========================================================================
    // Dismiss Tests
    // ========================================================================
    
    @Test
    fun resultPanel_closeButton_callsOnDismiss() {
        var dismissCalled = false
        
        composeTestRule.setContent {
            ResultPanel(
                result = mockSuccessResult,
                onDismiss = { dismissCalled = true },
                onAccept = {},
                onScanAgain = {}
            )
        }
        
        // Find and click close button (✕)
        composeTestRule.onAllNodesWithText("✕")[0].performClick()
        assert(dismissCalled)
    }
}
