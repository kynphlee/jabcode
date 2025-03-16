package com.jabcode;

import com.jabcode.internal.JABCodeNative;
import com.jabcode.internal.NativeLibraryLoader;

/**
 * Simple test to check if we can create a JABCode encode object
 */
public class CreateEncodeTest {
    public static void main(String[] args) {
        try {
            System.out.println("Attempting to load native library...");
            NativeLibraryLoader.load();
            System.out.println("Native library loaded successfully!");
            
            System.out.println("Attempting to create JABCode encode object...");
            JABCodeNative.jab_encode enc = JABCodeNative.createEncode(8, 1);
            if (enc != null) {
                System.out.println("JABCode encode object created successfully!");
                JABCodeNative.destroyEncode(enc);
                System.out.println("JABCode encode object destroyed successfully!");
            } else {
                System.err.println("Failed to create JABCode encode object!");
            }
        } catch (Throwable e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
