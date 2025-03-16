package com.jabcode.internal;

/**
 * JABCodeNativeBridge - A bridge class to load the native library
 * This class ensures that the native library is loaded before any native methods are called
 */
public class JABCodeNativeBridge {
    
    // Static initializer to load the native library
    static {
        try {
            // Load the native library
            NativeLibraryLoader.load();
            System.out.println("JABCode native library loaded successfully from: " + NativeLibraryLoader.getLoadedLibraryPath());
        } catch (Throwable e) {
            System.err.println("Failed to load JABCode native library: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Check if the native library is loaded
     * @return true if the native library is loaded, false otherwise
     */
    public static boolean isNativeLibraryLoaded() {
        return NativeLibraryLoader.getLoadedLibraryPath() != null;
    }
    
    /**
     * Get the path of the loaded native library
     * @return the path of the loaded native library, or null if the library has not been loaded
     */
    public static String getNativeLibraryPath() {
        return NativeLibraryLoader.getLoadedLibraryPath();
    }
}
