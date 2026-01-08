package com.jabcode.panama.mask;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataMaskingTest {

    @Test
    void maskPattern0() {
        // (x + y) % modulus
        assertEquals(0, DataMasking.maskAt(0, 0, 0, 4)); // (0+0)%4 = 0
        assertEquals(1, DataMasking.maskAt(1, 0, 0, 4)); // (1+0)%4 = 1
        assertEquals(1, DataMasking.maskAt(0, 1, 0, 4)); // (0+1)%4 = 1
        assertEquals(2, DataMasking.maskAt(1, 1, 0, 4)); // (1+1)%4 = 2
    }

    @Test
    void maskPattern1() {
        // x % modulus
        assertEquals(0, DataMasking.maskAt(0, 99, 1, 8)); // 0%8 = 0
        assertEquals(3, DataMasking.maskAt(3, 99, 1, 8)); // 3%8 = 3
        assertEquals(0, DataMasking.maskAt(8, 99, 1, 8)); // 8%8 = 0
    }

    @Test
    void maskPattern2() {
        // y % modulus
        assertEquals(0, DataMasking.maskAt(99, 0, 2, 8)); // 0%8 = 0
        assertEquals(3, DataMasking.maskAt(99, 3, 2, 8)); // 3%8 = 3
        assertEquals(0, DataMasking.maskAt(99, 8, 2, 8)); // 8%8 = 0
    }

    @Test
    void maskPattern3() {
        // (x/2 + y/3) % modulus
        assertEquals(0, DataMasking.maskAt(0, 0, 3, 4)); // (0+0)%4 = 0
        assertEquals(0, DataMasking.maskAt(1, 1, 3, 4)); // (0+0)%4 = 0
        assertEquals(2, DataMasking.maskAt(2, 3, 3, 4)); // (1+1)%4 = 2
    }

    @Test
    void allPatternsReturnValidRange() {
        int modulus = 8;
        for (int maskRef = 0; maskRef < 8; maskRef++) {
            for (int x = 0; x < 10; x++) {
                for (int y = 0; y < 10; y++) {
                    int mask = DataMasking.maskAt(x, y, maskRef, modulus);
                    assertTrue(mask >= 0 && mask < modulus,
                        String.format("mask(%d,%d,%d,%d) = %d out of range", x, y, maskRef, modulus, mask));
                }
            }
        }
    }

    @Test
    void invalidModulusThrows() {
        assertThrows(IllegalArgumentException.class, () -> DataMasking.maskAt(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> DataMasking.maskAt(0, 0, 0, -1));
    }
}
