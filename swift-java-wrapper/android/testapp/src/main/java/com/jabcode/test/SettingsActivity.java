package com.jabcode.test;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.slider.Slider;
import com.jabcode.test.databinding.ActivitySettingsBinding;

public class SettingsActivity extends AppCompatActivity {
    
    private ActivitySettingsBinding binding;
    private ScannerSettings settings;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        settings = new ScannerSettings(this);
        
        setupToolbar();
        loadSettings();
        setupListeners();
    }
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Scanner Settings");
        }
    }
    
    private void loadSettings() {
        binding.switchVibration.setChecked(settings.isVibrationEnabled());
        binding.switchSound.setChecked(settings.isSoundEnabled());
        binding.switchAutoFocus.setChecked(settings.isAutoFocusEnabled());
        binding.sliderVolume.setValue(settings.getBeepVolume() * 100);
        binding.sliderExposure.setValue(settings.getExposureCompensation());
        binding.sliderZoom.setValue(settings.getZoomRatio());
    }
    
    private void setupListeners() {
        binding.switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setVibrationEnabled(isChecked);
        });
        
        binding.switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setSoundEnabled(isChecked);
            binding.sliderVolume.setEnabled(isChecked);
        });
        
        binding.switchAutoFocus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setAutoFocusEnabled(isChecked);
        });
        
        binding.sliderVolume.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                settings.setBeepVolume(value / 100f);
            }
        });
        
        binding.sliderExposure.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                settings.setExposureCompensation((int)value);
            }
        });
        
        binding.sliderZoom.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                settings.setZoomRatio(value);
            }
        });
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
