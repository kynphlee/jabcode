package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JABCodeEncoder using Panama FFM
 * 
 * Note: Tests are disabled until Panama bindings are generated.
 * Run: ./jextract.sh to generate bindings, then enable tests.
 */
class JABCodeEncoderTest {
    
    @Test
    void testConfigBuilder() {
        var config = JABCodeEncoder.Config.builder()
            .colorNumber(8)
            .eccLevel(5)
            .symbolNumber(1)
            .moduleSize(12)
            .build();
        
        assertEquals(8, config.getColorNumber());
        assertEquals(5, config.getEccLevel());
        assertEquals(1, config.getSymbolNumber());
        assertEquals(12, config.getModuleSize());
    }
    
    @Test
    void testDefaultConfig() {
        var config = JABCodeEncoder.Config.defaults();
        
        assertEquals(8, config.getColorNumber());
        assertEquals(5, config.getEccLevel());
        assertEquals(1, config.getSymbolNumber());
        assertEquals(12, config.getModuleSize());
    }
    
    @Test
    void testInvalidColorNumber() {
        assertThrows(IllegalArgumentException.class, () -> {
            JABCodeEncoder.Config.builder().colorNumber(3).build();
        });
    }
    
    @Test
    void testInvalidEccLevel() {
        assertThrows(IllegalArgumentException.class, () -> {
            JABCodeEncoder.Config.builder().eccLevel(11).build();
        });
    }
    
    @Test
    @Disabled("Requires generated Panama bindings")
    void testSimpleEncode() {
        var encoder = new JABCodeEncoder();
        byte[] result = encoder.encode("Hello JABCode!");
        
        assertNotNull(result);
        assertTrue(result.length > 0);
    }
    
    @Test
    @Disabled("Requires generated Panama bindings")
    void testEncodeWithParameters() {
        var encoder = new JABCodeEncoder();
        byte[] result = encoder.encode("Test Data", 8, 5);
        
        assertNotNull(result);
        assertTrue(result.length > 0);
    }
    
    @Test
    @Disabled("Requires generated Panama bindings")
    void testEncodeWithConfig() {
        var encoder = new JABCodeEncoder();
        var config = JABCodeEncoder.Config.builder()
            .colorNumber(4)
            .eccLevel(3)
            .moduleSize(10)
            .build();
        
        byte[] result = encoder.encodeWithConfig("Custom Config Test", config);
        
        assertNotNull(result);
        assertTrue(result.length > 0);
    }
    
    @Test
    void testNullData() {
        var encoder = new JABCodeEncoder();
        
        assertThrows(IllegalArgumentException.class, () -> {
            encoder.encode(null);
        });
    }
    
    @Test
    void testEmptyData() {
        var encoder = new JABCodeEncoder();
        
        assertThrows(IllegalArgumentException.class, () -> {
            encoder.encode("");
        });
    }
}
