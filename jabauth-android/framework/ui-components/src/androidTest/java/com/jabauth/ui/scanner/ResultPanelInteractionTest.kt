package com.jabauth.ui.scanner

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI Automator tests for Result Panel button interactions
 *
 * Uses UI Automator to test ModalBottomSheet button clicks that are not
 * accessible via standard Compose testing due to framework limitations.
 *
 * These tests complement ResultPanelTest by validating actual button
 * interaction behavior in the bottom sheet modal.
 *
 * Phase 3 Day 4: Result Panel Interaction Testing
 */
class ResultPanelInteractionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var device: UiDevice

    // Test data
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

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    // ========================================================================
    // Success State Button Tests
    // ========================================================================

    @Test
    fun resultPanel_success_acceptButton_clicks() {
        var acceptClicked = false

        composeTestRule.setContent {
            ResultPanel(
                result = mockSuccessResult,
                onDismiss = {},
                onAccept = { acceptClicked = true },
                onScanAgain = {}
            )
        }

        // Wait for bottom sheet to appear
        composeTestRule.waitForIdle()

        // Verify button exists in Compose tree
        composeTestRule.onNodeWithText("✓ Accept").assertExists()

        // Use UI Automator to click the button
        val acceptButton = device.wait(
            Until.findObject(By.text("✓ Accept")),
            3000
        )

        assert(acceptButton != null) { "Accept button not found via UI Automator" }
        acceptButton?.click()

        // Wait for click to process
        Thread.sleep(500)

        assert(acceptClicked) { "Accept button callback was not triggered" }
    }

    @Test
    fun resultPanel_success_scanAgainButton_clicks() {
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

        // Verify button exists
        composeTestRule.onNodeWithText("↻ Scan Again").assertExists()

        // Click via UI Automator
        val scanAgainButton = device.wait(
            Until.findObject(By.text("↻ Scan Again")),
            3000
        )

        assert(scanAgainButton != null) { "Scan Again button not found via UI Automator" }
        scanAgainButton?.click()

        Thread.sleep(500)

        assert(scanAgainClicked) { "Scan Again button callback was not triggered" }
    }

    // ========================================================================
    // Failure State Button Tests
    // ========================================================================

    @Test
    fun resultPanel_failure_retryButton_clicks() {
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

        // Verify button exists
        composeTestRule.onNodeWithText("↻ Retry").assertExists()

        // Click via UI Automator
        val retryButton = device.wait(
            Until.findObject(By.text("↻ Retry")),
            3000
        )

        assert(retryButton != null) { "Retry button not found via UI Automator" }
        retryButton?.click()

        Thread.sleep(500)

        assert(retryClicked) { "Retry button callback was not triggered" }
    }

    // ========================================================================
    // Close Button Tests
    // ========================================================================

    @Test
    fun resultPanel_closeButton_clicks() {
        var dismissClicked = false

        composeTestRule.setContent {
            ResultPanel(
                result = mockSuccessResult,
                onDismiss = { dismissClicked = true },
                onAccept = {},
                onScanAgain = {}
            )
        }

        composeTestRule.waitForIdle()

        // Find close button (✕) via UI Automator
        // Note: Multiple ✕ exist, so we need to find the button specifically
        val closeButtons = device.wait(
            Until.findObjects(By.text("✕")),
            3000
        )

        assert(closeButtons.isNotEmpty()) { "No close buttons found" }

        // The close button is typically the last ✕ in the hierarchy
        val closeButton = closeButtons.lastOrNull()

        assert(closeButton != null) { "Close button not found via UI Automator" }
        closeButton?.click()

        Thread.sleep(500)

        assert(dismissClicked) { "Close button callback was not triggered" }
    }

    // ========================================================================
    // Multi-Step Interaction Tests
    // ========================================================================

    @Test
    fun resultPanel_multipleClicks_bothButtonsWork() {
        var acceptClickCount = 0
        var scanAgainClickCount = 0

        composeTestRule.setContent {
            ResultPanel(
                result = mockSuccessResult,
                onDismiss = {},
                onAccept = { acceptClickCount++ },
                onScanAgain = { scanAgainClickCount++ }
            )
        }

        composeTestRule.waitForIdle()

        // Click Accept button
        val acceptButton = device.wait(
            Until.findObject(By.text("✓ Accept")),
            3000
        )
        acceptButton?.click()
        Thread.sleep(500)

        // Click Scan Again button
        val scanAgainButton = device.wait(
            Until.findObject(By.text("↻ Scan Again")),
            3000
        )
        scanAgainButton?.click()
        Thread.sleep(500)

        // Both callbacks should have fired once
        assert(acceptClickCount == 1) { "Accept clicked $acceptClickCount times, expected 1" }
        assert(scanAgainClickCount == 1) { "Scan Again clicked $scanAgainClickCount times, expected 1" }
    }

    @Test
    fun resultPanel_failure_retryMultipleTimes_works() {
        var retryCount = 0

        composeTestRule.setContent {
            ResultPanel(
                result = mockFailureResult,
                onDismiss = {},
                onAccept = {},
                onScanAgain = { retryCount++ }
            )
        }

        composeTestRule.waitForIdle()

        // Click Retry button multiple times
        repeat(3) {
            val retryButton = device.wait(
                Until.findObject(By.text("↻ Retry")),
                3000
            )
            retryButton?.click()
            Thread.sleep(300)
        }

        assert(retryCount == 3) { "Retry clicked $retryCount times, expected 3" }
    }
}
