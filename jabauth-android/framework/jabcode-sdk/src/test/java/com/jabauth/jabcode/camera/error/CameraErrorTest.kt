package com.jabauth.jabcode.camera.error

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CameraError data class
 * 
 * Tests basic error properties and code enum
 * Android framework-dependent tests are in instrumented tests
 */
class CameraErrorTest {
    
    @Test
    fun cameraError_storesCorrectProperties() {
        val error = CameraError(
            code = CameraError.Code.CAMERA_IN_USE,
            message = "Camera already in use",
            isRecoverable = true,
            cause = null
        )
        
        assertEquals(CameraError.Code.CAMERA_IN_USE, error.code)
        assertEquals("Camera already in use", error.message)
        assertTrue(error.isRecoverable)
        assertNull(error.cause)
    }
    
    @Test
    fun cameraError_includesCauseException() {
        val cause = RuntimeException("Test exception")
        val error = CameraError(
            code = CameraError.Code.UNKNOWN,
            message = "Unknown error",
            isRecoverable = false,
            cause = cause
        )
        
        assertSame(cause, error.cause)
    }
    
    @Test
    fun cameraErrorCode_hasAllRequiredCodes() {
        // Verify all essential error codes exist
        assertNotNull(CameraError.Code.CAMERA_IN_USE)
        assertNotNull(CameraError.Code.CAMERA_DISABLED)
        assertNotNull(CameraError.Code.CAMERA_DISCONNECTED)
        assertNotNull(CameraError.Code.CAMERA_ERROR)
        assertNotNull(CameraError.Code.MAX_CAMERAS_IN_USE)
        assertNotNull(CameraError.Code.CAMERA_ACCESS_DENIED)
        assertNotNull(CameraError.Code.UNKNOWN)
    }
    
    @Test
    fun cameraError_dataClassEquality() {
        val error1 = CameraError(
            code = CameraError.Code.CAMERA_IN_USE,
            message = "Test",
            isRecoverable = true
        )
        
        val error2 = CameraError(
            code = CameraError.Code.CAMERA_IN_USE,
            message = "Test",
            isRecoverable = true
        )
        
        assertEquals(error1, error2)
    }
    
    @Test
    fun cameraError_copyWorks() {
        val error = CameraError(
            code = CameraError.Code.CAMERA_ERROR,
            message = "Original",
            isRecoverable = false
        )
        
        val copied = error.copy(message = "Modified")
        
        assertEquals(CameraError.Code.CAMERA_ERROR, copied.code)
        assertEquals("Modified", copied.message)
        assertFalse(copied.isRecoverable)
    }
}
