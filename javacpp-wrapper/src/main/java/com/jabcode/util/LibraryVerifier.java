package com.jabcode.util;

import java.io.File;

import com.jabcode.internal.NativeLibraryLoader;
import com.jabcode.internal.JABCodeNative;

/**
 * Utility class to verify the native library location and functionality
 */
public class LibraryVerifier {
    
    /**
     * Verify that the native library is loaded correctly
     */
    public static void verifyLibrary() {
        System.out.println("Verifying native library loading...");
        
        try {
            // Load the native library
            NativeLibraryLoader.load();
            
            // Get the path of the loaded library
            String libraryPath = NativeLibraryLoader.getLoadedLibraryPath();
            System.out.println("Loaded native library from classpath: " + libraryPath);
            
            // Version/Build date constants may be omitted from generated bindings
            System.out.println("JABCode version: N/A");
            System.out.println("JABCode build date: N/A");
            
            // Print diagnostic information
            System.out.println("\nDiagnostic information:");
            System.out.println("  java.library.path: " + System.getProperty("java.library.path"));
            System.out.println("  os.name: " + System.getProperty("os.name"));
            System.out.println("  os.arch: " + System.getProperty("os.arch"));
            System.out.println("  java.version: " + System.getProperty("java.version"));
            System.out.println("  user.dir: " + System.getProperty("user.dir"));
            
            System.out.println("\nNative library verification completed successfully!");
        } catch (Throwable t) {
            System.err.println("Failed to verify native library: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Main method to run the verifier
     */
    public static void main(String[] args) {
        verifyLibrary();
    }
}
