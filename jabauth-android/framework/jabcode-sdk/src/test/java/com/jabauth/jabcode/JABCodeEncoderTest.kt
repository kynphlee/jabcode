package com.jabauth.jabcode

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for JABCodeEncoder interface
 *
 * Tests encoding DATA → JABCode IMAGE
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JABCodeEncoderTest {

    private lateinit var context: Context
    private lateinit var encoder: JABCodeEncoder

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        encoder = TestJABCodeEncoderImpl()
    }

    @Test
    fun `encode creates bitmap with correct dimensions`() {
        val data = "test data".toByteArray()
        val options = EncodeOptions(width = 512, height = 512)
        
        val bitmap = encoder.encode(data, options)
        
        assertNotNull(bitmap)
        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
    }

    @Test
    fun `encode handles small data`() {
        val data = "Hi".toByteArray()
        
        val bitmap = encoder.encode(data)
        
        assertNotNull(bitmap)
        assertTrue(bitmap.width > 0)
        assertTrue(bitmap.height > 0)
    }

    @Test
    fun `encode handles large data up to capacity`() {
        val maxCapacity = encoder.getMaxDataCapacity(EncodeOptions())
        val data = ByteArray(maxCapacity) { 'A'.code.toByte() }
        
        val bitmap = encoder.encode(data)
        
        assertNotNull(bitmap)
    }

    @Test(expected = EncodeException::class)
    fun `encode throws on data exceeding capacity`() {
        val maxCapacity = encoder.getMaxDataCapacity(EncodeOptions())
        val data = ByteArray(maxCapacity + 1) { 'A'.code.toByte() }
        
        encoder.encode(data)
    }

    @Test
    fun `encode supports COLOR_2 mode`() {
        val data = "test".toByteArray()
        val options = EncodeOptions(colorMode = ColorMode.COLOR_2)
        
        val bitmap = encoder.encode(data, options)
        
        assertNotNull(bitmap)
    }

    @Test
    fun `encode supports COLOR_4 mode`() {
        val data = "test".toByteArray()
        val options = EncodeOptions(colorMode = ColorMode.COLOR_4)
        
        val bitmap = encoder.encode(data, options)
        
        assertNotNull(bitmap)
    }

    @Test
    fun `encode supports COLOR_8 mode`() {
        val data = "test".toByteArray()
        val options = EncodeOptions(colorMode = ColorMode.COLOR_8)
        
        val bitmap = encoder.encode(data, options)
        
        assertNotNull(bitmap)
    }

    @Test
    fun `encodeString convenience method works`() {
        val text = "Hello, JABAuth!"
        
        val bitmap = encoder.encodeString(text)
        
        assertNotNull(bitmap)
    }

    @Test
    fun `getMaxDataCapacity returns positive value`() {
        val capacity = encoder.getMaxDataCapacity(EncodeOptions())
        
        assertTrue(capacity > 0)
    }

    @Test
    fun `getMaxDataCapacity increases with COLOR_8 vs COLOR_2`() {
        val capacity2 = encoder.getMaxDataCapacity(EncodeOptions(colorMode = ColorMode.COLOR_2))
        val capacity8 = encoder.getMaxDataCapacity(EncodeOptions(colorMode = ColorMode.COLOR_8))
        
        assertTrue(capacity8 > capacity2)
    }

    @Test
    fun `canEncode returns true for small data`() {
        val result = encoder.canEncode(100, EncodeOptions())
        
        assertTrue(result)
    }

    @Test
    fun `canEncode returns false for oversized data`() {
        val maxCapacity = encoder.getMaxDataCapacity(EncodeOptions())
        
        val result = encoder.canEncode(maxCapacity + 1, EncodeOptions())
        
        assertFalse(result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encode options validates width positive`() {
        EncodeOptions(width = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encode options validates height positive`() {
        EncodeOptions(height = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encode options validates error correction range`() {
        EncodeOptions(errorCorrectionLevel = 11)
    }
}
