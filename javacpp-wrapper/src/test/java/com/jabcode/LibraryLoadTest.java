package com.jabcode;

import com.jabcode.internal.NativeLibraryLoader;

/**
 * Simple test to check if the native library loads correctly
 */
public class LibraryLoadTest {
    public static void main(String[] args) {
        try {
            System.out.println("Attempting to load native library...");
            NativeLibraryLoader.load();
            System.out.println("Native library loaded successfully!");
        } catch (Throwable e) {
            System.err.println("Failed to load native library: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
