package com.jabcode.panama.mask;

public final class DataMasking {
    private DataMasking() {}

    /**
     * Calculate mask value for position (x,y) given mask reference (0..7) and modulus (color count).
     * Patterns based on ISO/IEC 23634 Table 22.
     */
    public static int maskAt(int x, int y, int maskRef, int modulus) {
        if (modulus <= 0) throw new IllegalArgumentException("modulus>0");
        return switch (maskRef & 0x7) {
            case 0 -> (x + y) % modulus;
            case 1 -> x % modulus;
            case 2 -> y % modulus;
            case 3 -> ((x / 2) + (y / 3)) % modulus;
            case 4 -> ((x / 3) + (y / 2)) % modulus;
            case 5 -> (((x + y) / 2) + ((x + y) / 3)) % modulus;
            case 6 -> (((x * x * y) % 7) + ((2 * x * x + 2 * y) % 19)) % modulus;
            default -> (((x * y * y) % 5) + ((2 * x + y * y) % 13)) % modulus; // 7
        };
    }
}
