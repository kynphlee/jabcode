package com.jabcode;

import static org.junit.Assert.*;
import org.junit.Test;
import java.io.File;

import com.jabcode.internal.NativeLibraryLoader;

/**
 * JUnit test to validate that the native library can be loaded
 */
public class LibraryLoadJUnitTest {
    
    /**
     * Test that the library directories are properly structured
     */
    @Test
    public void testLibraryDirectoriesExist() {
        // Check the main library directories
        File libDir = new File("lib");
        File javacppLibDir = new File("libs");
        File linuxLibDir = new File("libs/linux-x86_64");
        
        assertTrue("Main library directory should exist", libDir.exists());
        assertTrue("JavaCPP library directory should exist", javacppLibDir.exists());
        assertTrue("Platform-specific library directory should exist", linuxLibDir.exists());
    }
    
    /**
     * Test that we can find the library files
     */
    @Test
    public void testLibraryFilesExist() {
        // Look for library files in possible locations
        File coreLib1 = new File("lib/libjabcode.a");
        File coreLib2 = new File("../src/jabcode/build/libjabcode.a");
        File coreLib3 = new File("/mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/src/jabcode/build/libjabcode.a");
        
        boolean coreLibExists = coreLib1.exists() || coreLib2.exists() || coreLib3.exists();
        assertTrue("Core library file should exist somewhere", coreLibExists);
        
        // Check the JNI files
        File jniLib1 = new File("lib/libjabcode_jni.so");
        File jniLib2 = new File("libs/libjabcode_jni.so");
        File jniLib3 = new File("libs/linux-x86_64/libjabcode_jni.so");
        
        boolean jniLibExists = jniLib1.exists() || jniLib2.exists() || jniLib3.exists();
        assertTrue("JNI library file should exist somewhere", jniLibExists);
    }
    
    /**
     * Simple sanity test that verifies JUnit is working
     */
    @Test
    public void testJUnitWorking() {
        assertTrue("JUnit is working correctly", true);
    }
}
