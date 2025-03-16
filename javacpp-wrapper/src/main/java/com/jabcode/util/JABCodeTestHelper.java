package com.jabcode.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for JABCode tests
 * This class helps set up the correct classpath for standalone tests
 */
public class JABCodeTestHelper {
    
    /**
     * Set up the classpath for standalone tests
     * This method adds the necessary JAR files to the classpath
     * @throws IOException if an I/O error occurs
     */
    public static void setupClasspath() throws IOException {
        // Get the current classpath
        String classpath = System.getProperty("java.class.path");
        System.out.println("Current classpath: " + classpath);
        
        // Check if JavaCPP is already in the classpath
        if (classpath.contains("javacpp")) {
            System.out.println("JavaCPP is already in the classpath");
            return;
        }
        
        // Find the Maven repository
        String userHome = System.getProperty("user.home");
        Path mavenRepo = Paths.get(userHome, ".m2", "repository");
        
        if (!Files.exists(mavenRepo)) {
            System.err.println("Maven repository not found at " + mavenRepo);
            return;
        }
        
        // Find the JavaCPP JAR files
        List<URL> urls = new ArrayList<>();
        
        // Add JavaCPP
        Path javacppPath = mavenRepo.resolve(Paths.get("org", "bytedeco", "javacpp", "1.5.9", "javacpp-1.5.9.jar"));
        if (Files.exists(javacppPath)) {
            urls.add(javacppPath.toUri().toURL());
            System.out.println("Added to classpath: " + javacppPath);
        } else {
            System.err.println("JavaCPP JAR not found at " + javacppPath);
        }
        
        // Add JavaCPP platform-specific JAR
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        
        String platform;
        if (osName.contains("win")) {
            platform = "windows";
        } else if (osName.contains("mac")) {
            platform = "macosx";
        } else if (osName.contains("linux")) {
            platform = "linux";
        } else {
            System.err.println("Unsupported operating system: " + osName);
            return;
        }
        
        String arch;
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            arch = "x86_64";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            arch = "arm64";
        } else if (osArch.contains("x86") || osArch.contains("i386")) {
            arch = "x86";
        } else {
            System.err.println("Unsupported architecture: " + osArch);
            return;
        }
        
        Path platformPath = mavenRepo.resolve(Paths.get("org", "bytedeco", "javacpp", "1.5.9", "javacpp-1.5.9-" + platform + "-" + arch + ".jar"));
        if (Files.exists(platformPath)) {
            urls.add(platformPath.toUri().toURL());
            System.out.println("Added to classpath: " + platformPath);
        } else {
            System.err.println("JavaCPP platform JAR not found at " + platformPath);
        }
        
        // Create a new class loader with the additional JAR files
        URLClassLoader classLoader = new URLClassLoader(
            urls.toArray(new URL[0]),
            ClassLoader.getSystemClassLoader()
        );
        
        // Set the new class loader as the context class loader
        Thread.currentThread().setContextClassLoader(classLoader);
        
        System.out.println("Classpath setup complete");
    }
    
    /**
     * Check if the native library is available
     * @return true if the native library is available, false otherwise
     */
    public static boolean isNativeLibraryAvailable() {
        try {
            // Try to load the native library
            com.jabcode.internal.NativeLibraryLoader.load();
            return true;
        } catch (Throwable e) {
            System.err.println("Failed to load native library: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Print diagnostic information about the environment
     */
    public static void printDiagnosticInfo() {
        System.out.println("=== JABCode Diagnostic Information ===");
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("Java home: " + System.getProperty("java.home"));
        System.out.println("OS name: " + System.getProperty("os.name"));
        System.out.println("OS arch: " + System.getProperty("os.arch"));
        System.out.println("OS version: " + System.getProperty("os.version"));
        System.out.println("User home: " + System.getProperty("user.home"));
        System.out.println("User dir: " + System.getProperty("user.dir"));
        System.out.println("Java library path: " + System.getProperty("java.library.path"));
        System.out.println("Java class path: " + System.getProperty("java.class.path"));
        
        // Check if the native library is available
        boolean nativeLibraryAvailable = isNativeLibraryAvailable();
        System.out.println("Native library available: " + nativeLibraryAvailable);
        
        if (nativeLibraryAvailable) {
            System.out.println("Native library path: " + com.jabcode.internal.NativeLibraryLoader.getLoadedLibraryPath());
        }
        
        System.out.println("=====================================");
    }
    
    /**
     * Main method for testing
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        try {
            // Set up the classpath
            setupClasspath();
            
            // Print diagnostic information
            printDiagnosticInfo();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
