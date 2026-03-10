package com.jabcode.test;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class CameraControlManagerTest {
    
    private CameraControlManager controlManager;
    private Camera mockCamera;
    private CameraControl mockCameraControl;
    private CameraInfo mockCameraInfo;
    
    @Before
    public void setUp() {
        mockCamera = mock(Camera.class);
        mockCameraControl = mock(CameraControl.class);
        mockCameraInfo = mock(CameraInfo.class);
        
        when(mockCamera.getCameraControl()).thenReturn(mockCameraControl);
        when(mockCamera.getCameraInfo()).thenReturn(mockCameraInfo);
        
        controlManager = new CameraControlManager();
        controlManager.setCamera(mockCamera);
    }
    
    @Test
    public void testInitialization() {
        assertNotNull("Control manager should be initialized", controlManager);
        assertFalse("Torch should be off by default", controlManager.isTorchEnabled());
    }
    
    @Test
    public void testTorchControl() {
        when(mockCameraInfo.hasFlashUnit()).thenReturn(true);
        
        boolean result = controlManager.setTorchEnabled(true);
        
        assertTrue("Torch enable should succeed", result);
        assertTrue("Torch state should be enabled", controlManager.isTorchEnabled());
        verify(mockCameraControl).enableTorch(true);
    }
    
    @Test
    public void testTorchControlWithoutFlash() {
        when(mockCameraInfo.hasFlashUnit()).thenReturn(false);
        
        boolean result = controlManager.setTorchEnabled(true);
        
        assertFalse("Torch enable should fail without flash unit", result);
        assertFalse("Torch state should remain disabled", controlManager.isTorchEnabled());
        verify(mockCameraControl, never()).enableTorch(anyBoolean());
    }
    
    @Test
    public void testExposureCompensation() {
        when(mockCameraInfo.getExposureState()).thenReturn(mock(androidx.camera.core.ExposureState.class));
        when(mockCameraInfo.getExposureState().isExposureCompensationSupported()).thenReturn(true);
        
        boolean result = controlManager.setExposureCompensation(2);
        
        assertTrue("Exposure compensation should succeed", result);
        verify(mockCameraControl).setExposureCompensationIndex(2);
    }
    
    @Test
    public void testExposureCompensationUnsupported() {
        when(mockCameraInfo.getExposureState()).thenReturn(mock(androidx.camera.core.ExposureState.class));
        when(mockCameraInfo.getExposureState().isExposureCompensationSupported()).thenReturn(false);
        
        boolean result = controlManager.setExposureCompensation(2);
        
        assertFalse("Exposure compensation should fail when unsupported", result);
        verify(mockCameraControl, never()).setExposureCompensationIndex(anyInt());
    }
    
    @Test
    public void testZoomControl() {
        when(mockCameraInfo.getZoomState()).thenReturn(mock(androidx.lifecycle.LiveData.class));
        
        boolean result = controlManager.setZoomRatio(2.0f);
        
        assertTrue("Zoom control should succeed", result);
        verify(mockCameraControl).setZoomRatio(2.0f);
    }
    
    @Test
    public void testZoomRatioValidation() {
        when(mockCameraInfo.getZoomState()).thenReturn(mock(androidx.lifecycle.LiveData.class));
        
        // Test minimum zoom (clamped to 1.0)
        controlManager.setZoomRatio(0.5f);
        verify(mockCameraControl).setZoomRatio(1.0f);
        
        // Test maximum zoom (clamped to 10.0)
        controlManager.setZoomRatio(15.0f);
        verify(mockCameraControl).setZoomRatio(10.0f);
    }
    
    @Test
    public void testFocusOnPoint() {
        float x = 0.5f;
        float y = 0.5f;
        
        boolean result = controlManager.focusOnPoint(x, y);
        
        assertTrue("Focus should succeed", result);
        verify(mockCameraControl).startFocusAndMetering(any());
    }
    
    @Test
    public void testAutoFocus() {
        boolean result = controlManager.enableAutoFocus(true);
        
        assertTrue("Auto focus should succeed", result);
        assertTrue("Auto focus state should be enabled", controlManager.isAutoFocusEnabled());
    }
    
    @Test
    public void testCameraReset() {
        controlManager.setTorchEnabled(true);
        controlManager.enableAutoFocus(false);
        
        controlManager.reset();
        
        assertFalse("Torch should be off after reset", controlManager.isTorchEnabled());
        assertTrue("Auto focus should be enabled after reset", controlManager.isAutoFocusEnabled());
    }
    
    @Test
    public void testCameraNotSet() {
        CameraControlManager newManager = new CameraControlManager();
        
        boolean torchResult = newManager.setTorchEnabled(true);
        boolean zoomResult = newManager.setZoomRatio(2.0f);
        
        assertFalse("Torch control should fail without camera", torchResult);
        assertFalse("Zoom control should fail without camera", zoomResult);
    }
}
