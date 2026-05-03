package com.jabauth.jabcode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for JABCodeDecoder interface
 *
 * Tests decoding JABCode IMAGE → DATA
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JABCodeDecoderTest {

    private lateinit var context: Context
    private lateinit var encoder: JABCodeEncoder
    private lateinit var decoder: JABCodeDecoder

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        encoder = TestJABCodeEncoderImpl()
        decoder = TestJABCodeDecoderImpl()
    }

    @Test
    fun `decode extracts original data from encoded image`() {
        val originalData = "Hello, JABCode!".toByteArray()
        val encoded = encoder.encode(originalData)
        
        val result = decoder.decode(encoded)
        
        assertNotNull(result)
        assertArrayEquals(originalData, result!!.data)
    }

    @Test
    fun `decode returns null for image without JABCode`() {
        val emptyBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        
        val result = decoder.decode(emptyBitmap)
        
        assertNull(result)
    }

    @Test
    fun `decode with scanRegion limits scan area`() {
        val originalData = "test".toByteArray()
        val encoded = encoder.encode(originalData)
        val options = DecodeOptions(scanRegion = Rect(0, 0, 256, 256))
        
        val result = decoder.decode(encoded, options)
        
        // Should still decode if JABCode is in region
        assertNotNull(result)
    }

    @Test
    fun `decode extracts correct color mode`() {
        val data = "test".toByteArray()
        val encodeOptions = EncodeOptions(colorMode = ColorMode.COLOR_8)
        val encoded = encoder.encode(data, encodeOptions)
        
        val result = decoder.decode(encoded)
        
        assertNotNull(result)
        assertEquals(ColorMode.COLOR_8, result!!.colorMode)
    }

    @Test
    fun `decode result contains position information`() {
        val data = "test".toByteArray()
        val encoded = encoder.encode(data)
        
        val result = decoder.decode(encoded)
        
        assertNotNull(result)
        assertNotNull(result!!.position)
        assertTrue(result.position.width() > 0)
        assertTrue(result.position.height() > 0)
    }

    @Test
    fun `decode result tracks decode time`() {
        val data = "test".toByteArray()
        val encoded = encoder.encode(data)
        
        val result = decoder.decode(encoded)
        
        assertNotNull(result)
        assertTrue(result!!.decodeTimeMs >= 0)
    }

    @Test
    fun `decode result asString converts to UTF-8`() {
        val originalText = "Hello, World!"
        val encoded = encoder.encodeString(originalText)
        
        val result = decoder.decode(encoded)
        
        assertNotNull(result)
        assertEquals(originalText, result!!.asString())
    }

    @Test
    fun `decode result contains substring search works`() {
        val text = "Hello, JABAuth!"
        val encoded = encoder.encodeString(text)
        
        val result = decoder.decode(encoded)
        
        assertNotNull(result)
        assertTrue(result!!.contains("JABAuth"))
        assertFalse(result.contains("notfound"))
    }

    @Test
    fun `decodeMultiple returns empty list for empty image`() {
        val emptyBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        
        val results = decoder.decodeMultiple(emptyBitmap)
        
        assertTrue(results.isEmpty())
    }

    @Test
    fun `decodeMultiple finds single JABCode`() {
        val data = "test".toByteArray()
        val encoded = encoder.encode(data)
        
        val results = decoder.decodeMultiple(encoded)
        
        assertEquals(1, results.size)
        assertArrayEquals(data, results[0].data)
    }

    @Test
    fun `containsJABCode returns true for encoded image`() {
        val data = "test".toByteArray()
        val encoded = encoder.encode(data)
        
        val result = decoder.containsJABCode(encoded)
        
        assertTrue(result)
    }

    @Test
    fun `containsJABCode returns false for empty image`() {
        val emptyBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        
        val result = decoder.containsJABCode(emptyBitmap)
        
        assertFalse(result)
    }

    @Test
    fun `decode handles timeout option`() {
        val data = "test".toByteArray()
        val encoded = encoder.encode(data)
        val options = DecodeOptions(timeout = 1000L)
        
        val result = decoder.decode(encoded, options)
        
        // Should complete within timeout
        assertNotNull(result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode options validates maxSymbols positive`() {
        DecodeOptions(maxSymbols = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode options validates timeout non-negative`() {
        DecodeOptions(timeout = -1L)
    }
}
