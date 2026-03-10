package com.jabcode.test.calibration;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import com.jabcode.test.JABCodeMobile;
import java.util.ArrayList;
import java.util.List;

public class CalibrationAnalyzer {
    private static final String TAG = "CalibrationAnalyzer";
    
    public static class AnalysisResult {
        public boolean success;
        public String errorMessage;
        public CalibrationProfile profile;
        public ColorDistanceMatrix distanceMatrix;
        
        public AnalysisResult(boolean success, String error) {
            this.success = success;
            this.errorMessage = error;
        }
    }
    
    public static class ColorDistanceMatrix {
        private float[][] distances;
        private String[] colorNames = {"Black", "White", "Red", "Green", "Blue", "Yellow", "Cyan", "Magenta"};
        
        public ColorDistanceMatrix() {
            distances = new float[8][8];
        }
        
        public void setDistance(int colorA, int colorB, float distance) {
            distances[colorA][colorB] = distance;
            distances[colorB][colorA] = distance;
        }
        
        public float getDistance(int colorA, int colorB) {
            return distances[colorA][colorB];
        }
        
        public float getMinSeparation() {
            float min = Float.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                for (int j = i + 1; j < 8; j++) {
                    if (distances[i][j] > 0 && distances[i][j] < min) {
                        min = distances[i][j];
                    }
                }
            }
            return min;
        }
        
        public float getAverageSeparation() {
            float sum = 0;
            int count = 0;
            for (int i = 0; i < 8; i++) {
                for (int j = i + 1; j < 8; j++) {
                    if (distances[i][j] > 0) {
                        sum += distances[i][j];
                        count++;
                    }
                }
            }
            return count > 0 ? sum / count : 0;
        }
        
