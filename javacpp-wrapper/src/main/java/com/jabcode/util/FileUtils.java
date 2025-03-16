package com.jabcode.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.apache.commons.io.FilenameUtils;

/**
 * Utility class for file operations related to JABCode
 */
public class FileUtils {
    
    // Directory for temporary files
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final String JAB_TEMP_DIR = "jabcode_temp";
    
    /**
     * Create a temporary file for JABCode operations
     * This method addresses the StackOverflowError issue by using a more reliable
     * approach to creating temporary files
     * 
     * @param prefix the prefix for the temporary file
     * @param suffix the suffix for the temporary file (e.g., ".png")
     * @return the created temporary file
     * @throws IOException if an I/O error occurs
     */
    public static File createTempFile(String prefix, String suffix) throws IOException {
        // Create a dedicated temporary directory for JABCode if it doesn't exist
        Path jabTempDir = Paths.get(TEMP_DIR, JAB_TEMP_DIR);
        if (!Files.exists(jabTempDir)) {
            Files.createDirectories(jabTempDir);
        }
        
        // Generate a unique filename
        String uniqueId = UUID.randomUUID().toString();
        String filename = prefix + "_" + uniqueId + suffix;
        
        // Create the file
        Path filePath = jabTempDir.resolve(filename);
        File file = filePath.toFile();
        file.deleteOnExit(); // Ensure the file is deleted when the JVM exits
        
        return file;
    }
    
    /**
     * Create a temporary file for JABCode image output
     * 
     * @param colorMode the color mode of the JABCode (used in the filename)
     * @return the created temporary file
     * @throws IOException if an I/O error occurs
     */
    public static File createJABCodeImageFile(int colorMode) throws IOException {
        return createTempFile("jabcode_" + colorMode, ".png");
    }
    
    /**
     * Ensure a directory exists, creating it if necessary
     * 
     * @param directory the directory to ensure exists
     * @throws IOException if an I/O error occurs
     */
    public static void ensureDirectoryExists(File directory) throws IOException {
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
            }
        }
    }
    
    /**
     * Get the file extension from a filename
     * 
     * @param filename the filename
     * @return the file extension (without the dot)
     */
    public static String getFileExtension(String filename) {
        return FilenameUtils.getExtension(filename);
    }
    
    /**
     * Clean up temporary JABCode files
     * This method deletes all files in the JABCode temporary directory
     */
    public static void cleanupTempFiles() {
        Path jabTempDir = Paths.get(TEMP_DIR, JAB_TEMP_DIR);
        if (Files.exists(jabTempDir)) {
            try {
                Files.list(jabTempDir).forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        // Ignore errors when deleting temporary files
                    }
                });
            } catch (IOException e) {
                // Ignore errors when listing files
            }
        }
    }
}
