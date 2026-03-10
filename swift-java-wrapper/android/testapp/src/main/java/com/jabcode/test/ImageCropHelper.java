package com.jabcode.test;

import android.graphics.Bitmap;
import android.graphics.Rect;

public class ImageCropHelper {
    
    public static final int MIN_CROP_SIZE = 50;
    private static final int DEFAULT_PADDING = 10;
    private static final float MIN_ASPECT_RATIO = 0.7f;
    private static final float MAX_ASPECT_RATIO = 1.3f;
    
    public Rect detectJABCodeBounds(Bitmap bitmap) {
        if (bitmap == null) {
            return new Rect(0, 0, MIN_CROP_SIZE, MIN_CROP_SIZE);
        }
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        // Simple center-based bounds detection
        // In production, this would use edge detection or pattern recognition
        int minDim = Math.min(width, height);
        int cropSize = Math.max(MIN_CROP_SIZE, (int)(minDim * 0.8));
        
        int left = (width - cropSize) / 2;
        int top = (height - cropSize) / 2;
        int right = left + cropSize;
        int bottom = top + cropSize;
        
        return new Rect(left, top, right, bottom);
    }
    
    public Bitmap cropBitmap(Bitmap original, Rect bounds) {
        if (original == null || bounds == null) {
            return original;
        }
        
        // Clamp bounds to valid image dimensions
        int left = Math.max(0, bounds.left);
        int top = Math.max(0, bounds.top);
        int right = Math.min(original.getWidth(), bounds.right);
        int bottom = Math.min(original.getHeight(), bounds.bottom);
        
        int width = right - left;
        int height = bottom - top;
        
        if (width <= 0 || height <= 0) {
            return original;
        }
        
        try {
            return Bitmap.createBitmap(original, left, top, width, height);
        } catch (Exception e) {
            return original;
        }
    }
    
    public Rect expandBounds(Rect bounds, int padding, int imageWidth, int imageHeight) {
        if (bounds == null) {
            return new Rect(0, 0, imageWidth, imageHeight);
        }
        
        int left = Math.max(0, bounds.left - padding);
        int top = Math.max(0, bounds.top - padding);
        int right = Math.min(imageWidth, bounds.right + padding);
        int bottom = Math.min(imageHeight, bounds.bottom + padding);
        
        return new Rect(left, top, right, bottom);
    }
    
    public float calculateAspectRatio(Rect bounds) {
        if (bounds == null || bounds.height() == 0) {
            return 1.0f;
        }
        
        return (float) bounds.width() / (float) bounds.height();
    }
    
    public boolean isValidJABCodeBounds(Rect bounds) {
        if (bounds == null) {
            return false;
        }
        
        int width = bounds.width();
        int height = bounds.height();
        
        // Check minimum size
        if (width < MIN_CROP_SIZE || height < MIN_CROP_SIZE) {
            return false;
        }
        
        // Check aspect ratio (JABCode should be roughly square)
        float aspectRatio = calculateAspectRatio(bounds);
        if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) {
            return false;
        }
        
        return true;
    }
    
    public Rect rotateBounds(Rect bounds, int degrees, int imageWidth, int imageHeight) {
        if (bounds == null) {
            return new Rect(0, 0, imageWidth, imageHeight);
        }
        
        // Normalize degrees to 0-360
        degrees = degrees % 360;
        if (degrees < 0) {
            degrees += 360;
        }
        
        switch (degrees) {
            case 90:
                return new Rect(
                    bounds.top,
                    imageWidth - bounds.right,
                    bounds.bottom,
                    imageWidth - bounds.left
                );
            case 180:
                return new Rect(
                    imageWidth - bounds.right,
                    imageHeight - bounds.bottom,
                    imageWidth - bounds.left,
                    imageHeight - bounds.top
                );
            case 270:
                return new Rect(
                    imageHeight - bounds.bottom,
                    bounds.left,
                    imageHeight - bounds.top,
                    bounds.right
                );
            default:
                return bounds;
        }
    }
    
    public Rect normalizeToSquare(Rect bounds) {
        if (bounds == null) {
            return new Rect(0, 0, MIN_CROP_SIZE, MIN_CROP_SIZE);
        }
        
        int width = bounds.width();
        int height = bounds.height();
        
        if (width == height) {
            return bounds;
        }
        
        int size = Math.max(width, height);
        int centerX = bounds.left + width / 2;
        int centerY = bounds.top + height / 2;
        
        int halfSize = size / 2;
        return new Rect(
            centerX - halfSize,
            centerY - halfSize,
            centerX + halfSize,
            centerY + halfSize
        );
    }
}
