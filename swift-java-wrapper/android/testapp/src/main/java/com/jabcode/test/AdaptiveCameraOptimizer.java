package com.jabcode.test;

import android.graphics.Bitmap;
import android.graphics.Rect;
import androidx.camera.core.CameraControl;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;

public class AdaptiveCameraOptimizer {
    
    private final CameraControlManager cameraControlManager;
    private final ImageQualityAnalyzer qualityAnalyzer;
    private int consecutiveFailures = 0;
    private float currentExposure = 0.0f;
    private static final int MAX_FAILURES_BEFORE_ADJUST = 3;
    
    public AdaptiveCameraOptimizer(CameraControlManager controlManager) {
        this.cameraControlManager = controlManager;
        this.qualityAnalyzer = new ImageQualityAnalyzer();
    }
    
    public void onDecodeSuccess(Bitmap frame, Rect detectedBounds) {
        consecutiveFailures = 0;
        
        if (detectedBounds != null) {
            lockFocusOnRegion(detectedBounds);
        }
    }
    
    public void onDecodeFailure(Bitmap frame) {
        consecutiveFailures++;
        
        if (consecutiveFailures >= MAX_FAILURES_BEFORE_ADJUST) {
            ImageQualityAnalyzer.QualityMetrics metrics = qualityAnalyzer.analyze(frame);
            adjustCameraSettings(metrics);
            consecutiveFailures = 0;
        }
    }
    
    private void adjustCameraSettings(ImageQualityAnalyzer.QualityMetrics metrics) {
        // Only adjust focus, NOT exposure (let CameraX auto-exposure handle brightness)
        // Manual exposure adjustments conflict with CameraX AE and cause flickering
        
        if (metrics.sharpness < 0.3f) {
            triggerAutoFocus();
        }
        
        // Exposure adjustments disabled - rely on Camera2Interop configuration
        // See camera-auto-exposure-analysis.md for details
    }
    
    private void triggerAutoFocus() {
        cameraControlManager.triggerAutoFocus();
    }
    
    private void lockFocusOnRegion(Rect bounds) {
        // Focus lock would be implemented with CameraControl.startFocusAndMetering
        // using the detected JABCode region as metering point
    }
    
    public Rect detectRegionOfInterest(Bitmap frame) {
        return qualityAnalyzer.findHighContrastRegion(frame);
    }
}
