package com.jabauth.jabcode.camera.lifecycle

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CameraLifecycleState enum
 * 
 * Tests lifecycle state transitions and properties
 */
class CameraLifecycleStateTest {
    
    @Test
    fun lifecycleState_hasAllRequiredStates() {
        assertNotNull(CameraLifecycleState.CREATED)
        assertNotNull(CameraLifecycleState.STARTED)
        assertNotNull(CameraLifecycleState.RESUMED)
        assertNotNull(CameraLifecycleState.PAUSED)
        assertNotNull(CameraLifecycleState.STOPPED)
        assertNotNull(CameraLifecycleState.DESTROYED)
    }
    
    @Test
    fun lifecycleState_canOpenCamera() {
        assertTrue("RESUMED should allow camera open",
            CameraLifecycleState.RESUMED.canOpenCamera)
        
        assertFalse("CREATED should not allow camera open",
            CameraLifecycleState.CREATED.canOpenCamera)
        assertFalse("PAUSED should not allow camera open",
            CameraLifecycleState.PAUSED.canOpenCamera)
        assertFalse("STOPPED should not allow camera open",
            CameraLifecycleState.STOPPED.canOpenCamera)
        assertFalse("DESTROYED should not allow camera open",
            CameraLifecycleState.DESTROYED.canOpenCamera)
    }
    
    @Test
    fun lifecycleState_requiresCleanup() {
        assertTrue("PAUSED requires cleanup",
            CameraLifecycleState.PAUSED.requiresCleanup)
        assertTrue("STOPPED requires cleanup",
            CameraLifecycleState.STOPPED.requiresCleanup)
        assertTrue("DESTROYED requires cleanup",
            CameraLifecycleState.DESTROYED.requiresCleanup)
        
        assertFalse("CREATED does not require cleanup",
            CameraLifecycleState.CREATED.requiresCleanup)
        assertFalse("STARTED does not require cleanup",
            CameraLifecycleState.STARTED.requiresCleanup)
        assertFalse("RESUMED does not require cleanup",
            CameraLifecycleState.RESUMED.requiresCleanup)
    }
    
    @Test
    fun lifecycleState_isActive() {
        assertTrue("STARTED is active",
            CameraLifecycleState.STARTED.isActive)
        assertTrue("RESUMED is active",
            CameraLifecycleState.RESUMED.isActive)
        
        assertFalse("CREATED is not active",
            CameraLifecycleState.CREATED.isActive)
        assertFalse("PAUSED is not active",
            CameraLifecycleState.PAUSED.isActive)
        assertFalse("STOPPED is not active",
            CameraLifecycleState.STOPPED.isActive)
        assertFalse("DESTROYED is not active",
            CameraLifecycleState.DESTROYED.isActive)
    }
}
