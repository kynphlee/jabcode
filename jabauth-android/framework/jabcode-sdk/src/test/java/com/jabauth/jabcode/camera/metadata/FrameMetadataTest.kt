package com.jabauth.jabcode.camera.metadata

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for FrameMetadata data class
 * 
 * Tests frame capture metadata properties
 */
class FrameMetadataTest {
    
    @Test
    fun frameMetadata_storesCorrectProperties() {
        val metadata = FrameMetadata(
            frameNumber = 42L,
            timestamp = 1000000000L,
            exposureTimeNs = 16666666L,
            iso = 800,
            focusDistance = 0.5f,
            afState = FrameMetadata.AfState.FOCUSED_LOCKED,
            aeState = FrameMetadata.AeState.CONVERGED,
            awbState = FrameMetadata.AwbState.CONVERGED
        )
        
        assertEquals(42L, metadata.frameNumber)
        assertEquals(1000000000L, metadata.timestamp)
        assertEquals(16666666L, metadata.exposureTimeNs)
        assertEquals(800, metadata.iso)
        assertNotNull(metadata.focusDistance)
        assertEquals(0.5f, metadata.focusDistance!!, 0.001f)
        assertEquals(FrameMetadata.AfState.FOCUSED_LOCKED, metadata.afState)
        assertEquals(FrameMetadata.AeState.CONVERGED, metadata.aeState)
        assertEquals(FrameMetadata.AwbState.CONVERGED, metadata.awbState)
    }
    
    @Test
    fun afState_hasAllRequiredStates() {
        assertNotNull(FrameMetadata.AfState.INACTIVE)
        assertNotNull(FrameMetadata.AfState.PASSIVE_SCAN)
        assertNotNull(FrameMetadata.AfState.PASSIVE_FOCUSED)
        assertNotNull(FrameMetadata.AfState.ACTIVE_SCAN)
        assertNotNull(FrameMetadata.AfState.FOCUSED_LOCKED)
        assertNotNull(FrameMetadata.AfState.NOT_FOCUSED_LOCKED)
        assertNotNull(FrameMetadata.AfState.PASSIVE_UNFOCUSED)
        assertNotNull(FrameMetadata.AfState.UNKNOWN)
    }
    
    @Test
    fun aeState_hasAllRequiredStates() {
        assertNotNull(FrameMetadata.AeState.INACTIVE)
        assertNotNull(FrameMetadata.AeState.SEARCHING)
        assertNotNull(FrameMetadata.AeState.CONVERGED)
        assertNotNull(FrameMetadata.AeState.LOCKED)
        assertNotNull(FrameMetadata.AeState.FLASH_REQUIRED)
        assertNotNull(FrameMetadata.AeState.PRECAPTURE)
        assertNotNull(FrameMetadata.AeState.UNKNOWN)
    }
    
    @Test
    fun awbState_hasAllRequiredStates() {
        assertNotNull(FrameMetadata.AwbState.INACTIVE)
        assertNotNull(FrameMetadata.AwbState.SEARCHING)
        assertNotNull(FrameMetadata.AwbState.CONVERGED)
        assertNotNull(FrameMetadata.AwbState.LOCKED)
        assertNotNull(FrameMetadata.AwbState.UNKNOWN)
    }
    
    @Test
    fun frameMetadata_copyWorks() {
        val original = FrameMetadata(
            frameNumber = 1L,
            timestamp = 1000L,
            exposureTimeNs = 10000L,
            iso = 100,
            focusDistance = 1.0f,
            afState = FrameMetadata.AfState.INACTIVE,
            aeState = FrameMetadata.AeState.INACTIVE,
            awbState = FrameMetadata.AwbState.INACTIVE
        )
        
        val modified = original.copy(
            iso = 200,
            aeState = FrameMetadata.AeState.CONVERGED
        )
        
        assertEquals(1L, modified.frameNumber)
        assertEquals(200, modified.iso)
        assertEquals(FrameMetadata.AeState.CONVERGED, modified.aeState)
        assertEquals(FrameMetadata.AwbState.INACTIVE, modified.awbState)
    }
    
    @Test
    fun exposureTimeNs_convertsToMilliseconds() {
        val metadata = FrameMetadata(
            frameNumber = 1L,
            timestamp = 1000L,
            exposureTimeNs = 16666666L, // ~1/60 second
            iso = 100,
            focusDistance = 0f,
            afState = FrameMetadata.AfState.INACTIVE,
            aeState = FrameMetadata.AeState.INACTIVE,
            awbState = FrameMetadata.AwbState.INACTIVE
        )
        
        assertNotNull(metadata.exposureTimeNs)
        val exposureMs = metadata.exposureTimeNs!! / 1_000_000.0
        assertEquals(16.666666, exposureMs, 0.001)
    }
    
    @Test
    fun frameMetadata_handlesNullableFields() {
        val metadata = FrameMetadata(
            frameNumber = 1L,
            timestamp = 1000L,
            exposureTimeNs = null,
            iso = null,
            focusDistance = null,
            afState = FrameMetadata.AfState.UNKNOWN,
            aeState = FrameMetadata.AeState.UNKNOWN,
            awbState = FrameMetadata.AwbState.UNKNOWN
        )
        
        assertNull(metadata.exposureTimeNs)
        assertNull(metadata.iso)
        assertNull(metadata.focusDistance)
    }
}
