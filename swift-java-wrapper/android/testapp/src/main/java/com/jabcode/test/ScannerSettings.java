package com.jabcode.test;

import android.content.Context;
import android.content.SharedPreferences;

public class ScannerSettings {
    
    private static final String PREFS_NAME = "jabcode_scanner_settings";
    
    private static final String KEY_VIBRATION_ENABLED = "vibration_enabled";
    private static final String KEY_SOUND_ENABLED = "sound_enabled";
    private static final String KEY_CONTINUOUS_SCAN = "continuous_scan";
    private static final String KEY_AUTO_FOCUS = "auto_focus";
    private static final String KEY_EXPOSURE_COMPENSATION = "exposure_compensation";
    private static final String KEY_SAVE_HISTORY = "save_history";
    private static final String KEY_BEEP_VOLUME = "beep_volume";
    private static final String KEY_ZOOM_RATIO = "zoom_ratio";
    
    private static final boolean DEFAULT_VIBRATION = true;
    private static final boolean DEFAULT_SOUND = true;
    private static final boolean DEFAULT_CONTINUOUS_SCAN = false;
    private static final boolean DEFAULT_AUTO_FOCUS = true;
    private static final int DEFAULT_EXPOSURE = 0;
    private static final boolean DEFAULT_SAVE_HISTORY = false;
    private static final float DEFAULT_BEEP_VOLUME = 1.0f;
    private static final float DEFAULT_ZOOM_RATIO = 1.0f;
    
    private static final int MIN_EXPOSURE = -6;
    private static final int MAX_EXPOSURE = 6;
    private static final float MIN_VOLUME = 0.0f;
    private static final float MAX_VOLUME = 1.0f;
    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 10.0f;
    
    private final SharedPreferences prefs;
    
    public ScannerSettings(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public boolean isVibrationEnabled() {
        return prefs.getBoolean(KEY_VIBRATION_ENABLED, DEFAULT_VIBRATION);
    }
    
    public void setVibrationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply();
    }
    
    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND_ENABLED, DEFAULT_SOUND);
    }
    
    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();
    }
    
    public boolean isContinuousScanEnabled() {
        return prefs.getBoolean(KEY_CONTINUOUS_SCAN, DEFAULT_CONTINUOUS_SCAN);
    }
    
    public void setContinuousScanEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CONTINUOUS_SCAN, enabled).apply();
    }
    
    public boolean isAutoFocusEnabled() {
        return prefs.getBoolean(KEY_AUTO_FOCUS, DEFAULT_AUTO_FOCUS);
    }
    
    public void setAutoFocusEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_FOCUS, enabled).apply();
    }
    
    public int getExposureCompensation() {
        return prefs.getInt(KEY_EXPOSURE_COMPENSATION, DEFAULT_EXPOSURE);
    }
    
    public void setExposureCompensation(int value) {
        int clamped = Math.max(MIN_EXPOSURE, Math.min(MAX_EXPOSURE, value));
        prefs.edit().putInt(KEY_EXPOSURE_COMPENSATION, clamped).apply();
    }
    
    public boolean isSaveHistoryEnabled() {
        return prefs.getBoolean(KEY_SAVE_HISTORY, DEFAULT_SAVE_HISTORY);
    }
    
    public void setSaveHistoryEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SAVE_HISTORY, enabled).apply();
    }
    
    public float getBeepVolume() {
        return prefs.getFloat(KEY_BEEP_VOLUME, DEFAULT_BEEP_VOLUME);
    }
    
    public void setBeepVolume(float volume) {
        float clamped = Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, volume));
        prefs.edit().putFloat(KEY_BEEP_VOLUME, clamped).apply();
    }
    
    public void resetToDefaults() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_VIBRATION_ENABLED, DEFAULT_VIBRATION);
        editor.putBoolean(KEY_SOUND_ENABLED, DEFAULT_SOUND);
        editor.putBoolean(KEY_CONTINUOUS_SCAN, DEFAULT_CONTINUOUS_SCAN);
        editor.putBoolean(KEY_AUTO_FOCUS, DEFAULT_AUTO_FOCUS);
        editor.putInt(KEY_EXPOSURE_COMPENSATION, DEFAULT_EXPOSURE);
        editor.putBoolean(KEY_SAVE_HISTORY, DEFAULT_SAVE_HISTORY);
        editor.putFloat(KEY_BEEP_VOLUME, DEFAULT_BEEP_VOLUME);
        editor.apply();
    }
    
    public void clear() {
        prefs.edit().clear().apply();
    }
    
    public int getMinExposure() {
        return MIN_EXPOSURE;
    }
    
    public int getMaxExposure() {
        return MAX_EXPOSURE;
    }
    
    public float getZoomRatio() {
        return prefs.getFloat(KEY_ZOOM_RATIO, DEFAULT_ZOOM_RATIO);
    }
    
    public void setZoomRatio(float ratio) {
        float clamped = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, ratio));
        prefs.edit().putFloat(KEY_ZOOM_RATIO, clamped).apply();
    }
}
