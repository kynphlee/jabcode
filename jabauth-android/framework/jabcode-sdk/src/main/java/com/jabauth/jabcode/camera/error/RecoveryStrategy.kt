package com.jabauth.jabcode.camera.error

import kotlin.math.min
import kotlin.math.pow

/**
 * Strategy for error recovery and retry logic
 */
sealed class RecoveryStrategy {
    
    /**
     * Determine if error should be retried
     * 
     * @param error The error that occurred
     * @param attemptNumber Current attempt number (1-indexed)
     * @return true if should retry
     */
    abstract fun shouldRetry(error: CameraError, attemptNumber: Int): Boolean
    
    /**
     * Get delay before next retry attempt
     * 
     * @param attemptNumber Current attempt number (1-indexed)
     * @return Delay in milliseconds
     */
    open fun getDelayMs(attemptNumber: Int): Long = 0L
    
    /**
     * Simple retry with max attempts
     */
    data class Retry(val maxAttempts: Int) : RecoveryStrategy() {
        override fun shouldRetry(error: CameraError, attemptNumber: Int): Boolean {
            return error.isRecoverable && attemptNumber <= maxAttempts
        }
    }
    
    /**
     * Exponential backoff delay calculator
     */
    data class ExponentialBackoff(
        val baseDelayMs: Long,
        val maxDelayMs: Long
    ) : RecoveryStrategy() {
        override fun shouldRetry(error: CameraError, attemptNumber: Int): Boolean = false
        
        override fun getDelayMs(attemptNumber: Int): Long {
            val exponentialDelay = baseDelayMs * 2.0.pow(attemptNumber - 1).toLong()
            return min(exponentialDelay, maxDelayMs)
        }
    }
    
    /**
     * Linear backoff delay calculator
     */
    data class LinearBackoff(
        val delayIncrementMs: Long,
        val maxDelayMs: Long
    ) : RecoveryStrategy() {
        override fun shouldRetry(error: CameraError, attemptNumber: Int): Boolean = false
        
        override fun getDelayMs(attemptNumber: Int): Long {
            val linearDelay = delayIncrementMs * attemptNumber
            return min(linearDelay, maxDelayMs)
        }
    }
    
    /**
     * Retry with backoff strategy
     */
    data class RetryWithBackoff(
        val maxAttempts: Int,
        val backoff: RecoveryStrategy
    ) : RecoveryStrategy() {
        override fun shouldRetry(error: CameraError, attemptNumber: Int): Boolean {
            return error.isRecoverable && attemptNumber <= maxAttempts
        }
        
        override fun getDelayMs(attemptNumber: Int): Long {
            return backoff.getDelayMs(attemptNumber)
        }
    }
    
    /**
     * Never retry
     */
    object NoRetry : RecoveryStrategy() {
        override fun shouldRetry(error: CameraError, attemptNumber: Int): Boolean = false
    }
    
    companion object {
        /**
         * Default strategy: 3 retries with exponential backoff
         */
        fun default(): RecoveryStrategy = RetryWithBackoff(
            maxAttempts = 3,
            backoff = ExponentialBackoff(
                baseDelayMs = 100L,
                maxDelayMs = 5000L
            )
        )
    }
}
