package com.jabcode.test;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class ImageCropHelperTest {
    
    private ImageCropHelper cropHelper;
    
    @Before
    public void setUp() {
        cropHelper = new ImageCropHelper();
    }
    
    @Test
    public void testInitialization() {
        assertNotNull("Crop helper should be initialized", cropHelper);
    }
    
    @Test
    public void testDetectBoundsFromBitmap() {
        Bitmap testBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        
        Rect bounds = cropHelper.detectJABCodeBounds(testBitmap);
        
        assertNotNull("Bounds should not be null", bounds);
        assertTrue("Bounds width should be positive", bounds.width() > 0);
        assertTrue("Bounds height should be positive", bounds.height() > 0);
    }
    
    @Test
    public void testDetectBoundsWithMinimumSize() {
        Bitmap testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        
        Rect bounds = cropHelper.detectJABCodeBounds(testBitmap);
        
        assertNotNull("Should handle small bitmaps", bounds);
        assertTrue("Minimum bounds should be enforced", 
                  bounds.width() >= ImageCropHelper.MIN_CROP_SIZE);
    }
    
    @Test
    public void testCropBitmapToBounds() {
        Bitmap original = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        Rect cropRect = new Rect(100, 100, 300, 300);
        
        Bitmap cropped = cropHelper.cropBitmap(original, cropRect);
        
        assertNotNull("Cropped bitmap should not be null", cropped);
        assertEquals("Cropped width should match", 200, cropped.getWidth());
        assertEquals("Cropped height should match", 200, cropped.getHeight());
    }
    
    @Test
    public void testCropWithInvalidBounds() {
        Bitmap original = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        Rect invalidRect = new Rect(-10, -10, 500, 500);
        
        Bitmap cropped = cropHelper.cropBitmap(original, invalidRect);
        
        assertNotNull("Should handle invalid bounds gracefully", cropped);
        assertTrue("Should clamp to valid dimensions", 
                  cropped.getWidth() <= original.getWidth());
    }
    
    @Test
    public void testExpandBoundsWithPadding() {
        Rect bounds = new Rect(100, 100, 200, 200);
        int padding = 20;
        int imageWidth = 400;
        int imageHeight = 400;
        
        Rect expanded = cropHelper.expandBounds(bounds, padding, imageWidth, imageHeight);
        
        assertEquals("Left should expand by padding", 80, expanded.left);
        assertEquals("Top should expand by padding", 80, expanded.top);
        assertEquals("Right should expand by padding", 220, expanded.right);
        assertEquals("Bottom should expand by padding", 220, expanded.bottom);
    }
    
    @Test
    public void testExpandBoundsAtImageEdge() {
        Rect bounds = new Rect(10, 10, 50, 50);
        int padding = 20;
        int imageWidth = 100;
        int imageHeight = 100;
        
        Rect expanded = cropHelper.expandBounds(bounds, padding, imageWidth, imageHeight);
        
        assertTrue("Should not expand beyond image bounds", expanded.left >= 0);
        assertTrue("Should not expand beyond image bounds", expanded.top >= 0);
        assertTrue("Should not expand beyond image bounds", expanded.right <= imageWidth);
        assertTrue("Should not expand beyond image bounds", expanded.bottom <= imageHeight);
    }
    
    @Test
    public void testCalculateAspectRatio() {
        Rect bounds = new Rect(0, 0, 100, 200);
        
        float ratio = cropHelper.calculateAspectRatio(bounds);
        
        assertEquals("Aspect ratio should be 0.5", 0.5f, ratio, 0.01f);
    }
    
    @Test
    public void testCalculateAspectRatioSquare() {
        Rect bounds = new Rect(0, 0, 100, 100);
        
        float ratio = cropHelper.calculateAspectRatio(bounds);
        
        assertEquals("Square should have ratio 1.0", 1.0f, ratio, 0.01f);
    }
    
    @Test
    public void testIsValidJABCodeBounds() {
        Rect validBounds = new Rect(0, 0, 100, 100);
        Rect tooSmall = new Rect(0, 0, 20, 20);
        Rect wrongAspect = new Rect(0, 0, 100, 300);
        
        assertTrue("Valid square bounds should pass", 
                  cropHelper.isValidJABCodeBounds(validBounds));
        assertFalse("Too small bounds should fail", 
                   cropHelper.isValidJABCodeBounds(tooSmall));
        assertFalse("Wrong aspect ratio should fail", 
                   cropHelper.isValidJABCodeBounds(wrongAspect));
    }
    
    @Test
    public void testRotateBoundsBy90Degrees() {
        Rect original = new Rect(50, 100, 150, 300);
        int imageWidth = 400;
        int imageHeight = 600;
        
        Rect rotated = cropHelper.rotateBounds(original, 90, imageWidth, imageHeight);
        
        assertNotNull("Rotated bounds should not be null", rotated);
        assertTrue("Rotated bounds should be valid", rotated.width() > 0);
    }
}
