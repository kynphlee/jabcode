package com.jabauth.jabcode.camera.metadata

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for MetadataExtractor
 * 
 * Tests metadata extraction from real Camera2 CaptureResult
 */
@RunWith(AndroidJUnit4::class)
class MetadataExtractorInstrumentedTest {
    
    private lateinit var extractor: MetadataExtractor
    private lateinit var cameraManager: CameraManager
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        extractor = MetadataExtractor()
    }
    
    @Test
    fun extractAfState_mapsAllStates() {
        // Test mapping of all AF states
        assertEquals(FrameMetadata.AfState.INACTIVE, 
            extractor.mapAfState(CaptureResult.CONTROL_AF_STATE_INACTIVE))
        
        assertEquals(FrameMetadata.AfState.PASSIVE_SCAN,
            extractor.mapAfState(CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN))
        
        assertEquals(FrameMetadata.AfState.PASSIVE_FOCUSED,
            extractor.mapAfState(CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED))
        
        assertEquals(FrameMetadata.AfState.ACTIVE_SCAN,
            extractor.mapAfState(CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN))
        
        assertEquals(FrameMetadata.AfState.FOCUSED_LOCKED,
            extractor.mapAfState(CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED))
        
        assertEquals(FrameMetadata.AfState.NOT_FOCUSED_LOCKED,
            extractor.mapAfState(CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED))
        
        assertEquals(FrameMetadata.AfState.PASSIVE_UNFOCUSED,
            extractor.mapAfState(CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED))
    }
    
    @Test
    fun extractAfState_handlesNull() {
        assertEquals(FrameMetadata.AfState.UNKNOWN, extractor.mapAfState(null))
    }
    
    @Test
    fun extractAeState_mapsAllStates() {
        assertEquals(FrameMetadata.AeState.INACTIVE,
            extractor.mapAeState(CaptureResult.CONTROL_AE_STATE_INACTIVE))
        
        assertEquals(FrameMetadata.AeState.SEARCHING,
            extractor.mapAeState(CaptureResult.CONTROL_AE_STATE_SEARCHING))
        
        assertEquals(FrameMetadata.AeState.CONVERGED,
            extractor.mapAeState(CaptureResult.CONTROL_AE_STATE_CONVERGED))
        
        assertEquals(FrameMetadata.AeState.LOCKED,
            extractor.mapAeState(CaptureResult.CONTROL_AE_STATE_LOCKED))
        
        assertEquals(FrameMetadata.AeState.FLASH_REQUIRED,
            extractor.mapAeState(CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED))
        
        assertEquals(FrameMetadata.AeState.PRECAPTURE,
            extractor.mapAeState(CaptureResult.CONTROL_AE_STATE_PRECAPTURE))
    }
    
    @Test
    fun extractAeState_handlesNull() {
        assertEquals(FrameMetadata.AeState.UNKNOWN, extractor.mapAeState(null))
    }
    
    @Test
    fun extractAwbState_mapsAllStates() {
        assertEquals(FrameMetadata.AwbState.INACTIVE,
            extractor.mapAwbState(CaptureResult.CONTROL_AWB_STATE_INACTIVE))
        
        assertEquals(FrameMetadata.AwbState.SEARCHING,
            extractor.mapAwbState(CaptureResult.CONTROL_AWB_STATE_SEARCHING))
        
        assertEquals(FrameMetadata.AwbState.CONVERGED,
            extractor.mapAwbState(CaptureResult.CONTROL_AWB_STATE_CONVERGED))
        
        assertEquals(FrameMetadata.AwbState.LOCKED,
            extractor.mapAwbState(CaptureResult.CONTROL_AWB_STATE_LOCKED))
    }
    
    @Test
    fun extractAwbState_handlesNull() {
        assertEquals(FrameMetadata.AwbState.UNKNOWN, extractor.mapAwbState(null))
    }
}
