package com.jabauth.jabcode.camera.metadata

import android.hardware.camera2.CaptureResult

/**
 * Extracts frame metadata from Camera2 CaptureResult
 */
class MetadataExtractor {
    
    /**
     * Extract FrameMetadata from CaptureResult
     * 
     * @param result Capture result from camera
     * @return FrameMetadata with extracted values
     */
    fun extract(result: CaptureResult): FrameMetadata {
        return FrameMetadata(
            frameNumber = result.frameNumber,
            timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: 0L,
            exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
            focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE),
            afState = mapAfState(result.get(CaptureResult.CONTROL_AF_STATE)),
            aeState = mapAeState(result.get(CaptureResult.CONTROL_AE_STATE)),
            awbState = mapAwbState(result.get(CaptureResult.CONTROL_AWB_STATE))
        )
    }
    
    /**
     * Map Camera2 AF state to FrameMetadata.AfState
     */
    fun mapAfState(state: Int?): FrameMetadata.AfState {
        return when (state) {
            CaptureResult.CONTROL_AF_STATE_INACTIVE -> FrameMetadata.AfState.INACTIVE
            CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> FrameMetadata.AfState.PASSIVE_SCAN
            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> FrameMetadata.AfState.PASSIVE_FOCUSED
            CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN -> FrameMetadata.AfState.ACTIVE_SCAN
            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> FrameMetadata.AfState.FOCUSED_LOCKED
            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> FrameMetadata.AfState.NOT_FOCUSED_LOCKED
            CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> FrameMetadata.AfState.PASSIVE_UNFOCUSED
            else -> FrameMetadata.AfState.UNKNOWN
        }
    }
    
    /**
     * Map Camera2 AE state to FrameMetadata.AeState
     */
    fun mapAeState(state: Int?): FrameMetadata.AeState {
        return when (state) {
            CaptureResult.CONTROL_AE_STATE_INACTIVE -> FrameMetadata.AeState.INACTIVE
            CaptureResult.CONTROL_AE_STATE_SEARCHING -> FrameMetadata.AeState.SEARCHING
            CaptureResult.CONTROL_AE_STATE_CONVERGED -> FrameMetadata.AeState.CONVERGED
            CaptureResult.CONTROL_AE_STATE_LOCKED -> FrameMetadata.AeState.LOCKED
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED -> FrameMetadata.AeState.FLASH_REQUIRED
            CaptureResult.CONTROL_AE_STATE_PRECAPTURE -> FrameMetadata.AeState.PRECAPTURE
            else -> FrameMetadata.AeState.UNKNOWN
        }
    }
    
    /**
     * Map Camera2 AWB state to FrameMetadata.AwbState
     */
    fun mapAwbState(state: Int?): FrameMetadata.AwbState {
        return when (state) {
            CaptureResult.CONTROL_AWB_STATE_INACTIVE -> FrameMetadata.AwbState.INACTIVE
            CaptureResult.CONTROL_AWB_STATE_SEARCHING -> FrameMetadata.AwbState.SEARCHING
            CaptureResult.CONTROL_AWB_STATE_CONVERGED -> FrameMetadata.AwbState.CONVERGED
            CaptureResult.CONTROL_AWB_STATE_LOCKED -> FrameMetadata.AwbState.LOCKED
            else -> FrameMetadata.AwbState.UNKNOWN
        }
    }
}
