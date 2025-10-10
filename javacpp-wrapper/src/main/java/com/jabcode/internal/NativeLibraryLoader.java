package com.jabcode.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility class for loading native libraries
 * This class handles loading the native library directly without using JavaCPP
 */
public class NativeLibraryLoader {
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);
    private static String loadedLibraryPath = null;
    
    // Standard library locations to search
    private static final String[] STANDARD_LOCATIONS = {
        "lib",                // Project lib directory
        "../lib",             // Parent lib directory
        "output/libs",        // Organized output directory
        "javacpp-wrapper/lib" // JavaCPP wrapper lib directory
    };
    
    /**
     * Get the path of the loaded library
     * @return the path of the loaded library, or null if the library has not been loaded
     */
    public static String getLoadedLibraryPath() {
        return loadedLibraryPath;
    }
    
    /**
     * Load the native library
     * This method will only load the library once, even if called multiple times
     */
    public static void load() {
        if (LOADED.compareAndSet(false, true)) {
            try {
                // Prefer JavaCPP loader first to avoid duplicate loads and path ambiguity
                try {
                    org.bytedeco.javacpp.Loader.load(com.jabcode.internal.JABCodeNative.class);
                    // Also attempt to load pointer JNI explicitly; ignore if absent
                    try { org.bytedeco.javacpp.Loader.load(com.jabcode.internal.JABCodeNativePtr.class); } catch (Throwable ignore2) {}
                    // Attempt to resolve actual file path from java.library.path so tests can verify existence
                    String resolved = findLibraryPath("jniJABCodeNative");
                    if (resolved == null || resolved.startsWith("Unknown")) {
                        resolved = findLibraryPath("jabcode_jni");
                    }
                    loadedLibraryPath = (resolved != null && !resolved.startsWith("Unknown"))
                        ? resolved
                        : "Unknown (Loaded via JavaCPP Loader)";
                    return;
                } catch (Throwable ignore) {
                    // Fallback to manual strategies below
                }
                // Prefer the canonical JavaCPP name first, then fall back to legacy alias (manual path)
                tryLoadByNameOrPath("jniJABCodeNative", getLibraryNameVariant("jniJABCodeNative"));
                if (loadedLibraryPath == null) {
                    tryLoadByNameOrPath("jabcode_jni", getLibraryName());
                }
                if (loadedLibraryPath != null) {
                    System.out.println("Loaded native library from java.library.path: " + loadedLibraryPath);
                    // Best-effort: load alternates so all JNI symbols (including Ptr) are available
                    bestEffortLoadAlternates();
                    return;
                }
            } catch (UnsatisfiedLinkError e) {
                // If that fails, try to load from standard locations
                String libraryPath = findLibraryInStandardLocations();
                if (libraryPath != null) {
                    System.load(libraryPath);
                    loadedLibraryPath = libraryPath;
                    System.out.println("Loaded native library from: " + loadedLibraryPath);
                    // Best-effort: load alternates so all JNI symbols (including Ptr) are available
                    bestEffortLoadAlternates();
                    return;
                }
                
                // If that fails, try to extract from classpath
                try {
                    String extractedPath = extractAndLoadFromClasspath();
                    loadedLibraryPath = extractedPath;
                    System.out.println("Loaded native library from classpath: " + loadedLibraryPath);
                    return;
                } catch (Exception ex) {
                    // If all methods fail, provide detailed diagnostic information
                    System.err.println("Failed to load native library. Diagnostic information:");
                    System.err.println("  java.library.path: " + System.getProperty("java.library.path"));
                    System.err.println("  os.name: " + System.getProperty("os.name"));
                    System.err.println("  os.arch: " + System.getProperty("os.arch"));
                    System.err.println("  Original error: " + e.getMessage());
                    System.err.println("  Extraction error: " + ex.getMessage());
                    
                    // Rethrow with more information
                    throw new RuntimeException("Failed to load native library. See above for details.", ex);
                }
            }
        }
    }

    private static void bestEffortLoadAlternates() {
        // These calls are best-effort; ignore failures if already loaded or not present
        tryLoadByNameOrPath("jniJABCodeNative", getLibraryNameVariant("jniJABCodeNative"));
        tryLoadByNameOrPath("jniJABCodeNativePtr", getLibraryNameVariant("jniJABCodeNativePtr"));
        tryLoadByNameOrPath("jabcode_jni", getLibraryName());
    }

    private static void tryLoadByNameOrPath(String baseName, String fileName) {
        try {
            System.loadLibrary(baseName);
            if (loadedLibraryPath == null) {
                loadedLibraryPath = findLibraryPath(baseName);
            }
            return;
        } catch (Throwable ignore) {
            // Fall through to search standard locations
        }
        try {
            String path = findSpecificLibraryInStandardLocations(fileName);
            if (path != null) {
                System.load(path);
                if (loadedLibraryPath == null) {
                    loadedLibraryPath = path;
                }
            }
        } catch (Throwable ignore) {
            // ignore
        }
    }
    
    /**
     * Find the library in standard locations
     * @return the path to the library, or null if not found
     */
    private static String findLibraryInStandardLocations() {
        // Prefer the canonical name first
        String libraryName = getLibraryNameVariant("jniJABCodeNative");
        String currentDir = System.getProperty("user.dir");
        
        for (String location : STANDARD_LOCATIONS) {
            File libraryFile = new File(currentDir, location + File.separator + libraryName);
            if (libraryFile.exists()) {
                return libraryFile.getAbsolutePath();
            }
        }
        // Fallback to the legacy alias used by some loaders
        libraryName = getLibraryName();
        for (String location : STANDARD_LOCATIONS) {
            File libraryFile = new File(currentDir, location + File.separator + libraryName);
            if (libraryFile.exists()) {
                return libraryFile.getAbsolutePath();
            }
        }
        return null;
    }

    private static String findSpecificLibraryInStandardLocations(String fileName) {
        String currentDir = System.getProperty("user.dir");
        for (String location : STANDARD_LOCATIONS) {
            File libraryFile = new File(currentDir, location + File.separator + fileName);
            if (libraryFile.exists()) {
                return libraryFile.getAbsolutePath();
            }
        }
        return null;
    }
    
    /**
     * Extract the library from the classpath and load it
     * @return the path to the extracted library
     * @throws IOException if an I/O error occurs
     */
    private static String extractAndLoadFromClasspath() throws IOException {
        String libraryName = getLibraryName();
        String resourcePath = "/native/" + libraryName;
        
        // Try to find the library in the classpath
        InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath);
        if (in == null) {
            // Try alternative paths
            resourcePath = "/" + libraryName;
            in = NativeLibraryLoader.class.getResourceAsStream(resourcePath);
            
            if (in == null) {
                throw new IOException("Native library not found in classpath: " + libraryName);
            }
        }
        
        try {
            // Create a temporary file
            Path tempPath = Files.createTempFile("jabcode-", libraryName);
            File tempFile = tempPath.toFile();
            tempFile.deleteOnExit();
            
            // Copy the library to the temporary file
            Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
            
            // Ensure the file is executable on Unix-like systems
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                tempFile.setExecutable(true, false);
            }
            
            // Load the library
            System.load(tempFile.getAbsolutePath());
            
            return tempFile.getAbsolutePath();
        } finally {
            in.close();
        }
    }
    
    /**
     * Get the platform-specific library name
     * @return the library name
     */
    private static String getLibraryName() {
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (osName.contains("win")) {
            return "jabcode_jni.dll";
        } else if (osName.contains("mac")) {
            return "libjabcode_jni.dylib";
        } else {
            return "libjabcode_jni.so";
        }
    }

    private static String getLibraryNameVariant(String base) {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return base + ".dll";
        } else if (osName.contains("mac")) {
            return "lib" + base + ".dylib";
        } else {
            return "lib" + base + ".so";
        }
    }
    
    private static String detectPlatform() {
        String osName = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        if (osName.contains("win")) {
            return arch.contains("64") ? "windows-x86_64" : "windows-x86";
        } else if (osName.contains("mac")) {
            return arch.contains("aarch64") || arch.contains("arm64") ? "macosx-arm64" : "macosx-x86_64";
        } else {
            return arch.contains("64") ? "linux-x86_64" : "linux-x86";
        }
    }
    
    /**
     * Find the path of a loaded library
     * @param libraryName the name of the library
     * @return the path of the library, or null if not found
     */
    private static String findLibraryPath(String libraryName) {
        try {
            String libNameWithExtension = System.mapLibraryName(libraryName);
            String[] paths = System.getProperty("java.library.path").split(File.pathSeparator);
            
            for (String path : paths) {
                File libFile = new File(path, libNameWithExtension);
                if (libFile.exists()) {
                    return libFile.getAbsolutePath();
                }
            }
            
            return "Unknown (loaded from java.library.path)";
        } catch (Exception e) {
            return "Unknown (error finding path: " + e.getMessage() + ")";
        }
    }
}
