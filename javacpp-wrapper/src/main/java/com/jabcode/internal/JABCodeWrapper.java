package com.jabcode.internal;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;

/**
 * JABCodeWrapper - Wrapper for the JABCode native library
 * This class provides a wrapper for the JABCode native library using the JABCodeNativeMapping class
 */
public class JABCodeWrapper {
    
    /**
     * Create a JABCode encoding context
     * @param colorNumber the number of colors to use
     * @param symbolNumber the number of symbols to use
     * @return the encoding context
     */
    public static JABCodeNativeMapping.jab_encode createEncode(int colorNumber, int symbolNumber) {
        return JABCodeNativeMapping.createEncode(colorNumber, symbolNumber);
    }
    
    /**
     * Destroy a JABCode encoding context
     * @param enc the encoding context to destroy
     */
    public static void destroyEncode(JABCodeNativeMapping.jab_encode enc) {
        JABCodeNativeMapping.destroyEncode(enc);
    }
    
    /**
     * Generate a JABCode
     * @param enc the encoding context
     * @param data the data to encode
     * @return the result code (JAB_SUCCESS or JAB_FAILURE)
     */
    public static int generateJABCode(JABCodeNativeMapping.jab_encode enc, JABCodeNativeMapping.jab_data data) {
        return JABCodeNativeMapping.generateJABCode(enc, data);
    }
    
    /**
     * Decode a JABCode
     * @param bitmap the bitmap containing the JABCode
     * @param mode the decoding mode (NORMAL_DECODE or COMPATIBLE_DECODE)
     * @param status the status code (output parameter)
     * @return the decoded data
     */
    public static JABCodeNativeMapping.jab_data decodeJABCode(JABCodeNativeMapping.jab_bitmap bitmap, int mode, IntPointer status) {
        return JABCodeNativeMapping.decodeJABCode(bitmap, mode, status);
    }
    
    /**
     * Decode a JABCode with extended information
     * @param bitmap the bitmap containing the JABCode
     * @param mode the decoding mode (NORMAL_DECODE or COMPATIBLE_DECODE)
     * @param status the status code (output parameter)
     * @param symbols the decoded symbols (output parameter)
     * @param maxSymbolNumber the maximum number of symbols to decode
     * @return the decoded data
     */
    public static JABCodeNativeMapping.jab_data decodeJABCodeEx(JABCodeNativeMapping.jab_bitmap bitmap, int mode, IntPointer status, JABCodeNativeMapping.jab_decoded_symbol symbols, int maxSymbolNumber) {
        return JABCodeNativeMapping.decodeJABCodeEx(bitmap, mode, status, symbols, maxSymbolNumber);
    }
    
    /**
     * Save a JABCode bitmap to a file
     * @param bitmap the bitmap to save
     * @param filename the filename to save to
     * @return true if successful, false otherwise
     */
    public static boolean saveImage(JABCodeNativeMapping.jab_bitmap bitmap, BytePointer filename) {
        return JABCodeNativeMapping.saveImage(bitmap, filename) != 0;
    }
    
    
    /**
     * Read an image from a file
     * @param filename the filename to read from
     * @return the bitmap
     */
    public static JABCodeNativeMapping.jab_bitmap readImage(BytePointer filename) {
        return JABCodeNativeMapping.readImage(filename);
    }
}
