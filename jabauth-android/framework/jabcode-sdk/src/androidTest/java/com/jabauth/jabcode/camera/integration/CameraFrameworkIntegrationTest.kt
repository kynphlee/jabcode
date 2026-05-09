package com.jabauth.jabcode.camera.integration

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jabauth.jabcode.camera.CameraDeviceProfiler
import com.jabauth.jabcode.camera.config.CameraConfig
import com.jabauth.jabcode.camera.error.CameraError
import com.jabauth.jabcode.camera.error.ErrorHandler
import com.jabauth.jabcode.camera.error.RecoveryStrategy
import com.jabauth.jabcode.camera.lifecycle.CameraLifecycleState
import com.jabauth.jabcode.camera.lifecycle.ResourceManager
import com.jabauth.jabcode.camera.metadata.PerformanceTracker
import com.jabauth.jabcode.camera.transform.DeviceOrientation
import com.jabauth.jabcode.camera.transform.OrientationCalculator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for camera framework components
 * 
 * Tests interaction between multiple framework modules
 */
@RunWith(AndroidJUnit4::class)
class CameraFrameworkIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var profiler: CameraDeviceProfiler
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        profiler = CameraDeviceProfiler(context)
    }
    
    @Test
    fun integration_cameraProfileAndConfig() {
        // Get camera profile
        val profile = profiler.getBackCameraProfile()
        assertNotNull("Back camera profile should exist", profile)
        
        // Create config based on profile capabilities
        val config = CameraConfig.Builder()
            .cameraId(profile!!.cameraId)
            .enableAutoFocus(profile.supportsManualFocus)
            .targetFps(30)
            .build()
        
        assertTrue("Config should be valid", config.isValid())
        assertEquals(profile.cameraId, config.preferredCameraId)
    }
    
    @Test
    fun integration_errorHandlingWithLifecycle() {
        // Create error handler with retry strategy
        var errorReceived: CameraError? = null
        val errorHandler = ErrorHandler(
            strategy = RecoveryStrategy.Retry(maxAttempts = 3),
            onError = { error -> errorReceived = error }
        )
        
        // Simulate error in PAUSED state (should trigger cleanup)
        val lifecycleState = CameraLifecycleState.PAUSED
        assertTrue("PAUSED state should require cleanup", lifecycleState.requiresCleanup)
        
        // Handle error
        val error = CameraError(
            code = CameraError.Code.CAMERA_DISCONNECTED,
            message = "Camera disconnected in PAUSED state",
            isRecoverable = true
        )
        errorHandler.handleError(error)
        
        assertNotNull("Error should be received", errorReceived)
        assertEquals(error.code, errorReceived?.code)
    }
    
    @Test
    fun integration_orientationWithCameraProfile() {
        // Get camera profile
        val profile = profiler.getBackCameraProfile()
        assertNotNull("Back camera profile should exist", profile)
        
        // Calculate preview rotation
        val calculator = OrientationCalculator()
        val rotation = calculator.calculatePreviewRotation(
            sensorOrientation = profile!!.sensorOrientation,
            deviceRotation = 0, // Portrait
            cameraFacing = profile.facing
        )
        
        // Verify rotation is normalized
        assertTrue("Rotation should be 0-359", rotation in 0..359)
        
        // Test device orientation
        val orientation = DeviceOrientation.fromRotation(rotation)
        assertNotNull("Orientation should be determined", orientation)
    }
    
    @Test
    fun integration_performanceTrackingWithMetadata() {
        // Create performance tracker
        val tracker = PerformanceTracker()
        
        // Simulate frame capture sequence
        val startTime = System.nanoTime()
        tracker.recordFrame(timestamp = startTime)
        tracker.recordLatency(latencyMs = 30.0f)
        
        tracker.recordFrame(timestamp = startTime + 33_333_333L) // ~30 FPS
        tracker.recordLatency(latencyMs = 32.0f)
        
        tracker.recordFrame(timestamp = startTime + 66_666_666L)
        tracker.recordLatency(latencyMs = 31.0f)
        
        // Get metrics
        val metrics = tracker.getCurrentMetrics()
        assertNotNull("Metrics should be available", metrics)
        
        // Verify FPS is reasonable
        assertTrue("FPS should be around 30", metrics!!.fps in 25f..35f)
        
        // Verify average latency
        assertTrue("Latency should be around 31ms", 
            metrics.averageLatencyMs in 30f..33f)
    }
    
    @Test
    fun integration_resourceManagementLifecycle() {
        // Create resource manager
        val resourceManager = ResourceManager()
        var resource1Released = false
        var resource2Released = false
        
        // Register mock resources
        val mockCamera = object : com.jabauth.jabcode.camera.lifecycle.ManagedResource {
            override val resourceId = "camera_device"
            override fun release() {
                resource1Released = true
            }
        }
        
        val mockImageReader = object : com.jabauth.jabcode.camera.lifecycle.ManagedResource {
            override val resourceId = "image_reader"
            override fun release() {
                resource2Released = true
            }
        }
        
        resourceManager.register(mockCamera)
        resourceManager.register(mockImageReader)
        
        assertEquals(2, resourceManager.getResourceCount())
        
        // Simulate lifecycle transition to DESTROYED
        val destroyedState = CameraLifecycleState.DESTROYED
        assertTrue("DESTROYED should require cleanup", destroyedState.requiresCleanup)
        
        // Release all resources
        resourceManager.releaseAll()
        
        assertTrue("Camera should be released", resource1Released)
        assertTrue("ImageReader should be released", resource2Released)
        assertEquals(0, resourceManager.getResourceCount())
    }
    
    @Test
    fun integration_fullConfigurationWorkflow() {
        // 1. Get camera profile
        val profile = profiler.getBackCameraProfile()
        assertNotNull(profile)
        
        // 2. Create config
        val config = CameraConfig.Builder()
            .cameraId(profile!!.cameraId)
            .enableAutoFocus(profile.supportsManualFocus)
            .enableAutoExposure(profile.supportsManualExposure)
            .targetFps(30)
            .build()
        
        // 3. Setup error handling
        val errorHandler = ErrorHandler(
            strategy = RecoveryStrategy.ExponentialBackoff(
                baseDelayMs = 100,
                maxDelayMs = 5000
            ),
            onError = { /* handle error */ }
        )
        
        // 4. Setup resource management
        val resourceManager = ResourceManager()
        
        // 5. Setup performance tracking
        val perfTracker = PerformanceTracker()
        
        // 6. Calculate orientation
        val calculator = OrientationCalculator()
        val rotation = calculator.calculatePreviewRotation(
            sensorOrientation = profile.sensorOrientation,
            deviceRotation = 0,
            cameraFacing = profile.facing
        )
        
        // Verify all components are initialized
        assertTrue(config.isValid())
        assertEquals(0, errorHandler.getAttemptCount())
        assertEquals(0, resourceManager.getResourceCount())
        assertNull(perfTracker.getCurrentMetrics())
        assertTrue(rotation in 0..359)
    }
}
