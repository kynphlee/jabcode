package com.jabauth.jabcode.camera.error

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraDevice
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for CameraError factory methods
 * 
 * Tests Android framework-dependent error mapping
 */
@RunWith(AndroidJUnit4::class)
class CameraErrorInstrumentedTest {
    
    @Test
    fun fromAccessException_mapsInUseCorrectly() {
        val exception = CameraAccessException(CameraAccessException.CAMERA_IN_USE)
        
        val error = CameraError.fromAccessException(exception)
        
        assertEquals(CameraError.Code.CAMERA_IN_USE, error.code)
        assertTrue("CAMERA_IN_USE should be recoverable", error.isRecoverable)
        assertSame(exception, error.cause)
        assertTrue(error.message.contains("in use", ignoreCase = true))
    }
    
    @Test
    fun fromAccessException_mapsDisabledCorrectly() {
        val exception = CameraAccessException(CameraAccessException.CAMERA_DISABLED)
        
        val error = CameraError.fromAccessException(exception)
        
        assertEquals(CameraError.Code.CAMERA_DISABLED, error.code)
        assertFalse("CAMERA_DISABLED should not be recoverable", error.isRecoverable)
        assertSame(exception, error.cause)
        assertTrue(error.message.contains("disabled", ignoreCase = true))
    }
    
    @Test
    fun fromAccessException_mapsDisconnectedCorrectly() {
        val exception = CameraAccessException(CameraAccessException.CAMERA_DISCONNECTED)
        
        val error = CameraError.fromAccessException(exception)
        
        assertEquals(CameraError.Code.CAMERA_DISCONNECTED, error.code)
        assertFalse("CAMERA_DISCONNECTED should not be recoverable", error.isRecoverable)
        assertTrue(error.message.contains("disconnected", ignoreCase = true))
    }
    
    @Test
    fun fromAccessException_mapsErrorCorrectly() {
        val exception = CameraAccessException(CameraAccessException.CAMERA_ERROR)
        
        val error = CameraError.fromAccessException(exception)
        
        assertEquals(CameraError.Code.CAMERA_ERROR, error.code)
        assertFalse("CAMERA_ERROR should not be recoverable", error.isRecoverable)
        assertTrue(error.message.contains("fatal", ignoreCase = true))
    }
    
    @Test
    fun fromAccessException_mapsMaxCamerasCorrectly() {
        val exception = CameraAccessException(CameraAccessException.MAX_CAMERAS_IN_USE)
        
        val error = CameraError.fromAccessException(exception)
        
        assertEquals(CameraError.Code.MAX_CAMERAS_IN_USE, error.code)
        assertTrue("MAX_CAMERAS_IN_USE should be recoverable", error.isRecoverable)
        assertTrue(error.message.contains("maximum", ignoreCase = true))
    }
    
    @Test
    fun fromStateCallback_mapsInUseCorrectly() {
        val error = CameraError.fromStateCallback(
            errorCode = CameraDevice.StateCallback.ERROR_CAMERA_IN_USE,
            cameraId = "0"
        )
        
        assertEquals(CameraError.Code.CAMERA_IN_USE, error.code)
        assertTrue(error.isRecoverable)
        assertTrue(error.message.contains("0"))
    }
    
    @Test
    fun fromStateCallback_mapsDeviceErrorCorrectly() {
        val error = CameraError.fromStateCallback(
            errorCode = CameraDevice.StateCallback.ERROR_CAMERA_DEVICE,
            cameraId = "1"
        )
        
        assertEquals(CameraError.Code.CAMERA_ERROR, error.code)
        assertFalse(error.isRecoverable)
        assertTrue(error.message.contains("1"))
    }
    
    @Test
    fun fromStateCallback_mapsServiceErrorCorrectly() {
        val error = CameraError.fromStateCallback(
            errorCode = CameraDevice.StateCallback.ERROR_CAMERA_SERVICE,
            cameraId = "2"
        )
        
        assertEquals(CameraError.Code.CAMERA_ERROR, error.code)
        assertFalse(error.isRecoverable)
        assertTrue(error.message.contains("2"))
    }
    
    @Test
    fun fromStateCallback_mapsDisabledErrorCorrectly() {
        val error = CameraError.fromStateCallback(
            errorCode = CameraDevice.StateCallback.ERROR_CAMERA_DISABLED,
            cameraId = "0"
        )
        
        assertEquals(CameraError.Code.CAMERA_DISABLED, error.code)
        assertFalse(error.isRecoverable)
    }
    
    @Test
    fun fromStateCallback_mapsMaxCamerasErrorCorrectly() {
        val error = CameraError.fromStateCallback(
            errorCode = CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE,
            cameraId = "0"
        )
        
        assertEquals(CameraError.Code.MAX_CAMERAS_IN_USE, error.code)
        assertTrue(error.isRecoverable)
    }
}
