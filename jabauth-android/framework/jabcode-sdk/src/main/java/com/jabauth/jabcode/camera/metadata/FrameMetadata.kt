package com.jabauth.jabcode.camera.metadata

/**
 * Frame capture metadata from Camera2 API
 * 
 * Contains exposure settings, focus state, and auto-control (3A) states
 * 
 * @property frameNumber Sequential frame number from camera
 * @property timestamp Frame timestamp in nanoseconds (CLOCK_BOOTTIME)
 * @property exposureTimeNs Exposure time in nanoseconds (null if unavailable)
 * @property iso ISO sensitivity value (null if unavailable)
 * @property focusDistance Focus distance in diopters (null if unavailable)
 * @property afState Auto-focus state
 * @property aeState Auto-exposure state
 * @property awbState Auto-white-balance state
 */
data class FrameMetadata(
    val frameNumber: Long,
    val timestamp: Long,
    val exposureTimeNs: Long?,
    val iso: Int?,
    val focusDistance: Float?,
    val afState: AfState,
    val aeState: AeState,
    val awbState: AwbState
) {
    
    /**
     * Auto-focus (AF) states from CaptureResult.CONTROL_AF_STATE
     */
    enum class AfState {
        /** AF routine is inactive */
        INACTIVE,
        
        /** AF is performing a passive scan */
        PASSIVE_SCAN,
        
        /** AF believes it is in focus (passive) */
        PASSIVE_FOCUSED,
        
        /** AF is performing an active scan */
        ACTIVE_SCAN,
        
        /** AF believes it is focused and locked */
        FOCUSED_LOCKED,
        
        /** AF failed to focus and locked */
        NOT_FOCUSED_LOCKED,
        
        /** AF finished passive scan but not focused */
        PASSIVE_UNFOCUSED,
        
        /** AF state unknown or not available */
        UNKNOWN
    }
    
    /**
     * Auto-exposure (AE) states from CaptureResult.CONTROL_AE_STATE
     */
    enum class AeState {
        /** AE routine is inactive */
        INACTIVE,
        
        /** AE is searching for good exposure */
        SEARCHING,
        
        /** AE has converged to good exposure */
        CONVERGED,
        
        /** AE is locked */
        LOCKED,
        
        /** AE has converged but flash is required */
        FLASH_REQUIRED,
        
        /** AE is in precapture state */
        PRECAPTURE,
        
        /** AE state unknown or not available */
        UNKNOWN
    }
    
    /**
     * Auto-white-balance (AWB) states from CaptureResult.CONTROL_AWB_STATE
     */
    enum class AwbState {
        /** AWB routine is inactive */
        INACTIVE,
        
        /** AWB is searching for good color temperature */
        SEARCHING,
        
        /** AWB has converged */
        CONVERGED,
        
        /** AWB is locked */
        LOCKED,
        
        /** AWB state unknown or not available */
        UNKNOWN
    }
}
