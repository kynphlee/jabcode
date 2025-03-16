package com.jabcode.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class to fix the BUILD_DATE constant in JABCodeNative.java files.
 */
public class FixBuildDate {
    
    public static void main(String[] args) {
        System.out.println("Fixing BUILD_DATE in JABCodeNative.java files...");
        
        // Get current date in "MMM d yyyy" format (e.g., "Mar 10 2025")
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d yyyy"));
        
        try {
            // Fix internal JABCodeNative.java
            fixBuildDate("src/main/java/com/jabcode/internal/JABCodeNative.java", currentDate);
            
            // Fix wrapper JABCodeNative.java
            fixBuildDate("src/main/java/com/jabcode/wrapper/JABCodeNative.java", currentDate);
            
            System.out.println("BUILD_DATE fixed successfully in all files.");
        } catch (IOException e) {
            System.err.println("Error fixing BUILD_DATE: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void fixBuildDate(String filePath, String currentDate) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            System.out.println("File not found: " + filePath);
            return;
        }
        
        System.out.println("Processing file: " + filePath);
        
        // Read all lines from the file
        List<String> lines = Files.readAllLines(path);
        
        // Pattern to match BUILD_DATE declaration
        Pattern buildDatePattern = Pattern.compile(".*BUILD_DATE.*=.*");
        
        // Replace the BUILD_DATE line
        List<String> updatedLines = lines.stream()
                .map(line -> {
                    if (buildDatePattern.matcher(line).matches()) {
                        String newLine = "public static final String BUILD_DATE = \"" + currentDate + "\";";
                        System.out.println("  Replacing: " + line.trim());
                        System.out.println("  With:      " + newLine);
                        return newLine;
                    }
                    return line;
                })
                .collect(Collectors.toList());
        
        // Write the updated content back to the file
        Files.write(path, updatedLines);
        
        System.out.println("Updated file: " + filePath);
    }
}
