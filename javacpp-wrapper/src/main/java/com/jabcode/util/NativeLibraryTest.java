package com.jabcode.util;

import com.jabcode.internal.NativeLibraryLoader;
import com.jabcode.internal.JABCodeNative;

/**
 * Test class for verifying native library loading
 */
public class NativeLibraryTest {
    
    /**
     * Main method to test native library loading
     * @param args command line arguments
     */
    public static void main(String[] args) {
        try {
            System.out.println("Testing native library loading...");
            
            // Try to load the native library
            NativeLibraryLoader.load();
            System.out.println("Native library loaded successfully!");
            
            // Try to access native constants
            System.out.println("JABCode version: " + JABCodeNative.VERSION);
            System.out.println("JABCode build date: " + JABCodeNative.BUILD_DATE);
            System.out.println("Default ECC level: " + JABCodeNative.DEFAULT_ECC_LEVEL);
            System.out.println("Default color number: " + JABCodeNative.DEFAULT_COLOR_NUMBER);
            
            // Print diagnostic information
            System.out.println("\nDiagnostic information:");
            System.out.println("  java.library.path: " + System.getProperty("java.library.path"));
            System.out.println("  os.name: " + System.getProperty("os.name"));
            System.out.println("  os.arch: " + System.getProperty("os.arch"));
            
            System.out.println("\nNative library test completed successfully!");
        } catch (Throwable t) {
            System.err.println("Failed to load or use native library:");
            t.printStackTrace();
            
            System.err.println("\nDiagnostic information:");
            System.err.println("  java.library.path: " + System.getProperty("java.library.path"));
            System.err.println("  os.name: " + System.getProperty("os.name"));
            System.err.println("  os.arch: " + System.getProperty("os.arch"));
            
            System.exit(1);
        }
    }
}
