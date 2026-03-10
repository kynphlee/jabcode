package com.jabcode.test;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class ScannerSettingsTest {
    
    private ScannerSettings settings;
    private Context context;
    
    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        settings = new ScannerSettings(context);
        settings.clear(); // Start fresh for each test
    }
    
    @Test
    public void testDefaultValues() {
        assertTrue("Vibration should be enabled by default", settings.isVibrationEnabled());
        assertTrue("Sound should be enabled by default", settings.isSoundEnabled());
        assertFalse("Continuous scan should be disabled by default", settings.isContinuousScanEnabled());
        assertTrue("Auto focus should be enabled by default", settings.isAutoFocusEnabled());
        assertEquals("Default exposure should be 0", 0, settings.getExposureCompensation());
    }
    
    @Test
    public void testVibrationSetting() {
        settings.setVibrationEnabled(false);
        assertFalse("Vibration should be disabled", settings.isVibrationEnabled());
        
        settings.setVibrationEnabled(true);
        assertTrue("Vibration should be enabled", settings.isVibrationEnabled());
    }
    
    @Test
    public void testSoundSetting() {
        settings.setSoundEnabled(false);
        assertFalse("Sound should be disabled", settings.isSoundEnabled());
        
        settings.setSoundEnabled(true);
        assertTrue("Sound should be enabled", settings.isSoundEnabled());
    }
    
    @Test
    public void testContinuousScanSetting() {
        settings.setContinuousScanEnabled(true);
        assertTrue("Continuous scan should be enabled", settings.isContinuousScanEnabled());
        
        settings.setContinuousScanEnabled(false);
        assertFalse("Continuous scan should be disabled", settings.isContinuousScanEnabled());
    }
    
    @Test
    public void testAutoFocusSetting() {
        settings.setAutoFocusEnabled(false);
        assertFalse("Auto focus should be disabled", settings.isAutoFocusEnabled());
        
        settings.setAutoFocusEnabled(true);
        assertTrue("Auto focus should be enabled", settings.isAutoFocusEnabled());
    }
    
    @Test
    public void testExposureCompensation() {
        settings.setExposureCompensation(3);
        assertEquals("Exposure should be 3", 3, settings.getExposureCompensation());
        
        settings.setExposureCompensation(-2);
        assertEquals("Exposure should be -2", -2, settings.getExposureCompensation());
    }
    
    @Test
    public void testExposureCompensationClamping() {
        settings.setExposureCompensation(10);
        assertTrue("Exposure should be clamped to max", 
                  settings.getExposureCompensation() <= 6);
        
        settings.setExposureCompensation(-10);
        assertTrue("Exposure should be clamped to min", 
                  settings.getExposureCompensation() >= -6);
    }
    
    @Test
    public void testPersistence() {
        // Set values
        settings.setVibrationEnabled(false);
        settings.setSoundEnabled(false);
        settings.setExposureCompensation(2);
        
        // Create new instance (should load from SharedPreferences)
        ScannerSettings newSettings = new ScannerSettings(context);
        
        assertFalse("Vibration setting should persist", newSettings.isVibrationEnabled());
        assertFalse("Sound setting should persist", newSettings.isSoundEnabled());
        assertEquals("Exposure setting should persist", 2, newSettings.getExposureCompensation());
    }
    
    @Test
    public void testReset() {
        // Change all settings
        settings.setVibrationEnabled(false);
        settings.setSoundEnabled(false);
        settings.setContinuousScanEnabled(true);
        settings.setAutoFocusEnabled(false);
        settings.setExposureCompensation(3);
        
        // Reset to defaults
        settings.resetToDefaults();
        
        assertTrue("Vibration should reset to default", settings.isVibrationEnabled());
        assertTrue("Sound should reset to default", settings.isSoundEnabled());
        assertFalse("Continuous scan should reset to default", settings.isContinuousScanEnabled());
        assertTrue("Auto focus should reset to default", settings.isAutoFocusEnabled());
        assertEquals("Exposure should reset to default", 0, settings.getExposureCompensation());
    }
    
    @Test
    public void testSaveHistoryEnabled() {
        settings.setSaveHistoryEnabled(true);
        assertTrue("Save history should be enabled", settings.isSaveHistoryEnabled());
        
        settings.setSaveHistoryEnabled(false);
        assertFalse("Save history should be disabled", settings.isSaveHistoryEnabled());
    }
    
    @Test
    public void testBeepVolume() {
        settings.setBeepVolume(0.5f);
        assertEquals("Beep volume should be 0.5", 0.5f, settings.getBeepVolume(), 0.01f);
        
        settings.setBeepVolume(1.0f);
        assertEquals("Beep volume should be 1.0", 1.0f, settings.getBeepVolume(), 0.01f);
    }
    
    @Test
    public void testBeepVolumeClamping() {
        settings.setBeepVolume(1.5f);
        assertEquals("Beep volume should be clamped to 1.0", 1.0f, settings.getBeepVolume(), 0.01f);
        
        settings.setBeepVolume(-0.5f);
        assertEquals("Beep volume should be clamped to 0.0", 0.0f, settings.getBeepVolume(), 0.01f);
    }
}
