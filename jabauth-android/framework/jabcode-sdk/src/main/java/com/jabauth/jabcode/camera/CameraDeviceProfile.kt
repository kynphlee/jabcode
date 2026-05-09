package com.jabauth.jabcode.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Log

/**
 * Camera device profiling and capability detection for Camera2 API
 *
 * Detects hardware-level support, manual control availability, and sensor characteristics
 * to enable adaptive camera optimization strategies.
 *
 * **Hardware Levels:**
 * - **LEGACY**: Backward compatible with Camera1, limited Camera2 features
 * - **LIMITED**: Partial Camera2 support, some manual controls available
 * - **FULL**: Complete Camera2 implementation, all manual controls
 * - **LEVEL_3**: FULL + additional RAW/YUV capture capabilities
 * - **EXTERNAL**: USB/external camera with variable capabilities
 *
 * **Usage:**
 * ```kotlin
 * val profiler = CameraDeviceProfiler(context)
 * val profile = profiler.getBackCameraProfile()
 *
 * if (profile.supportsManualFocus) {
 *     // Use manual focus distance
 * }
 *
 * val optimalISO = profile.getOptimalISO(sceneBrightness = 0.3f)
 * ```
 *
 * Created: 2026-05-08
 */
class CameraDeviceProfiler(private val context: Context) {

    private val TAG = "CameraDeviceProfiler"
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /**
     * Camera device profile with capabilities and characteristics
     */
    data class DeviceProfile(
        val cameraId: String,
        val hardwareLevel: HardwareLevel,
        val facing: Facing,

        // Sensor characteristics
        val sensorOrientation: Int,
        val physicalSize: Pair<Float, Float>?,  // Width x Height in mm
        val pixelArraySize: Pair<Int, Int>?,    // Width x Height in pixels

        // Manual control support
        val supportsManualFocus: Boolean,
        val supportsManualExposure: Boolean,
        val supportsManualISO: Boolean,
        val supportsManualWhiteBalance: Boolean,

        // Focus characteristics
        val minFocusDistance: Float?,  // In diopters (0 = infinity, >0 = closer)
        val focusDistanceCalibration: FocusCalibration,

        // Exposure characteristics
        val exposureTimeRange: Pair<Long, Long>?,  // Min-Max in nanoseconds
        val isoRange: Pair<Int, Int>?,             // Min-Max ISO

        // Auto-exposure/auto-focus modes
        val availableAEModes: List<Int>,
        val availableAFModes: List<Int>,
        val availableAWBModes: List<Int>
    ) {
        /**
         * Get optimal ISO for scene brightness
         *
         * @param sceneBrightness Normalized brightness (0.0-1.0)
         * @param handHeld True if handheld (limits exposure time), false if stabilized
         * @return Recommended ISO value
         */
        fun getOptimalISO(sceneBrightness: Float, handHeld: Boolean = true): Int {
            val (minISO, maxISO) = isoRange ?: return 800  // Default fallback

            return when {
                sceneBrightness < 0.1f -> maxISO.coerceAtMost(1600)  // Very dark
                sceneBrightness < 0.3f -> 800                         // Dark
                sceneBrightness < 0.6f -> 400                         // Low light
                else -> minISO.coerceAtLeast(100)                     // Well lit
            }
        }

        /**
         * Get optimal exposure time for scene brightness
         *
         * @param sceneBrightness Normalized brightness (0.0-1.0)
         * @param handHeld True if handheld (limits to 1/60s), false if stabilized
         * @return Recommended exposure time in nanoseconds
         */
        fun getOptimalExposureTime(sceneBrightness: Float, handHeld: Boolean = true): Long {
            val (minExp, maxExp) = exposureTimeRange ?: return 16_666_666L  // Default 1/60s

            // For handheld, limit to 1/60s to avoid motion blur
            val maxHandheldExp = 16_666_666L  // 1/60s in nanoseconds
            val effectiveMax = if (handHeld) maxExp.coerceAtMost(maxHandheldExp) else maxExp

            return when {
                sceneBrightness < 0.1f -> effectiveMax                  // Use max available
                sceneBrightness < 0.3f -> effectiveMax / 2              // Half max
                sceneBrightness < 0.6f -> effectiveMax / 4              // Quarter max
                else -> minExp.coerceAtLeast(1_000_000L)                // Fast shutter
            }
        }

        /**
         * Check if device supports full manual control (focus + exposure + ISO)
         */
        val supportsFullManualControl: Boolean
            get() = supportsManualFocus && supportsManualExposure && supportsManualISO

        /**
         * Check if hardware level is sufficient for advanced features
         */
        val isAdvancedHardware: Boolean
            get() = hardwareLevel in listOf(HardwareLevel.FULL, HardwareLevel.LEVEL_3)
    }

    enum class HardwareLevel {
        LEGACY, LIMITED, FULL, LEVEL_3, EXTERNAL, UNKNOWN
    }

    enum class Facing {
        BACK, FRONT, EXTERNAL, UNKNOWN
    }

    enum class FocusCalibration {
        UNCALIBRATED,      // Distance values not reliable
        APPROXIMATE,       // Distance values approximate
        CALIBRATED,        // Distance values accurate
        UNKNOWN
    }

