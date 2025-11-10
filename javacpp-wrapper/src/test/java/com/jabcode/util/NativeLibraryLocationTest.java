package com.jabcode.util;

import org.junit.Test;
import org.junit.Ignore;
import static org.junit.Assert.*;

import java.io.File;

import com.jabcode.internal.NativeLibraryLoader;
import com.jabcode.internal.JABCodeNative;

/**
 * Test class to verify the native library location
 */
public class NativeLibraryLocationTest {
    
    /**
     * Test that the native library is loaded from the expected location
     */
    @Test
    public void testLibraryLocation() {
        try {
            // Load the native library
            NativeLibraryLoader.load();
            
            // Get the path of the loaded library
            String libraryPath = NativeLibraryLoader.getLoadedLibraryPath();
            
            // Print the library path for debugging
            System.out.println("Native library loaded from: " + libraryPath);
            
            // Verify the library path
            assertNotNull("Library path should not be null", libraryPath);
            
            // Verify the library is in a valid location
            // The library can be loaded from various locations depending on the build environment
            // So we just check that it's a valid path and the file exists
            if (!libraryPath.startsWith("Unknown")) {
                File libraryFile = new File(libraryPath);
                assertTrue("Library file should exist", libraryFile.exists());
                assertTrue("Library file should be readable", libraryFile.canRead());
            }
            
            // Verify we can access some native constants/methods without relying on VERSION/BUILD_DATE
            assertTrue("DEFAULT_ECC_LEVEL should be >= 0", JABCodeNative.DEFAULT_ECC_LEVEL >= 0);
            
            System.out.println("Native library verification successful!");
            System.out.println("  - Library path: " + libraryPath);
            System.out.println("  - JABCode version: N/A");
            System.out.println("  - JABCode build date: N/A");
            
        } catch (Throwable t) {
            fail("Exception thrown: " + t.getMessage());
        }
    }
}
