package com.jabauth.jabcode.camera.error

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraDevice

/**
 * Represents a Camera2 API error with recovery metadata
 * 
 * @property code Error classification code
 * @property message Human-readable error description
 * @property isRecoverable Whether error can be recovered via retry
 * @property cause Original exception if available
 */
data class CameraError(
    val code: Code,
    val message: String,
    val isRecoverable: Boolean,
    val cause: Throwable? = null
) {
    
    /**
     * Error classification codes
     */
    enum class Code {
        /** Camera is currently in use by another process */
        CAMERA_IN_USE,
        
        /** Camera has been disabled by device policy */
        CAMERA_DISABLED,
        
        /** Camera device disconnected (external camera unplugged) */
        CAMERA_DISCONNECTED,
        
        /** Fatal camera hardware/service error */
        CAMERA_ERROR,
        
        /** Maximum number of cameras already in use */
        MAX_CAMERAS_IN_USE,
        
        /** Camera access denied (missing permissions) */
        CAMERA_ACCESS_DENIED,
        
        /** Unknown or unmapped error */
        UNKNOWN
    }
    
    companion object {
        /**
         * Create CameraError from CameraAccessException
         */
        fun fromAccessException(exception: CameraAccessException): CameraError {
            val (code, message, isRecoverable) = when (exception.reason) {
                CameraAccessException.CAMERA_IN_USE -> Triple(
                    Code.CAMERA_IN_USE,
                    "Camera is already in use by another process",
                    true
                )
                
                CameraAccessException.CAMERA_DISABLED -> Triple(
                    Code.CAMERA_DISABLED,
                    "Camera has been disabled due to device policy",
                    false
                )
                
                CameraAccessException.CAMERA_DISCONNECTED -> Triple(
                    Code.CAMERA_DISCONNECTED,
                    "Camera device was disconnected",
                    false
                )
                
                CameraAccessException.CAMERA_ERROR -> Triple(
                    Code.CAMERA_ERROR,
                    "Fatal camera hardware/service error",
                    false
                )
                
                CameraAccessException.MAX_CAMERAS_IN_USE -> Triple(
                    Code.MAX_CAMERAS_IN_USE,
                    "Maximum number of cameras are already in use",
                    true
                )
                
                else -> Triple(
                    Code.UNKNOWN,
                    "Unknown camera access error: ${exception.reason}",
                    false
                )
            }
            
            return CameraError(
                code = code,
                message = message,
                isRecoverable = isRecoverable,
                cause = exception
            )
        }
        
        /**
         * Create CameraError from StateCallback error code
         */
        fun fromStateCallback(errorCode: Int, cameraId: String): CameraError {
            val (code, message, isRecoverable) = when (errorCode) {
                CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> Triple(
                    Code.CAMERA_IN_USE,
                    "Camera $cameraId is already in use",
                    true
                )
                
                CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> Triple(
                    Code.CAMERA_DISABLED,
                    "Camera $cameraId has been disabled",
                    false
                )
                
                CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> Triple(
                    Code.CAMERA_ERROR,
                    "Fatal error occurred with camera $cameraId",
                    false
                )
                
                CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> Triple(
                    Code.CAMERA_ERROR,
                    "Camera service encountered a fatal error for camera $cameraId",
                    false
                )
                
                CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> Triple(
                    Code.MAX_CAMERAS_IN_USE,
                    "Maximum cameras in use, cannot open camera $cameraId",
                    true
                )
                
                else -> Triple(
                    Code.UNKNOWN,
                    "Unknown error $errorCode for camera $cameraId",
                    false
                )
            }
            
            return CameraError(
                code = code,
                message = message,
                isRecoverable = isRecoverable,
                cause = null
            )
        }
    }
}
