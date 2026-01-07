package com.jabcode.panama.example;

import java.lang.foreign.*;
import java.nio.file.Files;
import java.nio.file.Path;
import static com.jabcode.panama.jabcode_h.*;

/**
 * Example demonstrating Project Panama FFM API usage for JABCode.
 * 
 * This is what your code would look like with Panama instead of JNI.
 * Compare with: /javacpp-wrapper/src/main/c/JABCodeNative_jni.cpp
 * 
 * REQUIRES: JDK 22+
 * DOES NOT WORK: On Android
 */
public class PanamaExample {
    
    /**
     * Simple encoding example - compare with current JNI version
     */
    public static byte[] encodeSimple(String message) {
        // Arena automatically manages all native memory
        try (Arena arena = Arena.ofConfined()) {
            
            // Allocate C struct on arena (auto-freed)
            MemorySegment params = jab_encode_params.allocate(arena);
            
            // Set fields directly - type-safe!
            jab_encode_params.color_number(params, 8);
            jab_encode_params.ecc_level(params, 5);
            jab_encode_params.symbol_number(params, 1);
            jab_encode_params.module_size(params, 12);
            
            // Convert Java String to C string (auto-freed by arena)
            MemorySegment messagePtr = arena.allocateFrom(message);
            
            // Call C function directly - no JNI wrapper!
            MemorySegment result = generateJABCode(arena, params, messagePtr);
            
            // Check for errors
            if (result.address() == 0) {
                throw new RuntimeException("Encoding failed");
            }
            
            // Access C struct fields - type-safe!
            int length = jab_data.length(result);
            MemorySegment dataPtr = jab_data.data(result);
            
            // Copy to Java array
            return dataPtr.reinterpret(length)
                         .toArray(ValueLayout.JAVA_BYTE);
            
        } // All memory automatically freed here
    }
    
    /**
     * Advanced encoding with full parameter control
     */
    public static class JABCodeEncoder {
        
        public record Config(
            int colorNumber,      // 2, 4, or 8
            int eccLevel,         // 0-10
            int symbolNumber,     // Number of symbols
            int moduleSize,       // Size of modules in pixels
            int symbolWidth,      // Symbol width in modules
            int symbolHeight      // Symbol height in modules
        ) {
            public static Config defaults() {
                return new Config(8, 5, 1, 12, 0, 0);
            }
        }
        
        public byte[] encode(String message, Config config) {
            try (Arena arena = Arena.ofConfined()) {
                // Allocate params struct
                MemorySegment params = jab_encode_params.allocate(arena);
                
                // Populate all fields
                jab_encode_params.color_number(params, config.colorNumber());
                jab_encode_params.ecc_level(params, config.eccLevel());
                jab_encode_params.symbol_number(params, config.symbolNumber());
                jab_encode_params.module_size(params, config.moduleSize());
                jab_encode_params.symbol_width(params, config.symbolWidth());
                jab_encode_params.symbol_height(params, config.symbolHeight());
                
                // Convert message
                MemorySegment messagePtr = arena.allocateFrom(message);
                
                // Encode
                MemorySegment result = generateJABCode(arena, params, messagePtr);
                
                if (result.address() == 0) {
                    return null;
                }
                
                // Extract result
                int length = jab_data.length(result);
                MemorySegment data = jab_data.data(result);
                
                return data.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
            }
        }
        
        /**
         * Encode to PNG file
         */
        public void encodeToPNG(String message, Path outputPath, Config config) 
                throws Exception {
            
            try (Arena arena = Arena.ofConfined()) {
                // Setup params
                MemorySegment params = jab_encode_params.allocate(arena);
                jab_encode_params.color_number(params, config.colorNumber());
                jab_encode_params.ecc_level(params, config.eccLevel());
                jab_encode_params.symbol_number(params, config.symbolNumber());
                jab_encode_params.module_size(params, config.moduleSize());
                
                // Encode
                MemorySegment messagePtr = arena.allocateFrom(message);
                MemorySegment jabData = generateJABCode(arena, params, messagePtr);
                
                if (jabData.address() == 0) {
                    throw new RuntimeException("Encoding failed");
                }
                
                // Create bitmap
                MemorySegment bitmap = createBitmap(
                    arena,
                    jab_data.data(jabData),
                    jab_data.length(jabData),
                    config.moduleSize()
                );
                
                if (bitmap.address() == 0) {
                    throw new RuntimeException("Bitmap creation failed");
                }
                
                // Get bitmap data
                int width = jab_bitmap.width(bitmap);
                int height = jab_bitmap.height(bitmap);
                MemorySegment pixelData = jab_bitmap.pixel(bitmap);
                
                // Convert to Java array
                int pixelCount = width * height * 4; // RGBA
                byte[] pixels = pixelData.reinterpret(pixelCount)
                                        .toArray(ValueLayout.JAVA_BYTE);
                
                // Write PNG (would need PNG library)
                writePNG(outputPath, pixels, width, height);
                
                // Cleanup if needed
                destroyBitmap(arena, bitmap);
            }
        }
        
        private void writePNG(Path path, byte[] pixels, int width, int height) {
            // Implementation would use javax.imageio or similar
            throw new UnsupportedOperationException("PNG writing not implemented in example");
        }
    }
    
    /**
     * Decoding example
     */
    public static class JABCodeDecoder {
        
