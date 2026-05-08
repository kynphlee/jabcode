package com.jabauth.jabcode.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Camera utility functions for JABCode integration
 * 
 * Provides conversion utilities for CameraX ImageProxy to Bitmap format
 * needed for JABCode decoding.
 * 
 * **Migrated from diagnostic app** (2026-05-07)
 * - Source: JABCodeAnalyzer.kt (correct YUV interleaving)
 * - Replaces: Incorrect implementation in CameraAnalyzer.kt
 */
object CameraUtils {
    
    /**
     * Convert CameraX ImageProxy to Bitmap
     * 
     * Supports YUV_420_888 format (standard CameraX output).
     * Handles proper UV plane interleaving for NV21 format.
     * 
     * @param image ImageProxy from CameraX analyzer
     * @return Bitmap or null if conversion fails
     * 
     * @throws IllegalArgumentException if image format is not YUV_420_888
     */
    fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return when (image.format) {
            ImageFormat.YUV_420_888 -> yuv420ToBitmap(image)
            else -> {
                throw IllegalArgumentException(
                    "Unsupported image format: ${image.format}. Only YUV_420_888 is supported."
                )
            }
        }
    }
    
    /**
     * Convert YUV_420_888 ImageProxy to Bitmap
     * 
     * **CRITICAL:** Uses proper UV interleaving for NV21 format.
     * Incorrect interleaving causes color shifts in decoded images.
     * 
     * Process:
     * 1. Extract Y, U, V planes from ImageProxy
     * 2. Build NV21 byte array with interleaved UV
     * 3. Create YuvImage from NV21 data
     * 4. Compress to JPEG (quality 100)
     * 5. Decode JPEG to Bitmap
     * 
     * @param image ImageProxy with YUV_420_888 format
     * @return Bitmap or null if conversion fails
     */
    private fun yuv420ToBitmap(image: ImageProxy): Bitmap? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)
        
        // CRITICAL: Proper UV interleaving for NV21
        // NV21 format requires V-U-V-U... interleaving (not sequential copy)
        var uvIndex = ySize
        for (i in 0 until uSize) {
            nv21[uvIndex++] = vBuffer.get(i)  // V first
            nv21[uvIndex++] = uBuffer.get(i)  // U second
        }
        
        // Convert NV21 to Bitmap via JPEG
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    
    /**
     * Convert ImageProxy to RGBA byte buffer
     * 
     * Useful for direct native library integration where Bitmap
     * overhead is not desired.
     * 
     * @param image ImageProxy from CameraX
     * @param region Optional region to extract (null = full image)
     * @return RGBA byte array (4 bytes per pixel)
     */
    fun imageProxyToRgbaBuffer(image: ImageProxy, region: Rect? = null): ByteArray? {
        val bitmap = imageProxyToBitmap(image) ?: return null
        return try {
            bitmapToRgbaBuffer(bitmap, region)
        } finally {
            bitmap.recycle()
        }
    }
    
    /**
     * Convert Bitmap to RGBA byte buffer
     * 
     * @param bitmap Source bitmap
     * @param region Optional region to extract (null = full image)
     * @return RGBA byte array (R, G, B, A per pixel)
     */
    fun bitmapToRgbaBuffer(bitmap: Bitmap, region: Rect? = null): ByteArray {
        val width = region?.width() ?: bitmap.width
        val height = region?.height() ?: bitmap.height
        val x = region?.left ?: 0
        val y = region?.top ?: 0
        
        val buffer = ByteArray(width * height * 4)
        val byteBuffer = ByteBuffer.wrap(buffer)
        
        // Extract pixels from region
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, x, y, width, height)
        
        // Convert ARGB to RGBA
        for (pixel in pixels) {
            byteBuffer.put(((pixel shr 16) and 0xFF).toByte()) // R
            byteBuffer.put(((pixel shr 8) and 0xFF).toByte())  // G
            byteBuffer.put((pixel and 0xFF).toByte())          // B
            byteBuffer.put(((pixel shr 24) and 0xFF).toByte()) // A
        }
        
        return buffer
    }
}
