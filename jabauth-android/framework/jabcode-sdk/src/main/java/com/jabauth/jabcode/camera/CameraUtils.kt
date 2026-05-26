package com.jabauth.jabcode.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Camera utility functions for JABCode integration
 * 
 * Provides conversion utilities for Camera2 Image to Bitmap format
 * needed for JABCode decoding.
 * 
 * Uses raw Android Camera2 API - no CameraX dependencies.
 */
object CameraUtils {

    /**
     * WS-5 YUV-stride diagnostic (one-shot guard).
     *
     * Logs the U/V plane stride parameters on the first frame to verify the
     * device's YUV_420_888 layout. Galaxy S25 reported pixelStride=2 on both
     * chroma planes (trace tolerance4-test-20260524_134450.logcat), confirming
     * semi-planar NV21/NV12. The stride-aware conversion below handles this
     * correctly; the diagnostic remains so future devices can be verified.
     * Greppable prefix: `YUV_STRIDE_DIAG`.
     */
    @Volatile
    private var stridesLogged: Boolean = false

    /**
     * Convert CameraX ImageProxy to Bitmap (COMPATIBILITY WRAPPER)
     * 
     * Apps using CameraX can use this wrapper. Internally uses Camera2 Image.
     * 
     * @param imageProxy ImageProxy from CameraX
     * @return Bitmap or null if conversion fails
     */
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return imageToBitmap(imageProxy.image ?: return null)
    }
    
    /**
     * Convert Camera2 Image to Bitmap
     * 
     * Supports YUV_420_888 format (standard Camera2 output).
     * Handles proper UV plane interleaving for NV21 format.
     * 
     * @param image Image from Camera2 ImageReader
     * @return Bitmap or null if conversion fails
     * 
     * @throws IllegalArgumentException if image format is not YUV_420_888
     */
    fun imageToBitmap(image: Image): Bitmap? {
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
     * Convert YUV_420_888 Image to Bitmap
     * 
     * **CRITICAL:** Uses proper UV interleaving for NV21 format.
     * Incorrect interleaving causes color shifts in decoded images.
     * 
     * Process:
     * 1. Extract Y, U, V planes from Image
     * 2. Build NV21 byte array with interleaved UV
     * 3. Create YuvImage from NV21 data
     * 4. Compress to JPEG (quality 100)
     * 5. Decode JPEG to Bitmap
     * 
     * @param image Image with YUV_420_888 format
     * @return Bitmap or null if conversion fails
     */
    private fun yuv420ToBitmap(image: Image): Bitmap? {
        // WS-5 stride diagnostic: log once per session so we can verify the
        // YUV layout on this device. Greppable prefix: YUV_STRIDE_DIAG.
        if (!stridesLogged) {
            val y = image.planes[0]
            val u = image.planes[1]
            val v = image.planes[2]
            android.util.Log.i(
                "CameraUtils",
                "YUV_STRIDE_DIAG image=${image.width}x${image.height} " +
                "Y(rowStride=${y.rowStride} pixelStride=${y.pixelStride} bufSize=${y.buffer.remaining()}) " +
                "U(rowStride=${u.rowStride} pixelStride=${u.pixelStride} bufSize=${u.buffer.remaining()}) " +
                "V(rowStride=${v.rowStride} pixelStride=${v.pixelStride} bufSize=${v.buffer.remaining()})"
            )
            stridesLogged = true
        }

        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        // NV21 layout: full-resolution Y plane followed by quarter-resolution
        // V-U-interleaved chroma. Total bytes = width*height + width*height/2.
        val nv21 = ByteArray(width * height * 3 / 2)

        // ===== Y plane =====
        // Fast path when tightly packed; row-by-row otherwise to skip padding.
        var dstIdx = 0
        if (yRowStride == width) {
            yBuffer.position(0)
            yBuffer.get(nv21, 0, width * height)
            dstIdx = width * height
        } else {
            for (row in 0 until height) {
                for (col in 0 until width) {
                    nv21[dstIdx++] = yBuffer.get(row * yRowStride + col)
                }
            }
        }

        // ===== V-U interleaved chroma (NV21 ordering) =====
        // CRITICAL FIX: respect pixelStride and rowStride. The previous loop
        // treated the U/V buffers as flat byte arrays, which only works for
        // I420 (planar, pixelStride=1). On Galaxy S25 - and most modern
        // Android cameras - YUV_420_888 is delivered semi-planar (NV21/NV12)
        // with pixelStride=2 and overlapping U/V buffers, so byte-by-byte
        // copying scrambled the chroma into near-grayscale noise (RGB channels
        // within ~5-20 units of each other across all sampled pixels - see
        // WS-5 trace tolerance4-test-20260524_134450.logcat for the
        // diagnostic that confirmed this).
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vSrcIdx = row * vRowStride + col * vPixelStride
                val uSrcIdx = row * uRowStride + col * uPixelStride
                nv21[dstIdx++] = vBuffer.get(vSrcIdx)  // V first (NV21 convention)
                nv21[dstIdx++] = uBuffer.get(uSrcIdx)  // U second
            }
        }

        // ===== NV21 -> Bitmap via JPEG (Android's well-tested pipeline) =====
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    
    /**
     * Convert Image to RGBA byte buffer
     * 
     * Useful for direct native library integration where Bitmap
     * overhead is not desired.
     * 
     * @param image Image from Camera2
     * @param region Optional region to extract (null = full image)
     * @return RGBA byte array (4 bytes per pixel)
     */
    fun imageToRgbaBuffer(image: Image, region: Rect? = null): ByteArray? {
        val bitmap = imageToBitmap(image) ?: return null
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
