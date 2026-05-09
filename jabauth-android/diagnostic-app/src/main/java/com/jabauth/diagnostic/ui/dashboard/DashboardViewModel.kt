package com.jabauth.diagnostic.ui.dashboard

import androidx.lifecycle.ViewModel
import com.jabauth.jabcode.camera.CameraDeviceProfiler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for Dashboard screen
 * 
 * Manages camera enumeration and device summary state
 */
class DashboardViewModel(
    private val profileSupplier: () -> List<CameraDeviceProfiler.DeviceProfile>
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    /**
     * Load all available camera devices
     */
    fun loadCameras() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        val profiles = profileSupplier()
        val deviceSummary = createDeviceSummary(profiles)
        
        _uiState.value = DashboardUiState(
            cameras = profiles,
            deviceSummary = deviceSummary,
            isLoading = false
        )
    }
    
    /**
     * Create device summary from camera profiles
     */
    private fun createDeviceSummary(
        profiles: List<CameraDeviceProfiler.DeviceProfile>
    ): DeviceSummary {
        val backCameras = profiles.count { it.facing == CameraDeviceProfiler.Facing.BACK }
        val frontCameras = profiles.count { it.facing == CameraDeviceProfiler.Facing.FRONT }
        val externalCameras = profiles.count { it.facing == CameraDeviceProfiler.Facing.EXTERNAL }
        
        return DeviceSummary(
            totalCameras = profiles.size,
            backCameras = backCameras,
            frontCameras = frontCameras,
            externalCameras = externalCameras
        )
    }
}

/**
 * UI state for Dashboard screen
 */
data class DashboardUiState(
    val cameras: List<CameraDeviceProfiler.DeviceProfile> = emptyList(),
    val deviceSummary: DeviceSummary = DeviceSummary(),
    val isLoading: Boolean = false
)

/**
 * Device summary with camera counts
 */
data class DeviceSummary(
    val totalCameras: Int = 0,
    val backCameras: Int = 0,
    val frontCameras: Int = 0,
    val externalCameras: Int = 0
)
