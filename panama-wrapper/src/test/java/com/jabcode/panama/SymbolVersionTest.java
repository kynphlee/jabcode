package com.jabcode.panama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SymbolVersion value class.
 */
@DisplayName("SymbolVersion")
class SymbolVersionTest {
    
    @Test
    @DisplayName("Should create valid square version")
    void testSquareVersion() {
        var version = new SymbolVersion(10);
        
        assertEquals(10, version.getX());
        assertEquals(10, version.getY());
        assertTrue(version.isSquare());
    }
    
    @Test
    @DisplayName("Should create valid rectangular version")
    void testRectangularVersion() {
        var version = new SymbolVersion(10, 8);
        
        assertEquals(10, version.getX());
        assertEquals(8, version.getY());
        assertFalse(version.isSquare());
    }
    
    @Test
    @DisplayName("Should calculate correct module dimensions")
    void testModuleDimensions() {
        var v1 = new SymbolVersion(1);
        assertEquals(21, v1.getModuleWidth());
        assertEquals(21, v1.getModuleHeight());
        assertEquals(441, v1.getTotalModules());
        
        var v10 = new SymbolVersion(10, 8);
        assertEquals(57, v10.getModuleWidth());   // 17 + 4*10
        assertEquals(49, v10.getModuleHeight());  // 17 + 4*8
        assertEquals(2793, v10.getTotalModules()); // 57 * 49
        
        var v32 = new SymbolVersion(32);
        assertEquals(145, v32.getModuleWidth());  // 17 + 4*32
        assertEquals(145, v32.getModuleHeight());
        assertEquals(21025, v32.getTotalModules());
    }
    
    @Test
    @DisplayName("Should reject version below minimum")
    void testVersionBelowMinimum() {
        assertThrows(IllegalArgumentException.class, () -> new SymbolVersion(0));
        assertThrows(IllegalArgumentException.class, () -> new SymbolVersion(-1));
        assertThrows(IllegalArgumentException.class, () -> new SymbolVersion(1, 0));
    }
    
    @Test
    @DisplayName("Should reject version above maximum")
    void testVersionAboveMaximum() {
        assertThrows(IllegalArgumentException.class, () -> new SymbolVersion(33));
        assertThrows(IllegalArgumentException.class, () -> new SymbolVersion(100));
        assertThrows(IllegalArgumentException.class, () -> new SymbolVersion(32, 33));
    }
    
    @Test
    @DisplayName("Should accept boundary versions")
    void testBoundaryVersions() {
        assertDoesNotThrow(() -> new SymbolVersion(1));
        assertDoesNotThrow(() -> new SymbolVersion(32));
        assertDoesNotThrow(() -> new SymbolVersion(1, 32));
        assertDoesNotThrow(() -> new SymbolVersion(32, 1));
    }
    
    @Test
    @DisplayName("Should implement equals correctly")
    void testEquals() {
        var v1 = new SymbolVersion(10, 8);
        var v2 = new SymbolVersion(10, 8);
        var v3 = new SymbolVersion(10, 10);
        
        assertEquals(v1, v2);
        assertNotEquals(v1, v3);
        assertEquals(v1, v1); // reflexive
        assertNotEquals(v1, null);
        assertNotEquals(v1, "not a version");
    }
    
    @Test
    @DisplayName("Should implement hashCode correctly")
    void testHashCode() {
        var v1 = new SymbolVersion(10, 8);
        var v2 = new SymbolVersion(10, 8);
        
        assertEquals(v1.hashCode(), v2.hashCode());
    }
    
    @Test
    @DisplayName("Should have meaningful toString")
    void testToString() {
        var version = new SymbolVersion(10, 8);
        String str = version.toString();
        
        assertTrue(str.contains("10"));
        assertTrue(str.contains("8"));
        assertTrue(str.contains("57")); // module width
        assertTrue(str.contains("49")); // module height
    }
    
    @Test
    @DisplayName("Should handle all valid version range")
    void testAllValidVersions() {
        for (int x = 1; x <= 32; x++) {
            for (int y = 1; y <= 32; y++) {
                var version = new SymbolVersion(x, y);
                assertEquals(x, version.getX());
                assertEquals(y, version.getY());
                assertEquals(17 + 4*x, version.getModuleWidth());
                assertEquals(17 + 4*y, version.getModuleHeight());
            }
        }
    }
}
