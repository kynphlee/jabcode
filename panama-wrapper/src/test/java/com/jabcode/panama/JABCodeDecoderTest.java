package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JABCodeDecoder
 */
class JABCodeDecoderTest {
    
    private final JABCodeDecoder decoder = new JABCodeDecoder();
    
    @Test
    void testDecodeModesConstants() {
        assertEquals(0, JABCodeDecoder.MODE_NORMAL);
        assertEquals(1, JABCodeDecoder.MODE_FAST);
    }
    
    @Test
    void testDecodeRejectsNullPath() {
        assertThrows(IllegalArgumentException.class, () -> 
            decoder.decodeFromFile(null)
        );
    }
    
    @Test
    void testDecodeFromFileWithModeRejectsNullPath() {
        assertThrows(IllegalArgumentException.class, () -> 
            decoder.decodeFromFile(null, JABCodeDecoder.MODE_NORMAL)
        );
    }
    
    @Test
    void testDecodeFromFileExRejectsNullPath() {
        assertThrows(IllegalArgumentException.class, () -> 
            decoder.decodeFromFileEx(null, JABCodeDecoder.MODE_NORMAL)
        );
    }
    
    @Test
    void testDecodeRejectsNullByteArray() {
        assertThrows(IllegalArgumentException.class, () -> 
            decoder.decode(null)
        );
    }
    
    @Test
    void testDecodeRejectsEmptyByteArray() {
        assertThrows(IllegalArgumentException.class, () -> 
            decoder.decode(new byte[0])
        );
    }
    
    @Test
    void testDecodeExRejectsNullByteArray() {
        assertThrows(IllegalArgumentException.class, () -> 
            decoder.decodeEx(null)
        );
    }
    
    @Test
    void testDecodeExRejectsEmptyByteArray() {
        assertThrows(IllegalArgumentException.class, () -> 
            decoder.decodeEx(new byte[0])
        );
    }
    
    @Test
    void testDecodeByteArrayThrowsUnsupported() {
        byte[] dummyData = new byte[]{1, 2, 3};
        assertThrows(UnsupportedOperationException.class, () -> 
            decoder.decode(dummyData)
        );
    }
    
    @Test
    void testDecodeExByteArrayThrowsUnsupported() {
        byte[] dummyData = new byte[]{1, 2, 3};
        assertThrows(UnsupportedOperationException.class, () -> 
            decoder.decodeEx(dummyData)
        );
    }
    
    @Test
    void testDecodeResultConstructorAndGetters() {
        var result = new JABCodeDecoder.DecodedResult("test data", 2, true);
        
        assertEquals("test data", result.getData());
        assertEquals(2, result.getSymbolCount());
        assertTrue(result.isSuccess());
    }
    
    @Test
    void testDecodeResultWithFailure() {
        var result = new JABCodeDecoder.DecodedResult(null, 0, false);
        
        assertNull(result.getData());
        assertEquals(0, result.getSymbolCount());
        assertFalse(result.isSuccess());
    }
    
    // Note: Tests for actual file decoding are in JABCodeDecoderIntegrationTest
    // as they require the native library to be loaded
}
