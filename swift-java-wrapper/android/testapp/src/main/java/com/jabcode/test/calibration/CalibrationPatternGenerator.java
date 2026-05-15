package com.jabcode.test.calibration;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import com.jabcode.test.JABCodeMobile;

public class CalibrationPatternGenerator {
    private static final String TAG = "CalibrationPattern";
    private static final int QUANTIZATION_STEPS = 16;
    private static final int STEP_SIZE = 17;
    private static final int MODULE_SIZE = 12;
    private static final int SYMBOL_SIZE = 77;
    private static final int BITMAP_SIZE = SYMBOL_SIZE * MODULE_SIZE;
    
    public static class TestPattern {
        public byte[] colorSamples;
        public int sampleCount;
        public int quantization;
        
        public TestPattern(byte[] samples, int count, int quant) {
            this.colorSamples = samples;
            this.sampleCount = count;
            this.quantization = quant;
        }
    }
    
    public static TestPattern generateColorSamples() {
        int totalColors = QUANTIZATION_STEPS * QUANTIZATION_STEPS * QUANTIZATION_STEPS;
        byte[] samples = new byte[totalColors * 3];
        
        int index = 0;
        for (int r = 0; r < QUANTIZATION_STEPS; r++) {
            for (int g = 0; g < QUANTIZATION_STEPS; g++) {
                for (int b = 0; b < QUANTIZATION_STEPS; b++) {
                    samples[index++] = (byte) (r * STEP_SIZE);
                    samples[index++] = (byte) (g * STEP_SIZE);
                    samples[index++] = (byte) (b * STEP_SIZE);
                }
            }
        }
        
        Log.i(TAG, "Generated " + totalColors + " color samples (16^3)");
        return new TestPattern(samples, totalColors, QUANTIZATION_STEPS);
    }
    
    public static Bitmap generateTestPattern() {
        TestPattern pattern = generateColorSamples();
        
        Bitmap bitmap = Bitmap.createBitmap(
            BITMAP_SIZE, 
            BITMAP_SIZE, 
            Bitmap.Config.ARGB_8888
        );
        
        int sampleIndex = 0;
        int moduleIndex = 0;
        
        for (int moduleY = 0; moduleY < SYMBOL_SIZE; moduleY++) {
            for (int moduleX = 0; moduleX < SYMBOL_SIZE; moduleX++) {
                if (isFinderPattern(moduleX, moduleY) || isAlignmentPattern(moduleX, moduleY)) {
                    int finderColor = getFinderPatternColor(moduleX, moduleY);
                    fillModule(bitmap, moduleX, moduleY, finderColor);
                } else if (sampleIndex < pattern.sampleCount) {
                    int r = pattern.colorSamples[sampleIndex * 3] & 0xFF;
                    int g = pattern.colorSamples[sampleIndex * 3 + 1] & 0xFF;
                    int b = pattern.colorSamples[sampleIndex * 3 + 2] & 0xFF;
                    int color = Color.rgb(r, g, b);
                    fillModule(bitmap, moduleX, moduleY, color);
                    sampleIndex++;
                } else {
                    fillModule(bitmap, moduleX, moduleY, Color.WHITE);
                }
                moduleIndex++;
            }
        }
        
        Log.i(TAG, "Generated test pattern: " + SYMBOL_SIZE + "x" + SYMBOL_SIZE + 
              " modules, " + sampleIndex + " color samples");
        return bitmap;
    }
    
    private static void fillModule(Bitmap bitmap, int moduleX, int moduleY, int color) {
        int startX = moduleX * MODULE_SIZE;
        int startY = moduleY * MODULE_SIZE;
        
        for (int y = 0; y < MODULE_SIZE; y++) {
            for (int x = 0; x < MODULE_SIZE; x++) {
                bitmap.setPixel(startX + x, startY + y, color);
            }
        }
    }
    
    private static boolean isFinderPattern(int x, int y) {
        int size = SYMBOL_SIZE;
        int fpSize = 7;
        
        boolean topLeft = (x < fpSize && y < fpSize);
        boolean topRight = (x >= size - fpSize && y < fpSize);
        boolean bottomLeft = (x < fpSize && y >= size - fpSize);
        boolean bottomRight = (x >= size - fpSize && y >= size - fpSize);
        
        return topLeft || topRight || bottomLeft || bottomRight;
    }
    
    private static boolean isAlignmentPattern(int x, int y) {
        int apSize = 5;
        int spacing = 17;
        
        for (int apY = spacing; apY < SYMBOL_SIZE - spacing; apY += spacing) {
            for (int apX = spacing; apX < SYMBOL_SIZE - spacing; apX += spacing) {
                int dx = Math.abs(x - apX);
                int dy = Math.abs(y - apY);
                if (dx < apSize / 2 + 1 && dy < apSize / 2 + 1) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private static int getFinderPatternColor(int x, int y) {
        int localX = x % 7;
        int localY = y % 7;
        
        if (localX == 0 || localX == 6 || localY == 0 || localY == 6) {
            return Color.BLACK;
        } else if (localX >= 2 && localX <= 4 && localY >= 2 && localY <= 4) {
            int quadrant = getFinderQuadrant(x, y);
            switch (quadrant) {
                case 0: return Color.CYAN;
                case 1: return Color.YELLOW;
                case 2: return Color.BLACK;
                case 3: return Color.CYAN;
                default: return Color.BLACK;
            }
        } else {
            return Color.WHITE;
        }
    }
    
    private static int getFinderQuadrant(int x, int y) {
        int size = SYMBOL_SIZE;
        int fpSize = 7;
        
        if (x < fpSize && y < fpSize) return 0;
        if (x >= size - fpSize && y < fpSize) return 1;
        if (x < fpSize && y >= size - fpSize) return 2;
        if (x >= size - fpSize && y >= size - fpSize) return 3;
        return -1;
    }
    
    public static String getPatternInfo() {
        return String.format(
            "Calibration Test Pattern\n" +
            "Symbol Size: %dx%d modules\n" +
            "Bitmap Size: %dx%d pixels\n" +
            "Module Size: %d pixels\n" +
            "Color Samples: %d (16^3)\n" +
            "Quantization: %d steps per channel\n" +
            "Step Size: %d (0, 17, 34, ..., 255)",
            SYMBOL_SIZE, SYMBOL_SIZE,
            BITMAP_SIZE, BITMAP_SIZE,
            MODULE_SIZE,
            QUANTIZATION_STEPS * QUANTIZATION_STEPS * QUANTIZATION_STEPS,
            QUANTIZATION_STEPS,
            STEP_SIZE
        );
    }
}
