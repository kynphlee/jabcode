package com.jabcode.test;

import android.view.View;
import android.widget.ImageButton;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BottomControlSheetInstrumentationTest {
    
    private BottomControlSheet controlSheet;
    
    @Before
    public void setUp() {
        controlSheet = new BottomControlSheet(ApplicationProvider.getApplicationContext());
    }
    
    @Test
    public void testInitialization() {
        assertNotNull("Control sheet should be initialized", controlSheet);
        assertFalse("Torch should be off by default", controlSheet.isTorchEnabled());
    }
    
    @Test
    public void testTorchToggle() {
        assertFalse("Initial torch state should be off", controlSheet.isTorchEnabled());
        
        controlSheet.setTorchEnabled(true);
        assertTrue("Torch should be enabled", controlSheet.isTorchEnabled());
        
        controlSheet.setTorchEnabled(false);
        assertFalse("Torch should be disabled", controlSheet.isTorchEnabled());
    }
    
    @Test
    public void testTorchButton() {
        ImageButton torchButton = controlSheet.getTorchButton();
        assertNotNull("Torch button should exist", torchButton);
        assertEquals("Torch button should be visible", View.VISIBLE, torchButton.getVisibility());
    }
    
    @Test
    public void testGalleryButton() {
        ImageButton galleryButton = controlSheet.getGalleryButton();
        assertNotNull("Gallery button should exist", galleryButton);
        assertEquals("Gallery button should be visible", View.VISIBLE, galleryButton.getVisibility());
    }
    
    @Test
    public void testSettingsButton() {
        ImageButton settingsButton = controlSheet.getSettingsButton();
        assertNotNull("Settings button should exist", settingsButton);
        assertEquals("Settings button should be visible", View.VISIBLE, settingsButton.getVisibility());
    }
    
    @Test
    public void testCallbacksSet() {
        final boolean[] torchClicked = {false};
        final boolean[] galleryClicked = {false};
        final boolean[] settingsClicked = {false};
        
        controlSheet.setOnTorchClickListener(() -> torchClicked[0] = true);
        controlSheet.setOnGalleryClickListener(() -> galleryClicked[0] = true);
        controlSheet.setOnSettingsClickListener(() -> settingsClicked[0] = true);
        
        controlSheet.getTorchButton().performClick();
        controlSheet.getGalleryButton().performClick();
        controlSheet.getSettingsButton().performClick();
        
        assertTrue("Torch callback should fire", torchClicked[0]);
        assertTrue("Gallery callback should fire", galleryClicked[0]);
        assertTrue("Settings callback should fire", settingsClicked[0]);
    }
}
