package com.jabauth.jabcode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.random.Random

/**
 * Test implementation of JABCodeEncoder for unit testing
 *
 * Creates mock JABCode images without using native library.
 * Embeds data in bitmap metadata for later retrieval by TestJABCodeDecoderImpl.
 */
class TestJABCodeEncoderImpl : JABCodeEncoder {

    companion object {
        private const val MAX_CAPACITY_COLOR_2 = 1500  // bytes
        private const val MAX_CAPACITY_COLOR_4 = 2250  // bytes
        private const val MAX_CAPACITY_COLOR_8 = 3000  // bytes
        
        // Metadata keys for embedding data in bitmap
        const val META_DATA = "jabcode_data"
        const val META_COLOR_MODE = "jabcode_color_mode"
    }

    override fun encode(data: ByteArray, options: EncodeOptions): Bitmap {
        // Validate data size
        if (!canEncode(data.size, options)) {
            throw EncodeException("Data size ${data.size} exceeds capacity ${getMaxDataCapacity(options)}")
        }

        // Create bitmap with requested dimensions
        val bitmap = Bitmap.createBitmap(options.width, options.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // Draw colored background (simulates JABCode pattern)
        when (options.colorMode) {
            ColorMode.COLOR_2 -> drawTwoColorPattern(canvas, paint, options.width, options.height)
            ColorMode.COLOR_4 -> drawFourColorPattern(canvas, paint, options.width, options.height)
            ColorMode.COLOR_8 -> drawEightColorPattern(canvas, paint, options.width, options.height)
        }

        // Store metadata for test decoder
        bitmap.setHasAlpha(true)
        // Note: Bitmap doesn't support arbitrary metadata in Android
        // In a real implementation, data would be encoded in the pixel pattern
        // For tests, we'll use a companion object to store mappings
        TestDataStore.store(bitmap, data, options.colorMode)

        return bitmap
    }

    override fun getMaxDataCapacity(options: EncodeOptions): Int {
        return when (options.colorMode) {
            ColorMode.COLOR_2 -> MAX_CAPACITY_COLOR_2
            ColorMode.COLOR_4 -> MAX_CAPACITY_COLOR_4
            ColorMode.COLOR_8 -> MAX_CAPACITY_COLOR_8
        }
    }

    private fun drawTwoColorPattern(canvas: Canvas, paint: Paint, width: Int, height: Int) {
        // Black and white pattern
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        paint.color = Color.BLACK
        val moduleSize = 10
        for (y in 0 until height step moduleSize) {
            for (x in 0 until width step moduleSize) {
                if ((x / moduleSize + y / moduleSize) % 2 == 0) {
                    canvas.drawRect(
                        x.toFloat(), 
                        y.toFloat(), 
                        (x + moduleSize).toFloat(), 
                        (y + moduleSize).toFloat(), 
                        paint
                    )
                }
            }
        }
    }

    private fun drawFourColorPattern(canvas: Canvas, paint: Paint, width: Int, height: Int) {
        // 4-color pattern
        val colors = listOf(Color.BLACK, Color.GRAY, Color.LTGRAY, Color.WHITE)
        val moduleSize = 10
        
        for (y in 0 until height step moduleSize) {
            for (x in 0 until width step moduleSize) {
                paint.color = colors[(x / moduleSize + y / moduleSize) % 4]
                canvas.drawRect(
                    x.toFloat(), 
                    y.toFloat(), 
                    (x + moduleSize).toFloat(), 
                    (y + moduleSize).toFloat(), 
                    paint
                )
            }
        }
    }

    private fun drawEightColorPattern(canvas: Canvas, paint: Paint, width: Int, height: Int) {
        // 8-color pattern
        val colors = listOf(
            Color.BLACK, Color.RED, Color.GREEN, Color.BLUE,
            Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE
        )
        val moduleSize = 10
        
        for (y in 0 until height step moduleSize) {
            for (x in 0 until width step moduleSize) {
                paint.color = colors[(x / moduleSize + y / moduleSize) % 8]
                canvas.drawRect(
                    x.toFloat(), 
                    y.toFloat(), 
                    (x + moduleSize).toFloat(), 
                    (y + moduleSize).toFloat(), 
                    paint
                )
            }
        }
    }
}

/**
 * Companion store to associate bitmaps with their encoded data
 * 
 * In real implementation, data is encoded in pixel patterns.
 * For tests, we use this in-memory store.
 */
object TestDataStore {
    private val dataMap = mutableMapOf<Int, EncodedData>()
    
    data class EncodedData(
        val data: ByteArray,
        val colorMode: ColorMode
    )
    
    fun store(bitmap: Bitmap, data: ByteArray, colorMode: ColorMode) {
        dataMap[System.identityHashCode(bitmap)] = EncodedData(data, colorMode)
    }
    
    fun retrieve(bitmap: Bitmap): EncodedData? {
        return dataMap[System.identityHashCode(bitmap)]
    }
    
    fun clear() {
        dataMap.clear()
    }
}
