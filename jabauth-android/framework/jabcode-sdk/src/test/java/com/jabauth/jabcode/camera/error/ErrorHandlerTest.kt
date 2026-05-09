package com.jabauth.jabcode.camera.error

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ErrorHandler
 * 
 * Tests error handling coordination and callback notifications
 */
class ErrorHandlerTest {
    
    private lateinit var capturedErrors: MutableList<CameraError>
    private lateinit var errorHandler: ErrorHandler
    
    @Before
    fun setup() {
        capturedErrors = mutableListOf()
        errorHandler = ErrorHandler(
            strategy = RecoveryStrategy.Retry(maxAttempts = 3),
            onError = { error -> capturedErrors.add(error) }
        )
    }
    
    @Test
    fun handleError_notifiesCallback() {
        val error = CameraError(
            code = CameraError.Code.CAMERA_IN_USE,
            message = "Test",
            isRecoverable = true
        )
        
        errorHandler.handleError(error)
        
        assertEquals(1, capturedErrors.size)
        assertSame(error, capturedErrors[0])
    }
    
    @Test
    fun handleError_tracksAttemptCount() {
        val error = CameraError(
            code = CameraError.Code.CAMERA_IN_USE,
            message = "Test",
            isRecoverable = true
        )
        
        errorHandler.handleError(error)
        assertEquals(1, errorHandler.getAttemptCount())
        
        errorHandler.handleError(error)
        assertEquals(2, errorHandler.getAttemptCount())
        
        errorHandler.handleError(error)
        assertEquals(3, errorHandler.getAttemptCount())
    }
    
    @Test
    fun shouldRetry_delegatesToStrategy() {
        val recoverableError = CameraError(
            code = CameraError.Code.MAX_CAMERAS_IN_USE,
            message = "Test",
            isRecoverable = true
        )
        
        errorHandler.handleError(recoverableError)
        assertTrue("Should retry on first attempt",
            errorHandler.shouldRetry(recoverableError))
        
        errorHandler.handleError(recoverableError)
        assertTrue("Should retry on second attempt",
            errorHandler.shouldRetry(recoverableError))
        
        errorHandler.handleError(recoverableError)
        assertFalse("Should not retry after third attempt (max reached)",
            errorHandler.shouldRetry(recoverableError))
    }
    
    @Test
    fun shouldRetry_returnsFalseForNonRecoverableError() {
        val nonRecoverableError = CameraError(
            code = CameraError.Code.CAMERA_DISABLED,
            message = "Test",
            isRecoverable = false
        )
        
        errorHandler.handleError(nonRecoverableError)
        assertFalse("Should not retry non-recoverable error",
            errorHandler.shouldRetry(nonRecoverableError))
    }
    
    @Test
    fun reset_clearsAttemptCount() {
        val error = CameraError(
            code = CameraError.Code.CAMERA_IN_USE,
            message = "Test",
            isRecoverable = true
        )
        
        errorHandler.handleError(error)
        errorHandler.handleError(error)
        assertEquals(2, errorHandler.getAttemptCount())
        
        errorHandler.reset()
        
        assertEquals(0, errorHandler.getAttemptCount())
    }
    
    @Test
    fun getLastError_returnsNullInitially() {
        assertNull(errorHandler.getLastError())
    }
    
    @Test
    fun getLastError_returnsLatestError() {
        val error1 = CameraError(
            code = CameraError.Code.CAMERA_IN_USE,
            message = "First",
            isRecoverable = true
        )
        val error2 = CameraError(
            code = CameraError.Code.CAMERA_ERROR,
            message = "Second",
            isRecoverable = false
        )
        
        errorHandler.handleError(error1)
        assertSame(error1, errorHandler.getLastError())
        
        errorHandler.handleError(error2)
        assertSame(error2, errorHandler.getLastError())
    }
    
    @Test
    fun errorHandler_worksWithNoOpCallback() {
        val handler = ErrorHandler(
            strategy = RecoveryStrategy.NoRetry,
            onError = {} // No-op callback
        )
        
        val error = CameraError(
            code = CameraError.Code.CAMERA_ERROR,
            message = "Test",
            isRecoverable = false
        )
        
        // Should not throw
        handler.handleError(error)
        assertSame(error, handler.getLastError())
    }
}
