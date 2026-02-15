package com.jabcode.test;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.LinearLayout;

public class BottomControlSheet extends LinearLayout {
    
    private ImageButton torchButton;
    private ImageButton galleryButton;
    private ImageButton settingsButton;
    
    private boolean torchEnabled = false;
    
    private Runnable torchClickListener;
    private Runnable galleryClickListener;
    private Runnable settingsClickListener;
    
    public BottomControlSheet(Context context) {
        this(context, null);
    }
    
    public BottomControlSheet(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.bottom_control_sheet, this, true);
        
        torchButton = findViewById(R.id.torch_button);
        galleryButton = findViewById(R.id.gallery_button);
        settingsButton = findViewById(R.id.settings_button);
        
        setupListeners();
        updateTorchIcon();
    }
    
    private void setupListeners() {
        torchButton.setOnClickListener(v -> {
            if (torchClickListener != null) {
                torchClickListener.run();
            }
        });
        
        galleryButton.setOnClickListener(v -> {
            if (galleryClickListener != null) {
                galleryClickListener.run();
            }
        });
        
        settingsButton.setOnClickListener(v -> {
            if (settingsClickListener != null) {
                settingsClickListener.run();
            }
        });
    }
    
    public void setTorchEnabled(boolean enabled) {
        this.torchEnabled = enabled;
        updateTorchIcon();
    }
    
    public boolean isTorchEnabled() {
        return torchEnabled;
    }
    
    private void updateTorchIcon() {
        if (torchButton != null) {
            torchButton.setImageResource(
                torchEnabled ? R.drawable.ic_flash_on : R.drawable.ic_flash_off
            );
        }
    }
    
    public ImageButton getTorchButton() {
        return torchButton;
    }
    
    public ImageButton getGalleryButton() {
        return galleryButton;
    }
    
    public ImageButton getSettingsButton() {
        return settingsButton;
    }
    
    public void setOnTorchClickListener(Runnable listener) {
        this.torchClickListener = listener;
    }
    
    public void setOnGalleryClickListener(Runnable listener) {
        this.galleryClickListener = listener;
    }
    
    public void setOnSettingsClickListener(Runnable listener) {
        this.settingsClickListener = listener;
    }
    
    public void setButtonsEnabled(boolean enabled) {
        torchButton.setEnabled(enabled);
        galleryButton.setEnabled(enabled);
        settingsButton.setEnabled(enabled);
    }
}
