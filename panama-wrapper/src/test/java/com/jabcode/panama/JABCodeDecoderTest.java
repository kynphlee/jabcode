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
    
    // Phase 2: Arena-based API tests
    
    @Test
    void testDecodeWithObservationsRejectsNullPath() {
        assertThrows(IllegalArgumentException.class, () -> 
            decoder.decodeWithObservations(null, JABCodeDecoder.MODE_NORMAL, true)
        );
    }
    
    @Test
    void testDecodeWithObservationsRejectsNullPathWithoutObservations() {
        assertThrows(IllegalArgumentException.class, () -> 
            decoder.decodeWithObservations(null, JABCodeDecoder.MODE_NORMAL, false)
        );
    }
    
    @Test
    void testDecodedResultWithObservationsConstructor() {
        var result = new JABCodeDecoder.DecodedResultWithObservations(
            "test data", 1, true, null, 0
        );
        
        assertEquals("test data", result.getData());
        assertEquals(1, result.getSymbolCount());
        assertTrue(result.isSuccess());
        assertNull(result.getObservations());
        assertEquals(0, result.getObservationCount());
    }
    
    @Test
    void testDecodedResultWithObservationsFailure() {
        var result = new JABCodeDecoder.DecodedResultWithObservations(
            null, 0, false, null, 0
        );
        
        assertNull(result.getData());
        assertEquals(0, result.getSymbolCount());
        assertFalse(result.isSuccess());
        assertNull(result.getObservations());
        assertEquals(0, result.getObservationCount());
    }
    
    @Test
    void testDecodedResultWithObservationsInheritsDecodedResult() {
        var result = new JABCodeDecoder.DecodedResultWithObservations(
            "data", 2, true, null, 100
        );
        
        // Verify inheritance - can be used as DecodedResult
        JABCodeDecoder.DecodedResult baseResult = result;
        assertEquals("data", baseResult.getData());
        assertEquals(2, baseResult.getSymbolCount());
        assertTrue(baseResult.isSuccess());
    }
    
    @Test
    void testResetDecoderStateExists() {
        // Verify method exists (actual behavior tested in integration tests)
        // Cannot test execution here - requires native library loading
        assertDoesNotThrow(() -> {
            var method = JABCodeDecoder.class.getMethod("resetDecoderState");
            assertNotNull(method);
        });
    }
    
    @Test
    void testMaxObservationsConstantExists() {
        // Verify constant is accessible through reflection
        // This ensures the constant is properly defined
        assertDoesNotThrow(() -> {
            var field = JABCodeDecoder.class.getDeclaredField("MAX_OBSERVATIONS");
            field.setAccessible(true);
            int value = (int) field.get(null);
            assertEquals(10000, value, "MAX_OBSERVATIONS should be 10000");
        });
    }
    
    // Note: Tests for actual file decoding are in JABCodeDecoderIntegrationTest
    // as they require the native library to be loaded
}
