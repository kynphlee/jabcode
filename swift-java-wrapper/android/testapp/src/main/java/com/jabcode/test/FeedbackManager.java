package com.jabcode.test;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class FeedbackManager {
    
    private static final int[] SUCCESS_PATTERN = {0, 50};
    private static final int[] ERROR_PATTERN = {0, 100, 50, 100};
    
    private final Context context;
    private final ScannerSettings settings;
    private final Vibrator vibrator;
    private SoundPool soundPool;
    private int successSoundId = -1;
    private boolean soundLoaded = false;
    
    public FeedbackManager(Context context, ScannerSettings settings) {
        this.context = context;
        this.settings = settings;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        
        initializeSoundPool();
    }
    
    private void initializeSoundPool() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(attributes)
                    .build();
        } else {
            soundPool = new SoundPool(1, AudioManager.STREAM_NOTIFICATION, 0);
        }
        
        soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            soundLoaded = (status == 0);
        });
    }
    
    public void provideSuccessFeedback() {
        if (settings != null && settings.isVibrationEnabled()) {
            vibratePattern(SUCCESS_PATTERN);
        }
    }
    
    public void provideErrorFeedback() {
        if (settings != null && settings.isVibrationEnabled()) {
            vibratePattern(ERROR_PATTERN);
        }
    }
    
    private void vibratePattern(int[] pattern) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(
                    convertToLongArray(pattern), -1);
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(convertToLongArray(pattern), -1);
            }
        }
    }
    
    private long[] convertToLongArray(int[] input) {
        long[] output = new long[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }
    
    public boolean playSuccessSound() {
        if (settings == null || !settings.isSoundEnabled()) {
            return false;
        }
        
        if (soundPool != null && soundLoaded && successSoundId != -1) {
            float volume = settings.getBeepVolume();
            soundPool.play(successSoundId, volume, volume, 1, 0, 1.0f);
            return true;
        }
        
        return settings.isSoundEnabled();
    }
    
    public int[] getSuccessPattern() {
        return SUCCESS_PATTERN.clone();
    }
    
    public int[] getErrorPattern() {
        return ERROR_PATTERN.clone();
    }
    
    public boolean isVibratorAvailable() {
        return vibrator != null && vibrator.hasVibrator();
    }
    
    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        soundLoaded = false;
        successSoundId = -1;
    }
}
