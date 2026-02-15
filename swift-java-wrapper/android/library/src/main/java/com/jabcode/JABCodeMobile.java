package com.jabcode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * JABCode Mobile - Java/Kotlin API for JABCode barcode encoding and decoding.
 */
public class JABCodeMobile {
    
    static {
        System.loadLibrary("jabcode-mobile");
    }
    
    public static class EncodeParams {
        public int colorNumber = 8;
        public int symbolNumber = 1;
        public int eccLevel = 3;
        public int moduleSize = 12;
        
        public EncodeParams() {}
        
        public EncodeParams(int colorNumber, int eccLevel) {
            this.colorNumber = colorNumber;
            this.eccLevel = eccLevel;
        }
    }
    
    public static class EncodeResult {
        private long nativePtr;
        public final int width;
        public final int height;
        public final byte[] rgbaBuffer;
        
        private EncodeResult(long nativePtr, int width, int height, byte[] rgbaBuffer) {
            this.nativePtr = nativePtr;
            this.width = width;
            this.height = height;
            this.rgbaBuffer = rgbaBuffer;
        }
        
        public android.graphics.Bitmap toBitmap() {
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                width, height, android.graphics.Bitmap.Config.ARGB_8888);
            int[] pixels = new int[width * height];
            for (int i = 0; i < pixels.length; i++) {
                int r = rgbaBuffer[i * 4] & 0xFF;
                int g = rgbaBuffer[i * 4 + 1] & 0xFF;
                int b = rgbaBuffer[i * 4 + 2] & 0xFF;
                int a = rgbaBuffer[i * 4 + 3] & 0xFF;
                pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        }
        
        public void free() {
            if (nativePtr != 0) {
                nativeFreeEncodeResult(nativePtr);
                nativePtr = 0;
            }
        }
    }
    
    @Nullable
    public static EncodeResult encode(@NonNull String data, int colorNumber, int eccLevel) {
        EncodeParams params = new EncodeParams(colorNumber, eccLevel);
        return encode(data, params);
    }
    
    @Nullable
    public static EncodeResult encode(@NonNull String data, @NonNull EncodeParams params) {
        return nativeEncode(data.getBytes(), data.length(),
            params.colorNumber, params.symbolNumber, params.eccLevel, params.moduleSize);
    }
    
    @Nullable
    public static String decode(@NonNull EncodeResult encodeResult, int colorNumber, int eccLevel) {
        byte[] result = nativeDecode(encodeResult.nativePtr, colorNumber, eccLevel);
        return result != null ? new String(result) : null;
    }
    
    @Nullable
    public static String decodeFromBitmap(@NonNull android.graphics.Bitmap bitmap) {
        // Convert bitmap to byte array
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        
        byte[] rgbaBuffer = new byte[width * height * 4];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            rgbaBuffer[i * 4] = (byte) ((pixel >> 16) & 0xFF);     // R
            rgbaBuffer[i * 4 + 1] = (byte) ((pixel >> 8) & 0xFF);  // G
            rgbaBuffer[i * 4 + 2] = (byte) (pixel & 0xFF);         // B
            rgbaBuffer[i * 4 + 3] = (byte) ((pixel >> 24) & 0xFF); // A
        }
        
        byte[] result = nativeDecodeFromBitmap(rgbaBuffer, width, height);
        return result != null ? new String(result) : null;
    }
    
    @Nullable
    public static String getLastError() {
        return nativeGetLastError();
    }
    
    public static void clearError() {
        nativeClearError();
    }
    
    @NonNull
    public static String getVersion() {
        return nativeGetVersion();
    }
    
    public static boolean loadCalibration(@NonNull String jsonProfile) {
        return nativeLoadCalibration(jsonProfile);
    }
    
    public static void clearCalibration() {
        nativeClearCalibration();
    }
    
    public static boolean hasCalibration() {
        return nativeHasCalibration();
    }
    
    private static native EncodeResult nativeEncode(byte[] data, int length,
        int colorNumber, int symbolNumber, int eccLevel, int moduleSize);
    private static native byte[] nativeDecode(long encodeResultPtr, int colorNumber, int eccLevel);
    private static native byte[] nativeDecodeFromBitmap(byte[] rgbaBuffer, int width, int height);
    private static native void nativeFreeEncodeResult(long ptr);
    private static native String nativeGetLastError();
    private static native void nativeClearError();
    private static native String nativeGetVersion();
    private static native boolean nativeLoadCalibration(String jsonProfile);
    private static native void nativeClearCalibration();
    private static native boolean nativeHasCalibration();
}
