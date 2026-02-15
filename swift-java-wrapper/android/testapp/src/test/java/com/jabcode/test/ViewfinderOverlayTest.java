package com.jabcode.test;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ViewfinderOverlayTest {
    
    private Context context;
    private ViewfinderOverlay overlay;
    
    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        overlay = new ViewfinderOverlay(context);
    }
    
    @Test
    public void testViewfinderInitialization() {
        assertNotNull("Overlay should be initialized", overlay);
        assertEquals("Default state should be IDLE", ViewfinderOverlay.State.IDLE, overlay.getState());
    }
    
    @Test
    public void testViewfinderDimensions() {
        int width = 1080;
        int height = 1920;
        overlay.onSizeChanged(width, height, 0, 0);
        
        RectF scanArea = overlay.getScanArea();
        assertNotNull("Scan area should be calculated", scanArea);
        
        // Scan area should be 70% of width OR max size, whichever is smaller
        float density = context.getResources().getDisplayMetrics().density;
        float maxSize = 300 * density; // MAX_SCAN_SIZE_DP converted to px
        float calculatedSize = width * 0.7f;
        float expectedSize = Math.min(calculatedSize, maxSize);
        
        assertEquals("Scan area width should respect max size constraint", 
                    expectedSize, scanArea.width(), 2.0f);
        assertEquals("Scan area should be square", 
                    scanArea.width(), scanArea.height(), 1.0f);
        
        // Should be centered horizontally
        float expectedLeft = (width - expectedSize) / 2;
        assertEquals("Scan area should be centered horizontally", 
                    expectedLeft, scanArea.left, 2.0f);
    }
    
    @Test
    public void testViewfinderMaxDimension() {
        int width = 2000;
        int height = 3000;
        overlay.onSizeChanged(width, height, 0, 0);
        
        RectF scanArea = overlay.getScanArea();
        
        // Should not exceed 300dp (approximately 900px at 3x density)
        float maxSize = 300 * 3; // 300dp at xxhdpi
        assertTrue("Scan area should not exceed max size", 
                  scanArea.width() <= maxSize);
    }
    
    @Test
    public void testStateTransitions() {
        // IDLE -> SCANNING
        overlay.setState(ViewfinderOverlay.State.SCANNING);
        assertEquals("State should transition to SCANNING", 
                    ViewfinderOverlay.State.SCANNING, overlay.getState());
        
        // SCANNING -> DETECTED
        overlay.setState(ViewfinderOverlay.State.DETECTED);
        assertEquals("State should transition to DETECTED", 
                    ViewfinderOverlay.State.DETECTED, overlay.getState());
        
        // DETECTED -> SUCCESS
        overlay.setState(ViewfinderOverlay.State.SUCCESS);
        assertEquals("State should transition to SUCCESS", 
                    ViewfinderOverlay.State.SUCCESS, overlay.getState());
        
        // SUCCESS -> IDLE
        overlay.setState(ViewfinderOverlay.State.IDLE);
        assertEquals("State should transition back to IDLE", 
                    ViewfinderOverlay.State.IDLE, overlay.getState());
        
        // Any state -> ERROR
        overlay.setState(ViewfinderOverlay.State.ERROR);
        assertEquals("State should transition to ERROR", 
                    ViewfinderOverlay.State.ERROR, overlay.getState());
    }
    
    @Test
    public void testBorderColorByState() {
        overlay.setState(ViewfinderOverlay.State.IDLE);
        int idleColor = overlay.getBorderColor();
        
        overlay.setState(ViewfinderOverlay.State.SCANNING);
        int scanningColor = overlay.getBorderColor();
        
        overlay.setState(ViewfinderOverlay.State.DETECTED);
        int detectedColor = overlay.getBorderColor();
        
        overlay.setState(ViewfinderOverlay.State.SUCCESS);
        int successColor = overlay.getBorderColor();
        
        overlay.setState(ViewfinderOverlay.State.ERROR);
        int errorColor = overlay.getBorderColor();
        
        // Colors should be different for different states
        assertNotEquals("IDLE and SCANNING should have different colors", 
                       idleColor, scanningColor);
        assertNotEquals("DETECTED and SUCCESS should have different colors", 
                       detectedColor, successColor);
        assertNotEquals("SUCCESS and ERROR should have different colors", 
                       successColor, errorColor);
    }
    
    @Test
    public void testAnimationOnStateChange() {
        overlay.setState(ViewfinderOverlay.State.IDLE);
        assertFalse("Animation should not be running in IDLE state", 
                   overlay.isAnimating());
        
        overlay.setState(ViewfinderOverlay.State.SCANNING);
        assertTrue("Animation should start in SCANNING state", 
                  overlay.isAnimating());
        
        overlay.setState(ViewfinderOverlay.State.SUCCESS);
        assertTrue("Animation should run in SUCCESS state", 
                  overlay.isAnimating());
        
        overlay.setState(ViewfinderOverlay.State.ERROR);
        assertTrue("Animation should run in ERROR state", 
                  overlay.isAnimating());
    }
    
    @Test
    public void testDetectedBoundsDisplay() {
        RectF detectedBounds = new RectF(100, 100, 400, 400);
        overlay.showDetectedBounds(detectedBounds);
        
        RectF displayed = overlay.getDetectedBounds();
        assertNotNull("Detected bounds should be stored", displayed);
        assertEquals("Bounds should match", detectedBounds, displayed);
    }
    
    @Test
    public void testClearDetectedBounds() {
        RectF detectedBounds = new RectF(100, 100, 400, 400);
        overlay.showDetectedBounds(detectedBounds);
        assertNotNull("Bounds should be set", overlay.getDetectedBounds());
        
        overlay.clearDetectedBounds();
        assertNull("Bounds should be cleared", overlay.getDetectedBounds());
    }
    
    @Test
    public void testRotationHandling() {
        // Portrait
        overlay.onSizeChanged(1080, 1920, 0, 0);
        RectF portraitScanArea = overlay.getScanArea();
        
        // Landscape
        overlay.onSizeChanged(1920, 1080, 0, 0);
        RectF landscapeScanArea = overlay.getScanArea();
        
        assertNotEquals("Scan area should adjust for orientation", 
                       portraitScanArea, landscapeScanArea);
        
        // Both should still be square
        assertEquals("Portrait scan area should be square", 
                    portraitScanArea.width(), portraitScanArea.height(), 1.0f);
        assertEquals("Landscape scan area should be square", 
                    landscapeScanArea.width(), landscapeScanArea.height(), 1.0f);
    }
    
    @Test
    public void testDrawingDoesNotCrash() {
        overlay.onSizeChanged(1080, 1920, 0, 0);
        
        // Should not throw exception when drawing
        try {
            overlay.draw(null); // Robolectric allows null canvas
            // If we get here, no exception was thrown
            assertTrue("Drawing should complete without exception", true);
        } catch (Exception e) {
            fail("Drawing should not throw exception: " + e.getMessage());
        }
    }
}
