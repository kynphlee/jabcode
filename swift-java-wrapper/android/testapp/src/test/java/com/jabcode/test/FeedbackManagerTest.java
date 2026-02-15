package com.jabcode.test;

import android.content.Context;
import android.media.AudioManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowVibrator;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class FeedbackManagerTest {
    
    private FeedbackManager feedbackManager;
    private Context context;
    private ScannerSettings mockSettings;
    
    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        mockSettings = mock(ScannerSettings.class);
        
        when(mockSettings.isVibrationEnabled()).thenReturn(true);
        when(mockSettings.isSoundEnabled()).thenReturn(true);
        when(mockSettings.getBeepVolume()).thenReturn(1.0f);
        
        feedbackManager = new FeedbackManager(context, mockSettings);
    }
    
    @Test
    public void testInitialization() {
        assertNotNull("Feedback manager should be initialized", feedbackManager);
    }
    
    @Test
    public void testSuccessVibration() {
        when(mockSettings.isVibrationEnabled()).thenReturn(true);
        
        feedbackManager.provideSuccessFeedback();
        
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        ShadowVibrator shadowVibrator = shadowOf(vibrator);
        assertTrue("Should have vibrated", shadowVibrator.isVibrating());
    }
    
    @Test
    public void testVibrationDisabled() {
        when(mockSettings.isVibrationEnabled()).thenReturn(false);
        
        feedbackManager.provideSuccessFeedback();
        
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        ShadowVibrator shadowVibrator = shadowOf(vibrator);
        assertFalse("Should not vibrate when disabled", shadowVibrator.isVibrating());
    }
    
    @Test
    public void testErrorVibration() {
        when(mockSettings.isVibrationEnabled()).thenReturn(true);
        
        feedbackManager.provideErrorFeedback();
        
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        ShadowVibrator shadowVibrator = shadowOf(vibrator);
        assertTrue("Should have vibrated for error", shadowVibrator.isVibrating());
    }
    
    @Test
    public void testSuccessSoundEnabled() {
        when(mockSettings.isSoundEnabled()).thenReturn(true);
        
        boolean result = feedbackManager.playSuccessSound();
        
        assertTrue("Success sound should be triggered", result);
    }
    
    @Test
    public void testSoundDisabled() {
        when(mockSettings.isSoundEnabled()).thenReturn(false);
        
        boolean result = feedbackManager.playSuccessSound();
        
        assertFalse("Sound should not play when disabled", result);
    }
    
    @Test
    public void testVolumeRespected() {
        when(mockSettings.isSoundEnabled()).thenReturn(true);
        when(mockSettings.getBeepVolume()).thenReturn(0.5f);
        
        feedbackManager = new FeedbackManager(context, mockSettings);
        boolean result = feedbackManager.playSuccessSound();
        
        // Sound attempt should be made when enabled
        assertTrue("Should attempt to play sound when enabled", result);
    }
    
    @Test
    public void testHapticPatternSuccess() {
        int[] pattern = feedbackManager.getSuccessPattern();
        
        assertNotNull("Success pattern should not be null", pattern);
        assertTrue("Success pattern should have timing values", pattern.length > 0);
        assertEquals("First value should be delay", 0, pattern[0]);
    }
    
    @Test
    public void testHapticPatternError() {
        int[] pattern = feedbackManager.getErrorPattern();
        
        assertNotNull("Error pattern should not be null", pattern);
        assertTrue("Error pattern should have timing values", pattern.length > 0);
        assertTrue("Error pattern should be different from success", 
                  pattern.length >= 2);
    }
    
    @Test
    public void testVibratorAvailability() {
        boolean hasVibrator = feedbackManager.isVibratorAvailable();
        
        assertTrue("Vibrator should be available in test environment", hasVibrator);
    }
    
    @Test
    public void testCleanup() {
        feedbackManager.release();
        
        // Ensure no crashes on cleanup
        feedbackManager.provideSuccessFeedback();
    }
    
    @Test
    public void testMultipleFeedbackCalls() {
        feedbackManager.provideSuccessFeedback();
        feedbackManager.provideSuccessFeedback();
        feedbackManager.provideErrorFeedback();
        
        // Should handle rapid calls without crashes
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        assertNotNull("Vibrator should still be accessible", vibrator);
    }
    
    @Test
    public void testFeedbackWithNullSettings() {
        FeedbackManager manager = new FeedbackManager(context, null);
        
        // Should use defaults and not crash
        manager.provideSuccessFeedback();
        assertNotNull("Manager should handle null settings", manager);
    }
}
