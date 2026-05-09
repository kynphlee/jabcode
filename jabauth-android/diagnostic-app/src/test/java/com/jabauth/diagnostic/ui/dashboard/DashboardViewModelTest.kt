package com.jabauth.diagnostic.ui.dashboard

import com.jabauth.jabcode.camera.CameraDeviceProfiler
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DashboardViewModel
 * 
 * Tests camera enumeration and device summary
 */
class DashboardViewModelTest {
    
    @Test
    fun viewModel_initialState() {
        val viewModel = DashboardViewModel { emptyList() }
        
        val state = viewModel.uiState.value
        assertNotNull("UI state should not be null", state)
        assertEquals("Should start with 0 cameras", 0, state.cameras.size)
        assertFalse("Should not be loading initially", state.isLoading)
    }
    
    @Test
    fun loadCameras_withNoCameras() {
        val viewModel = DashboardViewModel { emptyList() }
        viewModel.loadCameras()
        
        val state = viewModel.uiState.value
        assertEquals("Should have 0 cameras", 0, state.cameras.size)
        assertFalse("Should not be loading", state.isLoading)
    }
    
    @Test
    fun loadCameras_withMultipleCameras() {
        val backCamera = createProfile("0", CameraDeviceProfiler.Facing.BACK)
        val frontCamera = createProfile("1", CameraDeviceProfiler.Facing.FRONT)
        
        val viewModel = DashboardViewModel { listOf(backCamera, frontCamera) }
        viewModel.loadCameras()
        
        val state = viewModel.uiState.value
        assertEquals("Should have 2 cameras", 2, state.cameras.size)
        assertEquals("First camera should be back", "0", state.cameras[0].cameraId)
        assertEquals("Second camera should be front", "1", state.cameras[1].cameraId)
    }
    
    @Test
    fun loadCameras_updatesLoadingState() {
        val viewModel = DashboardViewModel { emptyList() }
        
        // Before loading
        assertFalse("Should not be loading initially", viewModel.uiState.value.isLoading)
        
        // Load cameras
        viewModel.loadCameras()
        
        // After loading completes
        assertFalse("Should not be loading after completion", viewModel.uiState.value.isLoading)
    }
    
    @Test
    fun uiState_containsDeviceSummary() {
        val camera = createProfile("0", CameraDeviceProfiler.Facing.BACK)
        
        val viewModel = DashboardViewModel { listOf(camera) }
        viewModel.loadCameras()
        
        val state = viewModel.uiState.value
        assertNotNull("Device summary should not be null", state.deviceSummary)
        assertEquals("Should have 1 camera in summary", 1, state.deviceSummary.totalCameras)
    }
    
    @Test
    fun uiState_categorizesCamerasByType() {
        val backCamera = createProfile("0", CameraDeviceProfiler.Facing.BACK)
        val frontCamera = createProfile("1", CameraDeviceProfiler.Facing.FRONT)
        val externalCamera = createProfile("2", CameraDeviceProfiler.Facing.EXTERNAL)
        
        val viewModel = DashboardViewModel { listOf(backCamera, frontCamera, externalCamera) }
        viewModel.loadCameras()
        
        val state = viewModel.uiState.value
        assertEquals("Should have 1 back camera", 1, state.deviceSummary.backCameras)
        assertEquals("Should have 1 front camera", 1, state.deviceSummary.frontCameras)
        assertEquals("Should have 1 external camera", 1, state.deviceSummary.externalCameras)
    }
    
    // Helper to create test profile
    private fun createProfile(
        id: String,
        facing: CameraDeviceProfiler.Facing
    ): CameraDeviceProfiler.DeviceProfile {
        return CameraDeviceProfiler.DeviceProfile(
            cameraId = id,
            hardwareLevel = CameraDeviceProfiler.HardwareLevel.FULL,
            facing = facing,
            sensorOrientation = 90,
            physicalSize = Pair(6.4f, 4.8f),
            pixelArraySize = Pair(4032, 3024),
            supportsManualFocus = true,
            supportsManualExposure = true,
            supportsManualISO = true,
            supportsManualWhiteBalance = true,
            minFocusDistance = 0.1f,
            focusDistanceCalibration = CameraDeviceProfiler.FocusCalibration.CALIBRATED,
            exposureTimeRange = Pair(100_000L, 1_000_000_000L),
            isoRange = Pair(100, 3200),
            availableAEModes = listOf(1, 2, 3),
            availableAFModes = listOf(1, 4),
            availableAWBModes = listOf(1, 2)
        )
    }
}
