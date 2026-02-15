package com.jabcode.test;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;

public class CameraControlManager {
    
    private static final float MIN_ZOOM_RATIO = 1.0f;
    private static final float MAX_ZOOM_RATIO = 10.0f;
    
    private Camera camera;
    private boolean torchEnabled = false;
    private boolean autoFocusEnabled = true;
    
    public void setCamera(Camera camera) {
        this.camera = camera;
    }
    
    public boolean isTorchEnabled() {
        return torchEnabled;
    }
    
    public boolean setTorchEnabled(boolean enabled) {
        if (camera == null) {
            return false;
        }
        
        CameraInfo cameraInfo = camera.getCameraInfo();
        if (!cameraInfo.hasFlashUnit()) {
            return false;
        }
        
        try {
            camera.getCameraControl().enableTorch(enabled);
            torchEnabled = enabled;
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean setExposureCompensation(int value) {
        if (camera == null) {
            return false;
        }
        
        CameraInfo cameraInfo = camera.getCameraInfo();
        if (cameraInfo.getExposureState() == null || 
            !cameraInfo.getExposureState().isExposureCompensationSupported()) {
            return false;
        }
        
        try {
            camera.getCameraControl().setExposureCompensationIndex(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean setZoomRatio(float ratio) {
        if (camera == null) {
            return false;
        }
        
        // Clamp zoom ratio to valid range
        float clampedRatio = Math.max(MIN_ZOOM_RATIO, Math.min(MAX_ZOOM_RATIO, ratio));
        
        try {
            camera.getCameraControl().setZoomRatio(clampedRatio);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean focusOnPoint(float x, float y) {
        if (camera == null) {
            return false;
        }
        
        try {
            SurfaceOrientedMeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(1.0f, 1.0f);
            MeteringPoint point = factory.createPoint(x, y);
            
            FocusMeteringAction action = new FocusMeteringAction.Builder(point)
                    .build();
            
            camera.getCameraControl().startFocusAndMetering(action);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean enableAutoFocus(boolean enabled) {
        autoFocusEnabled = enabled;
        return true;
    }
    
    public boolean isAutoFocusEnabled() {
        return autoFocusEnabled;
    }
    
    public void reset() {
        if (camera != null && torchEnabled) {
            setTorchEnabled(false);
        }
        autoFocusEnabled = true;
    }
    
    public float getMinZoomRatio() {
        return MIN_ZOOM_RATIO;
    }
    
    public float getMaxZoomRatio() {
        if (camera != null && camera.getCameraInfo().getZoomState().getValue() != null) {
            return camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
        }
        return MAX_ZOOM_RATIO;
    }
    
    public int getExposureCompensationRange() {
        if (camera != null && camera.getCameraInfo().getExposureState() != null) {
            return camera.getCameraInfo().getExposureState().getExposureCompensationRange().getUpper();
        }
        return 0;
    }
    
    public boolean triggerAutoFocus() {
        if (camera == null) {
            return false;
        }
        
        try {
            SurfaceOrientedMeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(1.0f, 1.0f);
            MeteringPoint centerPoint = factory.createPoint(0.5f, 0.5f);
            
            FocusMeteringAction action = new FocusMeteringAction.Builder(centerPoint)
                    .build();
            
            camera.getCameraControl().startFocusAndMetering(action);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean focusOnRegion(android.graphics.Rect bounds, int imageWidth, int imageHeight) {
        if (camera == null || bounds == null) {
            return false;
        }
        
        try {
            float centerX = (bounds.left + bounds.right) / 2.0f / imageWidth;
            float centerY = (bounds.top + bounds.bottom) / 2.0f / imageHeight;
            
            SurfaceOrientedMeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(1.0f, 1.0f);
            MeteringPoint point = factory.createPoint(centerX, centerY);
            
            FocusMeteringAction action = new FocusMeteringAction.Builder(point)
                    .setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            
            camera.getCameraControl().startFocusAndMetering(action);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