        public String getReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("Color Distance Matrix:\n");
            for (int i = 0; i < 8; i++) {
                for (int j = i + 1; j < 8; j++) {
                    if (distances[i][j] > 0) {
                        sb.append(String.format("%s ↔ %s: %.2f\n", 
                            colorNames[i], colorNames[j], distances[i][j]));
                    }
                }
            }
            sb.append(String.format("\nMin Separation: %.2f\n", getMinSeparation()));
            sb.append(String.format("Avg Separation: %.2f\n", getAverageSeparation()));
            return sb.toString();
        }
    }
    
    public static AnalysisResult analyzeCapturedPattern(Bitmap capturedBitmap, String printerModel, String deviceModel) {
        try {
            Log.i(TAG, "Starting calibration analysis...");
            
            List<ColorSample> samples = extractColorSamples(capturedBitmap);
            if (samples.size() < 4096) {
                return new AnalysisResult(false, 
                    "Insufficient samples: " + samples.size() + " (expected 4096)");
            }
            
            Log.i(TAG, "Extracted " + samples.size() + " color samples");
            
            CalibrationProfile profile = new CalibrationProfile();
            profile.setPrinter(new CalibrationProfile.PrinterInfo(printerModel, "unknown", ""));
            profile.setCamera(new CalibrationProfile.CameraInfo(deviceModel, new java.util.Date()));
            
            CalibrationProfile.ColorMapping mapping = findOptimalColors(samples);
            profile.setColorMapping(mapping);
            
            ColorDistanceMatrix distanceMatrix = calculateDistances(mapping);
            
            CalibrationProfile.QualityMetrics quality = new CalibrationProfile.QualityMetrics(
                distanceMatrix.getAverageSeparation(),
                calculateColorDistance(mapping.red.calibrated, mapping.magenta.calibrated),
                distanceMatrix.getMinSeparation()
            );
            profile.setQuality(quality);
            
            Log.i(TAG, "Calibration analysis complete. Quality: " + quality.getQualityLevel());
            
            AnalysisResult result = new AnalysisResult(true, null);
            result.profile = profile;
            result.distanceMatrix = distanceMatrix;
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Analysis failed", e);
            return new AnalysisResult(false, "Analysis error: " + e.getMessage());
        }
    }
    
    private static class ColorSample {
        int r, g, b;
        int moduleX, moduleY;
        
        ColorSample(int r, int g, int b, int x, int y) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.moduleX = x;
            this.moduleY = y;
        }
        
        CalibrationProfile.RGBColor toRGBColor() {
            return new CalibrationProfile.RGBColor(r, g, b);
        }
    }
    
    private static List<ColorSample> extractColorSamples(Bitmap bitmap) {
        List<ColorSample> samples = new ArrayList<>();
        
        int moduleSize = bitmap.getWidth() / 77;
        
        for (int moduleY = 0; moduleY < 77; moduleY++) {
            for (int moduleX = 0; moduleX < 77; moduleX++) {
                if (!isFinderOrAlignment(moduleX, moduleY)) {
                    int centerX = moduleX * moduleSize + moduleSize / 2;
                    int centerY = moduleY * moduleSize + moduleSize / 2;
                    
                    if (centerX < bitmap.getWidth() && centerY < bitmap.getHeight()) {
                        int pixel = bitmap.getPixel(centerX, centerY);
                        int r = Color.red(pixel);
                        int g = Color.green(pixel);
                        int b = Color.blue(pixel);
                        
                        samples.add(new ColorSample(r, g, b, moduleX, moduleY));
                    }
                }
            }
        }
        
        return samples;
    }
    
    private static boolean isFinderOrAlignment(int x, int y) {
        int fpSize = 7;
        boolean topLeft = (x < fpSize && y < fpSize);
        boolean topRight = (x >= 77 - fpSize && y < fpSize);
        boolean bottomLeft = (x < fpSize && y >= 77 - fpSize);
        boolean bottomRight = (x >= 77 - fpSize && y >= 77 - fpSize);
        
        return topLeft || topRight || bottomLeft || bottomRight;
    }
    
    private static CalibrationProfile.ColorMapping findOptimalColors(List<ColorSample> samples) {
        CalibrationProfile.ColorMapping mapping = new CalibrationProfile.ColorMapping();
        
        CalibrationProfile.RGBColor stdRed = new CalibrationProfile.RGBColor(255, 0, 0);
        CalibrationProfile.RGBColor stdGreen = new CalibrationProfile.RGBColor(0, 255, 0);
        CalibrationProfile.RGBColor stdBlue = new CalibrationProfile.RGBColor(0, 0, 255);
        CalibrationProfile.RGBColor stdWhite = new CalibrationProfile.RGBColor(255, 255, 255);
        CalibrationProfile.RGBColor stdMagenta = new CalibrationProfile.RGBColor(255, 0, 255);
        
        mapping.red = new CalibrationProfile.ColorMap(stdRed, findClosestToCorner(samples, stdRed));
        mapping.green = new CalibrationProfile.ColorMap(stdGreen, findClosestToCorner(samples, stdGreen));
        mapping.blue = new CalibrationProfile.ColorMap(stdBlue, findClosestToCorner(samples, stdBlue));
        mapping.white = new CalibrationProfile.ColorMap(stdWhite, findClosestToCorner(samples, stdWhite));
        mapping.magenta = new CalibrationProfile.ColorMap(stdMagenta, findClosestToCorner(samples, stdMagenta));
        
        return mapping;
    }
    
    private static CalibrationProfile.RGBColor findClosestToCorner(List<ColorSample> samples, CalibrationProfile.RGBColor corner) {
        CalibrationProfile.RGBColor closest = null;
        float minDistance = Float.MAX_VALUE;
        
        for (ColorSample sample : samples) {
            CalibrationProfile.RGBColor sampleColor = sample.toRGBColor();
            float distance = corner.distanceTo(sampleColor);
            
            if (distance < minDistance) {
                minDistance = distance;
                closest = sampleColor;
            }
        }
        
        if (closest == null) {
            Log.w(TAG, "No samples found for corner " + corner.r + "," + corner.g + "," + corner.b);
            return corner;
        }
        
        Log.d(TAG, String.format("Corner [%d,%d,%d] → Calibrated [%d,%d,%d] (distance: %.2f)",
            corner.r, corner.g, corner.b, closest.r, closest.g, closest.b, minDistance));
        
        return closest;
    }
    
    private static ColorDistanceMatrix calculateDistances(CalibrationProfile.ColorMapping mapping) {
        ColorDistanceMatrix matrix = new ColorDistanceMatrix();
        
        CalibrationProfile.RGBColor[] colors = {
            mapping.black,
            mapping.white.calibrated,
            mapping.red.calibrated,
            mapping.green.calibrated,
            mapping.blue.calibrated,
            mapping.yellow,
            mapping.cyan,
            mapping.magenta.calibrated
        };
        
        for (int i = 0; i < 8; i++) {
            for (int j = i + 1; j < 8; j++) {
                float distance = colors[i].distanceTo(colors[j]);
                matrix.setDistance(i, j, distance);
            }
        }
        
        return matrix;
    }
    
    private static float calculateColorDistance(CalibrationProfile.RGBColor a, CalibrationProfile.RGBColor b) {
        return a.distanceTo(b);
    }
}