    /**
     * Get profiles for all available cameras
     * 
     * @return List of all camera device profiles
     */
    fun getAllCameraProfiles(): List<DeviceProfile> {
        return try {
            cameraManager.cameraIdList.mapNotNull { cameraId ->
                try {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    buildProfile(cameraId, characteristics)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get profile for camera $cameraId", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enumerate cameras", e)
            emptyList()
        }
    }
    
    /**
     * Get profile for back-facing camera
     */
    fun getBackCameraProfile(): DeviceProfile? {
        return getCameraProfile(CameraMetadata.LENS_FACING_BACK)
    }

    /**
     * Get profile for front-facing camera
     */
    fun getFrontCameraProfile(): DeviceProfile? {
        return getCameraProfile(CameraMetadata.LENS_FACING_FRONT)
    }

    /**
     * Get profile for specific camera by ID
     */
    fun getProfileById(cameraId: String): DeviceProfile? {
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            buildProfile(cameraId, characteristics)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get profile for camera $cameraId", e)
            null
        }
    }

    /**
     * Get camera profile by lens facing direction
     */
    private fun getCameraProfile(facing: Int): DeviceProfile? {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == facing
            } ?: return null

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            buildProfile(cameraId, characteristics)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get camera profile", e)
            null
        }
    }

    /**
     * Build device profile from camera characteristics
     */
    private fun buildProfile(cameraId: String, char: CameraCharacteristics): DeviceProfile {
        // Hardware level
        val hwLevel = when (char.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> HardwareLevel.LEGACY
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> HardwareLevel.LIMITED
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> HardwareLevel.FULL
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> HardwareLevel.LEVEL_3
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> HardwareLevel.EXTERNAL
            else -> HardwareLevel.UNKNOWN
        }

        // Lens facing
        val facing = when (char.get(CameraCharacteristics.LENS_FACING)) {
            CameraMetadata.LENS_FACING_BACK -> Facing.BACK
            CameraMetadata.LENS_FACING_FRONT -> Facing.FRONT
            CameraMetadata.LENS_FACING_EXTERNAL -> Facing.EXTERNAL
            else -> Facing.UNKNOWN
        }

        // Sensor characteristics
        val sensorOrientation = char.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val sensorSize = char.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val physicalSize = sensorSize?.let { Pair(it.width, it.height) }
        val pixelArray = char.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val pixelArraySize = pixelArray?.let { Pair(it.width, it.height) }

        // Manual control capabilities
        val availableCapabilities = char.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val supportsManualSensor = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in availableCapabilities
        val supportsManualPostProcessing = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING in availableCapabilities

        // Focus capabilities
        val minFocusDist = char.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        val focusCal = when (char.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION)) {
            CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED -> FocusCalibration.UNCALIBRATED
            CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE -> FocusCalibration.APPROXIMATE
            CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED -> FocusCalibration.CALIBRATED
            else -> FocusCalibration.UNKNOWN
        }

        // Exposure capabilities
        val expRange = char.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val exposureTimeRange = expRange?.let { Pair(it.lower, it.upper) }
        val isoRangeVal = char.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val isoRange = isoRangeVal?.let { Pair(it.lower, it.upper) }

        // Available modes
        val aeModes = char.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.toList() ?: emptyList()
        val afModes = char.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList() ?: emptyList()
        val awbModes = char.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList() ?: emptyList()

        return DeviceProfile(
            cameraId = cameraId,
            hardwareLevel = hwLevel,
            facing = facing,
            sensorOrientation = sensorOrientation,
            physicalSize = physicalSize,
            pixelArraySize = pixelArraySize,
            supportsManualFocus = minFocusDist != null && minFocusDist > 0f,
            supportsManualExposure = supportsManualSensor,
            supportsManualISO = supportsManualSensor,
            supportsManualWhiteBalance = supportsManualPostProcessing,
            minFocusDistance = minFocusDist,
            focusDistanceCalibration = focusCal,
            exposureTimeRange = exposureTimeRange,
            isoRange = isoRange,
            availableAEModes = aeModes,
            availableAFModes = afModes,
            availableAWBModes = awbModes
        )
    }

    /**
     * Log device profile for diagnostics
     */
    fun logProfile(profile: DeviceProfile) {
        Log.i(TAG, "=== Camera Device Profile ===")
        Log.i(TAG, "Camera ID: ${profile.cameraId}")
        Log.i(TAG, "Hardware Level: ${profile.hardwareLevel}")
        Log.i(TAG, "Facing: ${profile.facing}")
        Log.i(TAG, "Sensor Orientation: ${profile.sensorOrientation}°")
        Log.i(TAG, "Physical Size: ${profile.physicalSize?.first}mm x ${profile.physicalSize?.second}mm")
        Log.i(TAG, "Pixel Array: ${profile.pixelArraySize?.first} x ${profile.pixelArraySize?.second}")
        Log.i(TAG, "Manual Focus: ${profile.supportsManualFocus} (min distance: ${profile.minFocusDistance} diopters)")
        Log.i(TAG, "Manual Exposure: ${profile.supportsManualExposure}")
        Log.i(TAG, "Manual ISO: ${profile.supportsManualISO} (range: ${profile.isoRange})")
        Log.i(TAG, "Exposure Time Range: ${profile.exposureTimeRange?.let { "${it.first}ns - ${it.second}ns" }}")
        Log.i(TAG, "Focus Calibration: ${profile.focusDistanceCalibration}")
        Log.i(TAG, "Full Manual Control: ${profile.supportsFullManualControl}")
        Log.i(TAG, "===========================")
    }
}
