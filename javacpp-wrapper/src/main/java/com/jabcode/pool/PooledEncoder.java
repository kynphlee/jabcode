package com.jabcode.pool;

import com.jabcode.internal.JABCodeNativePtr;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * A pooled encoder that automatically returns to the pool when closed.
 * 
 * <p>Implements AutoCloseable for use with try-with-resources:</p>
 * <pre>
 * try (PooledEncoder encoder = pool.acquire(ColorMode.OCTAL, 1, 3)) {
 *     BufferedImage img = encoder.encode(data);
 * }
 * // Encoder automatically returned to pool
 * </pre>
 * 
 * <p><b>Thread Safety:</b> Not thread-safe. Each instance should only be used
 * by the thread that acquired it.</p>
 */
public class PooledEncoder implements AutoCloseable {
    
    private final EncoderPool pool;
    private final EncoderPool.CachedEncoder cached;
    private boolean closed = false;
    
    PooledEncoder(EncoderPool pool, EncoderPool.CachedEncoder cached) {
        this.pool = pool;
        this.cached = cached;
    }
    
    /**
     * Encode data to a BufferedImage.
     * 
     * @param data data to encode
     * @return encoded JABCode as BufferedImage
     * @throws IOException if encoding fails
     * @throws IllegalStateException if encoder has been closed
     */
    public BufferedImage encode(byte[] data) throws IOException {
        checkNotClosed();
        
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        
        long dataPtr = 0;
        try {
            // Create data pointer
            dataPtr = JABCodeNativePtr.createDataFromBytes(data);
            if (dataPtr == 0) {
                throw new IOException("Failed to allocate data");
            }
            
            // Generate code
            int status = JABCodeNativePtr.generateJABCodePtr(cached.encPtr, dataPtr);
            if (status != 0) {
                throw new IOException("Failed to generate JABCode (status=" + status + ")");
            }
            
            // Get bitmap
            long bmpPtr = JABCodeNativePtr.getBitmapFromEncodePtr(cached.encPtr);
            if (bmpPtr == 0) {
                throw new IOException("Failed to get bitmap");
            }
            
            // Convert to BufferedImage
            int[] argbData = JABCodeNativePtr.bitmapToARGB(bmpPtr);
            if (argbData == null || argbData.length < 2) {
                throw new IOException("Failed to convert bitmap to ARGB");
            }
            
            int width = argbData[0];
            int height = argbData[1];
            
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, argbData, 2, width);
            
            return image;
            
        } finally {
            // Cleanup data pointer
            if (dataPtr != 0) {
                try {
                    JABCodeNativePtr.destroyDataPtr(dataPtr);
                } catch (Throwable ignore) {}
            }
        }
    }
    
    /**
     * Encode string data to a BufferedImage.
     * 
     * @param data string data to encode
     * @return encoded JABCode as BufferedImage
     * @throws IOException if encoding fails
     * @throws IllegalStateException if encoder has been closed
     */
    public BufferedImage encode(String data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        return encode(data.getBytes());
    }
    
    /**
     * Get the native encoder pointer (for advanced use cases).
     * 
     * @return native encoder pointer
     * @throws IllegalStateException if encoder has been closed
     */
    public long getEncoderPtr() {
        checkNotClosed();
        return cached.encPtr;
    }
    
    /**
     * Return the encoder to the pool.
     * After calling close(), this encoder instance cannot be used again.
     */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            pool.release(cached);
        }
    }
    
    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Encoder has been closed and returned to pool");
        }
    }
}
