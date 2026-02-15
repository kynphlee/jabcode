package com.jabcode.test;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;

public class ImageQualityAnalyzer {
    
    private static final int SAMPLE_SIZE = 50;
    
    public static class QualityMetrics {
        public float averageBrightness;
        public float contrast;
        public float sharpness;
        public float colorBalance;
        
        public QualityMetrics(float brightness, float contrast, float sharpness, float colorBalance) {
            this.averageBrightness = brightness;
            this.contrast = contrast;
            this.sharpness = sharpness;
            this.colorBalance = colorBalance;
        }
    }
    
    public QualityMetrics analyze(Bitmap bitmap) {
        if (bitmap == null) {
            return new QualityMetrics(128, 0.5f, 0.5f, 0.5f);
        }
        
        float brightness = calculateBrightness(bitmap);
        float contrast = calculateContrast(bitmap);
        float sharpness = calculateSharpness(bitmap);
        float colorBalance = calculateColorBalance(bitmap);
        
        return new QualityMetrics(brightness, contrast, sharpness, colorBalance);
    }
    
    private float calculateBrightness(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int stepX = Math.max(1, width / SAMPLE_SIZE);
        int stepY = Math.max(1, height / SAMPLE_SIZE);
        
        long totalBrightness = 0;
        int sampleCount = 0;
        
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                totalBrightness += (r + g + b) / 3;
                sampleCount++;
            }
        }
        
        return sampleCount > 0 ? (float)totalBrightness / sampleCount : 128;
    }
    
    private float calculateContrast(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int stepX = Math.max(1, width / SAMPLE_SIZE);
        int stepY = Math.max(1, height / SAMPLE_SIZE);
        
        int minBrightness = 255;
        int maxBrightness = 0;
        
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int pixel = bitmap.getPixel(x, y);
                int brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                minBrightness = Math.min(minBrightness, brightness);
                maxBrightness = Math.max(maxBrightness, brightness);
            }
        }
        
        return (maxBrightness - minBrightness) / 255.0f;
    }
    
    private float calculateSharpness(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        if (width < 3 || height < 3) return 0.5f;
        
        int centerX = width / 2;
        int centerY = height / 2;
        int sampleRadius = Math.min(50, Math.min(width, height) / 4);
        
        float totalGradient = 0;
        int gradientCount = 0;
        
        for (int y = centerY - sampleRadius; y < centerY + sampleRadius - 1; y++) {
            for (int x = centerX - sampleRadius; x < centerX + sampleRadius - 1; x++) {
                if (x >= 0 && x < width - 1 && y >= 0 && y < height - 1) {
                    int pixel1 = bitmap.getPixel(x, y);
                    int pixel2 = bitmap.getPixel(x + 1, y);
                    int pixel3 = bitmap.getPixel(x, y + 1);
                    
                    int gray1 = getGray(pixel1);
                    int gray2 = getGray(pixel2);
                    int gray3 = getGray(pixel3);
                    
                    float gradientX = Math.abs(gray2 - gray1);
                    float gradientY = Math.abs(gray3 - gray1);
                    totalGradient += Math.sqrt(gradientX * gradientX + gradientY * gradientY);
                    gradientCount++;
                }
            }
        }
        
        float avgGradient = gradientCount > 0 ? totalGradient / gradientCount : 0;
        return Math.min(1.0f, avgGradient / 50.0f);
    }
    
    private float calculateColorBalance(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int stepX = Math.max(1, width / SAMPLE_SIZE);
        int stepY = Math.max(1, height / SAMPLE_SIZE);
        
        long totalR = 0, totalG = 0, totalB = 0;
        int sampleCount = 0;
        
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int pixel = bitmap.getPixel(x, y);
                totalR += Color.red(pixel);
                totalG += Color.green(pixel);
                totalB += Color.blue(pixel);
                sampleCount++;
            }
        }
        
        if (sampleCount == 0) return 0.5f;
        
        float avgR = (float)totalR / sampleCount;
        float avgG = (float)totalG / sampleCount;
        float avgB = (float)totalB / sampleCount;
        
        float maxChannel = Math.max(avgR, Math.max(avgG, avgB));
        float minChannel = Math.min(avgR, Math.min(avgG, avgB));
        
        return maxChannel > 0 ? 1.0f - ((maxChannel - minChannel) / maxChannel) : 0.5f;
    }
    
    public Rect findHighContrastRegion(Bitmap bitmap) {
        if (bitmap == null) return null;
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int regionSize = Math.min(width, height) / 3;
        
        Rect bestRegion = new Rect(
            (width - regionSize) / 2,
            (height - regionSize) / 2,
            (width + regionSize) / 2,
            (height + regionSize) / 2
        );
        float bestContrast = 0;
        
        int step = regionSize / 2;
        for (int y = 0; y < height - regionSize; y += step) {
            for (int x = 0; x < width - regionSize; x += step) {
                Rect region = new Rect(x, y, x + regionSize, y + regionSize);
                float contrast = calculateRegionContrast(bitmap, region);
                
                if (contrast > bestContrast) {
                    bestContrast = contrast;
                    bestRegion = region;
                }
            }
        }
        
        return bestRegion;
    }
    
    private float calculateRegionContrast(Bitmap bitmap, Rect region) {
        int minBrightness = 255;
        int maxBrightness = 0;
        int step = 5;
        
        for (int y = region.top; y < region.bottom; y += step) {
            for (int x = region.left; x < region.right; x += step) {
                if (x >= 0 && x < bitmap.getWidth() && y >= 0 && y < bitmap.getHeight()) {
                    int pixel = bitmap.getPixel(x, y);
                    int brightness = getGray(pixel);
                    minBrightness = Math.min(minBrightness, brightness);
                    maxBrightness = Math.max(maxBrightness, brightness);
                }
            }
        }
        
        return (maxBrightness - minBrightness) / 255.0f;
    }
    
    private int getGray(int pixel) {
        int r = Color.red(pixel);
        int g = Color.green(pixel);
        int b = Color.blue(pixel);
        return (r + g + b) / 3;
    }
}
