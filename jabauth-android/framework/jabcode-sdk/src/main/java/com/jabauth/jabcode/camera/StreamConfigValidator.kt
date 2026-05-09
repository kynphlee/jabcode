package com.jabauth.jabcode.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size
import android.graphics.ImageFormat

/**
 * Validates Camera2 stream configurations
 * 
 * Prevents session configuration failures by checking:
 * - Hardware level guarantees
 * - Format support
 * - Size support
 * - Stream combination validity
 */
class StreamConfigValidator {
    
    data class StreamConfig(
        val format: Int,
        val size: Size,
        val isInput: Boolean = false
    )
    
    data class ValidationResult(
        val isValid: Boolean,
        val reason: String? = null
    )
    
    /**
     * Validate stream configuration for a camera
     * 
     * @param cameraInfo Camera to validate against
     * @param streams List of desired streams
     * @return ValidationResult indicating if configuration is supported
     */
    fun validate(
        cameraInfo: CameraInfo,
        streams: List<StreamConfig>
    ): ValidationResult {
        for (stream in streams) {
            if (!isStreamSupported(cameraInfo, stream)) {
                return ValidationResult(
                    isValid = false,
                    reason = "Stream ${stream.format}@${stream.size} not supported"
                )
            }
        }
        
        val combinationValid = isStreamCombinationSupported(cameraInfo, streams)
        if (!combinationValid) {
            return ValidationResult(
                isValid = false,
                reason = "Stream combination not guaranteed for ${cameraInfo.hardwareLevelName} hardware level"
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Check if single stream is supported
     */
    private fun isStreamSupported(
        cameraInfo: CameraInfo,
        stream: StreamConfig
    ): Boolean {
        val streamConfigMap = cameraInfo.characteristics?.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        ) ?: return false
        
        val supportedSizes = streamConfigMap.getOutputSizes(stream.format) ?: return false
        return supportedSizes.contains(stream.size)
    }
    
    /**
     * Check if stream combination is guaranteed by hardware level
     * 
     * Based on Camera2 API guarantees:
     * - LEGACY: 1 PRIV stream only
     * - LIMITED: PRIV + JPEG or PRIV + YUV
     * - FULL: PRIV + PRIV + JPEG or PRIV + YUV + JPEG
     */
    private fun isStreamCombinationSupported(
        cameraInfo: CameraInfo,
        streams: List<StreamConfig>
    ): Boolean {
        when (cameraInfo.hardwareLevel) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> {
                return streams.size <= 1
            }
            
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> {
                if (streams.size > 2) return false
                
                val hasPriv = streams.any { it.format == ImageFormat.PRIVATE }
                val hasJpeg = streams.any { it.format == ImageFormat.JPEG }
                val hasYuv = streams.any { it.format == ImageFormat.YUV_420_888 }
                
                return (hasPriv && hasJpeg) || (hasPriv && hasYuv)
            }
            
            else -> {
                return streams.size <= 3
            }
        }
    }
    
    companion object {
        /**
         * Common stream configurations for JABCode scanning
         */
        fun previewPlusAnalysisConfig(
            previewSize: Size = Size(1280, 720),
            analysisSize: Size = Size(1280, 720)
        ): List<StreamConfig> = listOf(
            StreamConfig(ImageFormat.PRIVATE, previewSize),
            StreamConfig(ImageFormat.YUV_420_888, analysisSize)
        )
    }
}
