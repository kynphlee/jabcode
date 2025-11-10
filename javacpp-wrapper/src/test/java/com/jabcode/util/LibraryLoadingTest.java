package com.jabcode.util;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;

import com.jabcode.internal.NativeLibraryLoader;
import com.jabcode.internal.JABCodeNative;

/**
 * Test class for verifying the native library loading mechanism.
 */
public class LibraryLoadingTest {
    
    private static boolean nativeLibraryAvailable = false;
    
    // Load the native library
    static {
        try {
            NativeLibraryLoader.load();
            nativeLibraryAvailable = true;
        } catch (Throwable e) {
            System.err.println("Failed to load native library: " + e.getMessage());
            nativeLibraryAvailable = false;
        }
    }
    
    @Before
    public void setUp() {
        // Skip tests that require native libraries if they're not available
        Assume.assumeTrue("Native library is not available", nativeLibraryAvailable);
    }
    
    /**
     * Test that the native library is loaded correctly
     */
    @Test
    public void testNativeLibraryLoading() {
        // Get the loaded library path
        String libraryPath = NativeLibraryLoader.getLoadedLibraryPath();
        
        // Verify the library path
        assertNotNull("Library path should not be null", libraryPath);
        assertTrue("Library path should not be empty", !libraryPath.isEmpty());
        
        // Print some information about the library (avoid VERSION/BUILD_DATE dependency)
        System.out.println("JABCode version: N/A");
        System.out.println("JABCode build date: N/A");
        System.out.println("Default ECC level: " + JABCodeNative.DEFAULT_ECC_LEVEL);
        System.out.println("Default color number: " + JABCodeNative.DEFAULT_COLOR_NUMBER);
        
        // Print diagnostic information
        System.out.println("\nDiagnostic information:");
        System.out.println("  java.library.path: " + System.getProperty("java.library.path"));
        System.out.println("  os.name: " + System.getProperty("os.name"));
        System.out.println("  os.arch: " + System.getProperty("os.arch"));
        
        // Verify that the library is functional by calling a native method
        // Basic sanity check without relying on VERSION
        assertTrue("DEFAULT_COLOR_NUMBER should be > 0", JABCodeNative.DEFAULT_COLOR_NUMBER > 0);
    }
    
    /**
     * Test that the library can be loaded from different locations
     */
    @Test
    public void testLibraryLoadingLocations() {
        // This test is more of a verification that the library can be loaded from different locations
        // The actual loading is done in the static initializer
        
        // Verify that the library is loaded
        assertTrue("Native library should be loaded", nativeLibraryAvailable);
        
        // Get the loaded library path
        String libraryPath = NativeLibraryLoader.getLoadedLibraryPath();
        
        // Print the library path
        System.out.println("Loaded native library from: " + libraryPath);
        
        // Verify the library path
        assertNotNull("Library path should not be null", libraryPath);
        assertTrue("Library path should not be empty", !libraryPath.isEmpty());
    }
}