        public String decode(byte[] imageData, int width, int height) {
            try (Arena arena = Arena.ofConfined()) {
                // Allocate bitmap structure
                MemorySegment bitmap = jab_bitmap.allocate(arena);
                jab_bitmap.width(bitmap, width);
                jab_bitmap.height(bitmap, height);
                jab_bitmap.channel_count(bitmap, 4); // RGBA
                jab_bitmap.bits_per_channel(bitmap, 8);
                
                // Copy pixel data
                MemorySegment pixelData = arena.allocateFrom(
                    ValueLayout.JAVA_BYTE,
                    imageData
                );
                jab_bitmap.pixel(bitmap, pixelData);
                
                // Setup decode params
                MemorySegment params = jab_decode_params.allocate(arena);
                // Set any decode-specific parameters
                
                // Decode
                MemorySegment result = readJABCode(arena, bitmap, params);
                
                if (result.address() == 0) {
                    return null;
                }
                
                // Extract decoded data
                int length = jab_data.length(result);
                MemorySegment data = jab_data.data(result);
                
                byte[] decoded = data.reinterpret(length)
                                    .toArray(ValueLayout.JAVA_BYTE);
                
                return new String(decoded);
            }
        }
        
        public String decodeFromFile(Path imagePath) throws Exception {
            // Would load image file and extract pixels
            // Then call decode() method above
            throw new UnsupportedOperationException("File reading not implemented in example");
        }
    }
    
    /**
     * Main demo
     */
    public static void main(String[] args) {
        System.out.println("JABCode Panama FFM Demo");
        System.out.println("======================\n");
        
        // Simple example
        try {
            String message = "Hello JABCode with Panama!";
            System.out.println("Encoding: " + message);
            
            byte[] encoded = encodeSimple(message);
            System.out.println("Result: " + encoded.length + " bytes");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Advanced example
        try {
            var encoder = new JABCodeEncoder();
            var config = new JABCodeEncoder.Config(
                8,      // 8 colors
                5,      // ECC level 5
                1,      // Single symbol
                12,     // 12-pixel modules
                0,      // Auto width
                0       // Auto height
            );
            
            String message = "Advanced encoding example";
            byte[] result = encoder.encode(message, config);
            
            if (result != null) {
                System.out.println("\nAdvanced encoding successful: " + result.length + " bytes");
            }
            
        } catch (Exception e) {
            System.err.println("Advanced encoding error: " + e.getMessage());
        }
    }
}

/**
 * COMPARISON WITH CURRENT JNI APPROACH
 * ====================================
 * 
 * Current JNI (javacpp-wrapper/src/main/c/JABCodeNative_jni.cpp):
 * ----------------------------------------------------------------
 * 
 * JNIEXPORT jbyteArray JNICALL 
 * Java_com_jabcode_JABCodeNative_encode(JNIEnv *env, jobject obj, jstring data) {
 *     // Manual string conversion
 *     const char *nativeData = (*env)->GetStringUTFChars(env, data, 0);
 *     
 *     // Manual struct allocation
 *     jab_encode_params params;
 *     params.color_number = 8;
 *     params.ecc_level = 5;
 *     
 *     // Call C
 *     jab_data* encoded = generateJABCode(&params, (jab_byte*)nativeData);
 *     
 *     // Manual array creation
 *     jbyteArray result = (*env)->NewByteArray(env, encoded->length);
 *     (*env)->SetByteArrayRegion(env, result, 0, encoded->length, encoded->data);
 *     
 *     // Manual cleanup (easy to forget = memory leak!)
 *     (*env)->ReleaseStringUTFChars(env, data, nativeData);
 *     free(encoded);
 *     
 *     return result;
 * }
 * 
 * Problems:
 * - 500+ lines of boilerplate C++ code
 * - Manual memory management (leak-prone)
 * - JNI type conversions everywhere
 * - Runtime-only error checking
 * - Need C++ compiler for each platform
 * - Verbose and error-prone
 * 
 * 
 * Panama FFM (this file):
 * -----------------------
 * 
 * public byte[] encode(String message) {
 *     try (Arena arena = Arena.ofConfined()) {
 *         MemorySegment params = jab_encode_params.allocate(arena);
 *         jab_encode_params.color_number(params, 8);
 *         jab_encode_params.ecc_level(params, 5);
 *         
 *         MemorySegment messagePtr = arena.allocateFrom(message);
 *         MemorySegment result = generateJABCode(arena, params, messagePtr);
 *         
 *         int length = jab_data.length(result);
 *         return jab_data.data(result)
 *                        .reinterpret(length)
 *                        .toArray(ValueLayout.JAVA_BYTE);
 *     } // Auto cleanup
 * }
 * 
 * Benefits:
 * - Pure Java - no C++ wrapper
 * - ~100 lines of Java code
 * - Automatic memory management (arena)
 * - Compile-time type safety
 * - Generated bindings via jextract
 * - Cleaner, more maintainable
 * 
 * 
 * WHEN TO USE EACH:
 * =================
 * 
 * Use JNI (current) if:
 * - Android support needed ← MOST IMPORTANT
 * - Java 17 LTS required
 * - Already working
 * - Maximum compatibility
 * 
 * Use Panama if:
 * - Desktop/server only
 * - JDK 22+ acceptable
 * - Want pure Java
 * - Starting new project
 * - Maintainability priority
 */
