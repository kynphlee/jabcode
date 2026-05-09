package com.jabauth.jabcode.camera.error

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for RecoveryStrategy
 * 
 * Tests retry logic and backoff strategies
 */
class RecoveryStrategyTest {
    
    @Test
    fun retryStrategy_allowsRetryForRecoverableError() {
        val strategy = RecoveryStrategy.Retry(maxAttempts = 3)
        val recoverableError = CameraError(
            code = CameraError.Code.CAMERA_IN_USE,
            message = "Test",
            isRecoverable = true
        )
        
        assertTrue("Should allow retry for recoverable error",
            strategy.shouldRetry(recoverableError, attemptNumber = 1))
    }
    
    @Test
    fun retryStrategy_blocksRetryForNonRecoverableError() {
        val strategy = RecoveryStrategy.Retry(maxAttempts = 3)
        val nonRecoverableError = CameraError(
            code = CameraError.Code.CAMERA_DISABLED,
            message = "Test",
            isRecoverable = false
        )
        
        assertFalse("Should not retry non-recoverable error",
            strategy.shouldRetry(nonRecoverableError, attemptNumber = 1))
    }
    
    @Test
    fun retryStrategy_respectsMaxAttempts() {
        val strategy = RecoveryStrategy.Retry(maxAttempts = 3)
        val error = CameraError(
            code = CameraError.Code.CAMERA_IN_USE,
            message = "Test",
            isRecoverable = true
        )
        
        assertTrue(strategy.shouldRetry(error, attemptNumber = 1))
        assertTrue(strategy.shouldRetry(error, attemptNumber = 2))
        assertTrue(strategy.shouldRetry(error, attemptNumber = 3))
        assertFalse("Should stop after max attempts",
            strategy.shouldRetry(error, attemptNumber = 4))
    }
    
    @Test
    fun exponentialBackoff_calculatesCorrectDelay() {
        val strategy = RecoveryStrategy.ExponentialBackoff(
            baseDelayMs = 100L,
            maxDelayMs = 5000L
        )
        
        assertEquals(100L, strategy.getDelayMs(attemptNumber = 1))
        assertEquals(200L, strategy.getDelayMs(attemptNumber = 2))
        assertEquals(400L, strategy.getDelayMs(attemptNumber = 3))
        assertEquals(800L, strategy.getDelayMs(attemptNumber = 4))
    }
    
    @Test
    fun exponentialBackoff_respectsMaxDelay() {
        val strategy = RecoveryStrategy.ExponentialBackoff(
            baseDelayMs = 100L,
            maxDelayMs = 500L
        )
        
        assertEquals(100L, strategy.getDelayMs(attemptNumber = 1))
        assertEquals(200L, strategy.getDelayMs(attemptNumber = 2))
        assertEquals(400L, strategy.getDelayMs(attemptNumber = 3))
        assertEquals(500L, strategy.getDelayMs(attemptNumber = 4)) // Capped
        assertEquals(500L, strategy.getDelayMs(attemptNumber = 5)) // Still capped
    }
    
    @Test
    fun linearBackoff_calculatesCorrectDelay() {
        val strategy = RecoveryStrategy.LinearBackoff(
            delayIncrementMs = 200L,
            maxDelayMs = 1000L
        )
        
        assertEquals(200L, strategy.getDelayMs(attemptNumber = 1))
        assertEquals(400L, strategy.getDelayMs(attemptNumber = 2))
        assertEquals(600L, strategy.getDelayMs(attemptNumber = 3))
        assertEquals(800L, strategy.getDelayMs(attemptNumber = 4))
        assertEquals(1000L, strategy.getDelayMs(attemptNumber = 5))
        assertEquals(1000L, strategy.getDelayMs(attemptNumber = 6)) // Capped
    }
    
    @Test
    fun noRetry_neverAllowsRetry() {
        val strategy = RecoveryStrategy.NoRetry
        val error = CameraError(
            code = CameraError.Code.CAMERA_IN_USE,
            message = "Test",
            isRecoverable = true
        )
        
        assertFalse(strategy.shouldRetry(error, attemptNumber = 1))
        assertFalse(strategy.shouldRetry(error, attemptNumber = 100))
    }
    
    @Test
    fun retryWithBackoff_combinesRetryAndDelay() {
        val strategy = RecoveryStrategy.RetryWithBackoff(
            maxAttempts = 3,
            backoff = RecoveryStrategy.ExponentialBackoff(100L, 1000L)
        )
        val error = CameraError(
            code = CameraError.Code.MAX_CAMERAS_IN_USE,
            message = "Test",
            isRecoverable = true
        )
        
        assertTrue(strategy.shouldRetry(error, attemptNumber = 1))
        assertEquals(100L, strategy.getDelayMs(attemptNumber = 1))
        
        assertTrue(strategy.shouldRetry(error, attemptNumber = 2))
        assertEquals(200L, strategy.getDelayMs(attemptNumber = 2))
        
        assertTrue(strategy.shouldRetry(error, attemptNumber = 3))
        assertEquals(400L, strategy.getDelayMs(attemptNumber = 3))
        
        assertFalse(strategy.shouldRetry(error, attemptNumber = 4))
    }
}
