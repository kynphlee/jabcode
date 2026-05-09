package com.jabauth.jabcode.camera.error

/**
 * Coordinates error handling with recovery strategies
 * 
 * Handles Camera2 errors, tracks retry attempts, and notifies listeners
 * 
 * @property strategy Recovery strategy for retry logic
 * @property onError Callback invoked when errors occur
 */
class ErrorHandler(
    private val strategy: RecoveryStrategy,
    private val onError: (CameraError) -> Unit
) {
    
    private var attemptCount: Int = 0
    private var lastError: CameraError? = null
    
    /**
     * Handle a camera error
     * 
     * @param error The error that occurred
     */
    fun handleError(error: CameraError) {
        lastError = error
        attemptCount++
        onError(error)
    }
    
    /**
     * Determine if error should be retried
     * 
     * @param error The error to evaluate
     * @return true if retry should be attempted
     */
    fun shouldRetry(error: CameraError): Boolean {
        return strategy.shouldRetry(error, attemptCount + 1)
    }
    
    /**
     * Get current attempt count
     * 
     * @return Number of attempts made
     */
    fun getAttemptCount(): Int = attemptCount
    
    /**
     * Get last error that occurred
     * 
     * @return Last error or null if none
     */
    fun getLastError(): CameraError? = lastError
    
    /**
     * Reset error tracking state
     * 
     * Call when operation succeeds or when starting fresh
     */
    fun reset() {
        attemptCount = 0
        lastError = null
    }
    
    /**
     * Get delay before next retry (if applicable)
     * 
     * @return Delay in milliseconds
     */
    fun getRetryDelayMs(): Long {
        return strategy.getDelayMs(attemptCount + 1)
    }
}
